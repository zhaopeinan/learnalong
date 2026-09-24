package com.example.asr.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asr.domain.GuideContent
import com.example.asr.ui.components.AppBackTopBar
import com.example.asr.ui.components.AppCard

/** tint 色系（卡片封面 + 文章插图共用，对齐小程序 tint-*） */
fun tintBrush(tint: String): Brush {
    val (a, b) = when (tint) {
        "green" -> Color(0xFFE4F2E8) to Color(0xFFCDE7D6)
        "blue" -> Color(0xFFE3EEF7) to Color(0xFFCFDFEF)
        "amber" -> Color(0xFFFAEECC) to Color(0xFFF3DFA8)
        "sage" -> Color(0xFFE2EDE4) to Color(0xFFD0E2D5)
        "grey" -> Color(0xFFECEEEA) to Color(0xFFDDE2DA)
        "purple" -> Color(0xFFEEE8F6) to Color(0xFFDDD3EE)
        else -> Color(0xFFE4F2E8) to Color(0xFFCDE7D6)
    }
    return Brush.linearGradient(listOf(a, b))
}

/** 使用指南列表页：分类筛选 + 图文卡片网格（对应小程序 pages/guide/guide） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onRestartTour: () -> Unit,
) {
    var cat by remember { mutableStateOf("all") }
    val list = if (cat == "all") GuideContent.GUIDES else GuideContent.GUIDES.filter { it.cat == cat }

    Scaffold(topBar = { AppBackTopBar("使用指南", onBack = onBack) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column {
                    Text(
                        "让辅导更有方法",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "从第一次录音到复习巩固，跟着图文步骤做一遍就会了",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 新手引导重看入口
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onRestartTour),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🧭", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("新手引导", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "跟着蒙版指引，再走一遍完整流程",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "开始 ›",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GuideContent.CATS.forEach { c ->
                            FilterChip(
                                selected = cat == c.key,
                                onClick = { cat = c.key },
                                label = { Text(c.label, maxLines = 1) },
                                shape = CircleShape,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(list, key = { it.id }) { article ->
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenArticle(article.id) },
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(tintBrush(article.tint)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(article.emoji, fontSize = 44.sp)
                        }
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                article.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                article.sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 使用指南文章页：图文混排（对应小程序 pages/guide/article） */
@Composable
fun GuideArticleScreen(articleId: String, onBack: () -> Unit) {
    val article = GuideContent.articleById(articleId)

    Scaffold(topBar = { AppBackTopBar("使用指南", onBack = onBack) }) { padding ->
        if (article == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("文章不存在") }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // hero
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(tintBrush(article.tint))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(article.emoji, fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    article.sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            article.sections.forEach { sec ->
                Column {
                    Text(
                        sec.h,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    sec.p.forEach { para ->
                        Text(
                            para,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    sec.ill?.let { ill ->
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(tintBrush(article.tint))
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(ill, fontSize = 40.sp)
                            sec.illCap?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    sec.tip?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "💡 $it",
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
