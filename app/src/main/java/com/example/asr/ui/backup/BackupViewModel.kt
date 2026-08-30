package com.example.asr.ui.backup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.AsrApplication
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.sync.BackupMode
import com.example.asr.data.sync.BackupState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val configured: Boolean = false,
    val lastBackupAt: Long = 0L,
    val autoBackupOnWifi: Boolean = true,
    val backup: BackupState = BackupState(),
    val restoring: Boolean = false,
    val restoreMessage: String? = null,
)

class BackupViewModel(application: Application) : ViewModel() {

    private val app = application as AsrApplication
    private val settingsStore = app.container.settingsStore
    private val syncManager = app.container.syncManager
    private val backupController = app.container.backupController

    private val restoring = MutableStateFlow(false)
    private val restoreMessage = MutableStateFlow<String?>(null)

    val ui: StateFlow<BackupUiState> = combine(
        settingsStore.settings,
        backupController.state,
        restoring,
        restoreMessage,
    ) { s, backup, restoringNow, restoreMsg ->
        BackupUiState(
            configured = s.webdavUser.isNotBlank() && s.webdavPassword.isNotBlank(),
            lastBackupAt = s.lastBackupAt,
            autoBackupOnWifi = s.autoBackupOnWifi,
            backup = backup,
            restoring = restoringNow,
            restoreMessage = restoreMsg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupUiState())

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoBackupOnWifi(enabled) }
    }

    fun backupNow(mode: BackupMode) {
        viewModelScope.launch { backupController.backupNow(mode) }
    }

    fun restoreNow() {
        if (restoring.value) return
        viewModelScope.launch {
            restoring.value = true
            restoreMessage.value = null
            restoreMessage.value = try {
                val s = settingsStore.settings.first()
                val r = syncManager.restore(s.webdavUrl, s.webdavUser, s.webdavPassword)
                "已恢复：孩子 ${r.children}、记录 ${r.recordings}、分段 ${r.segments}、" +
                    "薄弱点 ${r.weakPoints}、复习任务 ${r.reviewTasks}；" +
                    "下载音频 ${r.audioDownloaded} 个" +
                    if (r.audioMissing > 0) "，云端缺失 ${r.audioMissing} 个" else ""
            } catch (e: Exception) {
                "恢复失败：${e.toUserMessage()}"
            }
            restoring.value = false
        }
    }
}
