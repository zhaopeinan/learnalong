package com.example.asr

import com.example.asr.data.local.dao.MasteryHistoryRow
import com.example.asr.data.local.dao.SubjectCount
import com.example.asr.domain.GrowthReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GrowthReportTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, 30, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `weekStartOf returns Monday midnight`() {
        // 2026-08-28 是周五
        val weekStart = GrowthReport.weekStartOf(at(2026, 8, 28, 15))
        val cal = Calendar.getInstance().apply { timeInMillis = weekStart }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(24, cal.get(Calendar.DAY_OF_MONTH)) // 8 月 24 日是周一
    }

    @Test
    fun `weekStartOf on Monday itself stays same day`() {
        val weekStart = GrowthReport.weekStartOf(at(2026, 8, 24, 9))
        val cal = Calendar.getInstance().apply { timeInMillis = weekStart }
        assertEquals(24, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `buildWeekly returns null when no activity`() {
        assertNull(GrowthReport.buildWeekly(emptyList(), emptyList()))
    }

    @Test
    fun `buildWeekly sums reviews and sorts improvements by gain desc`() {
        val rows = listOf(
            MasteryHistoryRow(weakPointId = 1, mastery = 40, recordedAt = 1, knowledgePoint = "分数通分", subject = "数学", childName = "小宇"),
            MasteryHistoryRow(weakPointId = 1, mastery = 50, recordedAt = 2, knowledgePoint = "分数通分", subject = "数学", childName = "小宇"),
            MasteryHistoryRow(weakPointId = 1, mastery = 70, recordedAt = 3, knowledgePoint = "分数通分", subject = "数学", childName = "小宇"),
            MasteryHistoryRow(weakPointId = 2, mastery = 60, recordedAt = 2, knowledgePoint = "拼音声调", subject = "拼音", childName = "小可"),
            MasteryHistoryRow(weakPointId = 2, mastery = 55, recordedAt = 3, knowledgePoint = "拼音声调", subject = "拼音", childName = "小可"), // 下降，不计入
        )
        val report = GrowthReport.buildWeekly(
            historyRows = rows,
            subjectCounts = listOf(SubjectCount("数学", 3), SubjectCount("拼音", 1)),
        )!!
        assertEquals(4, report.reviewsCompleted)
        assertEquals(1, report.improvements.size)
        val imp = report.improvements.first()
        assertEquals(1, imp.weakPointId)
        assertEquals(30, imp.gain)
        assertEquals("分数通分", imp.knowledgePoint)
        assertTrue(report.subjectCounts.first().subject == "数学")
    }

    @Test
    fun `buildWeekly keeps report when only reviews without improvements`() {
        val report = GrowthReport.buildWeekly(
            historyRows = emptyList(),
            subjectCounts = listOf(SubjectCount("英语", 2)),
        )!!
        assertEquals(2, report.reviewsCompleted)
        assertTrue(report.improvements.isEmpty())
    }
}
