package com.example.asr

import com.example.asr.domain.KidReward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 孩子端星星激励结算（对齐小程序 utils/kidReward.ts 与 kid-progress onMark） */
class KidRewardTest {

    @Test
    fun `mastery 100 is defeated, below is not`() {
        assertTrue(KidReward.isDefeated(100))
        assertTrue(KidReward.isDefeated(120))
        assertFalse(KidReward.isDefeated(99))
        assertFalse(KidReward.isDefeated(0))
    }

    @Test
    fun `almost done means 80 to 99`() {
        assertFalse(KidReward.isAlmostDone(79))
        assertTrue(KidReward.isAlmostDone(80))
        assertTrue(KidReward.isAlmostDone(99))
        assertFalse(KidReward.isAlmostDone(100))
    }

    @Test
    fun `not mastered gives no star, only encouragement`() {
        assertEquals(KidReward.Outcome.TRY_AGAIN, KidReward.outcomeFor(mastered = false, newMastery = 0))
        assertEquals(KidReward.Outcome.TRY_AGAIN, KidReward.outcomeFor(mastered = false, newMastery = 100))
    }

    @Test
    fun `mastered below full mastery earns a star`() {
        assertEquals(KidReward.Outcome.STAR, KidReward.outcomeFor(mastered = true, newMastery = 60))
        assertEquals(KidReward.Outcome.STAR, KidReward.outcomeFor(mastered = true, newMastery = 99))
    }

    @Test
    fun `mastered at full mastery defeats the monster`() {
        assertEquals(KidReward.Outcome.DEFEATED, KidReward.outcomeFor(mastered = true, newMastery = 100))
    }

    @Test
    fun `random praise always comes from miniprogram praise list`() {
        val random = Random(42)
        repeat(50) {
            assertTrue(KidReward.randomPraise(random) in KidReward.PRAISES)
        }
    }

    @Test
    fun `star toast is praise plus star suffix`() {
        val toast = KidReward.starToast(Random(1))
        assertTrue(KidReward.PRAISES.any { toast.startsWith(it) })
        assertTrue(toast.endsWith("⭐+1"))
    }
}
