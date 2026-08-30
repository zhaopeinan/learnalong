package com.example.asr.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 扁平卡片：无阴影，用 1dp outlineVariant 浅边框表达层级。
 * 传入 onClick 时附带 scale 0.98 按压反馈。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick == null) {
        Card(
            modifier = modifier,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content,
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.98f else 1f,
            animationSpec = tween(150),
            label = "pressScale",
        )
        Card(
            onClick = onClick,
            modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = interactionSource,
            content = content,
        )
    }
}

/**
 * 统一空状态：96dp 淡色大图标 + 标题 + 引导文案 + 可选主操作按钮。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, shape = MaterialTheme.shapes.small) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * 骨架屏块：圆角灰色块 + 无限呼吸闪烁，用于转写中 / 分析中等加载场景。
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

/**
 * 可展开的薄弱点卡片：默认只显示标题（+ 元信息），点击展开描述和掌握度。
 */
@Composable
fun WeakPointCard(
    knowledgePoint: String,
    description: String,
    mastery: Int,
    modifier: Modifier = Modifier,
    meta: String? = null,
    footer: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    /** 展开区域内、掌握度上方的额外内容（如练习题区块） */
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "expandArrow",
    )
    AppCard(
        onClick = { expanded = !expanded },
        modifier = modifier.animateContentSize(tween(200)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (meta != null) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    knowledgePoint,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                )
            }
            if (footer != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    footer,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(description, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (extraContent != null) {
                        Spacer(Modifier.height(8.dp))
                        extraContent()
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("掌握度", style = MaterialTheme.typography.labelMedium)
                        MasteryProgress(
                            mastery = mastery,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text("$mastery%", style = MaterialTheme.typography.labelMedium)
                    }
                    if (actions != null) {
                        Row { Spacer(Modifier.weight(1f)); actions() }
                    }
                }
            }
        }
    }
}

@Composable
fun MasteryProgress(
    mastery: Int,
    modifier: Modifier = Modifier,
) {
    val color = if (mastery < 60) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.primary
    LinearProgressIndicator(
        progress = { mastery / 100f },
        modifier = modifier,
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

/** 顶栏品牌底色：淡绿（与 child 气泡同族） */
private val topBarContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer

/**
 * 一级页面顶部：只垫一块品牌淡绿的状态栏区域，与信号栏融为一体。
 * 不放标题——底部导航已标明当前页面，标题属冗余信息（参考 TapTap 榜单页）。
 */
@Composable
fun AppTopSpace() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .background(topBarContainerColor)
            .windowInsetsPadding(WindowInsets.statusBars),
    )
}

/** 二级页面顶栏：标题 + 返回箭头，与一级页面同色 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBackTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = topBarContainerColor,
            scrolledContainerColor = topBarContainerColor,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
