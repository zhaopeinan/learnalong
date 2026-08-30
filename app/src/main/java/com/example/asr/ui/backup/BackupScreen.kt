package com.example.asr.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.example.asr.data.sync.BackupMode
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.util.toDateTimeString

@Composable
fun BackupScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: BackupViewModel = viewModel(factory = viewModelFactory {
        initializer { BackupViewModel(app) }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var backupMode by remember { mutableStateOf(BackupMode.ALL) }

    Scaffold(topBar = { AppBackTopBar("云备份", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("上次备份", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (ui.lastBackupAt > 0) ui.lastBackupAt.toDateTimeString() else "从未备份",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("WiFi 下启动时自动备份", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "仅在连接 WiFi 时执行，静默进行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = ui.autoBackupOnWifi,
                            onCheckedChange = vm::setAutoBackup,
                        )
                    }
                }
            }

            if (!ui.configured) {
                Text(
                    "尚未配置 WebDAV 账号，请先在设置页填写",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenSettings) { Text("去设置") }
            }

            // 备份进度
            ui.backup.progress?.let { p ->
                LinearProgressIndicator(
                    progress = { p.current.toFloat() / p.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "正在同步 ${p.current}/${p.total}：${p.itemName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ui.backup.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.backup.success) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            ui.restoreMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 备份范围选择
            Text("备份范围", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    BackupMode.ALL to "全部",
                    BackupMode.CONFIG_ONLY to "仅配置",
                    BackupMode.DATA_ONLY to "仅数据",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = backupMode == mode,
                        onClick = { backupMode = mode },
                        label = { Text(label, maxLines = 1) },
                        shape = CircleShape,
                    )
                }
            }
            Text(
                when (backupMode) {
                    BackupMode.ALL -> "配置（科目/模型等设置）+ 全部数据 + 音频"
                    BackupMode.CONFIG_ONLY -> "仅科目、模型、提醒等设置，换机恢复后无需重新配置"
                    BackupMode.DATA_ONLY -> "仅辅导数据与录音，不含设置"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { vm.backupNow(backupMode) },
                enabled = !ui.backup.running && !ui.restoring && ui.configured,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                if (ui.backup.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("立即备份", maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = { showRestoreConfirm = true },
                enabled = !ui.backup.running && !ui.restoring && ui.configured,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (ui.restoring) "恢复中…" else "从云端恢复", maxLines = 1)
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("从云端恢复") },
            text = { Text("将用云端数据覆盖本地同 id 数据，本地多出的数据保留。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    vm.restoreNow()
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("取消") }
            },
        )
    }
}
