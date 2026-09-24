package com.example.asr.data.repository

import com.example.asr.data.local.dao.ChatDao
import com.example.asr.data.local.dao.ChildDao
import com.example.asr.data.local.dao.WeakPointDao
import com.example.asr.data.local.entity.ChatMessageEntity
import com.example.asr.data.local.entity.ChatMode
import com.example.asr.data.local.entity.ChatRole
import com.example.asr.data.local.entity.ChatSessionEntity
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.remote.DebugLog
import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.remote.TaskContentParser
import com.example.asr.data.remote.VerboseJsonTranscriptParser
import com.example.asr.data.remote.dto.ChatMessage
import com.example.asr.data.remote.dto.ChatRequest
import com.example.asr.data.remote.dto.VisionChatMessage
import com.example.asr.data.remote.dto.VisionChatRequest
import com.example.asr.data.remote.dto.VisionContentPart
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.SettingsStore
import com.example.asr.domain.ChatText
import com.example.asr.domain.SocraticPrompt
import com.example.asr.media.PhotoImporter
import com.example.asr.media.SpeechSynthesizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** 图片消息的等待阶段（页面据此切换提示语，对应小程序 ImageStage） */
enum class ChatImageStage { LOOK, THINK }

/**
 * 苏格拉底辅导对话编排（对应小程序 tutorChat.ts）：
 * 会话管理（上限 30、上下文取最近 12 条）+ 语音转写 + LLM 多轮对话 + TTS 播报。
 * 与小程序差异：会话存 Room；进入时按 (childId, mode, refId) 复用最近会话（小程序每次新建）。
 */
class ChatRepository(
    private val chatDao: ChatDao,
    private val childDao: ChildDao,
    private val weakPointDao: WeakPointDao,
    private val settingsStore: SettingsStore,
    private val speechSynthesizer: SpeechSynthesizer,
    private val debugLog: DebugLog? = null,
) {

    /** 打开会话的结果：created=true 表示新建（调用方补固定开场白） */
    data class OpenedSession(val session: ChatSessionEntity, val created: Boolean)

    fun observeMessages(sessionId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessages(sessionId)

    fun observeSessions(childId: Long?): Flow<List<ChatSessionEntity>> =
        chatDao.observeSessions(childId)

    suspend fun getSession(id: Long): ChatSessionEntity? = chatDao.getSession(id)

    /** 按 (childId, mode, refId) 找最近会话复用；没有则新建 */
    suspend fun openSession(childId: Long, mode: String, refId: Long?): OpenedSession {
        chatDao.findSession(childId, mode, refId)?.let { return OpenedSession(it, created = false) }
        return OpenedSession(createSession(childId, mode, refId), created = true)
    }

    /** 「开始新对话」：强制新建一个同参数会话 */
    suspend fun newSession(childId: Long, mode: String, refId: Long?): OpenedSession =
        OpenedSession(createSession(childId, mode, refId), created = true)

    /** 创建会话并定型 systemPrompt（含孩子姓名/年级/辅导上下文，多轮对话始终携带） */
    private suspend fun createSession(childId: Long, mode: String, refId: Long?): ChatSessionEntity {
        val child = childDao.getById(childId) ?: throw IllegalArgumentException("孩子不存在")
        val (systemPrompt, title) = buildSessionContext(child, mode, refId, strict = true)
        val now = System.currentTimeMillis()
        val id = chatDao.insertSession(
            ChatSessionEntity(
                childId = childId,
                mode = mode,
                refId = refId,
                title = title,
                systemPrompt = systemPrompt,
                createdAt = now,
                updatedAt = now,
            )
        )
        chatDao.trimSessions(MAX_SESSIONS)
        return chatDao.getSession(id) ?: throw IllegalStateException("会话创建失败")
    }

    /**
     * 按模式推导辅导上下文并拼出 system prompt，返回 (systemPrompt, 会话标题)。
     * strict=true 时薄弱点不存在直接抛错（新建会话入口）；false 时退化为自由提问（历史会话兜底）。
     */
    private suspend fun buildSessionContext(
        child: ChildEntity,
        mode: String,
        refId: Long?,
        strict: Boolean,
    ): Pair<String, String> {
        var weakPoint: SocraticPrompt.WeakPointCtx? = null
        var exercise: SocraticPrompt.ExerciseCtx? = null
        val wp = if (mode == ChatMode.WEAKPOINT || mode == ChatMode.EXERCISE) {
            weakPointDao.getById(refId ?: 0)
        } else null
        if (wp == null && strict && (mode == ChatMode.WEAKPOINT || mode == ChatMode.EXERCISE)) {
            throw IllegalArgumentException("薄弱点不存在或已删除")
        }
        if (wp != null) {
            if (mode == ChatMode.WEAKPOINT) {
                weakPoint = SocraticPrompt.WeakPointCtx(wp.knowledgePoint, wp.description, wp.subject)
            } else {
                // exercise 模式：题目上下文来自该薄弱点缓存的练习内容（同小程序 wp.exerciseCache）
                val questions = wp.exerciseCache
                    ?.let { TaskContentParser.parse(it) }
                    ?.exercises?.map { it.question }
                    .orEmpty()
                if (questions.isNotEmpty()) {
                    exercise = SocraticPrompt.ExerciseCtx(questions, wp.subject)
                }
            }
        }
        val systemPrompt = SocraticPrompt.build(
            SocraticPrompt.Context(
                childName = child.name,
                grade = child.grade,
                weakPoint = weakPoint,
                exercise = exercise,
            )
        )
        return systemPrompt to (weakPoint?.knowledgePoint ?: "自由提问")
    }

    /** 固定开场白（不调大模型，秒出）：按会话模式给孩子一句招呼，写入会话并返回 */
    suspend fun addOpeningGreeting(sessionId: Long): ChatMessageEntity {
        val session = chatDao.getSession(sessionId) ?: throw IllegalArgumentException("会话不存在")
        val name = childDao.getById(session.childId)?.name ?: "小朋友"
        val text = ChatText.openingGreeting(session.mode, name)
        val now = System.currentTimeMillis()
        val id = chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ChatRole.ASSISTANT,
                text = text,
                createdAt = now,
            )
        )
        chatDao.touchSession(sessionId, now)
        return chatDao.getMessage(id)!!
    }

    /**
     * 发送孩子发言，返回 AI 的引导回复消息。
     * 多轮上下文 = system + 最近 CONTEXT_MESSAGES 条消息；失败时撤回孩子消息（避免重发时重复）。
     */
    suspend fun sendUserMessage(sessionId: Long, text: String): ChatMessageEntity {
        val session = chatDao.getSession(sessionId) ?: throw IllegalArgumentException("会话不存在")
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在「设置」里配置 API Key" }

        val now = System.currentTimeMillis()
        val userMsgId = chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ChatRole.USER,
                text = text,
                createdAt = now,
            )
        )
        maybeAutoTitle(session, text)
        try {
            val reply = requestReply(session)
            val id = chatDao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = ChatRole.ASSISTANT,
                    text = reply,
                    createdAt = System.currentTimeMillis(),
                )
            )
            chatDao.touchSession(sessionId, System.currentTimeMillis())
            return chatDao.getMessage(id)!!
        } catch (e: Exception) {
            chatDao.deleteMessage(userMsgId)
            throw e
        }
    }

    /**
     * 发送照片提问：VLM 先把照片转述成文字（LOOK 阶段），再由分析模型以苏格拉底方式回应（THINK 阶段）。
     * 消息里的 contextText 携带照片描述，后续多轮对话无需重复发图。
     */
    suspend fun sendImageMessage(
        sessionId: Long,
        images: List<File>,
        question: String,
        onStage: (ChatImageStage) -> Unit,
    ): ChatMessageEntity {
        val session = chatDao.getSession(sessionId) ?: throw IllegalArgumentException("会话不存在")
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在「设置」里配置 API Key" }
        val paths = images.take(ChatText.MAX_CHAT_IMAGES)
        require(paths.isNotEmpty()) { "请先选择照片" }

        onStage(ChatImageStage.LOOK)
        val parts = mutableListOf(
            VisionContentPart.text(SocraticPrompt.buildPhotoDescribePrompt(question, paths.size))
        )
        paths.forEach { parts.add(VisionContentPart.image(PhotoImporter.toDataUrl(it))) }
        val description = try {
            NetworkClient.api(settings.baseUrl).visionChatCompletions(
                authorization = "Bearer ${settings.apiKey}",
                request = VisionChatRequest(
                    model = settings.vlmModel,
                    messages = listOf(
                        VisionChatMessage(
                            "system",
                            listOf(VisionContentPart.text(SocraticPrompt.PHOTO_DESCRIBE_SYSTEM)),
                        ),
                        VisionChatMessage("user", parts),
                    ),
                ),
            ).text.trim()
        } catch (e: Exception) {
            debugLog?.record(
                action = "照片识别",
                model = settings.vlmModel,
                prompt = "[system]\n${SocraticPrompt.PHOTO_DESCRIBE_SYSTEM}\n\n[user]\n" +
                    SocraticPrompt.buildPhotoDescribePrompt(question, paths.size) +
                    "\n" + paths.joinToString("\n") { "[image] ${it.name}" },
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }
        if (description.isEmpty()) throw IllegalStateException("老师没看清照片，请再拍清楚一点")

        val q = question.trim()
        val contextText = "孩子发来 ${paths.size} 张照片。照片内容：$description" +
            (if (q.isNotEmpty()) "\n孩子的问题：$q" else "")
        val userMsgId = chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ChatRole.USER,
                text = q,
                imagePaths = encodeImagePaths(paths.map { it.absolutePath }),
                contextText = contextText,
                createdAt = System.currentTimeMillis(),
            )
        )
        maybeAutoTitle(session, q.ifEmpty { "看图提问" })

        onStage(ChatImageStage.THINK)
        try {
            val reply = requestReply(session)
            val id = chatDao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = ChatRole.ASSISTANT,
                    text = reply,
                    createdAt = System.currentTimeMillis(),
                )
            )
            chatDao.touchSession(sessionId, System.currentTimeMillis())
            return chatDao.getMessage(id)!!
        } catch (e: Exception) {
            chatDao.deleteMessage(userMsgId)
            throw e
        }
    }

    /** 孩子语音 → 文字（复用 ASR，单人不分离，直接拼接各段文本） */
    suspend fun transcribeVoice(file: File): String {
        val settings = settingsStore.settings.first()
        require(settings.apiKey.isNotBlank()) { "请先在「设置」里配置 API Key" }
        try {
            val filePart = MultipartBody.Part.createFormData(
                "file", file.name, file.asRequestBody("audio/m4a".toMediaType())
            )
            val raw = NetworkClient.api(settings.baseUrl).transcribe(
                authorization = "Bearer ${settings.apiKey}",
                file = filePart,
                model = settings.asrModel.toRequestBody("text/plain".toMediaType()),
                responseFormat = "verbose_json".toRequestBody("text/plain".toMediaType()),
            ).string()
            val text = VerboseJsonTranscriptParser().parse(raw)
                .joinToString("") { it.text }
                .trim()
            if (text.isEmpty()) throw IllegalStateException("没有听清，请靠近一点再说一次")
            return text
        } catch (e: Exception) {
            debugLog?.record(
                action = "语音转写",
                model = settings.asrModel,
                prompt = "POST audio/transcriptions（multipart）\n文件：${file.name}\n路径：${file.absolutePath}\nresponse_format：verbose_json",
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }
    }

    /**
     * AI 回复 → 语音（24h 有效 URL；TTS 失败时抛错，页面降级为只显示文字）。
     * 音色优先级由 SpeechSynthesizer 处理（孩子 voiceId → 全局默认 → 预置音色）。
     * forceRefresh 用于 URL 过期导致播放失败后的重取。
     */
    suspend fun ensureAudioUrl(messageId: Long, forceRefresh: Boolean = false): String {
        val message = chatDao.getMessage(messageId) ?: throw IllegalArgumentException("消息不存在")
        if (!forceRefresh) {
            message.audioUrl?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val voiceId = chatDao.getSession(message.sessionId)
            ?.let { childDao.getById(it.childId)?.voiceId }
        val speechText = ChatText.cleanReplyText(message.text).ifBlank { message.text }
        val url = speechSynthesizer.synthesize(speechText, voiceId)
        chatDao.updateMessageAudioUrl(messageId, url)
        return url
    }

    /** 多轮上下文 = system + 最近 CONTEXT_MESSAGES 条消息 → LLM，回复清洗后返回 */
    private suspend fun requestReply(session: ChatSessionEntity): String {
        val settings = settingsStore.settings.first()
        val system = session.systemPrompt ?: run {
            val child = childDao.getById(session.childId) ?: throw IllegalArgumentException("孩子不存在")
            buildSessionContext(child, session.mode, session.refId, strict = false).first
        }
        val history = chatDao.getRecentMessages(session.id, CONTEXT_MESSAGES).reversed()
        val userText = ChatText.buildHistoryText(history)
        val raw = try {
            NetworkClient.api(settings.baseUrl).chatCompletions(
                authorization = "Bearer ${settings.apiKey}",
                request = ChatRequest(
                    model = settings.llmModel,
                    messages = listOf(
                        ChatMessage(role = "system", content = system),
                        ChatMessage(role = "user", content = userText),
                    ),
                ),
            ).text.trim()
        } catch (e: Exception) {
            debugLog?.record(
                action = "辅导对话",
                model = settings.llmModel,
                prompt = "[system]\n$system\n\n[user]\n$userText",
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }
        val cleaned = ChatText.cleanReplyText(raw)
        if (cleaned.isEmpty()) throw IllegalStateException("老师暂时没有回应，请再试一次")
        return cleaned
    }

    /** 自由提问的会话标题用第一条提问自动生成 */
    private suspend fun maybeAutoTitle(session: ChatSessionEntity, text: String) {
        val now = System.currentTimeMillis()
        if (session.mode == ChatMode.FREE && chatDao.countUserMessages(session.id) == 1) {
            chatDao.touchSession(session.id, ChatText.autoTitle(text), now)
        } else {
            chatDao.touchSession(session.id, now)
        }
    }

    companion object {
        /** 会话上限（对应小程序 MAX_SESSIONS） */
        const val MAX_SESSIONS = 30

        /** 发给 LLM 的最近消息条数上限（对应小程序 CONTEXT_MESSAGES），控制 token */
        const val CONTEXT_MESSAGES = 12

        private val json = Json { ignoreUnknownKeys = true }

        fun encodeImagePaths(paths: List<String>): String = json.encodeToString(paths)

        /** 解析消息里的照片路径 JSON；解析失败返回空列表 */
        fun decodeImagePaths(raw: String?): List<String> =
            raw?.let {
                try {
                    json.decodeFromString<List<String>>(it)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
    }
}
