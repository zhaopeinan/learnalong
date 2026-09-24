package com.example.asr.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.remote.DebugLog
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.settings.ClonedVoice
import com.example.asr.domain.ParentLock
import com.example.asr.domain.VoiceCatalog
import com.example.asr.ui.components.AppBackTopBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory {
        initializer { SettingsViewModel(app) }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val asrTest by vm.asrTest.collectAsStateWithLifecycle()
    val llmTest by vm.llmTest.collectAsStateWithLifecycle()
    val vlmTest by vm.vlmTest.collectAsStateWithLifecycle()
    val webdavTest by vm.webdavTest.collectAsStateWithLifecycle()
    val minimaxTest by vm.minimaxTest.collectAsStateWithLifecycle()
    val clone by vm.clone.collectAsStateWithLifecycle()
    val previewingId by vm.previewingId.collectAsStateWithLifecycle()
    val debugEntries by vm.debugEntries.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeToast()
        }
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var deletingVoice by remember { mutableStateOf<ClonedVoice?>(null) }

    // 声音复刻录音权限
    val context = LocalContext.current
    var pendingRecord by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startVoiceRecording()
        pendingRecord = false
    }
    fun startCloneRecording() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            vm.startVoiceRecording()
        } else if (!pendingRecord) {
            pendingRecord = true
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = { AppBackTopBar("设置", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("外观", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AppSettings.THEME_SYSTEM to "跟随本机",
                    AppSettings.THEME_LIGHT to "白天",
                    AppSettings.THEME_DARK to "黑夜",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = ui.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        label = { Text(label, maxLines = 1) },
                        shape = CircleShape,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("SiliconFlow API", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = ui.apiKey,
                onValueChange = { v -> vm.update { it.copy(apiKey = v) } },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.baseUrl,
                onValueChange = { v -> vm.update { it.copy(baseUrl = v) } },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.asrModel,
                onValueChange = { v -> vm.update { it.copy(asrModel = v) } },
                label = { Text("ASR 模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TestRow(
                label = "测试转写模型",
                state = asrTest,
                onTest = vm::testAsr,
            )
            OutlinedTextField(
                value = ui.llmModel,
                onValueChange = { v -> vm.update { it.copy(llmModel = v) } },
                label = { Text("分析模型（LLM）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TestRow(
                label = "测试分析模型",
                state = llmTest,
                onTest = vm::testLlm,
            )
            OutlinedTextField(
                value = ui.vlmModel,
                onValueChange = { v -> vm.update { it.copy(vlmModel = v) } },
                label = { Text("多模态模型（VLM，错题照片分析）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TestRow(
                label = "测试多模态模型",
                state = vlmTest,
                onTest = vm::testVlm,
            )

            Spacer(Modifier.height(8.dp))
            Text("MiniMax 语音合成", style = MaterialTheme.typography.titleSmall)
            Text(
                "孩子端 AI 回复语音播报与家长声音复刻使用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = ui.minimaxApiKey,
                onValueChange = { v -> vm.update { it.copy(minimaxApiKey = v) } },
                label = { Text("MiniMax API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MinimaxModelDropdown(
                value = ui.minimaxModel,
                onChange = { v -> vm.update { it.copy(minimaxModel = v) } },
            )
            TestRow(
                label = "测试语音合成",
                state = minimaxTest,
                onTest = vm::testMinimax,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI 回复语音播报", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭时问老师纯文字回复",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ui.chatVoiceEnabled,
                    onCheckedChange = vm::setChatVoiceEnabled,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text("播报音色", style = MaterialTheme.typography.titleSmall)
            VoiceSection(
                ui = ui,
                previewingId = previewingId,
                onPreview = vm::previewVoice,
                onToggleDefault = vm::toggleDefaultVoice,
                onDelete = { deletingVoice = it },
            )

            Spacer(Modifier.height(4.dp))
            Text("家长声音复刻", style = MaterialTheme.typography.titleSmall)
            CloneSection(
                clone = clone,
                onNameChange = vm::setCloneName,
                onRecordToggle = {
                    if (clone.state == CloneState.RECORDING) vm.stopVoiceRecording()
                    else startCloneRecording()
                },
                onReRecord = {
                    if (ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        vm.reRecord()
                    } else if (!pendingRecord) {
                        pendingRecord = true
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStartClone = vm::startClone,
            )

            Spacer(Modifier.height(8.dp))
            Text("家长密码", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ParentLock.hasPin(ui.parentPin)) "已设置" else "未设置",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showPinDialog = true },
                    shape = MaterialTheme.shapes.small,
                ) { Text(if (ParentLock.hasPin(ui.parentPin)) "修改密码" else "设置密码") }
            }
            Text(
                "孩子端退出到家长端时需要输入，防止孩子误操作",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Text("录音清理", style = MaterialTheme.typography.titleSmall)
            Text(
                "分析完成后录音文件的处理方式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    AppSettings.CLEANUP_ASK to "每次询问",
                    AppSettings.CLEANUP_KEEP to "自动保留",
                    AppSettings.CLEANUP_DELETE to "自动删除",
                    AppSettings.CLEANUP_BACKUP_DELETE to "备份到云端后删除",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = ui.audioCleanupMode == mode,
                        onClick = { vm.setCleanupMode(mode) },
                        label = { Text(label, maxLines = 1) },
                        shape = CircleShape,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("科目管理", style = MaterialTheme.typography.titleSmall)
            Text(
                "录音和导入时从这里选择科目，可增删",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.subjects.forEach { s ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removeSubject(s) },
                        label = { Text(s, maxLines = 1) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除 $s",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
            var newSubject by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSubject,
                    onValueChange = { newSubject = it },
                    label = { Text("新增科目") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        vm.addSubject(newSubject)
                        newSubject = ""
                    },
                    enabled = newSubject.isNotBlank(),
                ) { Text("添加", maxLines = 1) }
            }

            Spacer(Modifier.height(8.dp))
            Text("云存储（WebDAV）", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = ui.webdavUrl,
                onValueChange = { v -> vm.update { it.copy(webdavUrl = v) } },
                label = { Text("WebDAV 地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.webdavUser,
                onValueChange = { v -> vm.update { it.copy(webdavUser = v) } },
                label = { Text("账号（邮箱）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.webdavPassword,
                onValueChange = { v -> vm.update { it.copy(webdavPassword = v) } },
                label = { Text("应用密码") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "坚果云用户：在 坚果云网页版 → 账户信息 → 安全选项 → 添加应用密码 获取授权码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TestRow(
                label = "测试连接",
                state = webdavTest,
                onTest = vm::testWebdav,
            )

            Spacer(Modifier.height(8.dp))
            Text("每日复习提醒时间", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ui.reminderHour.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { h -> vm.update { it.copy(reminderHour = h.coerceIn(0, 23)) } }
                    },
                    label = { Text("时") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(80.dp),
                )
                Text(" : ", modifier = Modifier.padding(horizontal = 4.dp))
                OutlinedTextField(
                    value = ui.reminderMinute.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.let { m -> vm.update { it.copy(reminderMinute = m.coerceIn(0, 59)) } }
                    },
                    label = { Text("分") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(80.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            DebugSection(
                debugMode = ui.debugMode,
                entries = debugEntries,
                onEnable = vm::enableDebug,
                onDisable = vm::disableDebug,
                onClear = vm::clearDebugLog,
                onRefresh = vm::refreshDebugEntries,
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = vm::save,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                if (saved) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("已保存", maxLines = 1)
                } else {
                    Text("保存", maxLines = 1)
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            pinSet = ParentLock.hasPin(ui.parentPin),
            onVerify = vm::verifyPin,
            onSetPin = vm::setPin,
            onDone = { showPinDialog = false },
            onDismiss = { showPinDialog = false },
        )
    }

    deletingVoice?.let { voice ->
        AlertDialog(
            onDismissRequest = { deletingVoice = null },
            title = { Text("删除音色") },
            text = { Text("确定删除「${voice.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVoice(voice)
                    deletingVoice = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingVoice = null }) { Text("取消") }
            },
        )
    }
}

/** 模型测试按钮 + 结果展示 */
@Composable
private fun TestRow(label: String, state: TestState, onTest: () -> Unit) {
    Column {
        OutlinedButton(
            onClick = onTest,
            enabled = !state.testing,
            shape = MaterialTheme.shapes.small,
        ) {
            if (state.testing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("测试中…")
            } else {
                Text(label)
            }
        }
        state.result?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.success) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** MiniMax 合成模型下拉（选项与小程序 MINIMAX_MODELS 一致） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimaxModelDropdown(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = VoiceCatalog.MINIMAX_MODELS.firstOrNull { it.value == value }?.label ?: value
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("合成模型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            VoiceCatalog.MINIMAX_MODELS.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label) },
                    onClick = {
                        onChange(m.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 音色列表：预置音色 + 复刻音色（设默认/试听/删除） */
@Composable
private fun VoiceSection(
    ui: AppSettings,
    previewingId: String,
    onPreview: (String) -> Unit,
    onToggleDefault: (String) -> Unit,
    onDelete: (ClonedVoice) -> Unit,
) {
    val effectiveDefault = ui.preferredVoiceId.ifBlank { AppSettings.DEFAULT_MINIMAX_VOICE }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VoiceCatalog.PRESET_VOICES.forEach { v ->
            VoiceRow(
                label = v.label,
                isDefault = v.value == effectiveDefault,
                previewing = previewingId == v.value,
                onPreview = { onPreview(v.value) },
                onToggleDefault = { onToggleDefault(v.value) },
                onDelete = null,
            )
        }
        ui.clonedVoices.forEach { v ->
            VoiceRow(
                label = v.name,
                isDefault = v.voiceId == ui.preferredVoiceId,
                previewing = previewingId == v.voiceId,
                onPreview = { onPreview(v.voiceId) },
                onToggleDefault = { onToggleDefault(v.voiceId) },
                onDelete = { onDelete(v) },
            )
        }
        val defaultLabel = VoiceCatalog.labelOf(
            ui.preferredVoiceId,
            ui.clonedVoices.map { it.voiceId to it.name },
        )
        Text(
            "默认播报音色：$defaultLabel，也可在对话中随时切换",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoiceRow(
    label: String,
    isDefault: Boolean,
    previewing: Boolean,
    onPreview: () -> Unit,
    onToggleDefault: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (isDefault) {
            Text(
                "默认",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        TextButton(onClick = onPreview, enabled = !previewing) {
            if (previewing) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Text("试听")
            }
        }
        TextButton(onClick = onToggleDefault) {
            Text(if (isDefault) "取消默认" else "设默认")
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 家长声音复刻区块（录音 → 命名 → 复刻） */
@Composable
private fun CloneSection(
    clone: CloneUiState,
    onNameChange: (String) -> Unit,
    onRecordToggle: () -> Unit,
    onReRecord: () -> Unit,
    onStartClone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "录一段家长声音（10 秒以上、最长 5 分钟），复刻成 AI 老师的声音给孩子讲题",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (clone.state) {
            CloneState.IDLE -> {
                OutlinedButton(onClick = onRecordToggle, shape = MaterialTheme.shapes.small) {
                    Text("开始录音")
                }
            }
            CloneState.RECORDING -> {
                Text(
                    "录音中 ${formatSec(clone.recordSec)}（10 秒以上，最长 5 分钟）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = onRecordToggle, shape = MaterialTheme.shapes.small) {
                    Text("结束录音")
                }
            }
            CloneState.RECORDED, CloneState.FAILED, CloneState.CLONING -> {
                Text(
                    "已录 ${formatSec(clone.recordSec)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = clone.name,
                    onValueChange = onNameChange,
                    label = { Text("音色名字（如：妈妈）") },
                    singleLine = true,
                    enabled = clone.state != CloneState.CLONING,
                    modifier = Modifier.fillMaxWidth(),
                )
                clone.error?.let {
                    Text(
                        "复刻失败：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onReRecord,
                        enabled = clone.state != CloneState.CLONING,
                        shape = MaterialTheme.shapes.small,
                    ) { Text("重新录") }
                    Button(
                        onClick = onStartClone,
                        enabled = clone.state != CloneState.CLONING,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        if (clone.state == CloneState.CLONING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("复刻中…")
                        } else {
                            Text("开始复刻")
                        }
                    }
                }
            }
        }
    }
}

/** 家长密码设置/修改弹窗：已设置时先验证旧密码 */
@Composable
private fun PinDialog(
    pinSet: Boolean,
    onVerify: (String) -> Boolean,
    onSetPin: (String) -> Boolean,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(if (pinSet) "verify" else "set") }
    var pinOld by remember { mutableStateOf("") }
    var pinNew by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == "verify") "验证家长密码" else "设置家长密码") },
        text = {
            Column {
                if (step == "set") {
                    Text(
                        "孩子端退出时需要输入，防止孩子误操作",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = if (step == "verify") pinOld else pinNew,
                    onValueChange = { v ->
                        if (v.length <= 6 && v.all { it.isDigit() }) {
                            if (step == "verify") pinOld = v else pinNew = v
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (step == "verify") "请输入当前密码" else "4-6 位数字") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { e -> { Text(e) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = MaterialTheme.shapes.small,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step == "verify") {
                        if (!onVerify(pinOld)) {
                            error = "密码不对"
                            return@TextButton
                        }
                        step = "set"
                        pinNew = ""
                        error = null
                        return@TextButton
                    }
                    if (!onSetPin(pinNew)) {
                        error = "请输入 4-6 位数字"
                        return@TextButton
                    }
                    onDone()
                },
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 调试模式：密码开启 → 记录最近 10 条失败模型请求（含完整提示词），可复制/导出/清空 */
@Composable
private fun DebugSection(
    debugMode: Boolean,
    entries: List<com.example.asr.data.remote.DebugEntry>,
    onEnable: (String) -> Boolean,
    onDisable: () -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Text("调试模式", style = MaterialTheme.typography.titleSmall)
    if (!debugMode) {
        var password by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        Text(
            "开启后记录失败的模型请求（含完整提示词），便于排查",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    wrong = false
                },
                label = { Text("调试密码") },
                singleLine = true,
                isError = wrong,
                supportingText = if (wrong) ({ Text("密码错误") }) else null,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    if (onEnable(password)) {
                        password = ""
                    } else {
                        wrong = true
                    }
                },
                enabled = password.isNotBlank(),
                shape = MaterialTheme.shapes.small,
            ) { Text("开启", maxLines = 1) }
        }
        return
    }

    Text(
        "已开启：记录最近 ${DebugLog.MAX_ENTRIES} 条失败请求",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (entries.isEmpty()) {
        Text(
            "暂无失败请求记录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    entries.forEach { e ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            Text(
                "${DebugLog.formatTime(e.time)} ${e.action}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${e.model} · ${e.error.take(80)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(DebugLog.formatDebugText(e)))
                }) { Text("复制") }
                TextButton(onClick = {
                    runCatching {
                        val dir = File(context.cacheDir, "debug").apply { mkdirs() }
                        val file = File(dir, DebugLog.exportFileName(e))
                        file.writeText(DebugLog.formatDebugText(e))
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file,
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "导出调试信息"))
                    }
                }) { Text("导出") }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRefresh, shape = MaterialTheme.shapes.small) { Text("刷新") }
        OutlinedButton(onClick = onClear, shape = MaterialTheme.shapes.small) { Text("清空") }
        OutlinedButton(onClick = onDisable, shape = MaterialTheme.shapes.small) { Text("关闭调试模式") }
    }
}

private fun formatSec(sec: Int): String = "${sec / 60}:${"%02d".format(sec % 60)}"
