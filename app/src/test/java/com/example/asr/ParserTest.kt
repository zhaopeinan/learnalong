package com.example.asr

import com.example.asr.data.remote.AnalysisResultParser
import com.example.asr.data.remote.VerboseJsonTranscriptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptParserTest {

    private val parser = VerboseJsonTranscriptParser()

    @Test
    fun `parses verbose json segments with speaker labels`() {
        val raw = """
            {"text":"整体文本","segments":[
              {"speaker":"SPEAKER_00","start":0.0,"end":2.5,"text":"这句话读什么"},
              {"speaker":"SPEAKER_01","start":2.5,"end":4.0,"text":"读拼音b"}
            ]}
        """.trimIndent()
        val segments = parser.parse(raw)
        assertEquals(2, segments.size)
        assertEquals("SPEAKER_00", segments[0].speakerLabel)
        assertEquals(0f, segments[0].startSec)
        assertEquals(2.5f, segments[0].endSec)
        assertEquals("这句话读什么", segments[0].text)
        assertEquals("SPEAKER_01", segments[1].speakerLabel)
    }

    @Test
    fun `loose field matching works`() {
        val raw = """{"segments":[{"spk":"1","begin":1.0,"stop":3.0,"content":"你好"}]}"""
        val segments = parser.parse(raw)
        assertEquals(1, segments.size)
        assertEquals("1", segments[0].speakerLabel)
        assertEquals(1f, segments[0].startSec)
        assertEquals(3f, segments[0].endSec)
    }

    @Test
    fun `falls back to whole text when no segments`() {
        val raw = """{"text":"只有一整段文本"}"""
        val segments = parser.parse(raw)
        assertEquals(1, segments.size)
        assertEquals("UNKNOWN", segments[0].speakerLabel)
        assertEquals("只有一整段文本", segments[0].text)
    }

    @Test
    fun `invalid json keeps raw text`() {
        val raw = "这不是JSON"
        val segments = parser.parse(raw)
        assertEquals(1, segments.size)
        assertEquals(raw, segments[0].text)
    }

    @Test
    fun `nested segments array is found`() {
        val raw = """{"result":{"segments":[{"speaker":"A","start":0,"end":1,"text":"嵌套"}]}}"""
        val segments = parser.parse(raw)
        assertEquals(1, segments.size)
        assertEquals("嵌套", segments[0].text)
    }
}

class AnalysisResultParserTest {

    @Test
    fun `parses clean json array`() {
        val raw = """[{"knowledgePoint":"拼音 b/d 混淆","description":"把 b 读成 d","mastery":40,"suggestion":"多做辨音练习"}]"""
        val result = AnalysisResultParser.parse(raw)
        assertEquals(1, result.size)
        assertEquals("拼音 b/d 混淆", result[0].knowledgePoint)
        assertEquals(40, result[0].mastery)
    }

    @Test
    fun `strips markdown code fence`() {
        val raw = "```json\n[{\"knowledgePoint\":\"乘法口诀\",\"description\":\"错\",\"mastery\":50,\"suggestion\":\"背\"}]\n```"
        val result = AnalysisResultParser.parse(raw)
        assertEquals(1, result.size)
        assertEquals("乘法口诀", result[0].knowledgePoint)
    }

    @Test
    fun `tolerates surrounding prose`() {
        val raw = "分析结果如下：\n[{\"knowledgePoint\":\"进位加法\",\"description\":\"忘进位\",\"mastery\":30,\"suggestion\":\"练\"}]\n以上。"
        val result = AnalysisResultParser.parse(raw)
        assertEquals(1, result.size)
    }

    @Test
    fun `empty array returns empty list`() {
        assertTrue(AnalysisResultParser.parse("[]").isEmpty())
        assertTrue(AnalysisResultParser.parse("没有可提取的内容").isEmpty())
    }
}
