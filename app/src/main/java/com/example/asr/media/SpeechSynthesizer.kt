package com.example.asr.media

import com.example.asr.data.remote.DebugLog
import com.example.asr.data.remote.MiniMaxApi
import com.example.asr.data.remote.MiniMaxResponseParser
import com.example.asr.data.remote.dto.MiniMaxTtsRequest
import com.example.asr.data.remote.dto.MiniMaxVoiceSetting
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * MiniMax 语音合成（对应小程序 tutorChat.synthesizeReply）：
 * 音色优先级——孩子专属 voiceId → 全局 preferredVoiceId → 预置默认音色。
 */
class SpeechSynthesizer(
    private val miniMaxApi: MiniMaxApi,
    private val settingsStore: SettingsStore,
    private val debugLog: DebugLog? = null,
) {

    /** 合成文本，返回 24h 有效的音频 URL */
    suspend fun synthesize(text: String, voiceId: String? = null): String {
        val settings = settingsStore.settings.first()
        require(settings.minimaxApiKey.isNotBlank()) { "请家长到「我的 → 设置 → 语音合成与音色」填写 MiniMax API Key" }
        val effectiveVoiceId = voiceId?.takeIf { it.isNotBlank() }
            ?: settings.preferredVoiceId.takeIf { it.isNotBlank() }
            ?: AppSettings.DEFAULT_MINIMAX_VOICE
        return try {
            val body = miniMaxApi.synthesize(
                authorization = "Bearer ${settings.minimaxApiKey}",
                request = MiniMaxTtsRequest(
                    model = settings.minimaxModel,
                    text = text,
                    voiceSetting = MiniMaxVoiceSetting(voiceId = effectiveVoiceId),
                ),
            ).string()
            MiniMaxResponseParser.parseTtsAudioUrl(body)
        } catch (e: Exception) {
            debugLog?.record(
                action = "MiniMax 语音合成",
                model = settings.minimaxModel,
                prompt = "voice_id：$effectiveVoiceId\ntext：$text",
                error = e.toUserMessage(),
            )
            throw Exception(e.toUserMessage())
        }
    }
}
