package com.example.asr

import com.example.asr.domain.SocraticPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 苏格拉底辅导 system prompt 组装（对齐小程序 prompts.ts buildSocraticSystem） */
class SocraticPromptTest {

    private fun assertPlaceholdersFilled(prompt: String) {
        assertFalse(prompt.contains("{{CHILD_INFO}}"))
        assertFalse(prompt.contains("{{CONTEXT_BLOCK}}"))
    }

    @Test
    fun `free mode without grade asks age first`() {
        val prompt = SocraticPrompt.build(SocraticPrompt.Context(childName = "小明", grade = null))
        assertTrue(prompt.contains("【孩子信息】小明。请先用简单的问候确认孩子的年龄段，再用合适的语言提问。"))
        assertTrue(prompt.contains("【辅导主题】孩子自由提问。先温柔地问孩子：\"你今天想问什么问题呀？\""))
        assertPlaceholdersFilled(prompt)
    }

    @Test
    fun `free mode with grade pins difficulty`() {
        val prompt = SocraticPrompt.build(SocraticPrompt.Context(childName = "小明", grade = "二年级"))
        assertTrue(prompt.contains("【孩子信息】小明，二年级。请严格用适合二年级孩子的语言和难度来提问。"))
        assertPlaceholdersFilled(prompt)
    }

    @Test
    fun `free mode with subject mentions subject in context`() {
        val prompt = SocraticPrompt.build(
            SocraticPrompt.Context(childName = "小明", grade = null, subject = "英语")
        )
        assertTrue(prompt.contains("【辅导主题】孩子自由提问（科目：英语）。先温柔地问孩子：\"你今天想问什么英语问题呀？\""))
        assertPlaceholdersFilled(prompt)
    }

    @Test
    fun `weakpoint mode injects knowledge point context`() {
        val prompt = SocraticPrompt.build(
            SocraticPrompt.Context(
                childName = "小红",
                grade = "三年级",
                weakPoint = SocraticPrompt.WeakPointCtx(
                    knowledgePoint = "声母 b 和 p 混淆",
                    description = "把「拔」读成「趴」",
                    subject = "拼音",
                ),
            )
        )
        assertTrue(prompt.contains("【辅导主题】孩子在「拼音」科目的薄弱点：声母 b 和 p 混淆。\n具体表现：把「拔」读成「趴」"))
        assertTrue(prompt.contains("从一个简单的小问题自然引入"))
        assertFalse(prompt.contains("孩子自由提问"))
        assertPlaceholdersFilled(prompt)
    }

    @Test
    fun `exercise mode lists numbered questions`() {
        val prompt = SocraticPrompt.build(
            SocraticPrompt.Context(
                childName = "小红",
                grade = null,
                exercise = SocraticPrompt.ExerciseCtx(
                    questions = listOf("3 + 5 = ?", "7 - 2 = ?"),
                    subject = "数学",
                ),
            )
        )
        assertTrue(prompt.contains("【辅导主题】孩子正在做「数学」的练习题，遇到了困难。当前题目：\n1. 3 + 5 = ?\n2. 7 - 2 = ?"))
        assertTrue(prompt.contains("绝对不要替孩子做题。"))
        assertPlaceholdersFilled(prompt)
    }

    @Test
    fun `system prompt core rules are intact`() {
        // 与小程序 SOCRATIC_SYSTEM 一致的关键规则
        assertTrue(SocraticPrompt.SYSTEM.contains("你是一位温柔耐心的老师，正在用「苏格拉底提问法」辅导孩子学习。你的对话对象就是孩子本人。"))
        assertTrue(SocraticPrompt.SYSTEM.contains("7. 绝对不要输出任何括号及括号内容"))
        assertTrue(SocraticPrompt.SYSTEM.endsWith("{{CONTEXT_BLOCK}}"))
    }

    @Test
    fun `photo describe prompt with question`() {
        assertEquals(
            "孩子发来了 2 张照片，并问：「这道题怎么做」。请把每张照片的内容详细转述成文字。",
            SocraticPrompt.buildPhotoDescribePrompt(" 这道题怎么做 ", 2),
        )
    }

    @Test
    fun `photo describe prompt without question`() {
        assertEquals(
            "孩子发来了 1 张照片。请把每张照片的内容详细转述成文字。",
            SocraticPrompt.buildPhotoDescribePrompt("", 1),
        )
    }
}
