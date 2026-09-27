package com.example.asr

import com.example.asr.media.SoundEffects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 音效 PCM 生成：时长正确、振幅不溢出、非静音、结尾淡出不爆音 */
class SoundEffectsTest {

    @Test
    fun `earn pcm is about 0_6 seconds`() {
        val pcm = SoundEffects.earnPcm()
        assertEquals((0.6f * SoundEffects.SAMPLE_RATE).toInt(), pcm.size)
    }

    @Test
    fun `redeem pcm is about 1_5 seconds`() {
        val pcm = SoundEffects.redeemPcm()
        assertEquals((1.5f * SoundEffects.SAMPLE_RATE).toInt(), pcm.size)
    }

    @Test
    fun `pcm amplitude never overflows`() {
        listOf(SoundEffects.earnPcm(), SoundEffects.redeemPcm()).forEach { pcm ->
            pcm.forEach { sample ->
                assertTrue(abs(sample.toInt()) <= Short.MAX_VALUE)
            }
        }
    }

    @Test
    fun `pcm is not silent`() {
        listOf(SoundEffects.earnPcm(), SoundEffects.redeemPcm()).forEach { pcm ->
            assertTrue(pcm.any { it.toInt() != 0 })
            val peak = pcm.maxOf { abs(it.toInt()) }
            // 归一化后峰值应接近 0.85 满幅
            assertTrue(peak > Short.MAX_VALUE / 2)
        }
    }

    @Test
    fun `pcm ends with fade out`() {
        val pcm = SoundEffects.earnPcm()
        // 结尾 20ms 淡出：末尾样本接近零，无咔哒声
        assertTrue(abs(pcm[pcm.size - 1].toInt()) < Short.MAX_VALUE / 100)
    }

    @Test
    fun `render with empty notes produces silence of correct length`() {
        val pcm = SoundEffects.render(emptyList(), 0.3f)
        assertEquals((0.3f * SoundEffects.SAMPLE_RATE).toInt(), pcm.size)
        assertTrue(pcm.all { it.toInt() == 0 })
    }

    @Test
    fun `note starting beyond total duration is safely skipped`() {
        val pcm = SoundEffects.render(
            listOf(SoundEffects.Note(440f, 5.0f, 1.0f)),
            0.3f,
        )
        assertTrue(pcm.all { it.toInt() == 0 })
    }
}
