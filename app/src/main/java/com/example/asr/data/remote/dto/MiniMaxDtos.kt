package com.example.asr.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** MiniMax 响应公共状态块：status_code 非 0 即业务错误 */
@Serializable
data class MiniMaxBaseResp(
    @SerialName("status_code") val statusCode: Int = 0,
    @SerialName("status_msg") val statusMsg: String = "",
)

@Serializable
data class MiniMaxVoiceSetting(
    @SerialName("voice_id") val voiceId: String,
    val speed: Int = 1,
    val vol: Int = 1,
    val pitch: Int = 0,
)

@Serializable
data class MiniMaxAudioSetting(
    @SerialName("sample_rate") val sampleRate: Int = 32000,
    val bitrate: Int = 128000,
    val format: String = "mp3",
    val channel: Int = 1,
)

/** t2a_v2 请求体（字段与小程序 minimax.ts synthesize 一致） */
@Serializable
data class MiniMaxTtsRequest(
    val model: String,
    val text: String,
    val stream: Boolean = false,
    @SerialName("voice_setting") val voiceSetting: MiniMaxVoiceSetting,
    @SerialName("audio_setting") val audioSetting: MiniMaxAudioSetting = MiniMaxAudioSetting(),
    /** url = 返回 24h 有效的音频链接 */
    @SerialName("output_format") val outputFormat: String = "url",
    @SerialName("language_boost") val languageBoost: String = "Chinese",
)

@Serializable
data class MiniMaxTtsResponse(
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp = MiniMaxBaseResp(),
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val audio: String? = null,
        val status: Int? = null,
    )
}

@Serializable
data class MiniMaxUploadResponse(
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp = MiniMaxBaseResp(),
    val file: File? = null,
) {
    @Serializable
    data class File(
        @SerialName("file_id") val fileId: Long? = null,
    )
}

/**
 * voice_clone 请求体。
 * voice_id 要求：8-256 位，字母开头，仅字母/数字/-/_。
 */
@Serializable
data class MiniMaxVoiceCloneRequest(
    @SerialName("file_id") val fileId: Long,
    @SerialName("voice_id") val voiceId: String,
    @SerialName("need_noise_reduction") val needNoiseReduction: Boolean = true,
    @SerialName("need_volume_normalization") val needVolumeNormalization: Boolean = true,
    /** 试听文本：提供时同时返回 demo_audio（需带 model） */
    val text: String? = null,
    val model: String? = null,
)

@Serializable
data class MiniMaxVoiceCloneResponse(
    @SerialName("base_resp") val baseResp: MiniMaxBaseResp = MiniMaxBaseResp(),
    @SerialName("demo_audio") val demoAudio: String? = null,
)
