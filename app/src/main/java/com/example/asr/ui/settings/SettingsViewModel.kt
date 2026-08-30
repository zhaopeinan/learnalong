package com.example.asr.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.AsrApplication
import com.example.asr.audio.TestAudio
import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.remote.dto.ChatMessage
import com.example.asr.data.remote.dto.ChatRequest
import com.example.asr.data.remote.dto.VisionChatMessage
import com.example.asr.data.remote.dto.VisionChatRequest
import com.example.asr.data.remote.dto.VisionContentPart
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.sync.WebDavClient
import com.example.asr.worker.DailyReviewWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/** 模型连通性测试状态 */
data class TestState(
    val testing: Boolean = false,
    val result: String? = null,
    val success: Boolean = false,
)

class SettingsViewModel(private val application: Application) : ViewModel() {

    private val settingsStore = (application as AsrApplication).container.settingsStore
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

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { _ui.value = it }
        }
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

    fun save() {
        viewModelScope.launch {
            val s = _ui.value
            settingsStore.setApiKey(s.apiKey)
            settingsStore.setBaseUrl(s.baseUrl)
            settingsStore.setAsrModel(s.asrModel)
            settingsStore.setLlmModel(s.llmModel)
            settingsStore.setVlmModel(s.vlmModel)
            settingsStore.setReminderTime(s.reminderHour, s.reminderMinute)
            settingsStore.setWebdavUrl(s.webdavUrl)
            settingsStore.setWebdavUser(s.webdavUser)
            settingsStore.setWebdavPassword(s.webdavPassword)
            // 重新按新提醒时间排程
            DailyReviewWorker.schedule(application, s.reminderHour, s.reminderMinute)
            _saved.value = true
        }
    }

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
}
