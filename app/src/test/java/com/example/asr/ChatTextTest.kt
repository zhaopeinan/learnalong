package com.example.asr

import com.example.asr.data.local.entity.ChatMessageEntity
import com.example.asr.data.local.entity.ChatMode
import com.example.asr.data.local.entity.ChatRole
import com.example.asr.domain.ChatText
import com.example.asr.data.repository.ChatRepository
import org.junit.Assert.assertEquals
import org.junit.Test

/** 辅导对话纯文本逻辑（对齐小程序 tutorChat.ts 的 cleanReplyText / 开场白 / 历史拼接 / 自动标题） */
class ChatTextTest {

    // ---------- cleanReplyText ----------

    @Test
    fun `cleanReplyText removes Chinese parentheses and content`() {
        assertEquals("你好呀！我们开始吧", ChatText.cleanReplyText("你好呀！（温柔地笑）我们开始吧"))
    }

    @Test
    fun `cleanReplyText removes English parentheses and content`() {
        assertEquals("答案是对的吗", ChatText.cleanReplyText("答案是对的(smiles)吗"))
    }

    @Test
    fun `cleanReplyText removes markdown symbols`() {
        assertEquals("重点 来了", ChatText.cleanReplyText("**重点** #来了##"))
    }

    @Test
    fun `cleanReplyText collapses whitespace and trims`() {
        assertEquals("你 好", ChatText.cleanReplyText("  你   好  "))
    }

    @Test
    fun `cleanReplyText combined cleanup matches miniprogram order`() {
        // 去括号 → 去 markdown → 压缩空白 → trim
        assertEquals(
            "好的，我们来看这道题 吧",
            ChatText.cleanReplyText("（轻轻拍手）**好的**，我们来看这道题  \n 吧"),
        )
    }

    @Test
    fun `cleanReplyText empty after cleaning`() {
        assertEquals("", ChatText.cleanReplyText("（嗯）(**)"))
    }

    // ---------- openingGreeting ----------

    @Test
    fun `opening greeting free mode`() {
        assertEquals(
            "你好呀小明！今天想问什么问题呀？可以说给我听，也可以拍张照片给我看。",
            ChatText.openingGreeting(ChatMode.FREE, "小明"),
        )
    }

    @Test
    fun `opening greeting weakpoint mode`() {
        assertEquals(
            "你好呀小明！今天我们一起来把这个小知识点弄明白，老师一步一步陪你，答错也没关系。准备好了吗？",
            ChatText.openingGreeting(ChatMode.WEAKPOINT, "小明"),
        )
    }

    @Test
    fun `opening greeting exercise mode`() {
        assertEquals(
            "你好呀小明！做题卡住了吗？告诉老师你卡在哪一道，我们一起想办法。",
            ChatText.openingGreeting(ChatMode.EXERCISE, "小明"),
        )
    }

    // ---------- buildHistoryText ----------

    private fun msg(role: String, text: String, contextText: String? = null) =
        ChatMessageEntity(id = 0, sessionId = 1, role = role, text = text, contextText = contextText, createdAt = 0)

    @Test
    fun `history text joins with role labels and blank lines`() {
        val history = listOf(
            msg(ChatRole.USER, "这题怎么做"),
            msg(ChatRole.ASSISTANT, "你先看看题目问的是什么"),
        )
        assertEquals("孩子：这题怎么做\n\n老师：你先看看题目问的是什么", ChatText.buildHistoryText(history))
    }

    @Test
    fun `history text prefers contextText over display text`() {
        val history = listOf(
            msg(ChatRole.USER, "这题不会", contextText = "孩子发来 1 张照片。照片内容：3+5="),
        )
        assertEquals("孩子：孩子发来 1 张照片。照片内容：3+5=", ChatText.buildHistoryText(history))
    }

    // ---------- autoTitle ----------

    @Test
    fun `auto title keeps short text`() {
        assertEquals("为什么天是蓝的", ChatText.autoTitle("为什么天是蓝的"))
    }

    @Test
    fun `auto title truncates beyond 12 chars with ellipsis`() {
        assertEquals("一二三四五六七八九十十一…", ChatText.autoTitle("一二三四五六七八九十十一十二十三"))
    }

    // ---------- 上限常量（对应小程序 MAX_SESSIONS / CONTEXT_MESSAGES） ----------

    @Test
    fun `session cap is 30 and context window is 12`() {
        assertEquals(30, ChatRepository.MAX_SESSIONS)
        assertEquals(12, ChatRepository.CONTEXT_MESSAGES)
        assertEquals(4, ChatText.MAX_CHAT_IMAGES)
    }
}
