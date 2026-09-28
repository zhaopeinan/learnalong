package com.example.asr.ui.recordings

import androidx.activity.compose.rememberLauncherForActivityResult
import com.example.asr.ui.util.OpenDocumentInRecorderFolder
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.R
import com.example.asr.audio.AudioImporter
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.RecordingWithChild
import com.example.asr.media.PhotoImporter
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.AppTopSpace
import com.example.asr.ui.components.EmptyState
import com.example.asr.ui.components.TourPlan
import com.example.asr.ui.components.rememberPhotoCapture
import com.example.asr.ui.components.tourTarget
import com.example.asr.ui.util.recordingStatusText
import com.example.asr.ui.util.toDateTimeString
import com.example.asr.ui.util.toDurationString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    onOpenDetail: (Long) -> Unit,
    onRecord: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AsrApplication
    val vm: RecordingsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            RecordingsViewModel(
                app.container.recordingRepository,
                app.container.tutorRepository,
                app.container.childRepository,
                app.container.settingsStore,
            )
        }
    })
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val children by vm.children.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val configSubjects by vm.configSubjects.collectAsStateWithLifecycle()
    val childFilter by vm.childFilter.collectAsStateWithLifecycle()
    val subjectFilter by vm.subjectFilter.collectAsStateWithLifecycle()
    val importMessage by vm.importMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<RecordingWithChild?>(null) }

    // 待确认归属的导入文件（App 内选择 或 其他 App 分享）
    var importFile by remember { mutableStateOf<File?>(null) }
    // 待确认归属的错题照片（已复制进私有目录）
    var photoFiles by remember { mutableStateOf<List<File>?>(null) }
    val sharedImport by app.container.pendingImport.collectAsStateWithLifecycle()
    LaunchedEffect(sharedImport) {
        sharedImport?.let {
            importFile = it
            app.container.pendingImport.value = null
        }
    }
    // 底部动作面板拍照/选图产出的错题照片
    val sharedPhotoImport by app.container.pendingPhotoImport.collectAsStateWithLifecycle()
    LaunchedEffect(sharedPhotoImport) {
        sharedPhotoImport?.let {
            photoFiles = it
            app.container.pendingPhotoImport.value = null
        }
    }

    // 系统文件选择器（音频）：自动定位到本机系统录音机文件夹
    val pickerScope = rememberCoroutineScope()
    val audioPicker = rememberLauncherForActivityResult(
        remember { OpenDocumentInRecorderFolder() }
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        pickerScope.launch(Dispatchers.IO) {
            try {
                importFile = AudioImporter.import(context, uri)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("导入失败：${e.message}")
            }
        }
    }

    // 照片采集：拍照（连拍）或相册多选，产出已复制进私有目录的文件
    val photoCapture = rememberPhotoCapture(
        onResult = { files -> photoFiles = files },
        onError = { msg -> pickerScope.launch { snackbarHostState.showSnackbar(msg) } },
    )

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearImportMessage()
        }
    }
    Scaffold(
        topBar = { AppTopSpace() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRecord,
                text = { Text("录音") },
                icon = { Icon(ImageVector.vectorResource(R.drawable.ic_nav_records), contentDescription = null) },
                shape = CircleShape,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 导入/拍照入口：右对齐小按钮，代替原顶栏 action
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { photoCapture.launch() }) {
                    Text("拍错题", maxLines = 1)
                }
                TextButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                    Text("导入音频", maxLines = 1)
                }
            }
            // 筛选行：孩子 + 科目
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

            if (recordings.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "还没有辅导记录",
                    description = "录下一段辅导过程，或导入已有的音频，自动转写并分析孩子的薄弱点",
                    actionLabel = "开始录音",
                    onAction = onRecord,
                    modifier = Modifier.tourTarget(TourPlan.TAG_REC_CARD),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recordings, key = { it.id }) { rec ->
                        AppCard(
                            onClick = { onOpenDetail(rec.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(
                                    fadeInSpec = tween(250),
                                    fadeOutSpec = tween(250),
                                    placementSpec = tween(250),
                                )
                                // 新手引导高亮目标：第一条记录卡（对应小程序 .rec-swipe）
                                .then(
                                    if (rec.id == recordings.first().id) {
                                        Modifier.tourTarget(TourPlan.TAG_REC_CARD)
                                    } else Modifier
                                ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${rec.childName} · ${rec.subject}",
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    StatusTag(status = rec.status)
                                    IconButton(onClick = { deleting = rec }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除记录",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (rec.durationSec > 0) {
                                        "${rec.createdAt.toDateTimeString()} · 时长 ${rec.durationSec.toDurationString()}"
                                    } else {
                                        "${rec.createdAt.toDateTimeString()} · 照片记录"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除记录二次确认
    deleting?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除辅导记录") },
            text = { Text("确定删除「${rec.childName} · ${rec.subject}」这条记录吗？音频、错题照片、转写文稿会一并删除，已提取的薄弱点保留。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(rec.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }

    // 导入归属确认弹窗
    importFile?.let { file ->
        ImportDialog(
            title = "导入音频",
            subtitle = file.name,
            children = children,
            subjects = configSubjects,
            confirmLabel = "导入",
            onConfirm = { childId, subject ->
                vm.saveImported(file, childId, subject) { importFile = null }
            },
            onDismiss = {
                file.delete() // 取消导入时删除已复制的临时文件
                importFile = null
            },
        )
    }

    // 拍错题归属确认弹窗：确认后建记录并直接进详情页分析
    photoFiles?.let { files ->
        ImportDialog(
            title = "拍错题",
            subtitle = "已选 ${files.size} 张照片",
            children = children,
            subjects = configSubjects,
            confirmLabel = "保存并分析",
            onConfirm = { childId, subject ->
                vm.savePhotoRecord(childId, subject, files) { newId ->
                    photoFiles = null
                    onOpenDetail(newId)
                }
            },
            onDismiss = {
                files.forEach { it.delete() } // 取消时删除已复制的照片
                photoFiles = null
            },
        )
    }
}

/** 状态标签：转写中/进行中用 amber 功能语义色，失败用 error，已分析用主色容器 */
@Composable
private fun StatusTag(status: String) {
    val (container, content) = when (status) {
        RecordingStatus.TRANSCRIBING ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        RecordingStatus.FAILED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        RecordingStatus.ANALYZED ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
    ) {
        Text(
            recordingStatusText(status),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** 导入/拍照后选择孩子、科目的确认弹窗 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportDialog(
    title: String,
    subtitle: String,
    children: List<ChildEntity>,
    subjects: List<String>,
    confirmLabel: String,
    onConfirm: (childId: Long, subject: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedChildId by remember { mutableStateOf<Long?>(null) }
    var subject by remember { mutableStateOf("") }
    var childExpanded by remember { mutableStateOf(false) }
    var subjectExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExposedDropdownMenuBox(
                    expanded = childExpanded,
                    onExpandedChange = { childExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = children.firstOrNull { it.id == selectedChildId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("孩子") },
                        placeholder = { Text("请选择孩子") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = childExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = childExpanded,
                        onDismissRequest = { childExpanded = false },
                    ) {
                        children.forEach { child ->
                            DropdownMenuItem(
                                text = { Text(child.name) },
                                onClick = {
                                    selectedChildId = child.id
                                    childExpanded = false
                                },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = subjectExpanded,
                    onExpandedChange = { subjectExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("科目") },
                        placeholder = { Text("请选择科目") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false },
                    ) {
                        subjects.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    subject = s
                                    subjectExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedChildId!!, subject) },
                enabled = selectedChildId != null && subject.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
