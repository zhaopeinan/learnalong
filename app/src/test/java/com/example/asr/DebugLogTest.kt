package com.example.asr

import com.example.asr.data.remote.DebugEntry
import com.example.asr.data.remote.DebugLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTest {

    @Test
    fun `最多保留最近 10 条`() {
        val list = (1..15).map {
            DebugEntry(time = it.toLong(), action = "a$it", model = "m", prompt = "p", error = "e")
        }
        val trimmed = DebugLog.trimToMax(list)
        assertEquals(10, trimmed.size)
        assertEquals("a1", trimmed.first().action)
        assertEquals("a10", trimmed.last().action)
    }

    @Test
    fun `导出文本格式对齐小程序（时间 操作 模型 错误 提示词分段）`() {
        val e = DebugEntry(
            time = 0L,
            action = "语音转写",
            model = "model-x",
            prompt = "[system]\ns\n\n[user]\nu",
            error = "HTTP 400",
        )
        val text = DebugLog.formatDebugText(e)
        val lines = text.lines()
        assertEquals("===== 伴学记 调试信息 =====", lines.first())
        assertEquals("===========================", lines.last())
        assertTrue(text.contains("操作：语音转写"))
        assertTrue(text.contains("模型：model-x"))
        assertTrue(text.contains("----- 错误信息 -----\nHTTP 400"))
        assertTrue(text.contains("----- 请求提示词 -----\n[system]\ns\n\n[user]\nu"))
    }

    @Test
    fun `导出文件名格式`() {
        val name = DebugLog.exportFileName(DebugEntry(0L, "a", "m", "p", "e"))
        assertTrue(name.startsWith("伴学记-调试-"))
        assertTrue(name.endsWith(".txt"))
    }
}
