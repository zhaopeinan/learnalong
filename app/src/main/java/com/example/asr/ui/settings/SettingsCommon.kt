package com.example.asr.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.core.content.FileProvider
import com.example.asr.data.remote.DebugEntry
import com.example.asr.data.remote.DebugLog
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.settings.ClonedVoice
import com.example.asr.domain.VoiceCatalog
import java.io.File

/** 设置各子页共用的区块组件（从原单页 SettingsScreen 拆出） */

/** 模型测试按钮 + 结果展示 */
@Composable
internal fun TestRow(label: String, state: TestState, onTest: () -> Unit) {
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

/** 保存按钮（含已保存状态），需要手动保存的字段子页底部共用 */
@Composable
internal fun SettingsSaveButton(saved: Boolean, onSave: () -> Unit) {
    Button(
        onClick = onSave,
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

/** MiniMax 合成模型下拉（选项与小程序 MINIMAX_MODELS 一致） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MinimaxModelDropdown(value: String, onChange: (String) -> Unit) {
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
internal fun VoiceSection(
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
internal fun CloneSection(
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
internal fun PinDialog(
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
internal fun DebugSection(
    debugMode: Boolean,
    entries: List<DebugEntry>,
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

internal fun formatSec(sec: Int): String = "${sec / 60}:${"%02d".format(sec % 60)}"
