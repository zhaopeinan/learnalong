package com.example.asr.ui.agreement

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asr.AsrApplication
import com.example.asr.domain.LegalText
import com.example.asr.ui.components.AppBackTopBar
import kotlinx.coroutines.launch

/**
 * 协议页：服务条款 / 隐私政策。
 * gate = true 时为强制确认门（未同意时由闪屏跳入，无返回、底部双按钮，返回键被拦截）；
 * gate = false 时为纯阅读页（显示返回，无底部按钮）。
 */
@Composable
fun AgreementScreen(
    gate: Boolean,
    onAgree: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as AsrApplication
    val scope = rememberCoroutineScope()
    var docType by remember { mutableStateOf(DocType.TERMS) }

    // 闸门模式：不允许返回绕过
    BackHandler(enabled = gate) {}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (gate) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "用户协议与隐私政策",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            AppBackTopBar(if (docType == DocType.TERMS) "服务条款" else "隐私政策", onBack)
        }

        DocSwitch(
            docType = docType,
            onSwitch = { docType = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (gate) {
                Spacer(Modifier.height(16.dp))
                Text("👋", fontSize = 36.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    "欢迎来到伴学记！",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "为了更好地保护您和孩子的权益，请仔细阅读并同意以下协议后开始使用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                if (docType == DocType.TERMS) LegalText.TERMS_TEXT else LegalText.PRIVACY_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(24.dp))
        }

        if (gate) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OutlinedButton(
                    onClick = { (context as? Activity)?.finishAffinity() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("不同意并退出") }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            app.container.settingsStore.setAgreementAgreedV1(true)
                            onAgree()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("同意并使用") }
            }
        }
    }
}

private enum class DocType { TERMS, PRIVACY }

/** 文档切换（对齐小程序 doc-switch）：服务条款 / 隐私政策 */
@Composable
private fun DocSwitch(
    docType: DocType,
    onSwitch: (DocType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        listOf(DocType.TERMS to "服务条款", DocType.PRIVACY to "隐私政策").forEach { (type, label) ->
            val active = docType == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = !active) { onSwitch(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
