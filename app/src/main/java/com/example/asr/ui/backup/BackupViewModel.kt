package com.example.asr.ui.backup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.AsrApplication
import com.example.asr.data.local.MediaStorage
import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.sync.BackupMode
import com.example.asr.data.sync.BackupState
import com.example.asr.domain.StorageCleanup
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

/** 存储管理：单条可清理记录（对应小程序 CleanupItem） */
data class CleanupItem(
    val id: Long,
    val title: String,
    val bytes: Long,
    val checked: Boolean = true,
)

/** 清理弹窗状态 */
data class CleanupUiState(
    val visible: Boolean = false,
    val items: List<CleanupItem> = emptyList(),
    val cleaning: Boolean = false,
) {
    val selectedCount: Int get() = items.count { it.checked }
    val selectedBytes: Long get() = items.filter { it.checked }.sumOf { it.bytes }
}

class BackupViewModel(application: Application) : ViewModel() {

    private val app = application as AsrApplication
    private val settingsStore = app.container.settingsStore
    private val syncManager = app.container.syncManager
    private val backupController = app.container.backupController
    private val mediaStorage = app.container.mediaStorage

    private val restoring = MutableStateFlow(false)
    private val restoreMessage = MutableStateFlow<String?>(null)

    private val _storageText = MutableStateFlow("")
    val storageText: StateFlow<String> = _storageText

    private val _cleanup = MutableStateFlow(CleanupUiState())
    val cleanup: StateFlow<CleanupUiState> = _cleanup

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

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

    init {
        refreshStorage()
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val (audio, photo) = mediaStorage.storageUsage()
            _storageText.value = StorageCleanup.storageText(audio, photo)
        }
    }

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
            refreshStorage()
        }
    }

    // ---------- 存储管理：批量清理已分析录音（对齐小程序 backup 页） ----------

    fun openCleanup() {
        if (_cleanup.value.cleaning) return
        viewModelScope.launch {
            val items = mediaStorage.listCleanupCandidates()
                .map { CleanupItem(id = it.id, title = it.title, bytes = it.bytes) }
            if (items.isEmpty()) {
                _message.value = "没有可清理的录音（仅已完成分析的记录可清理）"
                return@launch
            }
            _cleanup.value = CleanupUiState(visible = true, items = items)
        }
    }

    fun toggleCleanupItem(id: Long) {
        val c = _cleanup.value
        _cleanup.value = c.copy(
            items = c.items.map { if (it.id == id) it.copy(checked = !it.checked) else it }
        )
    }

    fun dismissCleanup() {
        if (_cleanup.value.cleaning) return
        _cleanup.value = CleanupUiState()
    }

    /**
     * 执行清理：toCloud=true 时先上传坚果云再删（上传失败即中断，避免一边删一边失败）。
     * 文本内容均保留。
     */
    fun confirmCleanup(toCloud: Boolean) {
        val selected = _cleanup.value.items.filter { it.checked }
        if (_cleanup.value.cleaning) return
        if (selected.isEmpty()) {
            _message.value = "请先勾选要清理的记录"
            return
        }
        viewModelScope.launch {
            _cleanup.value = _cleanup.value.copy(cleaning = true)
            var freed = 0L
            var failed = 0
            for (item in selected) {
                try {
                    freed += mediaStorage.removeRecordingAudio(item.id, toCloud)
                } catch (e: Exception) {
                    failed++
                    if (toCloud) {
                        // 云端上传失败时中断后续
                        _cleanup.value = CleanupUiState()
                        refreshStorage()
                        _message.value = e.message ?: "上传云端失败"
                        return@launch
                    }
                }
            }
            _cleanup.value = CleanupUiState()
            refreshStorage()
            _message.value = "已删除 ${selected.size - failed} 条记录的音频，释放 " +
                StorageCleanup.mbText(freed) +
                (if (failed > 0) "；${failed} 条失败" else "") +
                "。文本内容均保留。"
        }
    }

    /** 清理未被任何记录引用的孤儿音频文件 */
    fun cleanOrphanFiles() {
        viewModelScope.launch {
            val freed = mediaStorage.cleanOrphanAudioFiles()
            refreshStorage()
            _message.value = if (freed > 0) "已清理孤立文件，释放 ${StorageCleanup.mbText(freed)}"
            else "没有可清理的孤立文件"
        }
    }
}
