package com.example.asr.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.util.toDurationString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(onSaved: () -> Unit) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: RecordViewModel = viewModel(factory = viewModelFactory {
        initializer { RecordViewModel(app) }
    })
    val ui by vm.ui.collectAsStateWithLifecycle()
    val children by vm.children.collectAsStateWithLifecycle()
    val subjects by vm.subjects.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }
    LaunchedEffect(ui.saved) {
        if (ui.saved) onSaved()
    }

    Scaffold(
        topBar = { AppBackTopBar("录音", onBack = onSaved) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 选择孩子
            var childExpanded by remember { mutableStateOf(false) }
            val selectedChild = children.firstOrNull { it.id == ui.selectedChildId }
            ExposedDropdownMenuBox(
                expanded = childExpanded,
                onExpandedChange = { childExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedChild?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("孩子") },
                    placeholder = { Text("请选择孩子") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = childExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = childExpanded,
                    onDismissRequest = { childExpanded = false },
                ) {
                    children.forEach { child ->
                        DropdownMenuItem(
                            text = { Text(child.name) },
                            onClick = {
                                vm.selectChild(child.id)
                                childExpanded = false
                            },
                        )
                    }
                }
            }
            if (children.isEmpty()) {
                Text(
                    "还没有孩子档案，请先在「我的 → 孩子管理」中添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            // 科目下拉（科目列表在 设置 → 科目管理 中编辑）
            var subjectExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = subjectExpanded,
                onExpandedChange = { subjectExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = ui.subject,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("科目") },
                    placeholder = { Text("请选择科目") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = subjectExpanded,
                    onDismissRequest = { subjectExpanded = false },
                ) {
                    subjects.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            onClick = {
                                vm.setSubject(s)
                                subjectExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(48.dp))
            Text(
                text = ui.elapsedSec.toDurationString(),
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(24.dp))

            if (!hasAudioPermission) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("授权麦克风", maxLines = 1)
                }
            } else if (ui.isRecording) {
                Button(
                    onClick = vm::stopAndSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    enabled = !ui.saving,
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (ui.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        Text("停止并保存", maxLines = 1)
                    }
                }
            } else {
                Button(
                    onClick = vm::startRecording,
                    enabled = ui.selectedChildId != null && ui.subject.isNotBlank(),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("开始录音", maxLines = 1)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSaved, shape = MaterialTheme.shapes.small) {
                Text("返回")
            }
        }
    }
}
