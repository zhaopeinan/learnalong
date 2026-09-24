package com.example.asr.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.asr.domain.GrowthReport
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.AppTopSpace
import com.example.asr.ui.components.EmptyState
import com.example.asr.ui.components.ExerciseSection
import com.example.asr.ui.components.MasteryProgress
import com.example.asr.ui.components.TaskContentState
import com.example.asr.ui.components.TourPlan
import com.example.asr.ui.components.tourTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen() {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: TodayViewModel = viewModel(factory = viewModelFactory {
        initializer { TodayViewModel(app.container.tutorRepository, app.container.demoSeeder) }
    })
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val contents by vm.contents.collectAsStateWithLifecycle()
    val weeklyReport by vm.weeklyReport.collectAsStateWithLifecycle()

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
            if (tasks.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "全部完成",
                        description = "今天没有到期的复习任务，保持得不错，明天再来看看吧",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp)
                            .tourTarget(TourPlan.TAG_TODAY_CARD),
                    )
                }
            } else {
                items(tasks, key = { it.taskId }) { item ->
                    TodayTaskCard(
                        item = item,
                        contentState = contents[item.taskId],
                        onLoadContent = { vm.loadContent(item) },
                        onMastered = { vm.mark(item, mastered = true) },
                        onStillWeak = { vm.mark(item, mastered = false) },
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
        }
    }
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
