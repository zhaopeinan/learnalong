package com.example.asr.ui.points

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.asr.AsrApplication
import com.example.asr.ui.components.AppTopSpace
import com.example.asr.ui.components.EmptyState
import kotlinx.coroutines.launch

/**
 * 「积分」tab（一级页）：顶部孩子切换 chips（多孩子时显示，记住上次选择），
 * 下方复用积分乐园完整内容（加分任务 / 多目标兑换 / 积分流水）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointsTabScreen() {
    val app = LocalContext.current.applicationContext as AsrApplication
    val children by app.container.childRepository.children.collectAsStateWithLifecycle(emptyList())
    val settings by app.container.settingsStore.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()

    if (children.isEmpty()) {
        Scaffold(topBar = { AppTopSpace() }) { padding ->
            EmptyState(
                icon = Icons.Default.Star,
                title = "先添加孩子",
                description = "在「我的 → 孩子管理」添加孩子后，来这里给他攒积分、兑奖励",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
        return
    }

    val selectedId = settings?.pointsChildId?.takeIf { id -> children.any { it.id == id } }
        ?: children.first().id

    PointsView(
        childId = selectedId,
        topBar = { _ ->
            Column {
                AppTopSpace()
                if (children.size > 1) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        children.forEach { child ->
                            FilterChip(
                                selected = child.id == selectedId,
                                onClick = {
                                    scope.launch {
                                        app.container.settingsStore.setPointsChildId(child.id)
                                    }
                                },
                                label = { Text(child.name, maxLines = 1) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
        },
    )
}
