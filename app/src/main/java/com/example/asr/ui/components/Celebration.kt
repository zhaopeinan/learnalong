package com.example.asr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asr.ui.theme.Amber40
import com.example.asr.ui.theme.Amber80
import com.example.asr.ui.theme.Forest40
import com.example.asr.ui.theme.Forest80
import com.example.asr.ui.theme.Sage40
import com.example.asr.ui.theme.Sage80
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 庆祝动效（积分乐园加分 / 目标兑换）：
 * 短版（加分，1.2s）：彩带纸屑飘落 + ⭐ 弹跳 + 「+N」大字飘升淡出。
 * 长版（兑换，3.2s）：开场金光闪屏 + 中心烟花粒子四溅 + 双层金色光环扩散
 * + 🏆 弹跳摇摆 + 目标名称标题 + 彩带暴雨，让孩子在兑换瞬间有强烈的获得感。
 * 配色取自主题 Forest/Amber 语义色板。
 */
data class Celebration(
    /** 区分连续两次庆祝的动画 key */
    val key: Long,
    /** 飘升大字，如「+5」「-30」 */
    val deltaText: String,
    /** 长版中心大标题，如「「爱心萌可」兑换成功！」；短版为 null */
    val title: String? = null,
    /** 长版（目标兑换） */
    val long: Boolean = false,
) {
    val durationMs: Int get() = if (long) 3200 else 1200
    val particleCount: Int get() = if (long) 150 else 60
    val sparkCount: Int get() = if (long) 40 else 0
}

private data class Particle(
    val x: Float,            // 起始横向位置 0..1
    val delay: Float,        // 错峰下落 0..0.35
    val speed: Float,        // 下落速度系数 0.8..1.4
    val size: Float,         // px
    val color: Color,
    val rotationSpeed: Float,// 圈数
    val wobblePhase: Float,
    val isCircle: Boolean,
)

/** 烟花火花：从中心沿 angle 方向四溅，带重力下坠 */
private data class Spark(
    val angle: Float,        // 弧度
    val speed: Float,        // 0.55..1.25
    val size: Float,         // px
    val color: Color,
    val delay: Float,        // 0..0.18 错峰迸发
    val isStar: Boolean,     // true 画圆点，false 画短条
)

private val confettiColors = listOf(Forest40, Forest80, Amber40, Amber80, Sage40, Sage80)

private fun generateParticles(count: Int, seed: Long): List<Particle> {
    val random = Random(seed)
    return List(count) {
        Particle(
            x = random.nextFloat(),
            delay = random.nextFloat() * 0.35f,
            speed = 0.8f + random.nextFloat() * 0.6f,
            size = 8f + random.nextFloat() * 14f,
            color = confettiColors[random.nextInt(confettiColors.size)],
            rotationSpeed = (random.nextFloat() - 0.5f) * 4f,
            wobblePhase = random.nextFloat() * (2f * PI.toFloat()),
            isCircle = random.nextBoolean(),
        )
    }
}

private fun generateSparks(count: Int, seed: Long): List<Spark> {
    val random = Random(seed xor 0x5DEECE66D)
    return List(count) {
        Spark(
            angle = random.nextFloat() * (2f * PI.toFloat()),
            speed = 0.55f + random.nextFloat() * 0.7f,
            size = 6f + random.nextFloat() * 10f,
            color = confettiColors[random.nextInt(confettiColors.size)],
            delay = random.nextFloat() * 0.18f,
            isStar = random.nextBoolean(),
        )
    }
}

@Composable
fun CelebrationOverlay(
    celebration: Celebration?,
    onFinished: () -> Unit,
) {
    if (celebration == null) return
    val progress = remember(celebration.key) { Animatable(0f) }
    LaunchedEffect(celebration.key) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(celebration.durationMs, easing = LinearEasing),
        )
        onFinished()
    }
    val particles = remember(celebration.key) {
        generateParticles(celebration.particleCount, celebration.key)
    }
    val sparks = remember(celebration.key) {
        generateSparks(celebration.sparkCount, celebration.key)
    }
    val p = progress.value

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 拦截点击穿透（不响应操作）
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)

            // 长版开场金光闪屏：前 12% 快速淡出的暖色全屏覆盖
            if (celebration.long && p < 0.12f) {
                drawRect(
                    color = Amber80.copy(alpha = (1f - p / 0.12f) * 0.35f),
                    size = size,
                )
            }

            // 长版双层金色光环：从中心扩散、变细、淡出
            if (celebration.long) {
                for (i in 0..1) {
                    val ringP = ((p - 0.05f - i * 0.14f) / 0.55f).coerceIn(0f, 1f)
                    if (ringP <= 0f || ringP >= 1f) continue
                    val eased = 1f - (1f - ringP).pow(3) // easeOutCubic
                    drawCircle(
                        color = Amber40.copy(alpha = (1f - ringP) * 0.8f),
                        radius = eased * (w * 0.55f),
                        center = center,
                        style = Stroke(width = (1f - ringP) * 14f + 2f),
                    )
                }
            }

            // 长版烟花火花：中心四溅 + 重力下坠 + 尾段淡出
            for (spark in sparks) {
                val local = ((p - spark.delay) / (1f - spark.delay)).coerceIn(0f, 1f)
                if (local <= 0f || local >= 1f) continue
                val eased = 1f - (1f - local).pow(3)
                val dist = eased * (w * 0.42f) * spark.speed
                val sx = center.x + cos(spark.angle) * dist
                val sy = center.y + sin(spark.angle) * dist + local * local * 160f
                val alpha = if (local > 0.55f) (1f - local) / 0.45f else 1f
                if (spark.isStar) {
                    drawCircle(
                        color = spark.color,
                        radius = spark.size / 2f * (1f - local * 0.5f),
                        center = Offset(sx, sy),
                        alpha = alpha,
                    )
                } else {
                    drawRect(
                        color = spark.color,
                        topLeft = Offset(sx - spark.size / 2f, sy - spark.size / 6f),
                        size = Size(spark.size, spark.size / 3f),
                        alpha = alpha,
                    )
                }
            }

            // 彩带纸屑：顶部飘落带摇摆旋转
            for (particle in particles) {
                val local = ((p - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
                if (local <= 0f || local >= 1f) continue
                val cx = particle.x * w + sin(local * 3f * PI.toFloat() + particle.wobblePhase) * 24f
                val cy = -particle.size + local * (h + particle.size * 2) * particle.speed
                val alpha = if (local > 0.85f) (1f - local) / 0.15f else 1f
                withTransform(
                    transformBlock = {
                        translate(cx, cy)
                        rotate(particle.rotationSpeed * local * 360f, pivot = Offset.Zero)
                    },
                ) {
                    if (particle.isCircle) {
                        drawCircle(
                            color = particle.color,
                            radius = particle.size / 2f,
                            center = Offset.Zero,
                            alpha = alpha,
                        )
                    } else {
                        drawRect(
                            color = particle.color,
                            topLeft = Offset(-particle.size / 2f, -particle.size / 4f),
                            size = Size(particle.size, particle.size / 2f),
                            alpha = alpha,
                        )
                    }
                }
            }
        }

        // 中心内容：弹跳 emoji + 飘升淡出的加分大字（长版加目标达成标题）
        val bounceIn = (p / 0.2f).coerceIn(0f, 1f)
        val scale = if (p < 0.2f) {
            // 过冲弹跳：0 → 1.3 → 1
            val t = bounceIn
            if (t < 0.6f) (t / 0.6f) * 1.3f else 1.3f - ((t - 0.6f) / 0.4f) * 0.3f
        } else 1f
        // 长版奖杯登场后持续小幅左右摇摆，活力感
        val wiggle = if (celebration.long && p >= 0.2f) {
            sin(p * 8f * PI.toFloat()) * 10f * (1f - p)
        } else 0f
        val floatUp = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
        val textAlpha = when {
            p < 0.1f -> p / 0.1f
            p > 0.7f -> (1f - p) / 0.3f
            else -> 1f
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (celebration.long) "🏆" else "⭐",
                fontSize = if (celebration.long) 80.sp else 56.sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = wiggle
                },
            )
            celebration.title?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                celebration.deltaText,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .offset(y = (-60).dp * floatUp)
                    .alpha(textAlpha),
            )
        }
    }
}
