package com.example.asr.ui.kid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.ReviewTaskWithWeakPoint
import com.example.asr.data.remote.dto.Exercise
import com.example.asr.data.remote.dto.TaskContent
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.AppTopSpace
import com.example.asr.ui.components.MasteryProgress
import com.example.asr.ui.components.SkeletonBlock
import com.example.asr.ui.components.TaskContentState

/**
 * 孩子端「我的闯关」（对应小程序 kid-progress 页）：
 * 顶部战报（星星/已消灭）+ 今日任务做题 + 小怪兽图鉴；文字内容均可点 🔊 播给孩子听。
 */
@Composable
fun KidProgressScreen(
    onAskTutor: (weakPointId: Long, childId: Long) -> Unit,
    onExitToParent: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: KidProgressViewModel = viewModel(factory = viewModelFactory {
        initializer {
            KidProgressViewModel(
                tutorRepository = app.container.tutorRepository,
                childRepository = app.container.childRepository,
                kidReward = app.container.kidReward,
                speechSynthesizer = app.container.speechSynthesizer,
                settingsStore = app.container.settingsStore,
            )
        }
    })

    val child by vm.child.collectAsStateWithLifecycle()
    val boundChildId by vm.boundChildId.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val stars by vm.stars.collectAsStateWithLifecycle()
    val dueTasks by vm.dueTasks.collectAsStateWithLifecycle()
    val monsters by vm.monsters.collectAsStateWithLifecycle()
    val contents by vm.contents.collectAsStateWithLifecycle()
    val voiceEnabled by vm.voiceEnabled.collectAsStateWithLifecycle()
    val playingKey by vm.playingKey.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPin by remember { mutableStateOf(false) }

    // 离开页面停止播报（对应小程序 onHide stopCurrent）
    DisposableEffect(Unit) {
        onDispose { vm.stopSpeaking() }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                AppTopSpace()
                // 左上角小锁：回家长端（PIN 验证）
                Text(
                    "🔒",
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                        .clip(CircleShape)
                        .clickable { showPin = true }
                        .padding(6.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!ready) {
            // 设置加载中：短暂空白，避免「未绑定孩子」空态闪烁
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        if (boundChildId == null) {
            // 未绑定孩子（异常态：小程序提示由家长端进入）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🧒", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "请家长先在「我的」页进入孩子端",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        val defeatedCount = monsters.count { it.defeated }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column {
                    Text(
                        "我的闯关",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${child?.name ?: ""}，把这些小怪兽一个个消灭掉！".removePrefix("，"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "score") {
                ScoreCard(stars = stars, defeatedCount = defeatedCount)
            }

            if (dueTasks.isNotEmpty()) {
                item(key = "due_head") { SectionHead("今天要练（${dueTasks.size}）") }
                items(dueTasks, key = { it.taskId }) { item ->
                    KidTaskCard(
                        item = item,
                        contentState = contents[item.taskId],
                        voiceEnabled = voiceEnabled,
                        playingKey = playingKey,
                        onLoadContent = { vm.loadContent(item) },
                        onSpeak = vm::onSpeak,
                        onMark = { mastered -> vm.mark(item, mastered) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(250),
                            fadeOutSpec = tween(250),
                            placementSpec = tween(250),
                        ),
                    )
                }
            } else if (monsters.isNotEmpty()) {
                item(key = "all_done") {
                    HeroCard(title = "今天的任务都完成啦，真棒！", subtitle = "明天再来看看吧")
                }
            }

            if (monsters.isNotEmpty()) {
                item(key = "monster_head") { SectionHead("小怪兽图鉴") }
                items(monsters, key = { it.weakPoint.id }) { monster ->
                    MonsterCard(
                        monster = monster,
                        voiceEnabled = voiceEnabled,
                        playingKey = playingKey,
                        onSpeak = vm::onSpeak,
                        onAskTutor = { onAskTutor(monster.weakPoint.id, monster.weakPoint.childId) },
                    )
                }
            }

            if (dueTasks.isEmpty() && monsters.isEmpty()) {
                item(key = "hero") {
                    HeroCard(emoji = "🎉", title = "暂时没有小怪兽，继续保持！")
                }
            }
        }
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

/** 战报卡：我的星星 / 已消灭小怪兽 */
@Composable
private fun ScoreCard(stars: Int, defeatedCount: Int) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreItem(value = "⭐ $stars", label = "我的星星", modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            ScoreItem(value = "🏆 $defeatedCount", label = "已消灭小怪兽", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScoreItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHead(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** 空态/完成态大卡 */
@Composable
private fun HeroCard(title: String, subtitle: String? = null, emoji: String? = null) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (emoji != null) {
                Text(emoji, fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 今日任务卡：展开做题，「还要练」/「我会啦」反馈 */
@Composable
private fun KidTaskCard(
    item: ReviewTaskWithWeakPoint,
    contentState: TaskContentState?,
    voiceEnabled: Boolean,
    playingKey: String,
    onLoadContent: () -> Unit,
    onSpeak: (String, String) -> Unit,
    onMark: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "expandArrow",
    )

    AppCard(
        onClick = {
            expanded = !expanded
            if (expanded) onLoadContent()
        },
        modifier = modifier.fillMaxWidth().animateContentSize(tween(200)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.knowledgePoint, style = MaterialTheme.typography.titleMedium)
                    Text(
                        item.subject,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    when (contentState) {
                        is TaskContentState.Ready -> KidExerciseList(
                            content = contentState.content,
                            taskId = item.taskId,
                            voiceEnabled = voiceEnabled,
                            playingKey = playingKey,
                            onSpeak = onSpeak,
                        )
                        is TaskContentState.Failed -> Text(
                            "${contentState.message}（点按重试）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable(onClick = onLoadContent),
                        )
                        else -> Column {
                            repeat(3) {
                                SkeletonBlock(
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    shape = MaterialTheme.shapes.small,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onMark(false) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("还要练", maxLines = 1)
                        }
                        Button(
                            onClick = { onMark(true) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("我会啦", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KidExerciseList(
    content: TaskContent,
    taskId: Long,
    voiceEnabled: Boolean,
    playingKey: String,
    onSpeak: (String, String) -> Unit,
) {
    Column {
        content.exercises.forEachIndexed { i, exercise ->
            KidExerciseItem(
                index = i,
                exercise = exercise,
                speakKey = { prefix -> "$prefix$taskId-$i" },
                voiceEnabled = voiceEnabled,
                playingKey = playingKey,
                onSpeak = onSpeak,
            )
            Spacer(Modifier.height(8.dp))
        }
        if (content.tips.isNotBlank()) {
            Text(
                "💡 ${content.tips}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KidExerciseItem(
    index: Int,
    exercise: Exercise,
    speakKey: (String) -> String,
    voiceEnabled: Boolean,
    playingKey: String,
    onSpeak: (String, String) -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${index + 1}. ${exercise.question}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (voiceEnabled) {
                val key = speakKey("q")
                SpeakButton(
                    playing = playingKey == key,
                    onClick = { onSpeak(key, "第${index + 1}题。${exercise.question}") },
                )
            }
        }
        if (revealed) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "参考答案：${exercise.answer}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (voiceEnabled) {
                    val key = speakKey("a")
                    SpeakButton(
                        playing = playingKey == key,
                        onClick = {
                            onSpeak(
                                key,
                                "参考答案：${exercise.answer}。" +
                                    if (exercise.hint.isNotBlank()) "小提示：${exercise.hint}" else "",
                            )
                        },
                    )
                }
            }
            if (exercise.hint.isNotBlank()) {
                Text(
                    "小提示：${exercise.hint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "我会了，看解析",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { revealed = true }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** 🔊 播报钮：播放中变 ⏸，再点停止 */
@Composable
private fun SpeakButton(playing: Boolean, onClick: () -> Unit) {
    Text(
        text = if (playing) "⏸" else "🔊",
        fontSize = 16.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (playing) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
    )
}

/** 小怪兽卡：掌握度低张牙舞爪，掌握度满翻面成勋章（已消灭整卡降不透明度，同小程序） */
@Composable
private fun MonsterCard(
    monster: MonsterView,
    voiceEnabled: Boolean,
    playingKey: String,
    onSpeak: (String, String) -> Unit,
    onAskTutor: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val wp = monster.weakPoint

    AppCard(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (monster.defeated) 0.75f else 1f)
            .animateContentSize(tween(200)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (monster.defeated) "🏆" else "👾", fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            wp.knowledgePoint,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        when {
                            monster.dueToday && !monster.defeated -> MonsterBadge("今天要练", due = true)
                            monster.defeated -> MonsterBadge("已消灭")
                            monster.almostDone -> MonsterBadge("快消灭啦！")
                        }
                    }
                    Text(
                        wp.subject + " · " + if (monster.defeated) "已消灭" else "消灭进度 ${wp.mastery}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!monster.defeated) {
                        Spacer(Modifier.height(8.dp))
                        MasteryProgress(mastery = wp.mastery, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (voiceEnabled) {
                    Spacer(Modifier.width(8.dp))
                    val key = "m${wp.id}"
                    SpeakButton(
                        playing = playingKey == key,
                        onClick = { onSpeak(key, "${wp.knowledgePoint}。${wp.description}") },
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    if (wp.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            wp.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!monster.defeated) {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onAskTutor,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("找老师帮忙")
                        }
                    }
                }
            }
        }
    }
}

/** 怪兽状态徽章：今天要练 = 主色底白字；其余 = Amber 容器（同小程序 monster-badge） */
@Composable
private fun MonsterBadge(text: String, due: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (due) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(CircleShape)
            .background(
                if (due) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiaryContainer,
            )
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
