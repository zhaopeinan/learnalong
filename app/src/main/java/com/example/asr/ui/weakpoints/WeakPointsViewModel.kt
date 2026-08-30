package com.example.asr.ui.weakpoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.MasteryHistoryEntity
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.repository.ChildRepository
import com.example.asr.data.repository.TutorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WeakPointsViewModel(
    private val tutorRepository: TutorRepository,
    childRepository: ChildRepository,
) : ViewModel() {

    val childFilter = MutableStateFlow<Long?>(null)
    val subjectFilter = MutableStateFlow<String?>(null)

    val children: StateFlow<List<ChildEntity>> = childRepository.children
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjects: StateFlow<List<String>> = tutorRepository.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weakPoints: StateFlow<List<WeakPointEntity>> =
        combine(childFilter, subjectFilter) { c, s -> c to s }
            .flatMapLatest { (c, s) -> tutorRepository.observeWeakPoints(c, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前列表中每个薄弱点的掌握度历史（成长曲线用） */
    val histories: StateFlow<Map<Long, List<MasteryHistoryEntity>>> = weakPoints
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    list.map { wp ->
                        tutorRepository.observeMasteryHistory(wp.id).map { h -> wp.id to h }
                    }
                ) { arr -> arr.toMap() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun delete(id: Long) {
        viewModelScope.launch { tutorRepository.deleteWeakPoint(id) }
    }
}
