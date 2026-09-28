package com.example.asr.ui.points

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import com.example.asr.data.local.entity.PointRecordEntity
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.PointGoalEntity
import com.example.asr.data.local.entity.PointTaskEntity
import com.example.asr.domain.KidPoints
import com.example.asr.media.SoundEffects
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.CelebrationOverlay
import com.example.asr.ui.components.MasteryProgress
import com.example.asr.ui.util.toMinuteString

/**
 * 积分乐园（家长端，按孩子）：战报 + 多目标兑换 + 加分任务 + 积分流水。
 * 目标可多个并存，家长任选已达成的一个兑换；加分/兑换瞬间播放庆祝动效与合成音效。
 * PointsScreen = 带返回顶栏的独立页（孩子管理入口）；PointsView 供积分 tab 复用。
 */
@Composable
fun PointsScreen(
    childId: Long,
    onBack: () -> Unit,
) {
    PointsView(
        childId = childId,
        topBar = { childName -> AppBackTopBar("${childName}的积分乐园", onBack = onBack) },
    )
}

/**
 * 积分乐园内容（不含页面级顶栏）：topBar 由调用方提供，参数为当前孩子名。
 */
@Composable
fun PointsView(
    childId: Long,
    topBar: @Composable (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: PointsViewModel = viewModel(key = "points_$childId", factory = viewModelFactory {
        initializer {
            PointsViewModel(
                pointsRepository = app.container.pointsRepository,
                childRepository = app.container.childRepository,
                childId = childId,
            )
        }
    })

    val child by vm.child.collectAsStateWithLifecycle()
    val points by vm.points.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val activeGoals by vm.activeGoals.collectAsStateWithLifecycle()
    val redeemedGoals by vm.redeemedGoals.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val celebration by vm.celebration.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var editingTask by remember { mutableStateOf<PointTaskEntity?>(null) }
    var reversingRecord by remember { mutableStateOf<PointRecordEntity?>(null) }
    var showAddTask by remember { mutableStateOf(false) }
    var showAddGoal by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<PointGoalEntity?>(null) }
    var deletingGoal by remember { mutableStateOf<PointGoalEntity?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    // 庆祝动效伴随音效
    LaunchedEffect(celebration?.key) {
        celebration?.let {
            if (it.long) SoundEffects.playRedeem() else SoundEffects.playEarn()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { topBar(child?.name ?: "") },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "score") { ScoreCard(points = points) }

                item(key = "goal_head") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "兑换目标",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showAddGoal = true }) { Text("添加目标") }
                    }
                }
                if (activeGoals.isEmpty()) {
                    item(key = "goal_empty") {
                        GoalEmptyCard(onAdd = { showAddGoal = true })
                    }
                }
                items(activeGoals, key = { "goal_${it.id}" }) { goal ->
                    GoalCard(
                        goal = goal,
                        points = points,
                        onRedeem = { vm.redeem(goal) },
                        onEdit = { editingGoal = goal },
                        onDelete = { deletingGoal = goal },
                    )
                }
                if (redeemedGoals.isNotEmpty()) {
                    item(key = "goal_history") {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                redeemedGoals.forEach { g ->
                                    Text(
                                        "✅ 「${g.name}」（${g.targetPoints} 分）已兑现",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "task_head") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "加分任务",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showAddTask = true }) { Text("添加任务") }
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onEarn = { vm.earn(task) },
                        onEdit = { editingTask = task },
                        onDelete = { vm.deleteTask(task) },
                    )
                }

                if (records.isNotEmpty()) {
                    item(key = "record_head") {
                        Text(
                            "积分流水",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item(key = "records") {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                records.forEach { record ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(record.reason, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                record.createdAt.toMinuteString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            text = if (record.delta >= 0) "+${record.delta}" else "${record.delta}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (record.delta >= 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.tertiary,
                                        )
                                        if (record.delta > 0) {
                                            TextButton(onClick = { reversingRecord = record }) {
                                                Text("删除")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        CelebrationOverlay(
            celebration = celebration,
            onFinished = { vm.dismissCelebration() },
        )
    }

    if (showAddTask) {
        TaskEditDialog(
            title = "添加任务",
            initialName = "",
            initialPoints = "",
            onDismiss = { showAddTask = false },
            onConfirm = { name, pts ->
                vm.addTask(name, pts)
                showAddTask = false
            },
        )
    }
    editingTask?.let { task ->
        TaskEditDialog(
            title = "编辑任务",
            initialName = task.name,
            initialPoints = task.points.toString(),
            onDismiss = { editingTask = null },
            onConfirm = { name, pts ->
                vm.updateTask(task, name, pts)
                editingTask = null
            },
        )
    }
    if (showAddGoal) {
        GoalEditDialog(
            title = "添加兑换目标",
            initialName = "",
            initialTarget = "",
            onDismiss = { showAddGoal = false },
            onConfirm = { name, target ->
                vm.setGoal(name, target)
                showAddGoal = false
            },
        )
    }
    editingGoal?.let { goal ->
        GoalEditDialog(
            title = "编辑目标",
            initialName = goal.name,
            initialTarget = goal.targetPoints.toString(),
            onDismiss = { editingGoal = null },
            onConfirm = { name, target ->
                vm.updateGoal(goal, name, target)
                editingGoal = null
            },
        )
    }
    deletingGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { deletingGoal = null },
            title = { Text("删除目标「${goal.name}」？") },
            text = { Text("删除后不可恢复（已攒的积分不会受影响）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteGoal(goal)
                        deletingGoal = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGoal = null }) { Text("取消") }
            },
        )
    }
    reversingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { reversingRecord = null },
            title = { Text("删除这条加分？") },
            text = { Text("「${record.reason} +${record.delta}」将被删除，同时从当前积分中扣回 ${record.delta} 分。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.reverseEarn(record)
                        reversingRecord = null
                    },
                ) {
                    Text("删除并扣回", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { reversingRecord = null }) { Text("取消") }
            },
        )
    }
}

/** 顶部战报卡：大数字滚动 + 变化时弹跳 */
@Composable
private fun ScoreCard(points: Int) {
    val animatedPoints by animateIntAsState(
        targetValue = points,
        animationSpec = tween(500),
        label = "pointsCount",
    )
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(points) {
        bounce.animateTo(1.2f, tween(120))
        bounce.animateTo(1f, spring())
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$animatedPoints",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "当前积分",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    points <= 0 -> "完成任务就能赚积分啦"
                    else -> "太棒了，继续加油！"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 未设目标时的引导卡 */
@Composable
private fun GoalEmptyCard(onAdd: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "设一个小目标（比如「去游乐园 100 分」），攒够了就兑现奖励",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) { Text("设置目标") }
        }
    }
}

/** 单个目标卡：进度条 + 还差 N 分；达成后高亮并可兑换；⋮ 菜单编辑/删除 */
@Composable
private fun GoalCard(
    goal: PointGoalEntity,
    points: Int,
    onRedeem: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val achieved = KidPoints.isAchieved(points, goal.targetPoints)
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (achieved) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (achieved) "🎉 ${goal.name}" else goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${points}/${goal.targetPoints}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            MasteryProgress(
                mastery = (KidPoints.progress(points, goal.targetPoints) * 100).toInt(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (achieved) "已达成，快兑换奖励吧" else "还差 ${KidPoints.remaining(points, goal.targetPoints)} 分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (achieved) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRedeem,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                ) { Text("兑换奖励（-${goal.targetPoints} 分）") }
            }
        }
    }
}

/** 加分任务行：点名/分值可编辑删除，点「+N」给孩子加分 */
@Composable
private fun TaskRow(
    task: PointTaskEntity,
    onEarn: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                task.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onEarn, shape = CircleShape) { Text("+${task.points}") }
        }
    }
}

/** 任务新增/编辑弹窗：名称 + 分值（1-99） */
@Composable
private fun TaskEditDialog(
    title: String,
    initialName: String,
    initialPoints: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var pointsText by remember { mutableStateOf(initialPoints) }
    val points = pointsText.toIntOrNull()
    val valid = KidPoints.isValidTask(name, points ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("任务名称") },
                    placeholder = { Text("如：做家务") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pointsText,
                    onValueChange = { v -> if (v.length <= 2 && v.all { it.isDigit() }) pointsText = v },
                    label = { Text("分值（1-99）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { points?.let { onConfirm(name, it) } },
                enabled = valid,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 目标新增/编辑弹窗：目标名称 + 所需积分 */
@Composable
private fun GoalEditDialog(
    title: String,
    initialName: String,
    initialTarget: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var targetText by remember { mutableStateOf(initialTarget) }
    val target = targetText.toIntOrNull()
    val valid = KidPoints.isValidGoal(name, target ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("目标名称") },
                    placeholder = { Text("如：去游乐园") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) targetText = v },
                    label = { Text("所需积分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let { onConfirm(name, it) } },
                enabled = valid,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
