package com.example.asr.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// 统一圆角系统：按钮/输入框 small、卡片 medium、面板 large、对话框 extraLarge
// Chip 与 FAB 在使用处显式传 CircleShape 保持全圆
val Shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
