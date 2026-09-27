package com.example.asr.data.repository

import com.example.asr.data.local.dao.PointDao
import com.example.asr.data.local.entity.PointGoalEntity
import com.example.asr.data.local.entity.PointRecordEntity
import com.example.asr.data.local.entity.PointTaskEntity
import com.example.asr.domain.KidPoints
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 积分乐园：任务加分 / 目标兑换 / 流水（数据访问封装，纯逻辑见 domain/KidPoints） */
class PointsRepository(private val pointDao: PointDao) {

    fun observePoints(childId: Long): Flow<Int> =
        pointDao.observePoints(childId).map { it ?: 0 }

    fun observeRecords(childId: Long): Flow<List<PointRecordEntity>> =
        pointDao.observeRecords(childId)

    fun observeTasks(childId: Long): Flow<List<PointTaskEntity>> =
        pointDao.observeTasks(childId)

    fun observeGoals(childId: Long): Flow<List<PointGoalEntity>> =
        pointDao.observeGoals(childId)

    /** 完成任务加分：积分 +N 并记流水 */
    suspend fun earn(task: PointTaskEntity) =
        pointDao.earn(task.childId, task.points, task.name, System.currentTimeMillis())

    /** 兑换：扣减目标分值、目标进历史、记负流水；积分不足返回 false（拦截） */
    suspend fun redeem(goal: PointGoalEntity): Boolean =
        pointDao.redeem(goal, System.currentTimeMillis())

    suspend fun addTask(childId: Long, name: String, points: Int) {
        require(KidPoints.isValidTask(name, points)) { "任务名称不能为空，分值限 1-99" }
        pointDao.insertTask(
            PointTaskEntity(
                childId = childId,
                name = name.trim(),
                points = points,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun updateTask(task: PointTaskEntity, name: String, points: Int) {
        require(KidPoints.isValidTask(name, points)) { "任务名称不能为空，分值限 1-99" }
        pointDao.updateTask(task.copy(name = name.trim(), points = points))
    }

    suspend fun deleteTask(task: PointTaskEntity) = pointDao.deleteTask(task.id)

    /** 设置新目标（旧目标已兑现后才允许，由界面控制入口） */
    suspend fun setGoal(childId: Long, name: String, targetPoints: Int) {
        require(KidPoints.isValidGoal(name, targetPoints)) { "目标名称不能为空，目标分值需大于 0" }
        pointDao.insertGoal(
            PointGoalEntity(
                childId = childId,
                name = name.trim(),
                targetPoints = targetPoints,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    /** 首次进入该孩子积分页时预置默认任务（已有任务则不重复写入） */
    suspend fun seedDefaultTasksIfEmpty(childId: Long) {
        if (pointDao.countTasks(childId) > 0) return
        val now = System.currentTimeMillis()
        pointDao.insertTasks(
            KidPoints.DEFAULT_TASKS.map { (name, points) ->
                PointTaskEntity(childId = childId, name = name, points = points, createdAt = now)
            }
        )
    }
}
