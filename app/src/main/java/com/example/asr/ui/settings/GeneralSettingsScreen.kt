package com.example.asr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.asr.data.settings.AppSettings
import com.example.asr.domain.ParentLock
import com.example.asr.ui.components.AppBackTopBar

/** 设置 → 通用：外观主题、家长密码、调试模式（均立即生效，无需保存） */
@Composable
fun GeneralSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
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

    Scaffold(
        topBar = { AppBackTopBar("通用", onBack = onBack) },
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
            DebugSection(
                debugMode = ui.debugMode,
                entries = debugEntries,
                onEnable = vm::enableDebug,
                onDisable = vm::disableDebug,
                onClear = vm::clearDebugLog,
                onRefresh = vm::refreshDebugEntries,
            )
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
}
