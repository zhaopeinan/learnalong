package com.example.asr.ui.children

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.R
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildrenScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: ChildrenViewModel = viewModel(factory = viewModelFactory {
        initializer { ChildrenViewModel(app.container.childRepository) }
    })
    val children by vm.children.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ChildEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { AppBackTopBar("孩子管理", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                text = { Text("添加孩子", maxLines = 1) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                shape = CircleShape,
            )
        },
    ) { padding ->
        if (children.isEmpty()) {
            EmptyState(
                icon = ImageVector.vectorResource(R.drawable.ic_child),
                title = "还没有孩子档案",
                description = "添加孩子后才能开始录音，复习任务也会按孩子分开管理",
                actionLabel = "添加孩子",
                onAction = { showAddDialog = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(children, key = { it.id }) { child ->
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = tween(250),
                                fadeOutSpec = tween(250),
                                placementSpec = tween(250),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(child.name, style = MaterialTheme.typography.titleMedium)
                                child.grade?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { editing = child }) { Text("编辑") }
                            TextButton(onClick = { vm.delete(child) }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ChildEditDialog(
            title = "添加孩子",
            initialName = "",
            initialGrade = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, grade ->
                vm.add(name, grade)
                showAddDialog = false
            },
        )
    }
    editing?.let { child ->
        ChildEditDialog(
            title = "编辑孩子",
            initialName = child.name,
            initialGrade = child.grade ?: "",
            onDismiss = { editing = null },
            onConfirm = { name, grade ->
                vm.update(child.copy(name = name.trim(), grade = grade.trim().ifEmpty { null }))
                editing = null
            },
        )
    }
}

/** 学段 → 年级 选项 */
private val gradeOptions = linkedMapOf(
    "幼儿园" to listOf("小班", "中班", "大班"),
    "小学" to listOf("一年级", "二年级", "三年级", "四年级", "五年级", "六年级"),
    "初中" to listOf("一年级", "二年级", "三年级"),
    "高中" to listOf("一年级", "二年级", "三年级"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildEditDialog(
    title: String,
    initialName: String,
    initialGrade: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    // 旧数据可能是自由文本（如"三年级"），无法拆分时学段/年级留空让用户重选
    val (initialStage, initialLevel) = gradeOptions.entries
        .firstOrNull { (stage, _) -> initialGrade.startsWith(stage) }
        ?.let { (stage, levels) -> stage to initialGrade.removePrefix(stage).takeIf { it in levels } }
        ?: (null to null)
    var stage by remember { mutableStateOf(initialStage) }
    var level by remember { mutableStateOf(initialLevel) }
    var stageExpanded by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }
    val gradeValid = stage != null && level != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    singleLine = true,
                )
                // 先选学段
                ExposedDropdownMenuBox(
                    expanded = stageExpanded,
                    onExpandedChange = { stageExpanded = it },
                ) {
                    OutlinedTextField(
                        value = stage ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("学段") },
                        placeholder = { Text("请选择学段") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = stageExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = stageExpanded,
                        onDismissRequest = { stageExpanded = false },
                    ) {
                        gradeOptions.keys.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    if (stage != s) level = null // 换学段后年级需重选
                                    stage = s
                                    stageExpanded = false
                                },
                            )
                        }
                    }
                }
                // 再选年级（选项跟随学段）
                ExposedDropdownMenuBox(
                    expanded = levelExpanded,
                    onExpandedChange = { if (stage != null) levelExpanded = it },
                ) {
                    OutlinedTextField(
                        value = level ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = stage != null,
                        label = { Text("年级") },
                        placeholder = { Text(if (stage == null) "请先选学段" else "请选择年级") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false },
                    ) {
                        (gradeOptions[stage] ?: emptyList()).forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l) },
                                onClick = {
                                    level = l
                                    levelExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, "${stage}${level}") },
                enabled = name.isNotBlank() && gradeValid,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
