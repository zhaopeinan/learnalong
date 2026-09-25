package com.example.asr.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.AsrApplication
import com.example.asr.audio.AudioRecorder
import com.example.asr.audio.TestAudio
import com.example.asr.data.remote.DebugEntry
import com.example.asr.data.remote.DebugLog
import com.example.asr.data.remote.MiniMaxResponseParser
import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.remote.dto.ChatMessage
import com.example.asr.data.remote.dto.ChatRequest
import com.example.asr.data.remote.dto.MiniMaxTtsRequest
import com.example.asr.data.remote.dto.MiniMaxVoiceCloneRequest
import com.example.asr.data.remote.dto.MiniMaxVoiceSetting
import com.example.asr.data.remote.dto.VisionChatMessage
import com.example.asr.data.remote.dto.VisionChatRequest
import com.example.asr.data.remote.dto.VisionContentPart
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.settings.ClonedVoice
import com.example.asr.data.sync.WebDavClient
import com.example.asr.domain.ParentLock
import com.example.asr.domain.VoiceCatalog
import com.example.asr.media.TtsPlayer
import com.example.asr.media.TtsState
import com.example.asr.worker.DailyReviewWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File

/** 模型连通性测试状态 */
data class TestState(
    val testing: Boolean = false,
    val result: String? = null,
    val success: Boolean = false,
)

/** 声音复刻流程状态（对齐小程序 cloneState：idle/recording/recorded/cloning/failed） */
enum class CloneState { IDLE, RECORDING, RECORDED, CLONING, FAILED }

data class CloneUiState(
    val state: CloneState = CloneState.IDLE,
    val name: String = "",
    val recordSec: Int = 0,
    val error: String? = null,
)

class SettingsViewModel(private val application: Application) : ViewModel() {

    private val app = application as AsrApplication
    private val settingsStore = app.container.settingsStore
    private val debugLog = app.container.debugLog
    private val miniMaxApi = app.container.miniMaxApi

    private val _ui = MutableStateFlow(AppSettings())
    val ui: StateFlow<AppSettings> = _ui

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private val _asrTest = MutableStateFlow(TestState())
    val asrTest: StateFlow<TestState> = _asrTest

    private val _llmTest = MutableStateFlow(TestState())
    val llmTest: StateFlow<TestState> = _llmTest

    private val _vlmTest = MutableStateFlow(TestState())
    val vlmTest: StateFlow<TestState> = _vlmTest

    private val _webdavTest = MutableStateFlow(TestState())
    val webdavTest: StateFlow<TestState> = _webdavTest

    private val _minimaxTest = MutableStateFlow(TestState())
    val minimaxTest: StateFlow<TestState> = _minimaxTest

    private val _clone = MutableStateFlow(CloneUiState())
    val clone: StateFlow<CloneUiState> = _clone

    /** 正在合成试听的音色 voiceId（空串 = 无） */
    private val _previewingId = MutableStateFlow("")
    val previewingId: StateFlow<String> = _previewingId

    private val _debugEntries = MutableStateFlow<List<DebugEntry>>(emptyList())
    val debugEntries: StateFlow<List<DebugEntry>> = _debugEntries

    /** 一次性提示（snackbar） */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    // 声音复刻的录音器与文件（非渲染状态）
    private var voiceRecorder: AudioRecorder? = null
    private var voiceFile: File? = null
    private var recordTickJob: Job? = null
    private var previewPlayer: TtsPlayer? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { _ui.value = it }
        }
        refreshDebugEntries()
    }

    override fun onCleared() {
        recordTickJob?.cancel()
        voiceRecorder?.stop()
        previewPlayer?.release()
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        _ui.value = transform(_ui.value)
        _saved.value = false
    }

    /** 科目立即生效（不依赖保存按钮） */
    fun addSubject(name: String) {
        val n = name.trim()
        if (n.isEmpty() || n in _ui.value.subjects) return
        viewModelScope.launch { settingsStore.setSubjects(_ui.value.subjects + n) }
    }

    fun removeSubject(name: String) {
        viewModelScope.launch { settingsStore.setSubjects(_ui.value.subjects - name) }
    }

    /** 主题模式立即生效（不依赖保存按钮） */
    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    /** 录音清理模式立即生效（对齐小程序 onSetCleanupMode） */
    fun setCleanupMode(mode: String) {
        viewModelScope.launch {
            if (mode == AppSettings.CLEANUP_BACKUP_DELETE) {
                val s = settingsStore.settings.first()
                if (s.webdavUser.isBlank() || s.webdavPassword.isBlank()) {
                    _toast.value = "未配置坚果云 WebDAV，将回退为每次询问"
                }
            }
            settingsStore.setAudioCleanupMode(mode)
        }
    }

    /** AI 语音播报开关（立即生效）：开启前强制要求已配置 MiniMax API Key */
    fun setChatVoiceEnabled(on: Boolean) {
        viewModelScope.launch {
            if (on && _ui.value.minimaxApiKey.isBlank()) {
                _toast.value = "请先填写 MiniMax API Key 再开启语音播报"
                return@launch
            }
            settingsStore.setChatVoiceEnabled(on)
            _toast.value = if (on) "语音播报已开启" else "已切换为纯文字回复"
        }
    }

    // ---------- 家长密码（立即生效，不进保存） ----------

    /** 校验旧密码（已设置过时才需要） */
    fun verifyPin(input: String): Boolean = ParentLock.verify(_ui.value.parentPin, input)

    /** 设置新 PIN；不合法返回 false（页面提示「请输入 4-6 位数字」） */
    fun setPin(pin: String): Boolean {
        if (!ParentLock.isValidNewPin(pin)) return false
        viewModelScope.launch { settingsStore.setParentPin(pin.trim()) }
        return true
    }

    // ---------- 音色管理（clonedVoices/preferredVoiceId 立即生效） ----------

    /** 设为默认 / 再点取消默认（预置音色与复刻音色通用） */
    fun toggleDefaultVoice(voiceId: String) {
        viewModelScope.launch {
            val current = settingsStore.settings.first().preferredVoiceId
            settingsStore.setPreferredVoiceId(if (current == voiceId) "" else voiceId)
        }
    }

    /** 删除复刻音色（删默认音色时同时取消默认） */
    fun deleteVoice(voice: ClonedVoice) {
        viewModelScope.launch {
            val s = settingsStore.settings.first()
            settingsStore.setClonedVoices(s.clonedVoices.filter { it.voiceId != voice.voiceId })
            if (s.preferredVoiceId == voice.voiceId) settingsStore.setPreferredVoiceId("")
            _toast.value = "已删除"
        }
    }

    /** 试听：现场合成一句并播放；每次先停掉上一个 */
    fun previewVoice(voiceId: String) {
        val s = _ui.value
        if (s.minimaxApiKey.isBlank()) {
            _toast.value = "请先填写 MiniMax API Key"
            return
        }
        if (_previewingId.value.isNotEmpty()) return
        _previewingId.value = voiceId
        viewModelScope.launch {
            try {
                val url = synthesizeWith(s.minimaxApiKey, s.minimaxModel, voiceId, VoiceCatalog.PREVIEW_TEXT)
                _previewingId.value = ""
                val player = previewPlayer ?: TtsPlayer { state ->
                    if (state == TtsState.ERROR) {
                        _toast.value = "试听播放失败：${previewPlayer?.lastError ?: "未知原因"}"
                    }
                }.also { previewPlayer = it }
                player.play(url)
            } catch (e: Exception) {
                _previewingId.value = ""
                _toast.value = e.toUserMessage()
            }
        }
    }

    // ---------- 家长声音复刻（录音 → files/upload → voice_clone） ----------

    fun setCloneName(name: String) {
        _clone.value = _clone.value.copy(name = name)
    }

    /** 「开始录音」（调用前页面已确认麦克风权限） */
    fun startVoiceRecording() {
        if (_clone.value.state == CloneState.CLONING) return
        val dir = File(application.cacheDir, "voice_clone").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val recorder = AudioRecorder(application)
        runCatching { recorder.start(file) }.onFailure {
            _toast.value = "录音失败"
            return
        }
        voiceRecorder = recorder
        voiceFile = file
        _clone.value = CloneUiState(state = CloneState.RECORDING, name = _clone.value.name)
        recordTickJob?.cancel()
        recordTickJob = viewModelScope.launch {
            var sec = 0
            while (true) {
                delay(1000)
                sec++
                _clone.value = _clone.value.copy(recordSec = sec)
                if (sec >= MAX_VOICE_SAMPLE_SEC) stopVoiceRecording() // MiniMax 上限 5 分钟
            }
        }
    }

    /** 「结束录音」：不足 10 秒丢弃（对齐小程序「至少录 10 秒」） */
    fun stopVoiceRecording() {
        if (_clone.value.state != CloneState.RECORDING) return
        recordTickJob?.cancel()
        val sec = _clone.value.recordSec
        val file = voiceRecorder?.stop()
        voiceRecorder = null
        if (file == null) {
            _clone.value = CloneUiState(name = _clone.value.name)
            _toast.value = "录音失败"
            return
        }
        if (sec < MIN_VOICE_SAMPLE_SEC) {
            file.delete()
            voiceFile = null
            _clone.value = CloneUiState(name = _clone.value.name)
            _toast.value = "至少录 10 秒"
            return
        }
        voiceFile = file
        _clone.value = _clone.value.copy(state = CloneState.RECORDED, recordSec = sec, error = null)
    }

    /** 「重新录」：直接开始一段新录音 */
    fun reRecord() {
        if (_clone.value.state == CloneState.CLONING) return
        voiceFile?.delete()
        voiceFile = null
        startVoiceRecording()
    }

    /** 「开始复刻」：上传录音 → voice_clone，成功后追加进 clonedVoices */
    fun startClone() {
        val state = _clone.value
        if (state.state != CloneState.RECORDED && state.state != CloneState.FAILED) return
        val name = state.name.trim()
        if (name.isEmpty()) {
            _toast.value = "请先给音色起个名字"
            return
        }
        val s = _ui.value
        if (s.minimaxApiKey.isBlank()) {
            _toast.value = "请先填写 MiniMax API Key"
            return
        }
        if (s.minimaxModel.isBlank()) {
            _toast.value = "请先填写 MiniMax 合成模型"
            return
        }
        val file = voiceFile
        if (file == null || !file.exists()) {
            _toast.value = "请先录音"
            return
        }
        _clone.value = state.copy(state = CloneState.CLONING, error = null)
        val voiceId = "parentVoice${System.currentTimeMillis()}"
        viewModelScope.launch {
            try {
                val auth = "Bearer ${s.minimaxApiKey}"
                val fileId = withContext(Dispatchers.IO) {
                    val part = MultipartBody.Part.createFormData(
                        "file", file.name, file.asRequestBody("audio/m4a".toMediaType()),
                    )
                    val body = miniMaxApi.uploadVoiceFile(
                        authorization = auth,
                        file = part,
                        purpose = "voice_clone".toRequestBody("text/plain".toMediaType()),
                    ).string()
                    MiniMaxResponseParser.parseUploadFileId(body)
                }
                withContext(Dispatchers.IO) {
                    val body = miniMaxApi.cloneVoice(
                        authorization = auth,
                        request = MiniMaxVoiceCloneRequest(
                            fileId = fileId,
                            voiceId = voiceId,
                            text = VoiceCatalog.CLONE_DEMO_TEXT,
                            model = s.minimaxModel,
                        ),
                    ).string()
                    MiniMaxResponseParser.parseCloneDemoAudio(body)
                }
                val current = settingsStore.settings.first().clonedVoices
                settingsStore.setClonedVoices(current + ClonedVoice(voiceId = voiceId, name = name))
                file.delete()
                voiceFile = null
                _clone.value = CloneUiState()
                _toast.value = "复刻成功"
            } catch (e: Exception) {
                debugLog.record(
                    action = "家长声音复刻",
                    model = s.minimaxModel,
                    prompt = "voice_id：$voiceId\n文件：${file.name}",
                    error = e.toUserMessage(),
                )
                _clone.value = _clone.value.copy(state = CloneState.FAILED, error = e.toUserMessage())
            }
        }
    }

    // ---------- 调试模式 ----------

    /** 输入密码开启调试模式（密码错误提示「密码错误」） */
    fun enableDebug(password: String): Boolean {
        if (password.trim() != DebugLog.DEBUG_PASSWORD) return false
        viewModelScope.launch {
            settingsStore.setDebugMode(true)
            _toast.value = "调试模式已开启"
        }
        return true
    }

    fun disableDebug() {
        viewModelScope.launch {
            settingsStore.setDebugMode(false)
            _toast.value = "调试模式已关闭"
        }
    }

    fun refreshDebugEntries() {
        _debugEntries.value = debugLog.entries()
    }

    fun clearDebugLog() {
        debugLog.clear()
        refreshDebugEntries()
        _toast.value = "已清空"
    }

    // ---------- 保存 ----------

    fun save() {
        viewModelScope.launch {
            val s = _ui.value
            settingsStore.setApiKey(s.apiKey)
            settingsStore.setBaseUrl(s.baseUrl)
            settingsStore.setAsrModel(s.asrModel)
            settingsStore.setLlmModel(s.llmModel)
            settingsStore.setVlmModel(s.vlmModel)
            settingsStore.setMinimaxApiKey(s.minimaxApiKey)
            settingsStore.setMinimaxModel(s.minimaxModel)
            settingsStore.setReminderTime(s.reminderHour, s.reminderMinute)
            settingsStore.setWebdavUrl(s.webdavUrl)
            settingsStore.setWebdavUser(s.webdavUser)
            settingsStore.setWebdavPassword(s.webdavPassword)
            // 重新按新提醒时间排程
            DailyReviewWorker.schedule(application, s.reminderHour, s.reminderMinute)
            _saved.value = true
        }
    }

    // ---------- 模型测试（用当前页面填的值，无需先保存） ----------

    /** 测试分析模型：发一条短消息，验证 chat/completions 连通性（用当前页面填的值，无需先保存） */
    fun testLlm() {
        if (_llmTest.value.testing) return
        viewModelScope.launch {
            _llmTest.value = TestState(testing = true)
            _llmTest.value = try {
                val s = _ui.value
                require(s.apiKey.isNotBlank()) { "请先填写 API Key" }
                val api = NetworkClient.api(s.baseUrl)
                val resp = api.chatCompletions(
                    authorization = "Bearer ${s.apiKey}",
                    request = ChatRequest(
                        model = s.llmModel,
                        messages = listOf(ChatMessage(role = "user", content = "请只回复：ok")),
                    ),
                )
                TestState(result = "成功，模型返回：${resp.text.take(100)}", success = true)
            } catch (e: Exception) {
                TestState(result = "失败：${e.toUserMessage()}")
            }
        }
    }

    /** 测试转写模型：上传 1 秒静音 WAV，验证 audio/transcriptions 连通性，并展示原始返回便于核对格式 */
    fun testAsr() {
        if (_asrTest.value.testing) return
        viewModelScope.launch {
            _asrTest.value = TestState(testing = true)
            _asrTest.value = try {
                val s = _ui.value
                require(s.apiKey.isNotBlank()) { "请先填写 API Key" }
                val api = NetworkClient.api(s.baseUrl)
                val wav = TestAudio.silentWav()
                val filePart = MultipartBody.Part.createFormData(
                    "file", "test.wav", wav.toRequestBody("audio/wav".toMediaType()),
                )
                val raw = api.transcribe(
                    authorization = "Bearer ${s.apiKey}",
                    file = filePart,
                    model = s.asrModel.toRequestBody("text/plain".toMediaType()),
                    responseFormat = "verbose_json".toRequestBody("text/plain".toMediaType()),
                ).string()
                TestState(result = "成功，原始返回：${raw.take(300)}", success = true)
            } catch (e: Exception) {
                TestState(result = "失败：${e.toUserMessage()}")
            }
        }
    }

    /** 测试多模态模型：发一张本地生成的纯色小图，验证 vision 请求连通性（用当前页面填的值） */
    fun testVlm() {
        if (_vlmTest.value.testing) return
        viewModelScope.launch {
            _vlmTest.value = TestState(testing = true)
            _vlmTest.value = try {
                val s = _ui.value
                require(s.apiKey.isNotBlank()) { "请先填写 API Key" }
                val api = NetworkClient.api(s.baseUrl)
                val resp = api.visionChatCompletions(
                    authorization = "Bearer ${s.apiKey}",
                    request = VisionChatRequest(
                        model = s.vlmModel,
                        messages = listOf(
                            VisionChatMessage(
                                role = "user",
                                content = listOf(
                                    VisionContentPart.text("这张图片主要是什么颜色？只回答颜色名称。"),
                                    VisionContentPart.image(testImageDataUrl()),
                                ),
                            ),
                        ),
                    ),
                )
                TestState(result = "成功，模型返回：${resp.text.take(100)}", success = true)
            } catch (e: Exception) {
                TestState(result = "失败：${e.toUserMessage()}")
            }
        }
    }

    /** 测试语音合成：用当前生效的默认音色合成一句固定文案（用当前页面填的值，无需先保存） */
    fun testMinimax() {
        if (_minimaxTest.value.testing) return
        viewModelScope.launch {
            _minimaxTest.value = TestState(testing = true)
            _minimaxTest.value = try {
                val s = _ui.value
                require(s.minimaxApiKey.isNotBlank()) { "请先填写 MiniMax API Key" }
                val voiceId = s.preferredVoiceId.ifBlank { AppSettings.DEFAULT_MINIMAX_VOICE }
                synthesizeWith(s.minimaxApiKey, s.minimaxModel, voiceId, VoiceCatalog.PREVIEW_TEXT)
                TestState(result = "成功，已返回音频链接", success = true)
            } catch (e: Exception) {
                TestState(result = "失败：${e.toUserMessage()}")
            }
        }
    }

    /** 用指定 key/model/音色合成一句（试听与连通性测试共用） */
    private suspend fun synthesizeWith(apiKey: String, model: String, voiceId: String, text: String): String =
        withContext(Dispatchers.IO) {
            val body = miniMaxApi.synthesize(
                authorization = "Bearer $apiKey",
                request = MiniMaxTtsRequest(
                    model = model,
                    text = text,
                    voiceSetting = MiniMaxVoiceSetting(voiceId = voiceId),
                ),
            ).string()
            MiniMaxResponseParser.parseTtsAudioUrl(body)
        }

    /** 生成 64x64 深绿色测试图的 base64 data URL */
    private fun testImageDataUrl(): String {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF2E6B4F.toInt())
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** 测试 WebDAV 连接：PROPFIND 根地址（用当前页面填的值，无需先保存） */
    fun testWebdav() {
        if (_webdavTest.value.testing) return
        viewModelScope.launch {
            _webdavTest.value = TestState(testing = true)
            _webdavTest.value = try {
                val client = currentWebDavClient()
                withContext(Dispatchers.IO) {
                    client.testConnection()
                }
                TestState(result = "连接成功", success = true)
            } catch (e: Exception) {
                TestState(result = "失败：${e.toUserMessage()}")
            }
        }
    }

    /** 用页面当前填写的 WebDAV 配置构建客户端 */
    private fun currentWebDavClient(): WebDavClient {
        val s = _ui.value
        require(s.webdavUrl.isNotBlank()) { "请先填写 WebDAV 地址" }
        require(s.webdavUser.isNotBlank()) { "请先填写 WebDAV 账号" }
        require(s.webdavPassword.isNotBlank()) { "请先填写 WebDAV 应用密码" }
        return WebDavClient(s.webdavUrl, s.webdavUser, s.webdavPassword)
    }

    companion object {
        /** 声音复刻采样时长要求（MiniMax：10s-5min） */
        const val MIN_VOICE_SAMPLE_SEC = 10
        const val MAX_VOICE_SAMPLE_SEC = 300
    }
}
