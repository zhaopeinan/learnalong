package com.example.asr.ui.detail

import android.graphics.BitmapFactory
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.RecordingPhotoEntity
import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.SpeakerRole
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.domain.TranscriptText
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.MasteryProgress
import com.example.asr.ui.components.SkeletonBlock
import com.example.asr.ui.components.WeakPointCard
import com.example.asr.ui.components.rememberPhotoCapture
import com.example.asr.ui.util.recordingStatusText
import com.example.asr.ui.util.speakerRoleText
import com.example.asr.ui.util.toDateTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(recordingId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: RecordingDetailViewModel = viewModel(key = "detail_$recordingId", factory = viewModelFactory {
        initializer {
            RecordingDetailViewModel(
                recordingId,
                app.container.recordingRepository,
                app.container.tutorRepository,
            )
        }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val segments by vm.segments.collectAsStateWithLifecycle()
    val weakPoints by vm.weakPoints.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTranscriptDialog by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf<RecordingPhotoEntity?>(null) }

    // 照片采集：拍照（连拍）或相册多选，产出后直接附加到本记录
    val photoCapture = rememberPhotoCapture(
        onResult = { files -> vm.addPhotoFiles(files) },
        onError = { msg -> vm.notifyError(msg) },
    )

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearNotice()
        }
    }

    Scaffold(
        topBar = { AppBackTopBar("记录详情", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val rec = ui.recording
                if (rec != null) {
                    Text(
                        rec.subject,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        rec.createdAt.toDateTimeString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "状态：${recordingStatusText(rec.status)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (rec.transcribedAt != null) {
                        Text(
                            "转写于 ${rec.transcribedAt.toDateTimeString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (rec.polishedAt != null) {
                        Text(
                            "润色于 ${rec.polishedAt.toDateTimeString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // 纯照片记录（拍错题）没有音频，隐藏转写/润色/录音分析按钮
                    if (rec.filePath.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = vm::transcribe,
                                enabled = !ui.busy && vm.canTranscribe(),
                                shape = MaterialTheme.shapes.small,
                            ) { Text(if (rec.status == RecordingStatus.TRANSCRIBED || rec.status == RecordingStatus.ANALYZED) "重新转写" else "开始转写", maxLines = 1) }
                            OutlinedButton(
                                onClick = vm::polish,
                                enabled = !ui.busy && segments.isNotEmpty(),
                                shape = MaterialTheme.shapes.small,
                            ) { Text("润色文稿", maxLines = 1) }
                            Button(
                                onClick = vm::analyze,
                                enabled = !ui.busy && segments.isNotEmpty(),
                                shape = MaterialTheme.shapes.small,
                            ) { Text("分析薄弱点", maxLines = 1) }
                        }
                    }
                    if (segments.isNotEmpty()) {
                        TextButton(onClick = { showTranscriptDialog = true }) {
                            Text("查看文稿${if (rec.polishedText != null) "（已润色）" else ""}")
                        }
                    }
                    // 错题照片：可附加多张，发给多模态模型分析薄弱点
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("错题照片", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { photoCapture.launch() },
                            enabled = !ui.busy,
                        ) { Text("添加照片", maxLines = 1) }
                    }
                    if (photos.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            photos.forEach { photo ->
                                PhotoThumbnail(photo = photo, onClick = { viewingPhoto = photo })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = vm::analyzePhotos,
                            enabled = !ui.busy,
                            shape = MaterialTheme.shapes.small,
                        ) { Text("分析照片薄弱点", maxLines = 1) }
                    }
                    if (segments.isEmpty() && rec.filePath.isNotBlank()) {
                        Text(
                            "需先完成转写，才能润色和分析薄弱点",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 转写 / 分析进行中：上传阶段显示真实进度，其余阶段为不确定进度
                    if (ui.busy) {
                        Spacer(Modifier.height(8.dp))
                        val progress = ui.uploadProgress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "上传中 ${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                "识别 / 分析中，请稍候…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
            }

            if (segments.isEmpty()) {
                if (ui.busy) {
                    // 转写中：骨架屏占位，形状匹配最终的对话气泡卡片
                    items(3) { index ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.height(14.dp).width(72.dp),
                                shape = MaterialTheme.shapes.small,
                            )
                            Spacer(Modifier.height(8.dp))
                            SkeletonBlock(
                                modifier = Modifier
                                    .fillMaxWidth(if (index == 2) 0.6f else 1f)
                                    .height(56.dp),
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            if (ui.recording?.filePath?.isBlank() == true) {
                                "纯照片记录：点上方「分析照片薄弱点」开始分析"
                            } else {
                                "暂无转写内容，点击「开始转写」上传识别"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(segments, key = { it.id }) { seg ->
                    SegmentItem(
                        segment = seg,
                        playing = ui.playingSegmentId == seg.id,
                        onPlay = { vm.playSegment(seg) },
                        onAssignRole = { role -> vm.assignRole(seg.speakerLabel, role) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(250),
                            fadeOutSpec = tween(250),
                            placementSpec = tween(250),
                        ),
                    )
                }
            }

            if (weakPoints.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("分析出的薄弱点", style = MaterialTheme.typography.titleMedium)
                }
                items(weakPoints, key = { "wp_${it.id}" }) { wp ->
                    WeakPointCard(
                        knowledgePoint = wp.knowledgePoint,
                        description = wp.description,
                        mastery = wp.mastery,
                        footer = "提取于 ${wp.createdAt.toDateTimeString()}",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // 分析结果确认弹窗：列出候选薄弱点，标注新增/合并，确认后才入库
    ui.pendingAnalysis?.let { candidates ->
        AlertDialog(
            onDismissRequest = vm::dismissAnalysis,
            title = { Text("发现 ${candidates.size} 个薄弱点") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    candidates.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.knowledgePoint, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "掌握度约 ${c.mastery}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (c.duplicateOfId != null) "合并到已有" else "新增",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (c.duplicateOfId != null) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (c.duplicateOfPoint != null) {
                            Text(
                                "与「${c.duplicateOfPoint}」重复，将合并并重新排期",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::confirmAnalysis) { Text("确认入库") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissAnalysis) { Text("取消") }
            },
        )
    }

    // 照片大图查看 + 删除
    viewingPhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { viewingPhoto = null },
            title = { Text("错题照片") },
            text = {
                val bitmap = remember(photo.filePath) {
                    BitmapFactory.decodeFile(photo.filePath)?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "错题照片",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    Text("照片文件不存在或已损坏")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingPhoto = null }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deletePhoto(photo)
                    viewingPhoto = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    // 完整文稿弹窗：原文 / 润色后 切换查看
    if (showTranscriptDialog) {
        val polished = ui.recording?.polishedText
        var showPolished by remember { mutableStateOf(polished != null) }
        AlertDialog(
            onDismissRequest = { showTranscriptDialog = false },
            title = { Text("完整文稿") },
            text = {
                Column {
                    if (polished != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !showPolished,
                                onClick = { showPolished = false },
                                label = { Text("原文", maxLines = 1) },
                                shape = CircleShape,
                            )
                            FilterChip(
                                selected = showPolished,
                                onClick = { showPolished = true },
                                label = { Text("润色后", maxLines = 1) },
                                shape = CircleShape,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = if (showPolished && polished != null) polished
                        else TranscriptText.build(segments),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTranscriptDialog = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun PhotoThumbnail(photo: RecordingPhotoEntity, onClick: () -> Unit) {
    val bitmap = remember(photo.filePath) {
        // 缩略图按 4 倍降采样，避免大图占内存
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeFile(photo.filePath, opts)?.asImageBitmap()
    }
    AppCard(onClick = onClick) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "错题照片缩略图",
                modifier = Modifier.size(72.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                "无法读取",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.size(72.dp).padding(8.dp),
            )
        }
    }
}

@Composable
private fun SegmentItem(
    segment: TranscriptSegmentEntity,
    playing: Boolean,
    onPlay: () -> Unit,
    onAssignRole: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 说话人分色：家长 = primaryContainer，孩子 = secondaryContainer，未标注 = surfaceVariant
    val (containerColor, labelColor) = when (segment.role) {
        SpeakerRole.PARENT ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SpeakerRole.CHILD ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    AppCard(modifier = modifier.fillMaxWidth(), containerColor = containerColor) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    segment.speakerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                )
                Spacer(Modifier.weight(1f))
                // 角色指派下拉：该录音内同 speakerLabel 的所有段一起更新
                var menuExpanded by remember { mutableStateOf(false) }
                Surface(onClick = { menuExpanded = true }, color = Color.Transparent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "角色：${speakerRoleText(segment.role)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "选择角色",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        listOf(
                            SpeakerRole.PARENT to "家长",
                            SpeakerRole.CHILD to "孩子",
                            SpeakerRole.UNKNOWN to "未标注",
                        ).forEach { (role, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onAssignRole(role)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onPlay,
                    modifier = Modifier.padding(start = 8.dp),
                    shape = MaterialTheme.shapes.small,
                ) { Text(if (playing) "播放中" else "播放", maxLines = 1) }
            }
            Spacer(Modifier.height(4.dp))
            Text(segment.text, color = labelColor)
            Text(
                "%.1fs - %.1fs".format(segment.startSec, segment.endSec),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
    }
}
