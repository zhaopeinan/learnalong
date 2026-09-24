package com.example.asr.ui.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.WorkStatus
import com.example.asr.domain.WorkPrompt
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.util.toDateTimeString
import com.example.asr.ui.util.toDurationString
import com.example.asr.ui.util.workStatusText

/**
 * 工作端详情（对应小程序 work/detail）：
 * 信息卡 → 转写/分析流程 → AI 纪要 + 待办清单 + 转写稿 + 音频回放；失败可重试。
 */
@Composable
fun WorkDetailScreen(
    recordingId: Long,
    autoStart: Boolean,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: WorkDetailViewModel = viewModel(factory = viewModelFactory {
        initializer { WorkDetailViewModel(recordingId, app.container.workRepository, autoStart) }
    })
    val recording by vm.recording.collectAsStateWithLifecycle()
    val todos by vm.todos.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearNotice()
        }
    }
    LaunchedEffect(ui.deleted) {
        if (ui.deleted) onBack()
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReanalyzeConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AppBackTopBar(recording?.title ?: "记录详情", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val rec = recording
        if (rec == null) {
            if (ui.loaded) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "记录不存在或已被删除",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 信息卡
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(workScenarioIcon(rec.scenario), fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            rec.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${WorkPrompt.scenarioLabel(rec.scenario)} · " +
                                rec.createdAt.toDateTimeString().take(16) + " · " +
                                rec.durationSec.toDurationString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        workStatusText(rec.status),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (rec.status) {
                            WorkStatus.ANALYZED -> MaterialTheme.colorScheme.primary
                            WorkStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // 处理进度
            if (ui.busy) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(ui.stageText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        val percent = ui.uploadPercent
                        if (percent != null && percent >= 0) {
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "长录音会自动切块分段上传，请保持应用在前台",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 主操作（对应小程序 primaryAction）
            if (!ui.busy && rec.status != WorkStatus.ANALYZED) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        vm.runPipeline()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                ) {
                    Text(if (rec.transcriptText != null) "开始分析" else "开始转写并分析")
                }
            }

            // AI 纪要
            val summaryLines = remember(rec.summary) { renderSummary(rec.summary) }
            if (summaryLines.isNotEmpty()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "AI 纪要",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (!ui.busy) {
                                TextButton(onClick = { showReanalyzeConfirm = true }) {
                                    Text("重新分析")
                                }
                            }
                        }
                        summaryLines.forEach { line ->
                            when (line.type) {
                                SummaryLineType.HEADING -> {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        line.text,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                SummaryLineType.BULLET -> {
                                    Spacer(Modifier.height(4.dp))
                                    Row {
                                        Text("· ", style = MaterialTheme.typography.bodyMedium)
                                        Text(line.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                SummaryLineType.PARAGRAPH -> {
                                    Spacer(Modifier.height(4.dp))
                                    Text(line.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            // 待办事项
            if (todos.isNotEmpty()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("待办事项（${todos.size}）", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        todos.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        vm.toggleTodo(t.id, !t.done)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TodoCheck(done = t.done)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        t.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                                    )
                                    val sub = buildString {
                                        if (!t.assignee.isNullOrEmpty()) append("负责人：${t.assignee}")
                                        if (!t.assignee.isNullOrEmpty() && !t.deadline.isNullOrEmpty()) append(" · ")
                                        if (!t.deadline.isNullOrEmpty()) append("截止：${t.deadline}")
                                    }
                                    if (sub.isNotEmpty()) {
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 音频回放
            if (rec.filePath.isNotBlank() && !ui.busy) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { vm.togglePlay() }, shape = CircleShape) {
                            Text(if (ui.playing) "停止" else "播放")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (ui.playing) ui.playText else "回放录音（${rec.durationSec.toDurationString()}）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // 转写稿
            val transcriptLines = remember(rec.transcriptText) {
                rec.transcriptText?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            }
            if (transcriptLines.isNotEmpty()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (ui.showTranscript) 8.dp else 0.dp),
                        ) {
                            Text(
                                "转写稿（${transcriptLines.size} 段）",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.toggleTranscript() }) {
                                Text(if (ui.showTranscript) "收起 ⌃" else "展开 ⌄")
                            }
                        }
                        if (ui.showTranscript) {
                            transcriptLines.forEach { line ->
                                TranscriptLine(line)
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            // 删除
            if (!ui.busy) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("删除这条记录", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 失败重试（对应小程序 fail 弹窗）
    ui.failure?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.dismissFailure() },
            title = { Text("处理失败") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.dismissFailure()
                        vm.runPipeline()
                    },
                ) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissFailure() }) { Text("取消") }
            },
        )
    }

    // 重新分析确认（对应小程序 onReanalyze）
    if (showReanalyzeConfirm) {
        AlertDialog(
            onDismissRequest = { showReanalyzeConfirm = false },
            title = { Text("重新分析？") },
            text = { Text("将重新生成纪要，并替换现有待办事项") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReanalyzeConfirm = false
                        vm.runPipeline()
                    },
                ) { Text("重新分析") }
            },
            dismissButton = {
                TextButton(onClick = { showReanalyzeConfirm = false }) { Text("取消") }
            },
        )
    }

    // 删除确认（对应小程序 onDelete）
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这条工作记录？") },
            text = { Text("将同时删除录音文件、纪要与关联待办") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteRecording()
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

private enum class SummaryLineType { HEADING, BULLET, PARAGRAPH }

private data class SummaryLine(val type: SummaryLineType, val text: String)

/** 纪要 markdown 渲染（对齐小程序 renderSummary：## 标题 / - 要点 / 其余段落） */
private fun renderSummary(summary: String?): List<SummaryLine> =
    summary?.split("\n")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.map { line ->
            when {
                line.startsWith("## ") -> SummaryLine(SummaryLineType.HEADING, line.substring(3))
                line.startsWith("- ") -> SummaryLine(SummaryLineType.BULLET, line.substring(2))
                else -> SummaryLine(SummaryLineType.PARAGRAPH, line)
            }
        } ?: emptyList()

private val TRANSCRIPT_LINE_REGEX = Regex("""^\[(\d{1,3}:\d{2})]\s*([^：:]+)：(.+)$""")

/** 转写行：[mm:ss] 时间戳 + 说话人标签着色 + 正文 */
@Composable
private fun TranscriptLine(line: String) {
    val m = TRANSCRIPT_LINE_REGEX.matchEntire(line)
    if (m == null) {
        Text(line, style = MaterialTheme.typography.bodySmall)
        return
    }
    val (time, speaker, text) = m.destructured
    Row {
        Text(
            time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                speaker,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
