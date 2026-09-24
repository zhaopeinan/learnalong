package com.example.asr.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 工作端待办草稿（解析结果，未入库） */
data class WorkTodoDraft(
    val text: String,
    val assignee: String,
    val deadline: String,
)

/** 工作端 AI 分析结果（对应小程序 parsers.ts WorkAnalysisResult） */
data class WorkAnalysisResult(
    val title: String,
    val summary: String,
    val todos: List<WorkTodoDraft>,
)

/**
 * 解析工作录音分析的 JSON 对象输出（对应小程序 parseWorkAnalysis）：
 * 容忍 markdown 代码围栏与前后多余文字；解析失败返回 null（调用方按失败处理）。
 */
object WorkAnalysisParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): WorkAnalysisResult? {
        val cleaned = stripCodeFence(raw)
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = try {
            json.parseToJsonElement(cleaned.substring(start, end + 1)) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return null
        val todos = (obj["todos"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { t ->
                val text = (t["text"] as? JsonPrimitive)?.contentOrNull?.trim()
                if (text.isNullOrEmpty()) {
                    null
                } else {
                    WorkTodoDraft(
                        text = text,
                        assignee = (t["assignee"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
                        deadline = (t["deadline"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
                    )
                }
            }
        return WorkAnalysisResult(
            title = (obj["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
            summary = (obj["summary"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty(),
            todos = todos,
        )
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
