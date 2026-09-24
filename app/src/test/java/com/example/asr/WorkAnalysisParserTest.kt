package com.example.asr

import com.example.asr.data.remote.WorkAnalysisParser
import com.example.asr.data.repository.WorkRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkAnalysisParserTest {

    @Test
    fun `正常 JSON 解析成功`() {
        val raw = """{"title":"周会纪要","summary":"## 议题\n- 结论一","todos":[{"text":"发周报","assignee":"张三","deadline":"周五"}]}"""
        val result = WorkAnalysisParser.parse(raw)!!
        assertEquals("周会纪要", result.title)
        assertEquals("## 议题\n- 结论一", result.summary)
        assertEquals(1, result.todos.size)
        assertEquals("发周报", result.todos[0].text)
        assertEquals("张三", result.todos[0].assignee)
        assertEquals("周五", result.todos[0].deadline)
    }

    @Test
    fun `带 markdown 围栏也能解析`() {
        val raw = """
```json
{"title":"t","summary":"s","todos":[{"text":"x","assignee":"","deadline":""}]}
```
"""
        val result = WorkAnalysisParser.parse(raw)!!
        assertEquals("t", result.title)
        assertEquals(1, result.todos.size)
    }

    @Test
    fun `前后有多余文字也能解析`() {
        val raw = """好的，以下是纪要：
{"title":"t","summary":"s","todos":[]}
希望有帮助。"""
        val result = WorkAnalysisParser.parse(raw)!!
        assertEquals("t", result.title)
        assertTrue(result.todos.isEmpty())
    }

    @Test
    fun `空 todos 与缺省字段容错`() {
        val raw = """{"summary":"只有纪要"}"""
        val result = WorkAnalysisParser.parse(raw)!!
        assertEquals("", result.title)
        assertEquals("只有纪要", result.summary)
        assertTrue(result.todos.isEmpty())
    }

    @Test
    fun `text 为空或非字符串的待办被过滤`() {
        val raw = """{"title":"","summary":"s","todos":[{"text":"  "},{"text":"有效待办"},{"assignee":"李四"},"not-an-object"]}"""
        val result = WorkAnalysisParser.parse(raw)!!
        assertEquals(1, result.todos.size)
        assertEquals("有效待办", result.todos[0].text)
    }

    @Test
    fun `assignee 与 deadline 缺省或非字符串时按空串处理`() {
        val raw = """{"title":"","summary":"s","todos":[{"text":"x","assignee":null,"deadline":42}]}"""
        val result = WorkAnalysisParser.parse(raw)!!
        assertEquals("", result.todos[0].assignee)
        assertEquals("42", result.todos[0].deadline)
    }

    @Test
    fun `非 JSON 返回 null`() {
        assertNull(WorkAnalysisParser.parse("模型没有按要求输出"))
        assertNull(WorkAnalysisParser.parse(""))
    }

    @Test
    fun `空 JSON 对象解析为空结果（与小程序一致，由调用方判失败）`() {
        val result = WorkAnalysisParser.parse("{}")!!
        assertEquals("", result.title)
        assertEquals("", result.summary)
        assertTrue(result.todos.isEmpty())
    }

    @Test
    fun `非法 JSON 对象返回 null`() {
        assertNull(WorkAnalysisParser.parse("{title: 未闭合"))
    }

    // ---------- chunkTranscriptByChars（长文稿按行边界切分） ----------

    @Test
    fun `短文稿不切分`() {
        val text = "第一行\n第二行"
        assertEquals(listOf(text), WorkRepository.chunkTranscriptByChars(text, 1000))
    }

    @Test
    fun `超预算按行边界切分不切半句`() {
        val lines = (1..10).map { "第${it}行内容" } // 每行 5 字符 + 换行 = 6
        val chunks = WorkRepository.chunkTranscriptByChars(lines.joinToString("\n"), 20)
        assertTrue(chunks.size > 1)
        // 每块都不超预算，且拼接还原原文
        chunks.forEach { assertTrue(it.length <= 20) }
        assertEquals(lines.joinToString("\n"), chunks.joinToString("\n"))
    }

    @Test
    fun `单行超预算独占一块`() {
        val text = "很长的单行内容超过预算\n短行"
        val chunks = WorkRepository.chunkTranscriptByChars(text, 5)
        assertEquals(listOf("很长的单行内容超过预算", "短行"), chunks)
    }
}
