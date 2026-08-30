package com.example.asr.data.repository

import com.example.asr.data.local.dao.RecordingDao
import com.example.asr.data.local.dao.TranscriptDao
import com.example.asr.data.local.entity.RecordingEntity
import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.RecordingWithChild
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.remote.ProgressRequestBody
import com.example.asr.data.remote.TranscriptParser
import com.example.asr.data.remote.VerboseJsonTranscriptParser
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    suspend fun saveRecording(childId: Long, subject: String, file: File, durationSec: Int): Long =
        recordingDao.insert(
            RecordingEntity(
                childId = childId,
                subject = subject.trim(),
                filePath = file.absolutePath,
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
        }
        recordingDao.deleteById(recordingId)
    }

    /** 把某录音中同一 speakerLabel 的所有段统一标注角色 */
    suspend fun assignSpeakerRole(recordingId: Long, speakerLabel: String, role: String) =
        transcriptDao.updateRoleForSpeaker(recordingId, speakerLabel, role)

    /** 上传 SiliconFlow 转写并入库，返回解析到的段数；onProgress 回调上传进度（0-1），上传完成后回调 null 表示进入识别阶段；异常时状态置为 FAILED 并继续抛出 */
    suspend fun transcribe(recordingId: Long, onProgress: ((Float?) -> Unit)? = null): Int {
        val recording = recordingDao.getById(recordingId)
            ?: throw IllegalArgumentException("录音不存在")
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }

        recordingDao.updateStatus(recordingId, RecordingStatus.TRANSCRIBING)
        try {
            val api = NetworkClient.api(settings.baseUrl)
            val file = File(recording.filePath)
            require(file.exists()) { "录音文件不存在" }

            val fileBody = file.asRequestBody("audio/m4a".toMediaType())
            val filePart = MultipartBody.Part.createFormData(
                "file", file.name,
                if (onProgress != null) ProgressRequestBody(fileBody) { onProgress(it) } else fileBody,
            )
            val modelBody = settings.asrModel.toRequestBody("text/plain".toMediaType())
            val formatBody = "verbose_json".toRequestBody("text/plain".toMediaType())

            val response = api.transcribe(
                authorization = "Bearer ${settings.apiKey}",
                file = filePart,
                model = modelBody,
                responseFormat = formatBody,
            )
            onProgress?.invoke(null) // 上传完毕，进入识别阶段
            val raw = response.string()
            val parsed = transcriptParser.parse(raw)
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
        }
    }
}
