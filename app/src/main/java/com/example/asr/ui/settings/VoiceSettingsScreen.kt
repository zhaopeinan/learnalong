package com.example.asr.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.asr.data.settings.ClonedVoice
import com.example.asr.ui.components.AppBackTopBar

/** 设置 → 语音合成与音色：MiniMax Key/模型/测试、播报开关、音色列表、家长声音复刻 */
@Composable
fun VoiceSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val minimaxTest by vm.minimaxTest.collectAsStateWithLifecycle()
    val clone by vm.clone.collectAsStateWithLifecycle()
    val previewingId by vm.previewingId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeToast()
        }
    }

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
        topBar = { AppBackTopBar("语音合成与音色", onBack = onBack) },
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
            TestRow(label = "测试语音合成", state = minimaxTest, onTest = vm::testMinimax)
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

            Spacer(Modifier.height(16.dp))
            SettingsSaveButton(saved = saved, onSave = vm::save)
        }
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
