package com.example.asr.data.remote

import com.example.asr.data.remote.dto.Exercise
import com.example.asr.data.remote.dto.TaskContent
import kotlinx.serialization.json.Json

/**
 * 解析 LLM 出题输出：要求返回 JSON 对象，做剥离围栏、截取 {} 的容错处理。
 */
object TaskContentParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析失败（无有效题目）返回 null */
    fun parse(raw: String): TaskContent? {
        val cleaned = stripCodeFence(raw)
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.decodeFromString<TaskContent>(cleaned.substring(start, end + 1))
                .takeIf { it.exercises.any { e -> e.question.isNotBlank() } }
                ?.let { c -> c.copy(exercises = c.exercises.filter { e -> e.question.isNotBlank() }) }
        } catch (e: Exception) {
            null
        }
    }

    /** 单题替换输出解析（练习页「换一题」）：{"question","answer","hint"} */
    fun parseExerciseItem(raw: String): Exercise? {
        val cleaned = stripCodeFence(raw)
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.decodeFromString<Exercise>(cleaned.substring(start, end + 1))
                .takeIf { it.question.isNotBlank() }
        } catch (e: Exception) {
            null
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
