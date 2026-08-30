package com.example.asr.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.asr.data.remote.dto.Exercise
import com.example.asr.data.remote.dto.TaskContent

/** 练习内容加载状态（今日任务 / 薄弱点库共用） */
sealed interface TaskContentState {
    data object Loading : TaskContentState
    data class Ready(val content: TaskContent) : TaskContentState
    data class Failed(val message: String) : TaskContentState
}

/** 练习区块：按状态渲染 骨架屏 / 题目列表 / 失败重试 */
@Composable
fun ExerciseSection(
    state: TaskContentState?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when (state) {
            is TaskContentState.Ready -> {
                state.content.exercises.forEachIndexed { i, exercise ->
                    ExerciseItem(index = i + 1, exercise = exercise)
                    Spacer(Modifier.height(8.dp))
                }
                if (state.content.tips.isNotBlank()) {
                    Text(
                        "辅导建议：${state.content.tips}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is TaskContentState.Failed -> {
                Text(
                    "${state.message}（点按重试）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onRetry),
                )
            }
            else -> {
                repeat(3) {
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = MaterialTheme.shapes.small,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** 单道练习题：题干 + 点击展开答案/提示 */
@Composable
private fun ExerciseItem(index: Int, exercise: Exercise) {
    var revealed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { revealed = !revealed },
    ) {
        Text(
            "$index. ${exercise.question}",
            style = MaterialTheme.typography.bodyMedium,
        )
        AnimatedVisibility(visible = revealed) {
            Column {
                Spacer(Modifier.height(4.dp))
                Text(
                    "答案：${exercise.answer}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (exercise.hint.isNotBlank()) {
                    Text(
                        "提示：${exercise.hint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!revealed) {
            Text(
                "点按显示答案",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
