package com.example.asr.ui.children

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.remote.MiniMaxResponseParser
import com.example.asr.data.remote.dto.MiniMaxTtsRequest
import com.example.asr.data.remote.dto.MiniMaxVoiceSetting
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.AppSettings
import com.example.asr.domain.VoiceCatalog
import com.example.asr.media.TtsPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChildrenViewModel(application: Application) : ViewModel() {

    private val app = application as AsrApplication
    private val childRepository = app.container.childRepository
    private val settingsStore = app.container.settingsStore
    private val miniMaxApi = app.container.miniMaxApi

    /** 试听播放器（每次先停掉上一个） */
    private var previewPlayer: TtsPlayer? = null

    val children: StateFlow<List<ChildEntity>> = childRepository.children
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /** 正在合成试听的音色 key（空串 = 无） */
    private val _previewingId = MutableStateFlow("")
    val previewingId: StateFlow<String> = _previewingId

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    override fun onCleared() {
        previewPlayer?.release()
    }

    fun consumeToast() {
        _toast.value = null
    }

    /** 辅导音色选项（'' = 跟随全局默认；对齐小程序 listVoiceOptions） */
    fun voiceOptions(): List<Pair<String, String>> {
        val s = settings.value
        return listOf("" to VoiceCatalog.FOLLOW_DEFAULT_LABEL) +
            VoiceCatalog.PRESET_VOICES.map { it.value to it.label } +
            s.clonedVoices.map { it.voiceId to it.name }
    }

    /** 音色显示名（列表行展示用） */
    fun voiceLabel(voiceId: String?): String =
        voiceOptions().firstOrNull { it.first == (voiceId ?: "") }?.second
            ?: VoiceCatalog.FOLLOW_DEFAULT_LABEL

    /** 试听音色：现场合成一句并播放（空 voiceId 用全局默认音色） */
    fun previewVoice(voiceId: String) {
        val s = settings.value
        if (s.minimaxApiKey.isBlank()) {
            _toast.value = "请先填写 MiniMax API Key"
            return
        }
        if (_previewingId.value.isNotEmpty()) return
        _previewingId.value = voiceId
        viewModelScope.launch {
            try {
                val effective = voiceId.ifBlank {
                    s.preferredVoiceId.ifBlank { AppSettings.DEFAULT_MINIMAX_VOICE }
                }
                val url = withContext(Dispatchers.IO) {
                    val body = miniMaxApi.synthesize(
                        authorization = "Bearer ${s.minimaxApiKey}",
                        request = MiniMaxTtsRequest(
                            model = s.minimaxModel,
                            text = VoiceCatalog.PREVIEW_TEXT,
                            voiceSetting = MiniMaxVoiceSetting(voiceId = effective),
                        ),
                    ).string()
                    MiniMaxResponseParser.parseTtsAudioUrl(body)
                }
                _previewingId.value = ""
                val player = previewPlayer ?: TtsPlayer().also { previewPlayer = it }
                player.play(url)
            } catch (e: Exception) {
                _previewingId.value = ""
                _toast.value = e.toUserMessage()
            }
        }
    }

    fun add(name: String, grade: String, voiceId: String?) {
        if (name.isBlank()) return
        viewModelScope.launch { childRepository.add(name, grade, voiceId) }
    }

    fun update(child: ChildEntity) {
        if (child.name.isBlank()) return
        viewModelScope.launch { childRepository.update(child) }
    }

    fun delete(child: ChildEntity) {
        viewModelScope.launch { childRepository.delete(child) }
    }
}
