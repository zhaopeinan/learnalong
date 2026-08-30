package com.example.asr

import com.example.asr.data.remote.TaskContentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskContentParserTest {

    @Test
    fun `正常 JSON 解析成功`() {
        val raw = """{"exercises":[{"question":"拼读 zhuā","answer":"zh-u-ā","hint":"翘舌音"}],"tips":"多跟读"}"""
        val result = TaskContentParser.parse(raw)
        assertEquals(1, result!!.exercises.size)
        assertEquals("拼读 zhuā", result.exercises[0].question)
        assertEquals("多跟读", result.tips)
    }

    @Test
    fun `带 markdown 围栏也能解析`() {
        val raw = """
```json
{"exercises":[{"question":"q","answer":"a","hint":""}],"tips":"t"}
```
"""
        val result = TaskContentParser.parse(raw)
        assertEquals(1, result!!.exercises.size)
    }

    @Test
    fun `前后有多余文字也能解析`() {
        val raw = """好的，以下是练习：
{"exercises":[{"question":"q","answer":"a","hint":""}],"tips":"t"}
希望有帮助。"""
        val result = TaskContentParser.parse(raw)
        assertEquals(1, result!!.exercises.size)
    }

    @Test
    fun `非法 JSON 返回 null`() {
        assertNull(TaskContentParser.parse("这不是 JSON"))
    }

    @Test
    fun `空题目被过滤，全空返回 null`() {
        assertNull(TaskContentParser.parse("""{"exercises":[],"tips":"t"}"""))
        val r = TaskContentParser.parse(
            """{"exercises":[{"question":"","answer":"a"},{"question":"q2","answer":"a2"}],"tips":""}"""
        )
        assertEquals(1, r!!.exercises.size)
        assertEquals("q2", r.exercises[0].question)
    }

    @Test
    fun `未知字段被忽略`() {
        val raw = """{"exercises":[{"question":"q","answer":"a","extra":1}],"tips":"t","other":true}"""
        assertTrue(TaskContentParser.parse(raw) != null)
    }
}
