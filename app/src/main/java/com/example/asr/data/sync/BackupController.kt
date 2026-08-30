package com.example.asr.data.sync

import com.example.asr.data.remote.toUserMessage
import com.example.asr.data.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/** 备份操作状态（进度条 + 结果文案） */
data class BackupState(
    val running: Boolean = false,
    val progress: SyncProgress? = null,
    val message: String? = null,
    val success: Boolean = false,
)

/**
 * 共享的备份执行器：启动时自动备份、提醒弹窗、云备份页共用，
 * 保证同一时刻只有一次备份，成功后写入 lastBackupAt。
 */
class BackupController(
    private val syncManager: SyncManager,
    private val settingsStore: SettingsStore,
) {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state

    private val mutex = Mutex()

    /** 执行备份；已配置且成功返回 true。静默模式不更新 UI 状态（用于启动时自动备份，固定全部模式） */
    suspend fun backupNow(mode: BackupMode = BackupMode.ALL, silent: Boolean = false): Boolean {
        if (!mutex.tryLock()) return false
        try {
            if (!silent) _state.value = BackupState(running = true)
            return try {
                val s = settingsStore.settings.first()
                if (s.webdavUser.isBlank() || s.webdavPassword.isBlank()) {
                    if (!silent) _state.value = BackupState(message = "请先在设置页配置 WebDAV 账号和应用密码")
                    return false
                }
                val r = syncManager.backup(s.webdavUrl, s.webdavUser, s.webdavPassword, mode) { p ->
                    if (!silent) _state.value = BackupState(running = true, progress = p)
                }
                settingsStore.setLastBackupAt(System.currentTimeMillis())
                if (!silent) {
                    _state.value = BackupState(
                        message = when (mode) {
                            BackupMode.CONFIG_ONLY -> "已备份配置（科目、模型、提醒等设置）"
                            else -> "已备份：${r.recordCount} 条记录；音频新上传 ${r.audioUploaded} 个" +
                                (if (r.audioSkipped > 0) "，跳过已有 ${r.audioSkipped} 个" else "") +
                                if (r.configUploaded) "；含配置" else ""
                        },
                        success = true,
                    )
                }
                true
            } catch (e: Exception) {
                if (!silent) {
                    _state.value = BackupState(
                        message = "备份失败：${e.toUserMessage()}",
                    )
                }
                false
            }
        } finally {
            mutex.unlock()
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
