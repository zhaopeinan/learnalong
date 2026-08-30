package com.example.asr.data.remote

import com.example.asr.data.remote.dto.WeakPointResult
import kotlinx.serialization.json.Json

/**
 * 解析 LLM 分析输出：要求模型只返回 JSON 数组，但实际输出可能带 markdown 代码围栏
 * 或前后多余文字，这里做剥离与截取的容错处理。
 */
object AnalysisResultParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): List<WeakPointResult> {
        val cleaned = stripCodeFence(raw)
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val candidate = cleaned.substring(start, end + 1)
        return try {
            json.decodeFromString<List<WeakPointResult>>(candidate)
                .filter { it.knowledgePoint.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun stripCodeFence(text: String): String {
        var t = text.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            val endFence = t.lastIndexOf("```")
            if (endFence >= 0) t = t.substring(0, endFence)
        }
        return t.trim()
    }
}
