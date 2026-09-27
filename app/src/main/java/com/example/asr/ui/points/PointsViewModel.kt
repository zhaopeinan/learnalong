package com.example.asr.ui.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.PointGoalEntity
import com.example.asr.data.local.entity.PointRecordEntity
import com.example.asr.data.local.entity.PointTaskEntity
import com.example.asr.data.repository.ChildRepository
import com.example.asr.data.repository.PointsRepository
import com.example.asr.ui.components.Celebration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PointsViewModel(
    private val pointsRepository: PointsRepository,
    childRepository: ChildRepository,
    val childId: Long,
) : ViewModel() {

    val child: StateFlow<ChildEntity?> = childRepository.children
        .map { list -> list.find { it.id == childId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val points: StateFlow<Int> = pointsRepository.observePoints(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val tasks: StateFlow<List<PointTaskEntity>> = pointsRepository.observeTasks(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val goals: StateFlow<List<PointGoalEntity>> = pointsRepository.observeGoals(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 进行中的目标：可多个并存，家长任选其一兑换 */
    val activeGoals: StateFlow<List<PointGoalEntity>> = goals
        .map { list -> list.filter { it.redeemedAt == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 历史目标：已兑现 */
    val redeemedGoals: StateFlow<List<PointGoalEntity>> = goals
        .map { list -> list.filter { it.redeemedAt != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val records: StateFlow<List<PointRecordEntity>> = pointsRepository.observeRecords(childId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 进行中的庆祝动效（null 无） */
    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration

    /** 一次性提示（snackbar 展示后调 consumeMessage 清除） */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        // 首次进入该孩子积分页：预置默认任务
        viewModelScope.launch { pointsRepository.seedDefaultTasksIfEmpty(childId) }
    }

    /** 家长点任务按钮 → 加分 + 短版庆祝 */
    fun earn(task: PointTaskEntity) {
        viewModelScope.launch {
            pointsRepository.earn(task)
            _celebration.value = Celebration(
                key = System.nanoTime(),
                deltaText = "+${task.points}",
            )
        }
    }

    /** 点「兑换奖励」→ 扣减 + 长版庆祝；积分不足拦截提示 */
    fun redeem(goal: PointGoalEntity) {
        viewModelScope.launch {
            if (pointsRepository.redeem(goal)) {
                _celebration.value = Celebration(
                    key = System.nanoTime(),
                    deltaText = "-${goal.targetPoints}",
                    title = "「${goal.name}」兑换成功！",
                    long = true,
                )
            } else {
                _message.value = "积分还不够哦"
            }
        }
    }

    fun addTask(name: String, points: Int) {
        viewModelScope.launch {
            try {
                pointsRepository.addTask(childId, name, points)
            } catch (e: Exception) {
                _message.value = e.message
            }
        }
    }

    fun updateTask(task: PointTaskEntity, name: String, points: Int) {
        viewModelScope.launch {
            try {
                pointsRepository.updateTask(task, name, points)
            } catch (e: Exception) {
                _message.value = e.message
            }
        }
    }

    fun deleteTask(task: PointTaskEntity) {
        viewModelScope.launch { pointsRepository.deleteTask(task) }
    }

    /** 撤销误点的加分：删流水并扣回分值 */
    fun reverseEarn(record: PointRecordEntity) {
        viewModelScope.launch {
            try {
                pointsRepository.reverseEarn(record)
            } catch (e: Exception) {
                _message.value = e.message
            }
        }
    }

    fun setGoal(name: String, targetPoints: Int) {
        viewModelScope.launch {
            try {
                pointsRepository.setGoal(childId, name, targetPoints)
            } catch (e: Exception) {
                _message.value = e.message
            }
        }
    }

    fun updateGoal(goal: PointGoalEntity, name: String, targetPoints: Int) {
        viewModelScope.launch {
            try {
                pointsRepository.updateGoal(goal, name, targetPoints)
            } catch (e: Exception) {
                _message.value = e.message
            }
        }
    }

    fun deleteGoal(goal: PointGoalEntity) {
        viewModelScope.launch { pointsRepository.deleteGoal(goal) }
    }

    fun dismissCelebration() {
        _celebration.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }
}
