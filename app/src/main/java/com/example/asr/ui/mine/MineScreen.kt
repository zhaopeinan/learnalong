package com.example.asr.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.asr.ui.components.AppTopSpace

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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("孩子管理") },
                supportingContent = { Text("添加 / 编辑孩子档案") },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenChildren),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("云备份") },
                supportingContent = { Text("坚果云 WebDAV 备份、恢复、自动备份") },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBackup),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("设置") },
                supportingContent = { Text("API Key、模型、WebDAV 账号、每日提醒时间") },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = {
                    Text(
                        "亲子辅导记录：录音 → 说话人分离转写 → 薄弱点分析 → 艾宾浩斯复习",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }
    }
}
