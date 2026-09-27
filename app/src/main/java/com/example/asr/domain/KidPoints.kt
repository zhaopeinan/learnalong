package com.example.asr.domain

/**
 * 积分乐园纯逻辑（Android 原生功能，小程序无对应）：
 * 加分/兑换扣减/目标进度计算，供 ViewModel 与单测共用。
 */
object KidPoints {

    const val MIN_TASK_POINTS = 1
    const val MAX_TASK_POINTS = 99

    /** 首次进入积分页预置的默认任务（名称 + 分值） */
    val DEFAULT_TASKS = listOf(
        "做家务" to 5,
        "认真写作业" to 5,
        "课外习题" to 3,
        "自己收拾书包" to 2,
    )

    /** 任务合法性：名称非空，分值 1-99 */
    fun isValidTask(name: String, points: Int): Boolean =
        name.isNotBlank() && points in MIN_TASK_POINTS..MAX_TASK_POINTS

    /** 目标合法性：名称非空，目标分值 > 0 */
    fun isValidGoal(name: String, targetPoints: Int): Boolean =
        name.isNotBlank() && targetPoints > 0

    /** 加分后积分 */
    fun afterEarn(points: Int, delta: Int): Int = points + delta

    /** 是否达成目标（积分 >= 目标分值） */
    fun isAchieved(points: Int, targetPoints: Int): Boolean =
        targetPoints > 0 && points >= targetPoints

    /** 兑换扣减：积分不足返回 null（拦截），否则返回扣减后积分 */
    fun afterRedeem(points: Int, targetPoints: Int): Int? =
        if (isAchieved(points, targetPoints)) points - targetPoints else null

    /** 目标进度 0f..1f（未设目标为 0） */
    fun progress(points: Int, targetPoints: Int): Float =
        if (targetPoints <= 0) 0f else (points.toFloat() / targetPoints).coerceIn(0f, 1f)

    /** 还差多少分（已达标为 0） */
    fun remaining(points: Int, targetPoints: Int): Int =
        (targetPoints - points).coerceAtLeast(0)
}
