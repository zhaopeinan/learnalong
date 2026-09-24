package com.example.asr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.asr.audio.AudioImporter
import com.example.asr.audio.RecordingService
import com.example.asr.data.settings.AppSettings
import com.example.asr.ui.AppRoot
import com.example.asr.ui.theme.ASRTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleSharedAudio(intent)
        setContent {
            val settings by (application as AsrApplication).container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings?.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> systemDark // 跟随本机（含设置未读出的瞬间）
            }
            ASRTheme(darkTheme = darkTheme) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedAudio(intent)
        handleOpenRecord(intent)
    }

    override fun onResume() {
        super.onResume()
        handleOpenRecord(intent)
    }

    /** 录音常驻通知点击：跳到录音页（录音在服务里持续进行，回页后重新绑定展示） */
    private fun handleOpenRecord(intent: Intent?) {
        if (intent?.getBooleanExtra(RecordingService.EXTRA_OPEN_RECORD, false) == true) {
            intent.removeExtra(RecordingService.EXTRA_OPEN_RECORD)
            (application as AsrApplication).container.pendingOpenRecord.value = true
        }
    }

    /** 接收其他 App（如小米录音机）分享来的音频，复制后等用户在记录页确认 */
    private fun handleSharedAudio(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: return
        val app = application as AsrApplication
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = AudioImporter.import(this@MainActivity, uri)
                app.container.pendingImport.value = file
            } catch (_: Exception) {
                // 复制失败则忽略，不阻塞启动
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS,
            )
        }
    }

    private companion object {
        const val REQUEST_POST_NOTIFICATIONS = 42
    }
}
