package com.example.asr.data.repository

import com.example.asr.audio.AudioSplitter
import com.example.asr.audio.RecordingService
import com.example.asr.data.local.dao.WorkRecordingDao
import com.example.asr.data.local.dao.WorkTodoDao
import com.example.asr.data.local.entity.WorkRecordingEntity
import com.example.asr.data.local.entity.WorkScenario
import com.example.asr.data.local.entity.WorkStatus
import com.example.asr.data.local.entity.WorkTodoEntity
import com.example.asr.data.local.entity.WorkTodoWithRecording
import com.example.asr.data.remote.DebugLog
import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.remote.ProgressRequestBody
import com.example.asr.data.remote.SiliconFlowApi
import com.example.asr.data.remote.TranscriptParser
import com.example.asr.data.remote.VerboseJsonTranscriptParser
import com.example.asr.data.remote.WorkAnalysisParser
import com.example.asr.data.remote.WorkAnalysisResult
import com.example.asr.data.remote.dto.ChatMessage
import com.example.asr.data.remote.dto.ChatRequest
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.settings.SettingsStore
import com.example.asr.domain.WorkPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工作端业务层（对应小程序 utils/work.ts）：
 * 会议/工作谈话/通话录音 → 分段/切块上传转写 → AI 纪要 + 待办提取。
 * 复用设置里的 ASR/LLM 配置；音频文件与辅导录音同目录存储（recordings/）。
 */
class WorkRepository(
    private val workRecordingDao: WorkRecordingDao,
    private val workTodoDao: WorkTodoDao,
    private val settingsStore: SettingsStore,
    private val debugLog: DebugLog? = null,
    private val transcriptParser: TranscriptParser = VerboseJsonTranscriptParser(),
) {

    fun observeRecordings(): Flow<List<WorkRecordingEntity>> = workRecordingDao.observeAll()

    fun observeRecording(recordingId: Long): Flow<WorkRecordingEntity?> =
        workRecordingDao.observeById(recordingId)

    /** recordingId 为 null 时查全部待办（工作端首页清单），否则查单条记录的待办 */
    fun observeTodos(recordingId: Long? = null): Flow<List<WorkTodoWithRecording>> =
        workTodoDao.observeWithRecording(recordingId)

    suspend fun getRecording(recordingId: Long): WorkRecordingEntity? =
        workRecordingDao.getById(recordingId)

    /** 保存工作录音（标题自动生成：场景 + 日期时间，分析后会改成 AI 生成的一句话标题） */
    suspend fun saveRecording(
        scenario: String,
        file: File,
        durationSec: Int,
        segmentFiles: List<File>? = null,
    ): Long {
        val title = "${WorkPrompt.scenarioLabel(scenario)} " +
            SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).format(Date())
        return workRecordingDao.insert(
            WorkRecordingEntity(
                title = title,
                scenario = scenario,
                filePath = file.absolutePath,
                segments = segmentFiles?.takeIf { it.size > 1 }
                    ?.let { RecordingRepository.encodeSegments(it.map { f -> f.absolutePath }) },
                durationSec = durationSec,
                createdAt = System.currentTimeMillis(),
                status = WorkStatus.RECORDED,
            )
        )
    }

    /** 删除工作录音（含音频文件；关联待办由外键级联删除） */
    suspend fun deleteRecording(recordingId: Long) {
        workRecordingDao.getById(recordingId)?.let {
            if (it.filePath.isNotBlank()) File(it.filePath).delete()
            RecordingRepository.decodeSegments(it.segments)?.forEach { path -> File(path).delete() }
        }
        workRecordingDao.deleteById(recordingId)
    }

    suspend fun deleteTodo(todoId: Long) = workTodoDao.deleteById(todoId)

    suspend fun toggleTodo(todoId: Long, done: Boolean) = workTodoDao.updateDone(todoId, done)

    /**
     * 转写：逐段（>10 分钟自动续录产生）上传并合并，超 25MB 的文件先本地切块（AudioSplitter）。
     * 结果存为带时间戳和说话人标签的全文文本：[mm:ss] 说话人1：……
     * onProgress 回调上传进度（0-1），上传完成后回调 null 表示进入识别阶段；
     * 异常时状态置为 FAILED 并继续抛出。
     */
    suspend fun transcribe(recordingId: Long, onProgress: ((Float?) -> Unit)? = null): Int {
        val recording = workRecordingDao.getById(recordingId)
            ?: throw IllegalArgumentException("录音不存在")
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }

        val segments = RecordingRepository.decodeSegments(recording.segments)
        val files = if (segments != null && segments.size > 1) segments else listOf(recording.filePath)
        files.forEach { require(File(it).exists()) { "录音文件不存在" } }

        workRecordingDao.updateStatus(recordingId, WorkStatus.TRANSCRIBING)
        val tempChunks = mutableListOf<File>()
        try {
            val api = NetworkClient.api(settings.baseUrl)
            // 切块规划放在 try 内：规划阶段异常同样落 FAILED 状态并进调试日志
            val plans = files.map { AudioSplitter.planAsrSplit(File(it)) }
            val totalChunks = plans.sumOf { it.chunks.size }
            val lines = mutableListOf<String>()
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
                            lines.add("[${formatTimestamp((s.startSec + offset).toInt())}] ${s.speakerLabel}：${s.text}")
                        }
                    } finally {
                        // 上传完立即删临时块，避免占用存储
                        if (plans[i].needsSplit) AudioSplitter.deleteChunkFile(uploadFile)
                    }
                    done++
                }
            }
            onProgress?.invoke(null) // 上传完毕，进入识别阶段
            require(lines.isNotEmpty()) { "转写结果为空" }

            workRecordingDao.update(
                recording.copy(
                    status = WorkStatus.TRANSCRIBED,
                    transcriptText = lines.joinToString("\n"),
                    transcribedAt = System.currentTimeMillis(),
                )
            )
            return lines.size
        } catch (e: Exception) {
            workRecordingDao.updateStatus(recordingId, WorkStatus.FAILED)
            debugLog?.record(
                action = "语音转写（工作端）",
                model = settings.asrModel,
                prompt = "文件：${files.joinToString("、") { File(it).name }}\n路径：\n${files.joinToString("\n")}",
                error = e.stackTraceToString().take(4000),
            )
            throw Exception(e.toUserMessage())
        } finally {
            tempChunks.forEach { AudioSplitter.deleteChunkFile(it) }
        }
    }

    /**
     * 分析：转写稿 → 纪要 + 待办（按场景区分提示词）。
     * 长文稿先分段提取再合并；待办入库（全量替换旧待办）。
     * onChunk 回调分段进度（第几段/共几段）。
     */
    suspend fun analyze(
        recordingId: Long,
        onChunk: ((index: Int, total: Int) -> Unit)? = null,
    ): WorkAnalysisResult {
        val recording = workRecordingDao.getById(recordingId)
            ?: throw IllegalArgumentException("录音不存在")
        val transcript = recording.transcriptText
            ?: throw IllegalArgumentException("请先完成转写")
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 SiliconFlow API Key" }
        val meta = WorkPrompt.SCENARIO_META[recording.scenario]
            ?: WorkPrompt.SCENARIO_META.getValue(WorkScenario.MEETING)

        // 小程序按 settings.llmMaxTokensK（默认 5）估算字符预算；Android 设置项未暴露，沿用默认值
        val budget = maxOf(1000, LLM_MAX_TOKENS_K * 1000)
        val chunks = chunkTranscriptByChars(transcript, budget)

        workRecordingDao.updateStatus(recordingId, WorkStatus.ANALYZING)
        try {
            val api = NetworkClient.api(settings.baseUrl)
            val auth = "Bearer ${settings.apiKey}"
            var result: WorkAnalysisResult? = null
            if (chunks.size == 1) {
                val raw = chat(
                    api, auth, settings,
                    WorkPrompt.buildAnalysisPrompt(meta.label, meta.focus, chunks[0], false),
                    "工作录音分析",
                )
                result = WorkAnalysisParser.parse(raw)
            } else {
                val partials = mutableListOf<WorkAnalysisResult>()
                for (i in chunks.indices) {
                    onChunk?.invoke(i + 1, chunks.size)
                    val raw = chat(
                        api, auth, settings,
                        WorkPrompt.buildAnalysisPrompt(meta.label, meta.focus, chunks[i], true),
                        "工作录音分析(第${i + 1}/${chunks.size}段)",
                    )
                    WorkAnalysisParser.parse(raw)?.let { partials.add(it) }
                }
                if (partials.isEmpty()) throw IllegalStateException("分析结果解析失败")
                val mergeRaw = chat(
                    api, auth, settings,
                    WorkPrompt.buildMergePrompt(
                        meta.label,
                        partials.map { WorkPrompt.WorkPartial(it.summary, it.todos.map { t -> t.text }) },
                    ),
                    "工作纪要合并",
                )
                result = WorkAnalysisParser.parse(mergeRaw)
                // 合并失败兜底：直接拼接分段结果
                if (result == null) {
                    result = WorkAnalysisResult(
                        title = "",
                        summary = partials.joinToString("\n\n") { it.summary },
                        todos = partials.flatMap { it.todos },
                    )
                }
            }
            if (result == null || (result.summary.isEmpty() && result.todos.isEmpty())) {
                throw IllegalStateException("分析结果解析失败")
            }

            // 待办全量替换（重新分析时清掉旧的）
            workTodoDao.deleteByRecording(recordingId)
            val now = System.currentTimeMillis()
            workTodoDao.insertAll(
                result.todos.map {
                    WorkTodoEntity(
                        workRecordingId = recordingId,
                        text = it.text,
                        assignee = it.assignee,
                        deadline = it.deadline,
                        done = false,
                        createdAt = now,
                    )
                }
            )

            workRecordingDao.update(
                recording.copy(
                    status = WorkStatus.ANALYZED,
                    summary = result.summary,
                    title = result.title.ifEmpty { recording.title },
                    analyzedAt = now,
                )
            )
            return result
        } catch (e: Exception) {
            workRecordingDao.updateStatus(recordingId, WorkStatus.FAILED)
            throw Exception(e.toUserMessage())
        }
    }

    /** 单次 LLM 调用：失败记调试日志后原样抛出（由外层统一置 FAILED） */
    private suspend fun chat(
        api: SiliconFlowApi,
        auth: String,
        settings: AppSettings,
        userPrompt: String,
        action: String,
    ): String = try {
        api.chatCompletions(
            authorization = auth,
            request = ChatRequest(
                model = settings.llmModel,
                messages = listOf(
                    ChatMessage(role = "system", content = WorkPrompt.SYSTEM),
                    ChatMessage(role = "user", content = userPrompt),
                ),
            ),
        ).text
    } catch (e: Exception) {
        debugLog?.record(
            action = action,
            model = settings.llmModel,
            prompt = "[system]\n${WorkPrompt.SYSTEM}\n\n[user]\n$userPrompt",
            error = e.toUserMessage(),
        )
        throw e
    }

    private fun mimeFor(file: File) = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        else -> "audio/m4a"
    }

    companion object {
        /** 分析字符预算（小程序 settings.llmMaxTokensK 默认值 5；1 字符 ≈ 1 token 简单估算） */
        private const val LLM_MAX_TOKENS_K = 5

        /** 转写行内时间戳：[mm:ss]（对齐小程序 toDurationString） */
        fun formatTimestamp(totalSec: Int): String {
            val m = totalSec / 60
            val s = totalSec % 60
            return "%02d:%02d".format(m, s)
        }

        /** 长文稿按字符预算切分（按行边界，不切半句）；对齐小程序 chunkTranscriptByChars */
        internal fun chunkTranscriptByChars(text: String, maxChars: Int): List<String> {
            val lines = text.split("\n")
            val chunks = mutableListOf<String>()
            val current = mutableListOf<String>()
            var size = 0
            for (line in lines) {
                val len = line.length + 1
                if (current.isNotEmpty() && size + len > maxChars) {
                    chunks.add(current.joinToString("\n"))
                    current.clear()
                    size = 0
                }
                current.add(line)
                size += len
            }
            if (current.isNotEmpty()) chunks.add(current.joinToString("\n"))
            return chunks
        }
    }
}
