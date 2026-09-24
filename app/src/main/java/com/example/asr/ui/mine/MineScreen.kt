package com.example.asr.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.example.asr.R
import com.example.asr.ui.components.AppCard
import com.example.asr.ui.components.AppTopSpace
import com.example.asr.ui.components.MenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(
    onOpenChildren: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    Scaffold(
        topBar = { AppTopSpace() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    MenuItem(
                        title = "孩子管理",
                        subtitle = "添加 / 编辑孩子档案",
                        icon = Icons.Filled.Face,
                        onClick = onOpenChildren,
                    )
                    MenuDivider()
                    MenuItem(
                        title = "云备份",
                        subtitle = "坚果云 WebDAV 备份、恢复、自动备份",
                        icon = ImageVector.vectorResource(R.drawable.ic_menu_cloud),
                        onClick = onOpenBackup,
                    )
                    MenuDivider()
                    MenuItem(
                        title = "设置",
                        subtitle = "API Key、模型、WebDAV 账号、每日提醒时间",
                        icon = Icons.Filled.Settings,
                        onClick = onOpenSettings,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                MenuItem(
                    title = "关于",
                    subtitle = "亲子辅导记录：录音 → 说话人分离转写 → 薄弱点分析 → 艾宾浩斯复习",
                    icon = Icons.Filled.Info,
                )
            }
        }
    }
}

/** 菜单项之间的细分隔线，左对齐到文字起始位置 */
@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
