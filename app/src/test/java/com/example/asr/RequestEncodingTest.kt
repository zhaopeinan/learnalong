package com.example.asr

import com.example.asr.data.remote.dto.MiniMaxTtsRequest
import com.example.asr.data.remote.dto.MiniMaxVoiceSetting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NetworkClient 的 Json 未开 encodeDefaults：请求体默认值字段必须带 @EncodeDefault，
 * 否则 output_format=url 等字段被静默丢弃，MiniMax 会退化为返回 hex 音频。
 */
class RequestEncodingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `tts request encodes output_format and all default fields`() {
        val body = json.encodeToString(
            MiniMaxTtsRequest.serializer(),
            MiniMaxTtsRequest(
                model = "speech-2.8-hd",
                text = "你好",
                voiceSetting = MiniMaxVoiceSetting(voiceId = "v1"),
            ),
        )
        val obj = json.parseToJsonElement(body).jsonObject
        assertEquals("url", obj.getValue("output_format").jsonPrimitive.content)
        assertEquals("false", obj.getValue("stream").jsonPrimitive.content)
        assertEquals("Chinese", obj.getValue("language_boost").jsonPrimitive.content)
        val voice = obj.getValue("voice_setting").jsonObject
        assertEquals("v1", voice.getValue("voice_id").jsonPrimitive.content)
        assertEquals("1", voice.getValue("speed").jsonPrimitive.content)
        val audio = obj.getValue("audio_setting").jsonObject
        assertEquals("32000", audio.getValue("sample_rate").jsonPrimitive.content)
        assertEquals("mp3", audio.getValue("format").jsonPrimitive.content)
    }
}
