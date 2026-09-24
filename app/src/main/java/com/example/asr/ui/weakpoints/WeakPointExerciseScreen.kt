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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    /** 单题换题中的题目下标（其余题目保留） */
    private val _replacingIndex = MutableStateFlow<Int?>(null)
    val replacingIndex: StateFlow<Int?> = _replacingIndex

    /** 一次性提示（snackbar）：换题失败等 */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun clearNotice() {
        _notice.value = null
    }

    init {
        viewModelScope.launch {
            val wp = tutorRepository.getWeakPoint(weakPointId) ?: return@launch
            _weakPoint.value = wp
            _childName.value =
                childRepository.children.first().firstOrNull { it.id == wp.childId }?.name ?: ""
            loadCachedOrGenerate()
        }
    }

    /** 进页：优先读薄弱点上缓存的题目（退出重进不丢），没有才生成（对应小程序 generate(false)） */
    private fun loadCachedOrGenerate() {
        val wp = _weakPoint.value ?: return
        if (_content.value is TaskContentState.Loading) return
        viewModelScope.launch {
            _content.value = TaskContentState.Loading
            _content.value = try {
                TaskContentState.Ready(tutorRepository.getWeakPointContent(wp))
            } catch (e: Exception) {
                TaskContentState.Failed(e.message ?: "生成失败")
            }
        }
    }

    /** 换一批题：整批重新生成并覆盖缓存（避开当前已有的题目） */
    fun regenerate() {
        val wp = _weakPoint.value ?: return
        if (_content.value is TaskContentState.Loading) return
        viewModelScope.launch {
            _content.value = TaskContentState.Loading
            _content.value = try {
                TaskContentState.Ready(tutorRepository.regenerateWeakPointContent(wp))
            } catch (e: Exception) {
                TaskContentState.Failed(e.message ?: "生成失败")
            }
        }
    }

    /** 换一题：只重新生成这一道，其余题目保留 */
    fun replaceOne(index: Int) {
        val wp = _weakPoint.value ?: return
        val current = _content.value as? TaskContentState.Ready ?: return
        if (_replacingIndex.value != null) return
        viewModelScope.launch {
            _replacingIndex.value = index
            try {
                _content.value = TaskContentState.Ready(
                    tutorRepository.replaceExerciseInCache(wp, index)
                )
            } catch (e: Exception) {
                // 换题失败保留原题，仅提示（对齐小程序 toast）
                _content.value = current
                _notice.value = e.message ?: "换题失败"
            } finally {
                _replacingIndex.value = null
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
    val replacingIndex by vm.replacingIndex.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearNotice()
        }
    }

    Scaffold(
        topBar = { AppBackTopBar(title = "练习题", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        ExerciseSection(
                            state = content,
                            onRetry = { vm.regenerate() },
                            onReplaceOne = { vm.replaceOne(it) },
                            replacingIndex = replacingIndex,
                        )
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
                    // 换一批题：整批替换，先确认（生成失败态直接重试不确认，对齐小程序 onGenerate）
                    OutlinedButton(
                        onClick = {
                            if (content is TaskContentState.Failed) vm.regenerate()
                            else showRegenerateConfirm = true
                        },
                        enabled = content !is TaskContentState.Loading,
                        shape = MaterialTheme.shapes.small,
                    ) { Text("换一批题", maxLines = 1) }
                }
            }
        }
    }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("换一批题") },
            text = { Text("将替换当前全部题目，确定换一批吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showRegenerateConfirm = false
                    vm.regenerate()
                }) { Text("换一批") }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) { Text("取消") }
            },
        )
    }
}
