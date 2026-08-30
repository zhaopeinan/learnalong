package com.example.asr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.asr.data.local.entity.MasteryHistoryEntity
import com.example.asr.ui.util.toDateString

/**
 * 掌握度成长曲线：横轴时间、纵轴 0-100。
 * 至少 2 个快照才有意义；调用方在数据不足时不要渲染本组件。
 */
@Composable
fun MasteryCurve(
    history: List<MasteryHistoryEntity>,
    modifier: Modifier = Modifier,
) {
    if (history.size < 2) return
    val lineColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxWidth().height(72.dp)
        ) {
            val padH = 8.dp.toPx()
            val padV = 6.dp.toPx()
            val minT = history.first().recordedAt
            val spanT = (history.last().recordedAt - minT).coerceAtLeast(1L)
            val usableW = size.width - padH * 2
            val usableH = size.height - padV * 2

            fun xAt(t: Long) = padH + ((t - minT).toFloat() / spanT) * usableW
            fun yAt(m: Int) = padV + (1f - m.coerceIn(0, 100) / 100f) * usableH

            // 0 / 50 / 100 参考线
            listOf(0, 50, 100).forEach { m ->
                drawLine(
                    color = baselineColor,
                    start = Offset(padH, yAt(m)),
                    end = Offset(size.width - padH, yAt(m)),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val path = Path()
            history.forEachIndexed { i, h ->
                val x = xAt(h.recordedAt)
                val y = yAt(h.mastery)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            history.forEach { h ->
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = Offset(xAt(h.recordedAt), yAt(h.mastery)),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${history.first().recordedAt.toDateString()} ${history.first().mastery}%",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(
                "${history.last().recordedAt.toDateString()} ${history.last().mastery}%",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
