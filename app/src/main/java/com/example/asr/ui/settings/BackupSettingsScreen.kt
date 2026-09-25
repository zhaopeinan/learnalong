package com.example.asr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.asr.data.settings.AppSettings
import com.example.asr.ui.components.AppBackTopBar

/** 设置 → 备份与存储：WebDAV 账号配置、录音清理模式、云备份页入口 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val webdavTest by vm.webdavTest.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeToast()
        }
    }

    Scaffold(
        topBar = { AppBackTopBar("备份与存储", onBack = onBack) },
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
            TestRow(label = "测试连接", state = webdavTest, onTest = vm::testWebdav)

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
            Text("存储空间", style = MaterialTheme.typography.titleSmall)
            Text(
                "占用统计与录音清理在「云备份」页管理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.material3.OutlinedButton(
                onClick = onOpenBackup,
                shape = MaterialTheme.shapes.small,
            ) { Text("打开云备份") }

            Spacer(Modifier.height(16.dp))
            SettingsSaveButton(saved = saved, onSave = vm::save)
        }
    }
}
