package com.example.asr.ui.work

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.asr.data.local.entity.WorkRecordingEntity
import com.example.asr.data.local.entity.WorkStatus
import com.example.asr.data.local.entity.WorkTodoWithRecording
import com.example.asr.domain.WorkPrompt
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.util.toDateTimeString
import com.example.asr.ui.util.toDurationString
import com.example.asr.ui.util.workStatusText

/** 场景图标（对齐小程序 work home 的 SCENARIO_ICONS） */
fun workScenarioIcon(scenario: String): String = when (scenario) {
    "meeting" -> "👥"
    "talk" -> "💬"
    "call" -> "📞"
    else -> "🎙️"
}

/**
 * 工作端首页（对应小程序 work/home）：记录列表 / 待办清单两视图。
 * 底部三槽导航（记录 / 大录音钮 / 待办）在 AppRoot 中，与本页共享 WorkHomeViewModel。
 */
@Composable
fun WorkHomeScreen(
    vm: WorkHomeViewModel,
    onOpenDetail: (Long) -> Unit,
    onExitToParent: () -> Unit,
) {
    val view by vm.view.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppBackTopBar("工作端", onBack = onExitToParent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "会议、工作谈话、通话录音 → 自动转写、生成纪要与待办",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (view == WorkHomeViewModel.VIEW_TODOS) {
                TodoListView(
                    todos = todos,
                    onToggle = { vm.toggleTodo(it.id, !it.done) },
                    onDelete = { vm.deleteTodo(it) },
                )
            } else {
                RecordListView(
                    recordings = recordings,
                    onOpen = onOpenDetail,
                    onDelete = { vm.deleteRecording(it) },
                )
            }
        }
    }
}

@Composable
private fun RecordListView(
    recordings: List<WorkRecordingEntity>,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (recordings.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("还没有工作录音", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "点底部中间的按钮开始录音，支持会议、工作谈话、通话场景",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "也可以从文件导入外部录音",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(recordings, key = { it.id }) { r ->
            RecordCard(
                recording = r,
                onOpen = { onOpen(r.id) },
                onLongPress = { pendingDelete = r.id },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    // 长按删除整条记录（含音频文件），对齐小程序 onDeleteRecord
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条工作记录？") },
            text = { Text("将同时删除录音文件、纪要与关联待办") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(id)
                        pendingDelete = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordCard(
    recording: WorkRecordingEntity,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(workScenarioIcon(recording.scenario), fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recording.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${WorkPrompt.scenarioLabel(recording.scenario)} · " +
                        recording.createdAt.toDateTimeString().take(16) + " · " +
                        recording.durationSec.toDurationString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                workStatusText(recording.status),
                style = MaterialTheme.typography.labelMedium,
                color = when (recording.status) {
                    WorkStatus.ANALYZED -> MaterialTheme.colorScheme.primary
                    WorkStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TodoListView(
    todos: List<WorkTodoWithRecording>,
    onToggle: (WorkTodoWithRecording) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (todos.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("暂无待办事项", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "录音分析完成后，会自动把行动项提取到这里",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(todos, key = { it.id }) { t ->
            WorkTodoCard(
                text = t.text,
                assignee = t.assignee,
                deadline = t.deadline,
                done = t.done,
                meta = t.recordingTitle,
                onToggle = { onToggle(t) },
                onLongPress = { pendingDelete = t.id },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    // 长按删除单条待办，对齐小程序 onDeleteTodo
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条待办？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(id)
                        pendingDelete = null
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 待办卡片（工作端首页与详情页共用，对齐小程序 todo-card/todo-row）：
 * 圆形勾选框 + 文本（完成划线）+ 元信息行。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkTodoCard(
    text: String,
    assignee: String?,
    deadline: String?,
    done: Boolean,
    meta: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onToggle()
                },
                onLongClick = onLongPress,
            ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TodoCheck(done = done)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                val metaLine = buildString {
                    append(meta)
                    if (!assignee.isNullOrEmpty()) append(" · 负责人：$assignee")
                    if (!deadline.isNullOrEmpty()) append(" · 截止：$deadline")
                }
                if (metaLine.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 圆形勾选框（对齐小程序 todo-check） */
@Composable
fun TodoCheck(done: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .let {
                if (done) it.background(MaterialTheme.colorScheme.primary)
                else it.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Text(
                "✓",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
