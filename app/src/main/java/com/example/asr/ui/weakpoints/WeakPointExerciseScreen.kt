package com.example.asr.ui.weakpoints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.repository.ChildRepository
import com.example.asr.data.repository.TutorRepository
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.ExerciseSection
import com.example.asr.ui.components.TaskContentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WeakPointExerciseViewModel(
    private val tutorRepository: TutorRepository,
    private val childRepository: ChildRepository,
    private val weakPointId: Long,
) : ViewModel() {

    private val _weakPoint = MutableStateFlow<WeakPointEntity?>(null)
    val weakPoint: StateFlow<WeakPointEntity?> = _weakPoint

    private val _childName = MutableStateFlow("")
    val childName: StateFlow<String> = _childName

    private val _content = MutableStateFlow<TaskContentState?>(null)
    val content: StateFlow<TaskContentState?> = _content

    init {
        viewModelScope.launch {
            val wp = tutorRepository.getWeakPoint(weakPointId) ?: return@launch
            _weakPoint.value = wp
            _childName.value =
                childRepository.children.first().firstOrNull { it.id == wp.childId }?.name ?: ""
            generate()
        }
    }

    /** 每次生成都出新题，不缓存 */
    fun generate() {
        val wp = _weakPoint.value ?: return
        if (_content.value is TaskContentState.Loading) return
        viewModelScope.launch {
            _content.value = TaskContentState.Loading
            _content.value = try {
                TaskContentState.Ready(tutorRepository.generateContentForWeakPoint(wp))
            } catch (e: Exception) {
                TaskContentState.Failed(e.message ?: "生成失败")
            }
        }
    }
}

/** 薄弱点练习题独立页面：整页展示题目，与分析文案分开 */
@Composable
fun WeakPointExerciseScreen(
    weakPointId: Long,
    onBack: () -> Unit,
    onAskTutor: (childId: Long) -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: WeakPointExerciseViewModel = viewModel(
        key = "exercise_$weakPointId",
        factory = viewModelFactory {
            initializer {
                WeakPointExerciseViewModel(
                    app.container.tutorRepository,
                    app.container.childRepository,
                    weakPointId,
                )
            }
        },
    )
    val weakPoint by vm.weakPoint.collectAsStateWithLifecycle()
    val childName by vm.childName.collectAsStateWithLifecycle()
    val content by vm.content.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppBackTopBar(title = "练习题", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val wp = weakPoint
                if (wp != null) {
                    Column {
                        Text(
                            wp.knowledgePoint,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOf(childName, wp.subject).filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ExerciseSection(state = content, onRetry = { vm.generate() })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.weight(1f))
                    // 「问老师」：带当前练习题目进入苏格拉底对话（对应小程序 exercise 页 onAskTutor）
                    OutlinedButton(
                        onClick = { weakPoint?.let { onAskTutor(it.childId) } },
                        enabled = weakPoint != null,
                        shape = MaterialTheme.shapes.small,
                    ) { Text("问老师", maxLines = 1) }
                    OutlinedButton(
                        onClick = { vm.generate() },
                        enabled = content !is TaskContentState.Loading,
                        shape = MaterialTheme.shapes.small,
                    ) { Text("换一批题", maxLines = 1) }
                }
            }
        }
    }
}
