package com.example.asr.ui.work

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.audio.AudioImporter
import com.example.asr.data.local.entity.WorkScenario
import com.example.asr.domain.WorkPrompt
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.util.toDurationString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 场景选项（对齐小程序 work/record 的 SCENARIOS） */
private data class ScenarioOption(val key: String, val label: String, val desc: String)

private val SCENARIOS = listOf(
    ScenarioOption(WorkScenario.MEETING, "会议", "议题结论 + 行动项"),
    ScenarioOption(WorkScenario.TALK, "工作谈话", "观点共识 + 跟进事项"),
    ScenarioOption(WorkScenario.CALL, "通话录音", "关键信息 + 待办约定"),
)

/**
 * 工作端录音页（对应小程序 work/record）：场景选择 → 分段录音（复用 RecordingService）
 * → 保存为工作录音并跳详情页自动开始转写分析；支持从文件导入外部音频。
 */
@Composable
fun WorkRecordScreen(
    onSaved: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: WorkRecordViewModel = viewModel(factory = viewModelFactory {
        initializer { WorkRecordViewModel(app) }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    var importing by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    // 导入外部音频（按当前选择的场景分析）
    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importing = true
        scope.launch(Dispatchers.IO) {
            try {
                val file = AudioImporter.import(context, uri)
                withContext(Dispatchers.Main) { vm.saveImported(file) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    importing = false
                    snackbarHostState.showSnackbar("导入失败：${e.message}")
                }
            }
        }
    }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }
    LaunchedEffect(ui.savedId) {
        ui.savedId?.let { onSaved(it) }
    }

    Scaffold(
        topBar = { AppBackTopBar("工作录音", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!ui.isRecording && !ui.interruptedFinished) {
                // 待机：选择场景
                Text(
                    "选择场景（影响 AI 纪要与待办的提取方式）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SCENARIOS.forEach { s ->
                    ScenarioItem(
                        option = s,
                        selected = ui.scenario == s.key,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            vm.selectScenario(s.key)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(32.dp))
            }

            Text(
                text = ui.elapsedSec.toDurationString(),
                style = MaterialTheme.typography.displayMedium,
            )
            // 实时音量条（录音中才有振幅）
            if (ui.isRecording && !ui.isPaused) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ui.amplitude },
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
            if (ui.isRecording) {
                Spacer(Modifier.height(4.dp))
                Text(
                    WorkPrompt.scenarioLabel(ui.scenario) + if (ui.isPaused) " · 已暂停" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.isRecording && ui.segmentCount > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "超长录音已自动分段 · 第 ${ui.segmentCount} 段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ui.interruptedFinished) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "录音曾被系统中断，已保留实际录到的内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(24.dp))

            if (!hasAudioPermission && !ui.isRecording && !ui.interruptedFinished) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    shape = CircleShape,
                ) {
                    Text("授权麦克风", maxLines = 1)
                }
            } else if (ui.isRecording) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { showDiscardConfirm = true },
                        enabled = !ui.saving,
                        shape = CircleShape,
                    ) {
                        Text("取消", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { if (ui.isPaused) vm.resumeRecording() else vm.pauseRecording() },
                        enabled = !ui.saving,
                        shape = CircleShape,
                    ) {
                        Text(if (ui.isPaused) "继续" else "暂停", maxLines = 1)
                    }
                    Button(
                        onClick = vm::stopAndSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        enabled = !ui.saving,
                        shape = CircleShape,
                    ) {
                        if (ui.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError,
                            )
                        } else {
                            Text("结束", maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "结束后自动转写并生成纪要",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (ui.interruptedFinished) {
                Button(
                    onClick = vm::stopAndSave,
                    enabled = !ui.saving,
                    shape = CircleShape,
                ) {
                    if (ui.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("保存录音", maxLines = 1)
                    }
                }
            } else {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.startRecording()
                    },
                    shape = CircleShape,
                ) {
                    Text("点一下，开始录音", maxLines = 1)
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    enabled = !importing,
                    shape = CircleShape,
                ) {
                    Text(if (importing) "导入中…" else "导入外部音频", maxLines = 1)
                }
            }
        }
    }

    // 丢弃确认（对齐小程序 onCancelTap）
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("丢弃这段录音？") },
            text = { Text("取消后录音不会被保存") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        vm.discardRecording()
                        onBack()
                    },
                ) { Text("丢弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 场景选择项（对齐小程序 scenario-item） */
@Composable
private fun ScenarioItem(
    option: ScenarioOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.shapes.medium,
                ) else Modifier
            ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(workScenarioIcon(option.key), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    option.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
