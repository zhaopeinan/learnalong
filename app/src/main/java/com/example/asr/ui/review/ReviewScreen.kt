package com.example.asr.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.ReviewTaskWithWeakPoint
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.domain.EbbinghausScheduler
import com.example.asr.domain.GrowthReport
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.AppTopSpace
import com.example.asr.ui.components.ExerciseSection
import com.example.asr.ui.components.MasteryCurve
import com.example.asr.ui.components.MasteryProgress
import com.example.asr.ui.components.TaskContentState
import com.example.asr.ui.components.TourPlan
import com.example.asr.ui.components.WeakPointCard
import com.example.asr.ui.components.tourTarget
import com.example.asr.ui.today.TodayViewModel
import com.example.asr.ui.util.toDateString
import com.example.asr.ui.util.toDateTimeString
import com.example.asr.ui.weakpoints.WeakPointsViewModel

/**
 * 「复习」tab：原「今日」（本周成长 + 今日待复习任务）与「薄弱点」（全部薄弱点库）合并页。
 * 自上而下：本周成长卡 → 今日复习任务 → 薄弱点库（孩子/科目筛选 + 卡片列表）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onOpenExercise: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val todayVm: TodayViewModel = viewModel(factory = viewModelFactory {
        initializer { TodayViewModel(app.container.tutorRepository, app.container.demoSeeder) }
    })
    val wpVm: WeakPointsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            WeakPointsViewModel(app.container.tutorRepository, app.container.childRepository)
        }
    })

    val tasks by todayVm.tasks.collectAsStateWithLifecycle()
    val contents by todayVm.contents.collectAsStateWithLifecycle()
    val weeklyReport by todayVm.weeklyReport.collectAsStateWithLifecycle()

    val weakPoints by wpVm.weakPoints.collectAsStateWithLifecycle()
    val children by wpVm.children.collectAsStateWithLifecycle()
    val subjects by wpVm.subjects.collectAsStateWithLifecycle()
    val childFilter by wpVm.childFilter.collectAsStateWithLifecycle()
    val subjectFilter by wpVm.subjectFilter.collectAsStateWithLifecycle()
    val histories by wpVm.histories.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<WeakPointEntity?>(null) }

    Scaffold(
        topBar = { AppTopSpace() },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            weeklyReport?.let { report ->
                item(key = "weekly_report") { WeeklyReportCard(report) }
            }

            item(key = "today_header") { SectionHeader("今日复习") }
            if (tasks.isEmpty()) {
                item(key = "today_empty") {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourTarget(TourPlan.TAG_TODAY_CARD),
                    ) {
                        Text(
                            "今天没有到期的复习任务，保持得不错",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(tasks, key = { "task_${it.taskId}" }) { item ->
                    TodayTaskCard(
                        item = item,
                        contentState = contents[item.taskId],
                        onLoadContent = { todayVm.loadContent(item) },
                        onMastered = { todayVm.mark(item, mastered = true) },
                        onStillWeak = { todayVm.mark(item, mastered = false) },
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = tween(250),
                                fadeOutSpec = tween(250),
                                placementSpec = tween(250),
                            )
                            // 新手引导高亮目标：第一张任务卡（对应小程序 .today-card）
                            .then(
                                if (item.taskId == tasks.first().taskId) {
                                    Modifier.tourTarget(TourPlan.TAG_TODAY_CARD)
                                } else Modifier
                            ),
                    )
                }
            }

            item(key = "wp_header") { SectionHeader("薄弱点库") }
            item(key = "wp_child_filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = childFilter == null,
                        onClick = { wpVm.childFilter.value = null },
                        label = { Text("全部孩子", maxLines = 1) },
                        shape = CircleShape,
                    )
                    children.forEach { child ->
                        FilterChip(
                            selected = childFilter == child.id,
                            onClick = {
                                wpVm.childFilter.value = if (childFilter == child.id) null else child.id
                            },
                            label = { Text(child.name, maxLines = 1) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            if (subjects.isNotEmpty()) {
                item(key = "wp_subject_filters") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        subjects.forEach { subject ->
                            FilterChip(
                                selected = subjectFilter == subject,
                                onClick = {
                                    wpVm.subjectFilter.value =
                                        if (subjectFilter == subject) null else subject
                                },
                                label = { Text(subject, maxLines = 1) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }

            if (weakPoints.isEmpty()) {
                item(key = "wp_empty") {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tourTarget(TourPlan.TAG_WP_CARD),
                    ) {
                        Text(
                            "暂无薄弱点：在「记录」页录音并完成分析后，孩子的薄弱知识点会汇总到这里",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(weakPoints, key = { "wp_${it.id}" }) { wp ->
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
                            )
                            // 新手引导高亮目标：第一张薄弱点卡（对应小程序 .wp-card）
                            .then(
                                if (wp.id == weakPoints.first().id) {
                                    Modifier.tourTarget(TourPlan.TAG_WP_CARD)
                                } else Modifier
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

    // 删除二次确认
    deleting?.let { wp ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除薄弱点") },
            text = { Text("确定删除「${wp.knowledgePoint}」吗？相关的复习任务也会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    wpVm.delete(wp.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** 本周成长卡片：复习次数、科目分布、进步最大的薄弱点 */
@Composable
private fun WeeklyReportCard(report: GrowthReport.WeeklyReport) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "本周成长",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (report.reviewsCompleted > 0) {
                Text(
                    "完成复习 ${report.reviewsCompleted} 次",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    report.subjectCounts.joinToString(" · ") { "${it.subject} ${it.count} 次" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (report.improvements.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "进步最大",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                report.improvements.forEach { imp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "+${imp.gain}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            "${imp.knowledgePoint}（${imp.childName} · ${imp.subject}）",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTaskCard(
    item: ReviewTaskWithWeakPoint,
    contentState: TaskContentState?,
    onLoadContent: () -> Unit,
    onMastered: () -> Unit,
    onStillWeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "expandArrow",
    )

    AppCard(
        onClick = {
            expanded = !expanded
            if (expanded) onLoadContent()
        },
        modifier = modifier.fillMaxWidth().animateContentSize(tween(200)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${item.childName} · ${item.subject}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "第 ${item.reviewStage + 1} 轮",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.knowledgePoint,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        expanded = !expanded
                        if (expanded) onLoadContent()
                    },
                ) {
                    Text("练习题", maxLines = 1)
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开练习",
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("掌握度", style = MaterialTheme.typography.labelMedium)
                MasteryProgress(
                    mastery = item.mastery,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(
                    "${item.mastery}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    ExerciseSection(state = contentState, onRetry = onLoadContent)
                    if (contentState is TaskContentState.Ready) Spacer(Modifier.height(8.dp))
                    if (item.description.isNotBlank()) {
                        Text(
                            "薄弱点回顾：${item.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onStillWeak,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("仍薄弱", maxLines = 1)
                }
                Button(
                    onClick = onMastered,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("已掌握", maxLines = 1)
                }
            }
        }
    }
}
