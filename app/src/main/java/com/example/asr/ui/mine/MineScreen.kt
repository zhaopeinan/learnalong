package com.example.asr.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.asr.AsrApplication
import com.example.asr.R
import com.example.asr.data.settings.AppSettings
import com.example.asr.domain.ParentLock
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.AppTopSpace
import com.example.asr.ui.components.MenuItem
import com.example.asr.ui.components.TourPlan
import com.example.asr.ui.components.tourTarget
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    onOpenChildren: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenAgreement: () -> Unit,
    onOpenAbout: () -> Unit,
    onEnterKidMode: () -> Unit,
    onEnterWorkMode: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val scope = rememberCoroutineScope()
    val settings by app.container.settingsStore.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val children by app.container.childRepository.children
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    var showPinSet by remember { mutableStateOf(false) }
    var showChildPicker by remember { mutableStateOf(false) }

    fun enterKid(childId: Long) {
        scope.launch {
            // 进入孩子端免密（家长把手机递给孩子），退出需家长锁验证
            app.container.settingsStore.setKidChildId(childId)
            app.container.settingsStore.setAppMode(AppSettings.MODE_KID)
            onEnterKidMode()
        }
    }

    /** 进入孩子端（同小程序 mine.onEnterKid）：无孩子提示；未设家长密码先设置；多个孩子则选择 */
    fun onEnterKid() {
        when {
            children.isEmpty() -> scope.launch {
                snackbarHostState.showSnackbar("请先在孩子管理中添加孩子")
            }
            settings?.let { ParentLock.hasPin(it.parentPin) } != true -> showPinSet = true
            children.size == 1 -> enterKid(children[0].id)
            else -> showChildPicker = true
        }
    }

    Scaffold(
        topBar = { AppTopSpace() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // 工作端入口（对齐小程序 mine「工作」分组：navigateTo 工作端首页）
            AppCard(modifier = Modifier.fillMaxWidth()) {
                MenuItem(
                    title = "工作端",
                    subtitle = "会议 / 工作谈话 / 通话录音 → 纪要与待办",
                    icon = ImageVector.vectorResource(R.drawable.ic_menu_work),
                    onClick = {
                        scope.launch {
                            app.container.settingsStore.setAppMode(AppSettings.MODE_WORK)
                            onEnterWorkMode()
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    MenuItem(
                        title = "孩子管理",
                        subtitle = "添加 / 编辑孩子档案",
                        icon = Icons.Filled.Face,
                        onClick = onOpenChildren,
                    )
                    MenuDivider()
                    MenuItem(
                        title = "孩子端",
                        subtitle = "切换到孩子专用界面（练习 / 问老师 / 闯关）",
                        icon = Icons.Filled.Star,
                        onClick = { onEnterKid() },
                    )
                    MenuDivider()
                    MenuItem(
                        title = "云备份",
                        subtitle = "坚果云 WebDAV 备份、恢复、自动备份",
                        icon = ImageVector.vectorResource(R.drawable.ic_menu_cloud),
                        onClick = onOpenBackup,
                    )
                    MenuDivider()
                    MenuItem(
                        title = "设置",
                        subtitle = "模型服务、语音合成与音色、备份存储、家长密码",
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    MenuItem(
                        title = "使用指南",
                        subtitle = "图文教程，从第一次录音到复习巩固",
                        icon = ImageVector.vectorResource(R.drawable.ic_menu_guide),
                        modifier = Modifier.tourTarget(TourPlan.TAG_MENU_GUIDE),
                        onClick = onOpenGuide,
                    )
                    MenuDivider()
                    MenuItem(
                        title = "用户协议与隐私政策",
                        subtitle = "了解我们如何收集、使用和保护信息",
                        icon = ImageVector.vectorResource(R.drawable.ic_menu_doc),
                        onClick = onOpenAgreement,
                    )
                    MenuDivider()
                    MenuItem(
                        title = "关于伴学记",
                        subtitle = "版本信息与开发团队",
                        icon = Icons.Filled.Info,
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }

    // 设置家长密码弹窗（首次进入孩子端前，对应小程序 mine kidSheet='pin'）
    if (showPinSet) {
        var pin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showPinSet = false },
            title = { Text("设置家长密码") },
            text = {
                Column {
                    Text(
                        "孩子端退出时需要输入，防止孩子误操作",
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
                        if (!ParentLock.isValidNewPin(pin)) {
                            error = "请输入 4-6 位数字"
                            return@TextButton
                        }
                        scope.launch {
                            app.container.settingsStore.setParentPin(pin)
                            showPinSet = false
                            snackbarHostState.showSnackbar("密码已设置")
                            if (children.size == 1) enterKid(children[0].id) else showChildPicker = true
                        }
                    },
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showPinSet = false }) { Text("取消") }
            },
        )
    }

    // 选孩子弹窗（多个孩子时，对应小程序 mine kidSheet='child'）
    if (showChildPicker) {
        AlertDialog(
            onDismissRequest = { showChildPicker = false },
            title = { Text("把手机交给谁？") },
            text = {
                Column {
                    children.forEach { child ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showChildPicker = false
                                    enterKid(child.id)
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(child.name, style = MaterialTheme.typography.bodyLarge)
                            child.grade?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChildPicker = false }) { Text("取消") }
            },
        )
    }
}

/** 菜单项之间的细分隔线，左对齐到文字起始位置 */
@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
