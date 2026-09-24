package com.example.asr.domain

import com.example.asr.data.local.entity.ChatMessageEntity
import com.example.asr.data.local.entity.ChatMode
import com.example.asr.data.local.entity.ChatRole

/**
 * 辅导对话纯文本逻辑（与小程序 tutorChat.ts 的 cleanReplyText / addOpeningGreeting /
 * 历史拼接 / 自动标题逐字对齐）。
 */
object ChatText {

    /** 一次最多处理的照片数（对应小程序 MAX_CHAT_IMAGES，兼顾 VLM 输入预算与等待时长） */
    const val MAX_CHAT_IMAGES = 4

    private val CN_PARENS = Regex("（[^（）]*）")
    private val EN_PARENS = Regex("\\([^()]*\\)")
    private val MARKDOWN_SYMBOLS = Regex("[*#`>]")
    private val MULTI_WHITESPACE = Regex("\\s{2,}")

    /**
     * 清洗 AI 回复（界面显示与语音播报共用）：
     * 去掉括号及其中内容（如"（温柔地笑）"语气描述）、markdown 符号等。
     */
    fun cleanReplyText(text: String): String =
        text
            .replace(CN_PARENS, "")
            .replace(EN_PARENS, "")
            .replace(MARKDOWN_SYMBOLS, "")
            .replace(MULTI_WHITESPACE, " ")
            .trim()

    /** 固定开场白（不调大模型，秒出）：按会话模式给孩子一句招呼 */
    fun openingGreeting(mode: String, childName: String): String = when (mode) {
        ChatMode.WEAKPOINT ->
            "你好呀${childName}！今天我们一起来把这个小知识点弄明白，老师一步一步陪你，答错也没关系。准备好了吗？"
        ChatMode.EXERCISE ->
            "你好呀${childName}！做题卡住了吗？告诉老师你卡在哪一道，我们一起想办法。"
        else ->
            "你好呀${childName}！今天想问什么问题呀？可以说给我听，也可以拍张照片给我看。"
    }

    /**
     * 多轮上下文拼接（与小程序一致）：
     * 每条消息一行「孩子：…」/「老师：…」，双换行连接；有 contextText（照片描述等）时优先用。
     */
    fun buildHistoryText(messages: List<ChatMessageEntity>): String =
        messages.joinToString("\n\n") { m ->
            val who = if (m.role == ChatRole.USER) "孩子" else "老师"
            "$who：${m.contextText ?: m.text}"
        }

    /** 自由提问的会话标题：第一条提问截断 12 字（与小程序一致） */
    fun autoTitle(text: String): String =
        if (text.length > 12) text.take(12) + "…" else text
}
