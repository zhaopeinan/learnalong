package com.example.asr.domain

import com.example.asr.data.local.dao.MasteryHistoryRow
import com.example.asr.data.local.dao.SubjectCount
import java.util.Calendar

/** 本周成长报告的纯计算逻辑（方便单测） */
object GrowthReport {

    data class Improvement(
        val weakPointId: Long,
        val knowledgePoint: String,
        val childName: String,
        val subject: String,
        val gain: Int,
    )

    data class WeeklyReport(
        val reviewsCompleted: Int,
        val subjectCounts: List<SubjectCount>,
        val improvements: List<Improvement>,
    )

    /** 本周一零点（周一为一周起点） */
    fun weekStartOf(nowMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 汇总周报：复习次数按科目；进步 = 本周内某薄弱点最后一次快照 - 第一次快照，
     * 只保留有提升的，按提升幅度降序取前 3。本周无任何活动时返回 null（界面隐藏卡片）。
     */
    fun buildWeekly(
        historyRows: List<MasteryHistoryRow>,
        subjectCounts: List<SubjectCount>,
    ): WeeklyReport? {
        val reviewsCompleted = subjectCounts.sumOf { it.count }
        val improvements = historyRows
            .groupBy { it.weakPointId }
            .mapNotNull { (id, rows) ->
                val gain = rows.last().mastery - rows.first().mastery
                if (gain <= 0) return@mapNotNull null
                val first = rows.first()
                Improvement(id, first.knowledgePoint, first.childName, first.subject, gain)
            }
            .sortedByDescending { it.gain }
            .take(3)
        if (reviewsCompleted == 0 && improvements.isEmpty()) return null
        return WeeklyReport(reviewsCompleted, subjectCounts, improvements)
    }
}
