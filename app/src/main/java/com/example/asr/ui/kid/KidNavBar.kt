package com.example.asr.ui.kid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asr.AsrApplication
import com.example.asr.data.settings.AppSettings
import com.example.asr.domain.ParentLock
import com.example.asr.ui.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 孩子端底部栏（对应小程序 kid-nav 组件 + 页面左上角家长锁）：
 * 我的闯关 / 问老师 / 家长锁；家长锁弹 PIN 验证，成功回家长端。
 * 未设置 PIN（异常情况）按小程序逻辑直接回家长端。
 */
@Composable
fun KidNavBar(
    currentRoute: String?,
    onProgress: () -> Unit,
    onAsk: () -> Unit,
    onExitToParent: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val scope = rememberCoroutineScope()
    var showPin by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(modifier = Modifier.fillMaxSize()) {
                KidNavItem(
                    emoji = "🏆",
                    label = "我的闯关",
                    selected = currentRoute == Routes.KID_PROGRESS,
                    onClick = onProgress,
                )
                KidNavItem(
                    emoji = "💬",
                    label = "问老师",
                    selected = false,
                    onClick = onAsk,
                )
                KidNavItem(
                    emoji = "🔒",
                    label = "家长锁",
                    selected = false,
                    onClick = {
                        scope.launch {
                            val pin = app.container.settingsStore.settings.first().parentPin
                            if (!ParentLock.hasPin(pin)) {
                                // 未设 PIN（异常情况）：同小程序，直接回家长端
                                app.container.settingsStore.setAppMode(AppSettings.MODE_PARENT)
                                onExitToParent()
                            } else {
                                showPin = true
                            }
                        }
                    },
                )
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding(),
        )
    }

    if (showPin) {
        ParentLockDialog(
            onDismiss = { showPin = false },
            onSuccess = {
                showPin = false
                onExitToParent()
            },
        )
    }
}

@Composable
private fun RowScope.KidNavItem(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * 家长锁 PIN 验证弹窗（对应小程序 kid-nav 的 pin-dialog）：
 * 4-6 位数字，校验失败提示「密码不对」，成功切回家长端。
 */
@Composable
fun ParentLockDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("家长验证") },
        text = {
            Column {
                Text(
                    "输入家长密码，回到家长端",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v ->
                        if (v.length <= 6 && v.all { it.isDigit() }) {
                            pin = v
                            error = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("4-6 位数字") },
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
                    scope.launch {
                        val store = app.container.settingsStore
                        val saved = store.settings.first().parentPin
                        if (ParentLock.verify(saved, pin)) {
                            store.setAppMode(AppSettings.MODE_PARENT)
                            onSuccess()
                        } else {
                            error = "密码不对"
                        }
                    }
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
