package com.example.asr.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.asr.R
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard

private data class SettingsGroup(
    val title: String,
    val subtitle: String,
    val badgeColor: Color,
    val iconTint: Color,
    @DrawableRes val iconRes: Int,
    val onClickTag: String,
)

// 徽标配色与动作面板一致（固定浅色徽章 + 深色图标，深浅主题下均可读，同小程序 panel-badge）
private val settingsGroups = listOf(
    SettingsGroup(
        "模型服务", "SiliconFlow API Key、转写/分析/多模态模型与连通性测试",
        Color(0xFFD3E7DA), Color(0xFF17271F), R.drawable.ic_menu_model, "model",
    ),
    SettingsGroup(
        "语音合成与音色", "MiniMax 语音合成、播报音色与家长声音复刻",
        Color(0xFFE9E2F7), Color(0xFF5B3E9E), R.drawable.ic_menu_voice, "voice",
    ),
    SettingsGroup(
        "学习与复习", "科目管理与每日复习提醒时间",
        Color(0xFFF8E3B8), Color(0xFF8A6100), R.drawable.ic_menu_study, "study",
    ),
    SettingsGroup(
        "备份与存储", "WebDAV 云存储账号、录音清理与存储空间",
        Color(0xFFE2EDE4), Color(0xFF3E5A48), R.drawable.ic_menu_cloud, "backup",
    ),
    SettingsGroup(
        "通用", "外观主题、家长密码与调试模式",
        Color(0xFFE4EAE2), Color(0xFF46524A), R.drawable.ic_menu_general, "general",
    ),
)

/** 设置主页：分组菜单（对齐「我的」页 AppCard + MenuItem 风格） */
@Composable
fun SettingsMenuScreen(
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    Scaffold(topBar = { AppBackTopBar("设置", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    settingsGroups.forEachIndexed { i, group ->
                        if (i > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenGroup(group.onClickTag) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(group.badgeColor),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(group.iconRes),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(group.iconTint),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    group.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                )
                                Text(
                                    group.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
