package com.example.asr.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
)

/** 多模态消息内容块：文本或图片（data URL base64） */
@Serializable
data class VisionContentPart(
    val type: String, // "text" | "image_url"
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrl? = null,
) {
    @Serializable
    data class ImageUrl(val url: String)

    companion object {
        fun text(text: String) = VisionContentPart(type = "text", text = text)
        fun image(dataUrl: String) = VisionContentPart(type = "image_url", imageUrl = ImageUrl(dataUrl))
    }
}

@Serializable
data class VisionChatMessage(
    val role: String,
    val content: List<VisionContentPart>,
)

@Serializable
data class VisionChatRequest(
    val model: String,
    val messages: List<VisionChatMessage>,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
)

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList(),
) {
    @Serializable
    data class Choice(
        val message: ChatMessage? = null,
    )

    val text: String
        get() = choices.firstOrNull()?.message?.content.orEmpty()
}

/** LLM 分析输出中的单条薄弱点 */
@Serializable
data class WeakPointResult(
    @SerialName("knowledgePoint") val knowledgePoint: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("mastery") val mastery: Int = 50,
    @SerialName("suggestion") val suggestion: String = "",
)

/** LLM 查重输出：新提取项序号 → 重复的已有记录 id（null 表示无重复） */
@Serializable
data class DedupEntry(
    @SerialName("index") val index: Int = -1,
    @SerialName("duplicateOf") val duplicateOf: Long? = null,
)

/** LLM 出题输出：复习练习内容 */
@Serializable
data class TaskContent(
    @SerialName("exercises") val exercises: List<Exercise> = emptyList(),
    @SerialName("tips") val tips: String = "",
)

@Serializable
data class Exercise(
    @SerialName("question") val question: String = "",
    @SerialName("answer") val answer: String = "",
    @SerialName("hint") val hint: String = "",
)
