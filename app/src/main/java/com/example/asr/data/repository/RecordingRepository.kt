package com.example.asr.data.repository

import com.example.asr.audio.AudioSplitter
import com.example.asr.audio.RecordingService
import com.example.asr.data.local.dao.RecordingDao
import com.example.asr.data.local.dao.TranscriptDao
import com.example.asr.data.local.entity.RecordingEntity
import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.RecordingWithChild
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.remote.ParsedSegment
import com.example.asr.data.remote.ProgressRequestBody
import com.example.asr.data.remote.TranscriptParser
import com.example.asr.data.remote.VerboseJsonTranscriptParser
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class RecordingRepository(
    private val recordingDao: RecordingDao,
    private val transcriptDao: TranscriptDao,
    private val settingsStore: SettingsStore,
    private val transcriptParser: TranscriptParser = VerboseJsonTranscriptParser(),
) {

    fun observeRecordings(childId: Long?, subject: String?): Flow<List<RecordingWithChild>> =
        recordingDao.observeWithChild(childId, subject)

    fun observeSubjects(): Flow<List<String>> = recordingDao.observeSubjects()

    fun observeSegments(recordingId: Long): Flow<List<TranscriptSegmentEntity>> =
        transcriptDao.observeByRecording(recordingId)

    suspend fun getRecording(recordingId: Long): RecordingEntity? = recordingDao.getById(recordingId)

    suspend fun saveRecording(
        childId: Long,
        subject: String,
        file: File,
        durationSec: Int,
        segmentFiles: List<File>? = null,
    ): Long =
        recordingDao.insert(
            RecordingEntity(
                childId = childId,
                subject = subject.trim(),
                filePath = file.absolutePath,
                // 多段录音：记录全部分段文件（首段与 filePath 相同）
                segments = segmentFiles?.takeIf { it.size > 1 }
                    ?.let { encodeSegments(it.map { f -> f.absolutePath }) },
                durationSec = durationSec,
                createdAt = System.currentTimeMillis(),
                status = RecordingStatus.RECORDED,
            )
        )

    /** 纯照片记录（拍错题）：无音频文件，filePath 置空 */
    suspend fun savePhotoRecord(childId: Long, subject: String): Long =
        recordingDao.insert(
            RecordingEntity(
                childId = childId,
                subject = subject.trim(),
                filePath = "",
                durationSec = 0,
                createdAt = System.currentTimeMillis(),
                status = RecordingStatus.RECORDED,
            )
        )

    suspend fun deleteRecording(recordingId: Long) {
        recordingDao.getById(recordingId)?.let {
            if (it.filePath.isNotBlank()) File(it.filePath).delete()
            decodeSegments(it.segments)?.forEach { path -> File(path).delete() }
        }
        recordingDao.deleteById(recordingId)
    }

    /** 把某录音中同一 speakerLabel 的所有段统一标注角色 */
    suspend fun assignSpeakerRole(recordingId: Long, speakerLabel: String, role: String) =
        transcriptDao.updateRoleForSpeaker(recordingId, speakerLabel, role)

    /**
     * 上传 SiliconFlow 转写并入库，返回解析到的段数；onProgress 回调上传进度（0-1），
     * 上传完成后回调 null 表示进入识别阶段；异常时状态置为 FAILED 并继续抛出。
     *
     * 长录音（分段或单文件 >25MB）对齐小程序 transcribeRecording：
     * 逐段/逐块上传，各块带时间偏移（第 i 段偏移 i * 600s，段内切块按切块自身偏移），
     * 合并 transcript segments（说话人标签跨块独立，与小程序一致：直接拼接由用户标注角色）。
     */
    suspend fun transcribe(recordingId: Long, onProgress: ((Float?) -> Unit)? = null): Int {
        val recording = recordingDao.getById(recordingId)
            ?: throw IllegalArgumentException("录音不存在")
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }

        val segments = decodeSegments(recording.segments)
        val files = if (segments != null && segments.size > 1) segments else listOf(recording.filePath)
        files.forEach { require(File(it).exists()) { "录音文件不存在" } }

        recordingDao.updateStatus(recordingId, RecordingStatus.TRANSCRIBING)
        val tempChunks = mutableListOf<File>()
        try {
            val api = NetworkClient.api(settings.baseUrl)
            // 切块规划也放在 try 内：规划异常同样落 FAILED 状态
            val plans = files.map { AudioSplitter.planAsrSplit(File(it)) }
            val totalChunks = plans.sumOf { it.chunks.size }
            val parsed = mutableListOf<ParsedSegment>()
            var done = 0
            for (i in files.indices) {
                val baseOffset = i * RecordingService.SEGMENT_DURATION_SEC.toFloat()
                for (j in plans[i].chunks.indices) {
                    var uploadFile = File(files[i])
                    if (plans[i].needsSplit) {
                        uploadFile = plans[i].materialize(j)
                        tempChunks.add(uploadFile)
                    }
                    val chunkIndex = done
                    try {
                        val fileBody = uploadFile.asRequestBody(mimeFor(uploadFile).toMediaType())
                        val filePart = MultipartBody.Part.createFormData(
                            "file", uploadFile.name,
                            if (onProgress != null) ProgressRequestBody(fileBody) { p ->
                                onProgress((chunkIndex + p) / totalChunks)
                            } else fileBody,
                        )
                        val modelBody = settings.asrModel.toRequestBody("text/plain".toMediaType())
                        val formatBody = "verbose_json".toRequestBody("text/plain".toMediaType())
                        val response = api.transcribe(
                            authorization = "Bearer ${settings.apiKey}",
                            file = filePart,
                            model = modelBody,
                            responseFormat = formatBody,
                        )
                        val part = transcriptParser.parse(response.string())
                        val offset = baseOffset + plans[i].chunks[j].offsetSec.toFloat()
                        part.forEach { s ->
                            parsed.add(s.copy(startSec = s.startSec + offset, endSec = s.endSec + offset))
                        }
                    } finally {
                        // 上传完立即删临时块，避免占用存储
                        if (plans[i].needsSplit) AudioSplitter.deleteChunkFile(uploadFile)
                    }
                    done++
                }
            }
            onProgress?.invoke(null) // 上传完毕，进入识别阶段
            require(parsed.isNotEmpty()) { "转写结果为空" }

            transcriptDao.deleteByRecording(recordingId)
            transcriptDao.insertAll(parsed.map {
                TranscriptSegmentEntity(
                    recordingId = recordingId,
                    speakerLabel = it.speakerLabel,
                    startSec = it.startSec,
                    endSec = it.endSec,
                    text = it.text,
                )
            })
            recordingDao.updateStatus(recordingId, RecordingStatus.TRANSCRIBED)
            recordingDao.updateTranscribedAt(recordingId, System.currentTimeMillis())
            return parsed.size
        } catch (e: Exception) {
            recordingDao.updateStatus(recordingId, RecordingStatus.FAILED)
            throw Exception(e.toUserMessage())
        } finally {
            tempChunks.forEach { AudioSplitter.deleteChunkFile(it) }
        }
    }

    private fun mimeFor(file: File) = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        else -> "audio/m4a"
    }

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun encodeSegments(paths: List<String>): String = json.encodeToString(paths)

        /** 解析 segments JSON；解析失败返回 null（按单文件处理） */
        fun decodeSegments(raw: String?): List<String>? =
            raw?.let {
                try { json.decodeFromString<List<String>>(it) } catch (e: Exception) { null }
            }
    }
}
