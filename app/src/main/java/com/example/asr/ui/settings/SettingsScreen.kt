package com.example.asr.ui.settings

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.settings.AppSettings
import com.example.asr.ui.components.AppBackTopBar

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

    Scaffold(topBar = { AppBackTopBar("设置", onBack = onBack) }) { padding ->
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
