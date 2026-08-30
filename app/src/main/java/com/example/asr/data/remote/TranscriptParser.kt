package com.example.asr.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

data class ParsedSegment(
    val speakerLabel: String,
    val startSec: Float,
    val endSec: Float,
    val text: String,
)

/**
 * ASR 转写结果解析接口。XingChenAGI/XingChenASR-Diarize-V3.0 的响应格式尚未公开验证，
 * 若真实返回与默认实现不符，新增一个实现替换即可，调用方不用改。
 */
interface TranscriptParser {
    fun parse(rawResponse: String): List<ParsedSegment>
}

/**
 * 默认按 verbose_json 风格解析：
 * - 优先找 segments 数组，每项取 speaker/start/end/text（字段名宽松匹配）
 * - 没有 segments 则退化为整段 text 的单条记录（speaker=UNKNOWN）
 * - 任何解析异常都不会丢原始文本
 */
class VerboseJsonTranscriptParser : TranscriptParser {

    private val json = Json { ignoreUnknownKeys = true }

    override fun parse(rawResponse: String): List<ParsedSegment> {
        val root: JsonElement = try {
            json.parseToJsonElement(rawResponse)
        } catch (e: Exception) {
            // 不是合法 JSON：整段原始文本保留为一条记录
            return fallback(rawResponse)
        }

        val rootObj = root as? JsonObject ?: return fallback(root.toPlainText())

        val segments = findSegmentsArray(rootObj)
        if (segments != null && segments.isNotEmpty()) {
            val parsed = segments.mapNotNull { parseSegment(it) }
            if (parsed.isNotEmpty()) return parsed
        }

        // 无 segments：尝试 root.text / 拼接每项 text
        val wholeText = rootObj.firstText(TEXT_KEYS) ?: run {
            val pieces = segments?.mapNotNull { (it as? JsonObject)?.firstText(TEXT_KEYS) }.orEmpty()
            pieces.joinToString("\n").ifEmpty { null }
        }
        return fallback(wholeText ?: rootObj.toPlainText())
    }

    private fun fallback(text: String): List<ParsedSegment> =
        if (text.isBlank()) emptyList()
        else listOf(ParsedSegment(speakerLabel = UNKNOWN_SPEAKER, startSec = 0f, endSec = 0f, text = text.trim()))

    /** 在任意深度上找名为 segments/utterances/sentences 的数组 */
    private fun findSegmentsArray(obj: JsonObject, depth: Int = 0): JsonArray? {
        if (depth > 3) return null
        for (key in SEGMENT_ARRAY_KEYS) {
            val arr = obj[key] as? JsonArray
            if (arr != null) return arr
        }
        for ((_, v) in obj) {
            if (v is JsonObject) {
                val found = findSegmentsArray(v, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }

    private fun parseSegment(element: JsonElement): ParsedSegment? {
        val obj = element as? JsonObject ?: return null
        val text = obj.firstText(TEXT_KEYS)?.trim().orEmpty()
        if (text.isEmpty()) return null
        val speaker = obj.firstText(SPEAKER_KEYS)?.trim()?.ifEmpty { null } ?: UNKNOWN_SPEAKER
        val start = obj.firstNumber(START_KEYS) ?: 0.0
        val end = obj.firstNumber(END_KEYS) ?: start
        return ParsedSegment(
            speakerLabel = speaker,
            startSec = start.toFloat(),
            endSec = end.toFloat(),
            text = text,
        )
    }

    private fun JsonObject.firstText(keys: List<String>): String? {
        for (k in keys) {
            val v = this[k] ?: continue
            if (v is JsonPrimitive) {
                v.contentOrNull?.let { return it }
            }
        }
        // 宽松匹配：忽略大小写
        for ((k, v) in this) {
            if (v is JsonPrimitive && keys.any { it.equals(k, ignoreCase = true) }) {
                v.contentOrNull?.let { return it }
            }
        }
        return null
    }

    private fun JsonObject.firstNumber(keys: List<String>): Double? {
        for (k in keys) {
            val v = this[k] as? JsonPrimitive ?: continue
            v.doubleOrNull?.let { return it }
        }
        for ((k, v) in this) {
            if (v is JsonPrimitive && keys.any { it.equals(k, ignoreCase = true) }) {
                v.doubleOrNull?.let { return it }
            }
        }
        return null
    }

    private fun JsonElement.toPlainText(): String =
        (this as? JsonPrimitive)?.contentOrNull ?: this.toString()

    companion object {
        const val UNKNOWN_SPEAKER = "UNKNOWN"
        private val SEGMENT_ARRAY_KEYS = listOf("segments", "utterances", "sentences", "results")
        private val SPEAKER_KEYS = listOf("speaker", "speaker_label", "speakerLabel", "spk", "role")
        private val START_KEYS = listOf("start", "begin", "start_time", "startTime")
        private val END_KEYS = listOf("end", "stop", "end_time", "endTime")
        private val TEXT_KEYS = listOf("text", "content", "sentence", "transcript")
    }
}
