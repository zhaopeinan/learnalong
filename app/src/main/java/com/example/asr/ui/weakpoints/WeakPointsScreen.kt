package com.example.asr.ui.weakpoints

import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.domain.EbbinghausScheduler
import com.example.asr.ui.components.AppTopSpace
import com.example.asr.ui.components.EmptyState
import com.example.asr.ui.components.MasteryCurve
import com.example.asr.ui.components.WeakPointCard
import com.example.asr.ui.util.toDateString
import com.example.asr.ui.util.toDateTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeakPointsScreen(onOpenExercise: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: WeakPointsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            WeakPointsViewModel(app.container.tutorRepository, app.container.childRepository)
        }
    })
    val weakPoints by vm.weakPoints.collectAsStateWithLifecycle()
    val children by vm.children.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val childFilter by vm.childFilter.collectAsStateWithLifecycle()
    val subjectFilter by vm.subjectFilter.collectAsStateWithLifecycle()
    val histories by vm.histories.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<WeakPointEntity?>(null) }

    Scaffold(
        topBar = { AppTopSpace() },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = childFilter == null,
                    onClick = { vm.childFilter.value = null },
                    label = { Text("全部孩子", maxLines = 1) },
                    shape = CircleShape,
                )
                children.forEach { child ->
                    FilterChip(
                        selected = childFilter == child.id,
                        onClick = {
                            vm.childFilter.value = if (childFilter == child.id) null else child.id
                        },
                        label = { Text(child.name, maxLines = 1) },
                        shape = CircleShape,
                    )
                }
            }
            if (subjects.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    subjects.forEach { subject ->
                        FilterChip(
                            selected = subjectFilter == subject,
                            onClick = {
                                vm.subjectFilter.value =
                                    if (subjectFilter == subject) null else subject
                            },
                            label = { Text(subject, maxLines = 1) },
                            shape = CircleShape,
                        )
                    }
                }
            }

            if (weakPoints.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Star,
                    title = "暂无薄弱点",
                    description = "先在「记录」页录音并完成分析，孩子的薄弱知识点会汇总到这里",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(weakPoints, key = { it.id }) { wp ->
                        val childName = children.firstOrNull { it.id == wp.childId }?.name ?: ""
                        val finished = wp.nextReviewAt == EbbinghausScheduler.FINISHED
                        WeakPointCard(
                            knowledgePoint = wp.knowledgePoint,
                            description = wp.description,
                            mastery = wp.mastery,
                            meta = "$childName · ${wp.subject}",
                            footer = "提取于 ${wp.createdAt.toDateTimeString()}　" +
                                if (finished) "已完成" else "下次复习 ${wp.nextReviewAt.toDateString()}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(
                                    fadeInSpec = tween(250),
                                    fadeOutSpec = tween(250),
                                    placementSpec = tween(250),
                                ),
                            actions = {
                                OutlinedButton(
                                    onClick = { onOpenExercise(wp.id) },
                                    shape = MaterialTheme.shapes.small,
                                ) { Text("生成练习题", maxLines = 1) }
                                TextButton(onClick = { deleting = wp }) {
                                    Text("删除", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            extraContent = {
                                val history = histories[wp.id].orEmpty()
                                if (history.size >= 2) {
                                    Text(
                                        "掌握度趋势",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    MasteryCurve(history = history)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // 删除二次确认
    deleting?.let { wp ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除薄弱点") },
            text = { Text("确定删除「${wp.knowledgePoint}」吗？相关的复习任务也会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(wp.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}
