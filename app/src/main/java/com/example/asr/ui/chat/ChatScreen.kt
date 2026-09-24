package com.example.asr.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.asr.AsrApplication
import com.example.asr.data.local.entity.ChatMessageEntity
import com.example.asr.data.local.entity.ChatRole
import com.example.asr.data.local.entity.ChatSessionEntity
import com.example.asr.data.repository.ChatRepository
import com.example.asr.domain.ChatText
import com.example.asr.ui.components.rememberPhotoCapture
import com.example.asr.ui.util.toDateTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** 左右滑判定阈值（对应小程序 TALK_SLIDE_THRESHOLD=60px） */
private val TalkSlideThreshold = 28.dp

/**
 * 问老师（苏格拉底辅导对话页，对应小程序 chat 页）：
 * AI 左气泡（可点喇叭重播）+ 孩子右气泡 + 底部按住说话/打字 + 拍照提问。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    mode: String,
    childId: Long?,
    refId: Long?,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as AsrApplication
    val vm: ChatViewModel = viewModel(
        key = "chat_${mode}_${childId}_${refId}",
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    app = app,
                    chatRepository = app.container.chatRepository,
                    childRepository = app.container.childRepository,
                    settingsStore = app.container.settingsStore,
                    argMode = mode,
                    argChildId = childId,
                    argRefId = refId,
                )
            }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 麦克风权限：按住说话时未授权则先请求
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    // 拍照 / 相册选图：进入输入框上方的暂存区，不立即发送
    val photoCapture = rememberPhotoCapture(
        onResult = { files -> vm.addPendingImages(files) },
        onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
    )

    // 离开页面停止播报（对应小程序 onHide stopCurrent）
    DisposableEffect(Unit) {
        onDispose { vm.stopPlayback() }
    }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeError()
        }
    }
    // 致命错误（无孩子 / 薄弱点已删除）：提示后退出（对应小程序 toast + navigateBack）
    LaunchedEffect(ui.fatalError) {
        val msg = ui.fatalError ?: return@LaunchedEffect
        launch { snackbarHostState.showSnackbar(msg) }
        delay(800)
        onBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            ui.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (ui.sessionId != 0L) {
                            Text(
                                "对话",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        vm.openHistory()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                MessageList(
                    ui = ui,
                    onReplay = vm::onReplay,
                    modifier = Modifier.weight(1f),
                )
                if (ui.pendingImages.isNotEmpty()) {
                    PendingImageStrip(
                        images = ui.pendingImages,
                        onAdd = {
                            if (!ui.busy && !ui.transcribing && !ui.recording) photoCapture.launch()
                        },
                        onRemove = vm::removePendingImage,
                    )
                }
                ChatInputBar(
                    ui = ui,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onTextChange = vm::setTextInput,
                    onSend = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        vm.submitInput()
                    },
                    onPickImages = {
                        if (!ui.busy && !ui.transcribing && !ui.recording) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            photoCapture.launch()
                        }
                    },
                    onToggleMode = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        vm.toggleInputMode()
                    },
                    onTalkStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.onTalkStart()
                    },
                    onTalkMove = vm::setTalkState,
                    onTalkEnd = vm::onTalkEnd,
                )
            }
        }

        // 按住说话手势蒙层：左滑取消 / 原地松开发送 / 右滑转文字
        if (ui.recording) {
            TalkMask(ui = ui)
        }
    }

    // 选孩子弹层（query 未带 childId 且有多个孩子时）
    if (ui.showChildPicker) {
        ModalBottomSheet(onDismissRequest = { /* 必须选人才能开始 */ }) {
            Text(
                "和谁一起学？",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            ui.children.forEach { child ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.pickChild(child.id) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    Text(child.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    child.grade?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 历史会话弹层（顶部放「新对话」入口）
    if (ui.showHistory) {
        ModalBottomSheet(onDismissRequest = vm::closeHistory) {
            Text(
                "对话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { vm.newChat() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "＋ 开始新对话",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (ui.sessions.isEmpty()) {
                    Text(
                        "还没有历史对话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
                ui.sessions.forEach { session ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { vm.pickSession(session) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(
                            session.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            session.updatedAt.toDateTimeString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 消息列表：AI 左气泡 + 孩子右气泡 + AI 工作占位 */
@Composable
private fun MessageList(
    ui: ChatUiState,
    onReplay: (ChatMessageEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var previewPath by remember { mutableStateOf<String?>(null) }

    // 新消息 / 占位出现时滚到底部
    LaunchedEffect(ui.messages.size, ui.busy) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp,
        ),
    ) {
        items(ui.messages, key = { it.id }) { message ->
            if (message.role == ChatRole.ASSISTANT) {
                AssistantBubble(
                    message = message,
                    voiceEnabled = ui.voiceEnabled,
                    playing = ui.playingMessageId == message.id,
                    onReplay = { onReplay(message) },
                )
            } else {
                UserBubble(
                    message = message,
                    onPreviewImage = { previewPath = it },
                )
            }
        }
        if (ui.busy) {
            item(key = "busy") { BusyBubble(ui) }
        }
        item(key = "anchor") { Spacer(Modifier.height(1.dp)) }
    }

    // 点气泡里的照片放大预览
    previewPath?.let { path ->
        AlertDialog(
            onDismissRequest = { previewPath = null },
            text = {
                val bitmap = remember(path) {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "照片预览",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    Text("照片文件不存在或已损坏")
                }
            },
            confirmButton = {
                Text(
                    "关闭",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { previewPath = null }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }
}

/** AI 左气泡：白底 + 边框 + 喇叭重播（对应小程序 .bubble-ai） */
@Composable
private fun AssistantBubble(
    message: ChatMessageEntity,
    voiceEnabled: Boolean,
    playing: Boolean,
    onReplay: () -> Unit,
) {
    // AI 消息显示前清洗括号语气描述（含历史消息）
    val text = remember(message.text) { ChatText.cleanReplyText(message.text).ifBlank { message.text } }
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(
            modifier = Modifier
                .weight(0.78f, fill = false)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(text, fontSize = 15.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurface)
            if (voiceEnabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (playing) "⏸" else "🔊",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (playing) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable(onClick = onReplay)
                        .padding(6.dp),
                )
            }
        }
        Spacer(Modifier.weight(0.22f))
    }
}

/** 孩子右气泡：绿底白字，可带照片；纯图片消息不显示文字（对应小程序 .bubble-user） */
@Composable
private fun UserBubble(
    message: ChatMessageEntity,
    onPreviewImage: (String) -> Unit,
) {
    val images = remember(message.imagePaths) { ChatRepository.decodeImagePaths(message.imagePaths) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Spacer(Modifier.weight(0.22f))
        Column(
            modifier = Modifier
                .weight(0.78f, fill = false)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    images.forEach { path ->
                        ChatImageThumb(
                            path = path,
                            size = 100.dp,
                            onClick = { onPreviewImage(path) },
                        )
                    }
                }
                if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
            }
            if (message.text.isNotBlank()) {
                Text(
                    message.text,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** AI 工作占位：阶段提示 + 趣味小贴士 + 吉祥物蹦跳（对应小程序 .bubble-busy） */
@Composable
private fun BusyBubble(ui: ChatUiState) {
    val mascot = when (ui.busyStage) {
        BusyStage.LOOK -> "👀"
        BusyStage.SPEAK -> "🔊"
        BusyStage.THINK -> "🤔"
    }
    val stageText = when (ui.busyStage) {
        BusyStage.LOOK -> "老师正在看你拍的照片…"
        BusyStage.SPEAK -> "老师准备开口啦…"
        BusyStage.THINK -> "老师正在想一个好问题…"
    }
    val transition = rememberInfiniteTransition(label = "busyBounce")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "busyBounceY",
    )
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(
            modifier = Modifier
                .weight(0.78f, fill = false)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                mascot,
                fontSize = 22.sp,
                modifier = Modifier.graphicsLayer { translationY = bounce.dp.toPx() },
            )
            Text(stageText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            if (ui.busyTip.isNotEmpty()) {
                Text(ui.busyTip, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(0.22f))
    }
}

/** 聊天照片缩略图（4 倍降采样，避免大图占内存） */
@Composable
private fun ChatImageThumb(path: String, size: Dp, onClick: (() -> Unit)? = null) {
    val bitmap = remember(path) {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "照片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** 照片暂存区（输入框上方）：可加可删，点发送随问题一起发出 */
@Composable
private fun PendingImageStrip(
    images: List<File>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        images.forEachIndexed { index, file ->
            Box(modifier = Modifier.size(60.dp)) {
                ChatImageThumb(path = file.absolutePath, size = 60.dp)
                Text(
                    "✕",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { onRemove(index) },
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (images.size < ChatText.MAX_CHAT_IMAGES) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Text("＋", fontSize = 24.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** 底部输入区：按住说话 / 打字 + 拍照 + 输入方式切换 */
@Composable
private fun ChatInputBar(
    ui: ChatUiState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImages: () -> Unit,
    onToggleMode: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkMove: (TalkState) -> Unit,
    onTalkEnd: () -> Unit,
) {
    val talkEnabled = !ui.busy && !ui.transcribing
    val density = LocalDensity.current
    val slideThresholdPx = with(density) { TalkSlideThreshold.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ui.inputMode == ChatInputMode.VOICE) {
            // 按住说话：按下开始录音，拖动判定左右滑，松手结束
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .alpha(if (talkEnabled) 1f else 0.5f)
                    .clip(CircleShape)
                    .background(
                        if (ui.recording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                    .pointerInput(talkEnabled, hasAudioPermission) {
                        if (!talkEnabled) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            if (hasAudioPermission) onTalkStart() else onRequestPermission()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                val dx = change.position.x - down.position.x
                                onTalkMove(
                                    when {
                                        dx <= -slideThresholdPx -> TalkState.CANCEL
                                        dx >= slideThresholdPx -> TalkState.TEXT
                                        else -> TalkState.SEND
                                    }
                                )
                                change.consume()
                            }
                            onTalkEnd()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        ui.transcribing -> "识别中…"
                        ui.recording -> "松开发送"
                        else -> "按住 说话"
                    },
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            OutlinedTextField(
                value = ui.textInput,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                enabled = !ui.busy,
                placeholder = {
                    Text(
                        if (ui.pendingImages.isNotEmpty()) "想顺便问点什么？也可以直接发送"
                        else "把问题打在这里",
                    )
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            Spacer(Modifier.width(8.dp))
            val sendEnabled = ui.canSend && !ui.busy
            Text(
                "发送",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp,
                modifier = Modifier
                    .alpha(if (sendEnabled) 1f else 0.4f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(enabled = sendEnabled, onClick = onSend)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        // 拍照/相册
        val photoEnabled = !ui.busy && !ui.transcribing && !ui.recording
        InputCircleButton(
            emoji = "📷",
            desc = "拍照提问",
            enabled = photoEnabled,
            onClick = onPickImages,
        )
        Spacer(Modifier.width(8.dp))
        // 输入方式切换
        InputCircleButton(
            emoji = if (ui.inputMode == ChatInputMode.VOICE) "⌨" else "🎤",
            desc = "切换输入方式",
            enabled = true,
            onClick = onToggleMode,
        )
    }
}

@Composable
private fun InputCircleButton(emoji: String, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 18.sp)
    }
}

/** 按住说话手势蒙层：左滑取消 / 原地松开发送 / 右滑转文字（对应小程序 .talk-mask） */
@Composable
private fun TalkMask(ui: ChatUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            // 拦截点击穿透
            .pointerInput(Unit) {},
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 录音气泡：音浪条 / 转文字时的「文」字
            Box(
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (ui.talkState == TalkState.CANCEL) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (ui.talkState == TalkState.TEXT) {
                    Text("文", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                } else {
                    TalkWave()
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("${ui.recordSec}″", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TalkZone(glyph = "✕", label = "取消", active = ui.talkState == TalkState.CANCEL)
                TalkZone(glyph = "文", label = "转文字", active = ui.talkState == TalkState.TEXT)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (ui.talkState) {
                    TalkState.CANCEL -> "松开手指，取消"
                    TalkState.TEXT -> "松开手指，转成文字"
                    TalkState.SEND -> "松开发送 · 左滑取消 · 右滑转文字"
                },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** 录音音浪：9 根白色竖条错峰起伏（对应小程序 .talk-wave） */
@Composable
private fun TalkWave() {
    val transition = rememberInfiniteTransition(label = "talkWave")
    val delays = listOf(0, 120, 240, 360, 480, 360, 240, 120, 0)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(36.dp),
    ) {
        delays.forEachIndexed { i, delayMs ->
            val scale by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(delayMs),
                ),
                label = "talkBar$i",
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .graphicsLayer { scaleY = scale }
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
            )
        }
    }
}

/** 手势区（取消 / 转文字）：激活时圆底放大变绿 */
@Composable
private fun TalkZone(glyph: String, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (active) 75.dp else 60.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else Color.White.copy(alpha = 0.18f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, fontSize = 22.sp, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 13.sp,
            color = if (active) Color.White else Color.White.copy(alpha = 0.6f),
        )
    }
}
