package com.example.asr

import com.example.asr.domain.EbbinghausScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EbbinghausSchedulerTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, 30, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `dayStartOf returns midnight of same day`() {
        val now = at(2026, 8, 27, 15)
        val start = EbbinghausScheduler.dayStartOf(now)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(27, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `initial schedule is stage 0 due next day`() {
        val now = at(2026, 8, 27)
        val out = EbbinghausScheduler.initialSchedule(now)
        assertEquals(0, out.newStage)
        assertFalse(out.finished)
        val expected = EbbinghausScheduler.dayStartOf(now) + 1L * 24 * 60 * 60 * 1000
        assertEquals(expected, out.nextReviewAt)
    }

    @Test
    fun `mastered advances stage and schedules by interval list`() {
        val now = at(2026, 8, 27)
        val out = EbbinghausScheduler.applyReview(stage = 0, mastery = 60, mastered = true, nowMillis = now)
        assertEquals(1, out.newStage)
        assertEquals(70, out.newMastery)
        assertFalse(out.finished)
        // intervals[1] = 2 天
        val expected = EbbinghausScheduler.dayStartOf(now) + 2L * 24 * 60 * 60 * 1000
        assertEquals(expected, out.nextReviewAt)
    }

    @Test
    fun `mastered mastery gain is capped at 100`() {
        val now = at(2026, 8, 27)
        val out = EbbinghausScheduler.applyReview(stage = 0, mastery = 95, mastered = true, nowMillis = now)
        assertEquals(100, out.newMastery)
    }

    @Test
    fun `mastered at last stage finishes without new task`() {
        val now = at(2026, 8, 27)
        val lastStage = EbbinghausScheduler.DEFAULT_INTERVALS_DAYS.size - 1
        val out = EbbinghausScheduler.applyReview(stage = lastStage, mastery = 90, mastered = true, nowMillis = now)
        assertEquals(lastStage + 1, out.newStage)
        assertTrue(out.finished)
        assertEquals(EbbinghausScheduler.FINISHED, out.nextReviewAt)
    }

    @Test
    fun `still weak resets stage and penalizes mastery`() {
        val now = at(2026, 8, 27)
        val out = EbbinghausScheduler.applyReview(stage = 3, mastery = 50, mastered = false, nowMillis = now)
        assertEquals(0, out.newStage)
        assertEquals(30, out.newMastery)
        assertFalse(out.finished)
        val expected = EbbinghausScheduler.dayStartOf(now) + 1L * 24 * 60 * 60 * 1000
        assertEquals(expected, out.nextReviewAt)
    }

    @Test
    fun `mastery penalty has floor of zero`() {
        val now = at(2026, 8, 27)
        val out = EbbinghausScheduler.applyReview(stage = 2, mastery = 10, mastered = false, nowMillis = now)
        assertEquals(0, out.newMastery)
    }
}
