package com.example.asr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.asr.ui.components.AppBackTopBar

/** 设置 → 模型服务：SiliconFlow API（Key/Base URL/ASR/LLM/VLM 模型 + 连通性测试） */
@Composable
fun ModelSettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val asrTest by vm.asrTest.collectAsStateWithLifecycle()
    val llmTest by vm.llmTest.collectAsStateWithLifecycle()
    val vlmTest by vm.vlmTest.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeToast()
        }
    }

    Scaffold(
        topBar = { AppBackTopBar("模型服务", onBack = onBack) },
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
            TestRow(label = "测试转写模型", state = asrTest, onTest = vm::testAsr)
            OutlinedTextField(
                value = ui.llmModel,
                onValueChange = { v -> vm.update { it.copy(llmModel = v) } },
                label = { Text("分析模型（LLM）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TestRow(label = "测试分析模型", state = llmTest, onTest = vm::testLlm)
            OutlinedTextField(
                value = ui.vlmModel,
                onValueChange = { v -> vm.update { it.copy(vlmModel = v) } },
                label = { Text("多模态模型（VLM，错题照片分析）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TestRow(label = "测试多模态模型", state = vlmTest, onTest = vm::testVlm)

            Spacer(Modifier.height(16.dp))
            SettingsSaveButton(saved = saved, onSave = vm::save)
        }
    }
}
