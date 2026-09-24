package com.example.asr

import com.example.asr.data.remote.MiniMaxApiException
import com.example.asr.data.remote.MiniMaxResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniMaxResponseParserTest {

    @Test
    fun `tts response returns audio url`() {
        val body = """
            {"base_resp":{"status_code":0,"status_msg":"success"},
             "data":{"audio":"https://cdn.minimaxi.com/abc.mp3","status":2},
             "extra_info":{"audio_length":3200}}
        """.trimIndent()
        assertEquals("https://cdn.minimaxi.com/abc.mp3", MiniMaxResponseParser.parseTtsAudioUrl(body))
    }

    @Test
    fun `tts response without audio throws`() {
        val body = """{"base_resp":{"status_code":0,"status_msg":"success"},"data":{"status":1}}"""
        val e = assertThrows(MiniMaxApiException::class.java) {
            MiniMaxResponseParser.parseTtsAudioUrl(body)
        }
        assertEquals("合成成功但未返回音频链接", e.message)
    }

    @Test
    fun `upload response returns file id`() {
        val body = """
            {"base_resp":{"status_code":0,"status_msg":"success"},
             "file":{"file_id":266017363741702,"bytes":12345,"purpose":"voice_clone"}}
        """.trimIndent()
        assertEquals(266017363741702L, MiniMaxResponseParser.parseUploadFileId(body))
    }

    @Test
    fun `upload response without file id throws`() {
        val body = """{"base_resp":{"status_code":0,"status_msg":"success"}}"""
        val e = assertThrows(MiniMaxApiException::class.java) {
            MiniMaxResponseParser.parseUploadFileId(body)
        }
        assertEquals("上传成功但未返回 file_id", e.message)
    }

    @Test
    fun `clone response returns demo audio when present`() {
        val body = """
            {"base_resp":{"status_code":0,"status_msg":"success"},
             "demo_audio":"https://cdn.minimaxi.com/demo.mp3"}
        """.trimIndent()
        assertEquals("https://cdn.minimaxi.com/demo.mp3", MiniMaxResponseParser.parseCloneDemoAudio(body))
    }

    @Test
    fun `clone response without demo audio returns null`() {
        val body = """{"base_resp":{"status_code":0,"status_msg":"success"}}"""
        assertNull(MiniMaxResponseParser.parseCloneDemoAudio(body))
    }

    @Test
    fun `non zero base resp throws with status message`() {
        val body = """{"base_resp":{"status_code":1004,"status_msg":"invalid voice_id"}}"""
        val e = assertThrows(MiniMaxApiException::class.java) {
            MiniMaxResponseParser.parseTtsAudioUrl(body)
        }
        assertTrue(e.message!!.contains("1004"))
        assertTrue(e.message!!.contains("invalid voice_id"))
    }

    @Test
    fun `invalid json throws parse failure`() {
        val e = assertThrows(MiniMaxApiException::class.java) {
            MiniMaxResponseParser.parseUploadFileId("这不是JSON")
        }
        assertEquals("MiniMax 返回解析失败", e.message)
    }

    @Test
    fun `missing base resp treated as success`() {
        val body = """{"data":{"audio":"https://cdn.minimaxi.com/x.mp3"}}"""
        assertEquals("https://cdn.minimaxi.com/x.mp3", MiniMaxResponseParser.parseTtsAudioUrl(body))
    }
}
