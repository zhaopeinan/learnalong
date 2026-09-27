package com.example.asr

import com.example.asr.domain.KidPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 积分乐园纯逻辑：加减/兑换扣减/不足拦截/目标进度 */
class KidPointsTest {

    @Test
    fun `earn adds delta to points`() {
        assertEquals(5, KidPoints.afterEarn(0, 5))
        assertEquals(17, KidPoints.afterEarn(12, 5))
    }

    @Test
    fun `redeem deducts target when points suffice`() {
        assertEquals(0, KidPoints.afterRedeem(50, 50))
        assertEquals(7, KidPoints.afterRedeem(57, 50))
    }

    @Test
    fun `redeem is blocked when points are insufficient`() {
        assertNull(KidPoints.afterRedeem(49, 50))
        assertNull(KidPoints.afterRedeem(0, 1))
    }

    @Test
    fun `achieved means points at or above target`() {
        assertTrue(KidPoints.isAchieved(50, 50))
        assertTrue(KidPoints.isAchieved(51, 50))
        assertFalse(KidPoints.isAchieved(49, 50))
        assertFalse(KidPoints.isAchieved(10, 0))
    }

    @Test
    fun `progress is clamped between 0 and 1`() {
        assertEquals(0f, KidPoints.progress(0, 100), 0.001f)
        assertEquals(0.5f, KidPoints.progress(50, 100), 0.001f)
        assertEquals(1f, KidPoints.progress(150, 100), 0.001f)
        assertEquals(0f, KidPoints.progress(10, 0), 0.001f)
    }

    @Test
    fun `remaining counts down to zero`() {
        assertEquals(100, KidPoints.remaining(0, 100))
        assertEquals(40, KidPoints.remaining(60, 100))
        assertEquals(0, KidPoints.remaining(120, 100))
    }

    @Test
    fun `task validation requires name and 1-99 points`() {
        assertTrue(KidPoints.isValidTask("做家务", 5))
        assertTrue(KidPoints.isValidTask("做家务", 1))
        assertTrue(KidPoints.isValidTask("做家务", 99))
        assertFalse(KidPoints.isValidTask("", 5))
        assertFalse(KidPoints.isValidTask("  ", 5))
        assertFalse(KidPoints.isValidTask("做家务", 0))
        assertFalse(KidPoints.isValidTask("做家务", 100))
    }

    @Test
    fun `goal validation requires name and positive target`() {
        assertTrue(KidPoints.isValidGoal("去游乐园", 100))
        assertFalse(KidPoints.isValidGoal("", 100))
        assertFalse(KidPoints.isValidGoal("去游乐园", 0))
        assertFalse(KidPoints.isValidGoal("去游乐园", -5))
    }

    @Test
    fun `default tasks match product spec`() {
        assertEquals(4, KidPoints.DEFAULT_TASKS.size)
        assertEquals("做家务" to 5, KidPoints.DEFAULT_TASKS[0])
        assertEquals("认真写作业" to 5, KidPoints.DEFAULT_TASKS[1])
        assertEquals("课外习题" to 3, KidPoints.DEFAULT_TASKS[2])
        assertEquals("自己收拾书包" to 2, KidPoints.DEFAULT_TASKS[3])
    }
}
