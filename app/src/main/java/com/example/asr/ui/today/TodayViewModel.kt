package com.example.asr.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.data.local.DemoSeeder
import com.example.asr.data.local.entity.ReviewTaskWithWeakPoint
import com.example.asr.data.repository.TutorRepository
import com.example.asr.domain.GrowthReport
import com.example.asr.ui.components.TaskContentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 任务练习内容状态见 ui.components.TaskContentState */

class TodayViewModel(
    private val tutorRepository: TutorRepository,
    private val demoSeeder: DemoSeeder? = null,
) : ViewModel() {

    val tasks: StateFlow<List<ReviewTaskWithWeakPoint>> =
        tutorRepository.observeDueTasks(System.currentTimeMillis())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 本周成长报告；本周无活动时为 null（界面隐藏卡片） */
    private val _weeklyReport = MutableStateFlow<GrowthReport.WeeklyReport?>(null)
    val weeklyReport: StateFlow<GrowthReport.WeeklyReport?> = _weeklyReport

    init {
        // 全新安装时先补示例数据，让首屏（含新手引导）有真实内容可看（对齐小程序 today.onShow）
        viewModelScope.launch { demoSeeder?.seedIfEmpty() }
        refreshWeeklyReport()
    }

    private fun refreshWeeklyReport() {
        viewModelScope.launch {
            val weekStart = GrowthReport.weekStartOf(System.currentTimeMillis())
            _weeklyReport.value = GrowthReport.buildWeekly(
                historyRows = tutorRepository.getMasteryHistorySince(weekStart),
                subjectCounts = tutorRepository.countCompletedBySubject(weekStart),
            )
        }
    }

    /** taskId → 练习内容状态 */
    private val _contents = MutableStateFlow<Map<Long, TaskContentState>>(emptyMap())
    val contents: StateFlow<Map<Long, TaskContentState>> = _contents

    /** 展开任务时调用：加载/生成练习内容 */
    fun loadContent(item: ReviewTaskWithWeakPoint) {
        if (_contents.value[item.taskId] is TaskContentState.Loading) return
        if (_contents.value[item.taskId] is TaskContentState.Ready) return
        viewModelScope.launch {
            _contents.update { it + (item.taskId to TaskContentState.Loading) }
            _contents.update {
                try {
                    it + (item.taskId to TaskContentState.Ready(tutorRepository.getTaskContent(item)))
                } catch (e: Exception) {
                    it + (item.taskId to TaskContentState.Failed(e.message ?: "生成失败"))
                }
            }
        }
    }

    fun mark(item: ReviewTaskWithWeakPoint, mastered: Boolean) {
        viewModelScope.launch {
            tutorRepository.applyReview(item.taskId, item.weakPointId, mastered)
            _contents.update { it - item.taskId }
            refreshWeeklyReport()
        }
    }
}
