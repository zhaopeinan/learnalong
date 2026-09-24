package com.example.asr.ui.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.audio.AudioRecorder
import com.example.asr.data.local.entity.ChatMessageEntity
import com.example.asr.data.local.entity.ChatRole
import com.example.asr.data.local.entity.ChatSessionEntity
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.repository.ChatRepository
import com.example.asr.data.repository.ChildRepository
import com.example.asr.data.settings.SettingsStore
import com.example.asr.domain.ChatText
import com.example.asr.media.TtsPlayer
import com.example.asr.media.TtsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** 输入方式（与小程序 inputMode 一致） */
enum class ChatInputMode { VOICE, TEXT }

/** 按住说话手势态：原地松开发送 / 左滑取消 / 右滑转文字 */
enum class TalkState { SEND, CANCEL, TEXT }

/** AI 工作流阶段（气泡占位提示语据此切换） */
enum class BusyStage { LOOK, THINK, SPEAK }

data class ChatUiState(
    /** 初始化完成（会话已就绪或在选孩子） */
    val ready: Boolean = false,
    /** 致命错误（无孩子 / 薄弱点已删除等）：Snackbar 提示后退出 */
    val fatalError: String? = null,
    val sessionId: Long = 0,
    val title: String = "问老师",
    val messages: List<ChatMessageEntity> = emptyList(),
    /** AI 工作流进行中（气泡占位） */
    val busy: Boolean = false,
    val busyStage: BusyStage = BusyStage.THINK,
    /** 等待时轮换的趣味小贴士 */
    val busyTip: String = "",
    /** 语音转写中 */
    val transcribing: Boolean = false,
    val inputMode: ChatInputMode = ChatInputMode.VOICE,
    val textInput: String = "",
    /** 待发送照片暂存区（输入框上方，可加可删） */
    val pendingImages: List<File> = emptyList(),
    val recording: Boolean = false,
    val recordSec: Int = 0,
    val talkState: TalkState = TalkState.SEND,
    /** 正在播放的 assistant 消息 id（-1 无） */
    val playingMessageId: Long = -1,
    /** AI 语音播报开关（关闭时隐藏气泡上的 🔊 重播，纯文字回复） */
    val voiceEnabled: Boolean = true,
    /** 选孩子弹层（未带 childId 且有多个孩子时） */
    val showChildPicker: Boolean = false,
    val children: List<ChildEntity> = emptyList(),
    /** 历史会话弹层 */
    val showHistory: Boolean = false,
    val sessions: List<ChatSessionEntity> = emptyList(),
    val error: String? = null,
) {
    /** 有可发送内容（文字或暂存照片） */
    val canSend: Boolean
        get() = textInput.isNotBlank() || pendingImages.isNotEmpty()
}

/**
 * 问老师（苏格拉底辅导对话，对应小程序 chat 页 + tutorChat.ts 编排）：
 * 语音按住说话（原地松开发送 / 左滑取消 / 右滑转文字）+ 文字输入 + 拍照提问 + TTS 播报。
 */
class ChatViewModel(
    private val app: Application,
    private val chatRepository: ChatRepository,
    private val childRepository: ChildRepository,
    private val settingsStore: SettingsStore,
    private val argMode: String,
    private val argChildId: Long?,
    private val argRefId: Long?,
) : ViewModel() {

    /** 当前会话的创建参数（「开始新对话」复用） */
    private data class SessionArgs(val mode: String, val childId: Long, val refId: Long?)

    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui

    private var sessionArgs: SessionArgs? = null
    private var messagesJob: Job? = null
    private var busyTipJob: Job? = null
    private var busyTipIndex = 0
    private var recordTickJob: Job? = null
    private var recordStartAt = 0L

    private val audioRecorder = AudioRecorder(app)
    private val ttsPlayer = TtsPlayer(onStateChanged = ::onTtsState)
    private var currentPlayingId = -1L
    /** 已因播放失败重取过一次 URL 的消息（24h 过期只重试一次） */
    private val retriedAudioIds = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _ui.update { it.copy(voiceEnabled = s.chatVoiceEnabled) }
            }
        }
        viewModelScope.launch { initSession() }
    }

    // ---------- 会话建立 ----------

    /** 解析孩子上下文（与小程序 chat 页 onLoad 一致）：带 childId 直接用；否则单孩子自动选，多孩子弹选择 */
    private suspend fun initSession() {
        val childId = argChildId?.takeIf { it > 0 } ?: run {
            val children = childRepository.children.first()
            when (children.size) {
                0 -> {
                    _ui.update { it.copy(ready = true, fatalError = "请先在孩子管理添加孩子") }
                    return
                }
                1 -> children[0].id
                else -> {
                    _ui.update {
                        it.copy(ready = true, showChildPicker = true, children = children)
                    }
                    return
                }
            }
        }
        startSession(childId, argMode, argRefId, forceNew = false)
    }

    /** 按模式找/建会话；新建时让 AI 先开口（固定开场白，语音开启时用孩子档案的音色播报） */
    private suspend fun startSession(childId: Long, mode: String, refId: Long?, forceNew: Boolean) {
        sessionArgs = SessionArgs(mode, childId, refId)
        val opened = try {
            if (forceNew) chatRepository.newSession(childId, mode, refId)
            else chatRepository.openSession(childId, mode, refId)
        } catch (e: Exception) {
            _ui.update { it.copy(ready = true, fatalError = e.message ?: "会话创建失败") }
            return
        }
        val session = opened.session
        _ui.update {
            it.copy(
                ready = true,
                showChildPicker = false,
                sessionId = session.id,
                title = session.title,
                pendingImages = emptyList(),
                playingMessageId = -1,
            )
        }
        observeMessages(session.id)
        if (opened.created) {
            val greeting = chatRepository.addOpeningGreeting(session.id)
            // 直接读设置（init 时 settings collect 可能尚未首发）
            if (settingsStore.settings.first().chatVoiceEnabled) {
                startBusy(BusyStage.SPEAK)
                speak(greeting)
                stopBusy()
            }
        }
    }

    fun pickChild(childId: Long) {
        viewModelScope.launch { startSession(childId, argMode, argRefId, forceNew = false) }
    }

    private fun observeMessages(sessionId: Long) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.observeMessages(sessionId).collect { list ->
                _ui.update { it.copy(messages = list) }
            }
        }
    }

    private suspend fun refreshTitle() {
        chatRepository.getSession(_ui.value.sessionId)?.let { s ->
            _ui.update { it.copy(title = s.title) }
        }
    }

    // ---------- 等待状态（阶段提示 + 趣味小贴士轮换，每 3 秒一条） ----------

    private fun startBusy(stage: BusyStage) {
        busyTipJob?.cancel()
        busyTipIndex = 0
        _ui.update { it.copy(busy = true, busyStage = stage, busyTip = BUSY_TIPS[0]) }
        busyTipJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                busyTipIndex = (busyTipIndex + 1) % BUSY_TIPS.size
                _ui.update { it.copy(busyTip = BUSY_TIPS[busyTipIndex]) }
            }
        }
    }

    private fun stopBusy() {
        busyTipJob?.cancel()
        _ui.update { it.copy(busy = false, busyTip = "") }
    }

    /** 回复就绪收尾：开启语音则进入「准备开口」阶段并播报（TTS 失败静默降级），关闭则直接结束等待态 */
    private suspend fun finishReply(reply: ChatMessageEntity) {
        if (!_ui.value.voiceEnabled) {
            stopBusy()
            return
        }
        _ui.update { it.copy(busyStage = BusyStage.SPEAK) }
        speak(reply)
        stopBusy()
    }

    // ---------- 消息收发 ----------

    fun setTextInput(value: String) = _ui.update { it.copy(textInput = value) }

    fun toggleInputMode() = _ui.update {
        it.copy(inputMode = if (it.inputMode == ChatInputMode.VOICE) ChatInputMode.TEXT else ChatInputMode.VOICE)
    }

    /** 发送：有暂存照片时图文一起发（文字可空），否则纯文字 */
    fun submitInput() {
        val s = _ui.value
        if (s.pendingImages.isNotEmpty()) submitImages(s.pendingImages, s.textInput.trim())
        else submitText(s.textInput)
    }

    fun submitText(text: String) {
        val content = text.trim()
        val sessionId = _ui.value.sessionId
        if (content.isEmpty() || _ui.value.busy || sessionId == 0L) return
        _ui.update { it.copy(textInput = "") }
        viewModelScope.launch {
            startBusy(BusyStage.THINK)
            try {
                val reply = chatRepository.sendUserMessage(sessionId, content)
                refreshTitle()
                finishReply(reply)
            } catch (e: Exception) {
                stopBusy()
                showError(e.message ?: "发送失败，请重试")
            }
        }
    }

    /** 照片提问：暂存区照片 + 可选问题一起发出（VLM 描述 → LLM 引导 → TTS） */
    private fun submitImages(files: List<File>, question: String) {
        val sessionId = _ui.value.sessionId
        if (files.isEmpty() || _ui.value.busy || sessionId == 0L) return
        _ui.update { it.copy(textInput = "", pendingImages = emptyList()) }
        viewModelScope.launch {
            startBusy(BusyStage.LOOK)
            try {
                val reply = chatRepository.sendImageMessage(sessionId, files, question) { stage ->
                    _ui.update {
                        it.copy(
                            busyStage = when (stage) {
                                com.example.asr.data.repository.ChatImageStage.LOOK -> BusyStage.LOOK
                                com.example.asr.data.repository.ChatImageStage.THINK -> BusyStage.THINK
                            },
                        )
                    }
                }
                refreshTitle()
                finishReply(reply)
            } catch (e: Exception) {
                stopBusy()
                showError(e.message ?: "发送失败，请重试")
            }
        }
    }

    // ---------- 照片暂存区 ----------

    /** 拍照/相册选图进入暂存区（可继续加、可删除），最多 MAX_CHAT_IMAGES 张 */
    fun addPendingImages(files: List<File>) {
        if (files.isEmpty()) return
        val current = _ui.value.pendingImages
        val remain = ChatText.MAX_CHAT_IMAGES - current.size
        if (remain <= 0) {
            showError("最多 ${ChatText.MAX_CHAT_IMAGES} 张照片")
            return
        }
        if (files.size > remain) showError("最多 ${ChatText.MAX_CHAT_IMAGES} 张照片")
        // 有暂存照片时切到打字模式，方便孩子补一句问题再发（对齐小程序）
        _ui.update {
            it.copy(
                pendingImages = current + files.take(remain),
                inputMode = ChatInputMode.TEXT,
            )
        }
    }

    fun removePendingImage(index: Int) = _ui.update {
        it.copy(pendingImages = it.pendingImages.filterIndexed { i, _ -> i != index })
    }

    // ---------- 语音输入（按住说话） ----------

    fun onTalkStart() {
        val s = _ui.value
        if (s.busy || s.transcribing || s.recording || s.sessionId == 0L) return
        val dir = File(app.cacheDir, "chat_voice").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        try {
            audioRecorder.start(file)
        } catch (e: Exception) {
            showError("录音失败，请检查麦克风权限")
            return
        }
        recordStartAt = System.currentTimeMillis()
        _ui.update { it.copy(recording = true, recordSec = 0, talkState = TalkState.SEND) }
        recordTickJob?.cancel()
        recordTickJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _ui.update { it.copy(recordSec = it.recordSec + 1) }
            }
        }
    }

    fun setTalkState(state: TalkState) {
        if (_ui.value.recording && _ui.value.talkState != state) {
            _ui.update { it.copy(talkState = state) }
        }
    }

    fun onTalkEnd() {
        if (!_ui.value.recording) return
        recordTickJob?.cancel()
        val talkState = _ui.value.talkState
        _ui.update { it.copy(recording = false, talkState = TalkState.SEND) }
        val tooShort = System.currentTimeMillis() - recordStartAt < 500
        val file = audioRecorder.stop()
        when {
            talkState == TalkState.CANCEL -> {
                file?.delete()
                showError("已取消")
            }
            tooShort -> {
                file?.delete()
                showError("说话时间太短")
            }
            file == null -> showError("录音失败")
            talkState == TalkState.TEXT -> transcribeToInput(file)
            else -> transcribeAndSend(file)
        }
    }

    /** 转写后直接发送 */
    private fun transcribeAndSend(file: File) {
        viewModelScope.launch {
            _ui.update { it.copy(transcribing = true) }
            try {
                val text = chatRepository.transcribeVoice(file)
                _ui.update { it.copy(transcribing = false) }
                submitText(text)
            } catch (e: Exception) {
                _ui.update { it.copy(transcribing = false) }
                showError(e.message ?: "没有听清，请再说一次")
            } finally {
                file.delete()
            }
        }
    }

    /** 转写后填入输入框（可改错别字再手动发送） */
    private fun transcribeToInput(file: File) {
        viewModelScope.launch {
            _ui.update { it.copy(transcribing = true) }
            try {
                val text = chatRepository.transcribeVoice(file)
                _ui.update {
                    it.copy(
                        transcribing = false,
                        inputMode = ChatInputMode.TEXT,
                        textInput = text,
                    )
                }
                showError("已转成文字，可修改后发送")
            } catch (e: Exception) {
                _ui.update { it.copy(transcribing = false) }
                showError(e.message ?: "没有听清，请再说一次")
            } finally {
                file.delete()
            }
        }
    }

    // ---------- TTS 播报 ----------

    /** AI 回复转语音并播放（用孩子档案里配置的辅导声音）；TTS 失败静默降级为只显示文字 */
    private suspend fun speak(message: ChatMessageEntity, forceRefresh: Boolean = false) {
        if (!_ui.value.voiceEnabled) return
        try {
            val url = chatRepository.ensureAudioUrl(message.id, forceRefresh)
            currentPlayingId = message.id
            _ui.update { it.copy(playingMessageId = message.id) }
            ttsPlayer.play(url)
        } catch (e: Exception) {
            currentPlayingId = -1
            _ui.update { it.copy(playingMessageId = -1) }
        }
    }

    /** 气泡上的 🔊/⏸：播放中再点停止，否则播放 */
    fun onReplay(message: ChatMessageEntity) {
        if (!_ui.value.voiceEnabled || message.role != ChatRole.ASSISTANT) return
        if (_ui.value.playingMessageId == message.id) {
            stopPlayback()
            return
        }
        viewModelScope.launch { speak(message) }
    }

    fun stopPlayback() {
        currentPlayingId = -1
        ttsPlayer.stop()
        _ui.update { it.copy(playingMessageId = -1) }
    }

    private fun onTtsState(state: TtsState) {
        when (state) {
            TtsState.COMPLETED, TtsState.IDLE -> {
                currentPlayingId = -1
                _ui.update { it.copy(playingMessageId = -1) }
            }
            TtsState.ERROR -> {
                val id = currentPlayingId
                // URL 24h 有效，过期导致播放失败时重新合成一次再播（对齐小程序重取 t2a_v2）
                if (id > 0 && retriedAudioIds.add(id)) {
                    val message = _ui.value.messages.firstOrNull { it.id == id }
                    if (message != null) {
                        viewModelScope.launch { speak(message, forceRefresh = true) }
                        return
                    }
                }
                currentPlayingId = -1
                _ui.update { it.copy(playingMessageId = -1) }
            }
            else -> {}
        }
    }

    // ---------- 历史会话 / 新对话 ----------

    fun openHistory() {
        val args = sessionArgs ?: return
        viewModelScope.launch {
            val sessions = chatRepository.observeSessions(args.childId).first()
            _ui.update { it.copy(showHistory = true, sessions = sessions) }
        }
    }

    fun closeHistory() = _ui.update { it.copy(showHistory = false) }

    fun pickSession(session: ChatSessionEntity) {
        stopPlayback()
        stopBusy()
        sessionArgs = SessionArgs(session.mode, session.childId, session.refId)
        _ui.update {
            it.copy(
                showHistory = false,
                sessionId = session.id,
                title = session.title,
                pendingImages = emptyList(),
            )
        }
        observeMessages(session.id)
    }

    fun newChat() {
        val args = sessionArgs ?: return
        if (_ui.value.busy) return
        stopPlayback()
        _ui.update { it.copy(showHistory = false) }
        viewModelScope.launch { startSession(args.childId, args.mode, args.refId, forceNew = true) }
    }

    // ---------- 错误提示 ----------

    private fun showError(message: String) = _ui.update { it.copy(error = message) }

    fun consumeError() = _ui.update { it.copy(error = null) }

    override fun onCleared() {
        busyTipJob?.cancel()
        recordTickJob?.cancel()
        ttsPlayer.release()
        if (audioRecorder.isRecording) audioRecorder.stop()?.delete()
    }

    companion object {
        /** 等待时轮换给孩子的趣味小贴士（与小程序 BUSY_TIPS 一致） */
        val BUSY_TIPS = listOf(
            "好问题值得多想一会儿",
            "你可以先猜猜老师会问你什么",
            "转动小脑筋，答案马上来",
            "认真思考的孩子最棒啦",
            "老师马上就回来，别走开哦",
        )
    }
}
