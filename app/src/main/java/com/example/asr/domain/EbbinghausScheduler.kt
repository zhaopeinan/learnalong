package com.example.asr.domain

import java.util.Calendar

/**
 * 艾宾浩斯复习调度（纯函数，方便单测）。
 * 间隔：1 → 2 → 4 → 7 → 15 → 30 天；reviewStage 从 0 开始。
 */
object EbbinghausScheduler {

    val DEFAULT_INTERVALS_DAYS: List<Int> = listOf(1, 2, 4, 7, 15, 30)

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    const val MASTERY_GAIN = 10     // “已掌握”加分（上限 100）
    const val MASTERY_PENALTY = 20  // “仍薄弱”降分（下限 0）
    const val FINISHED = -1L  // nextReviewAt 的“已完成”标记值

    data class ReviewOutcome(
        val newStage: Int,
        val newMastery: Int,
        /** -1 表示已完成，不再生成任务 */
        val nextReviewAt: Long,
        val finished: Boolean,
    )

    /** 新薄弱点首次排期：stage=0，明日零点到期 */
    fun initialSchedule(nowMillis: Long, intervals: List<Int> = DEFAULT_INTERVALS_DAYS): ReviewOutcome {
        val next = dayStartOf(nowMillis) + intervals.first() * DAY_MILLIS
        return ReviewOutcome(newStage = 0, newMastery = -1 /* 由调用方保留原 mastery */, nextReviewAt = next, finished = false)
    }

    /**
     * 复习反馈：
     * - mastered=true → stage+1，mastery 加 10（上限 100）；超过间隔列表末尾则完成（nextReviewAt=-1）
     * - mastered=false → stage 归 0，mastery 降 20（下限 0），重新排期
     */
    fun applyReview(
        stage: Int,
        mastery: Int,
        mastered: Boolean,
        nowMillis: Long,
        intervals: List<Int> = DEFAULT_INTERVALS_DAYS,
    ): ReviewOutcome {
        val gained = (mastery + MASTERY_GAIN).coerceAtMost(100)
        return if (mastered) {
            val newStage = stage + 1
            if (newStage >= intervals.size) {
                ReviewOutcome(newStage = newStage, newMastery = gained, nextReviewAt = FINISHED, finished = true)
            } else {
                val next = dayStartOf(nowMillis) + intervals[newStage] * DAY_MILLIS
                ReviewOutcome(newStage = newStage, newMastery = gained, nextReviewAt = next, finished = false)
            }
        } else {
            val newMastery = (mastery - MASTERY_PENALTY).coerceAtLeast(0)
            val next = dayStartOf(nowMillis) + intervals.first() * DAY_MILLIS
            ReviewOutcome(newStage = 0, newMastery = newMastery, nextReviewAt = next, finished = false)
        }
    }

    /** 某时间戳所在自然日的零点 */
    fun dayStartOf(timeMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 当日 23:59:59.999，用于 dueDate<=今天 的比较 */
    fun dayEndOf(timeMillis: Long): Long = dayStartOf(timeMillis) + DAY_MILLIS - 1
}
