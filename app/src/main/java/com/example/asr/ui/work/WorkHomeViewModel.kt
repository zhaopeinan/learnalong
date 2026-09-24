package com.example.asr.ui.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.data.local.entity.WorkRecordingEntity
import com.example.asr.data.local.entity.WorkTodoWithRecording
import com.example.asr.data.repository.WorkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 工作端首页 ViewModel（对应小程序 work/home）：记录列表 / 待办清单两视图。
 * 由 AppRoot 以 activity 作用域创建并共享给底部导航（切换视图 + 待办数角标）。
 */
class WorkHomeViewModel(
    private val workRepository: WorkRepository,
) : ViewModel() {

    /** 当前视图：list=记录列表，todos=待办清单 */
    private val _view = MutableStateFlow(VIEW_LIST)
    val view: StateFlow<String> = _view

    val recordings: StateFlow<List<WorkRecordingEntity>> = workRepository.observeRecordings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todos: StateFlow<List<WorkTodoWithRecording>> = workRepository.observeTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 未完成待办数（底部导航「待办 N」角标） */
    val pendingCount: StateFlow<Int> = workRepository.observeTodos()
        .map { list -> list.count { !it.done } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun switchView(view: String) {
        _view.value = view
    }

    fun toggleTodo(todoId: Long, done: Boolean) {
        viewModelScope.launch { workRepository.toggleTodo(todoId, done) }
    }

    fun deleteTodo(todoId: Long) {
        viewModelScope.launch { workRepository.deleteTodo(todoId) }
    }

    fun deleteRecording(recordingId: Long) {
        viewModelScope.launch { workRepository.deleteRecording(recordingId) }
    }

    companion object {
        const val VIEW_LIST = "list"
        const val VIEW_TODOS = "todos"
    }
}
