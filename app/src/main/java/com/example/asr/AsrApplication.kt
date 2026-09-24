package com.example.asr

import android.app.Application
import com.example.asr.data.local.AppDatabase
import com.example.asr.data.remote.NetworkClient
import com.example.asr.data.repository.ChildRepository
import com.example.asr.data.repository.ChatRepository
import com.example.asr.data.repository.RecordingRepository
import com.example.asr.data.repository.TutorRepository
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.settings.SettingsStore
import com.example.asr.data.sync.BackupController
import com.example.asr.data.sync.SyncManager
import com.example.asr.data.sync.isOnWifi
import com.example.asr.domain.KidReward
import com.example.asr.media.SpeechSynthesizer
import com.example.asr.worker.DailyReviewWorker
import com.example.asr.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** 手写服务定位容器（不引入 Hilt，保持简单） */
class AppContainer(context: Application) {
    val settingsStore = SettingsStore(context)
    private val db = AppDatabase.get(context)

    val childRepository = ChildRepository(db.childDao())
    val recordingRepository = RecordingRepository(db.recordingDao(), db.transcriptDao(), settingsStore)
    val tutorRepository = TutorRepository(
        weakPointDao = db.weakPointDao(),
        reviewTaskDao = db.reviewTaskDao(),
        transcriptDao = db.transcriptDao(),
        recordingDao = db.recordingDao(),
        childDao = db.childDao(),
        masteryHistoryDao = db.masteryHistoryDao(),
        recordingPhotoDao = db.recordingPhotoDao(),
        settingsStore = settingsStore,
    )

    /** 坚果云 WebDAV 备份/恢复 */
    val syncManager = SyncManager(context, db, settingsStore)

    /** 共享备份执行器（启动自动备份 / 提醒弹窗 / 云备份页共用） */
    val backupController = BackupController(syncManager, settingsStore)

    /** 超过 3 天未备份时置 true，AppRoot 弹提醒 */
    val backupReminder = MutableStateFlow(false)

    /** 其他 App（如小米录音机）分享过来的音频文件，等待用户在记录页确认归属 */
    val pendingImport = MutableStateFlow<File?>(null)

    /** 底部动作面板拍照/相册产出的错题照片（已复制进私有目录），等待用户在记录页确认归属 */
    val pendingPhotoImport = MutableStateFlow<List<File>?>(null)

    /** 录音常驻通知点击后置 true，AppRoot 据此跳到录音页 */
    val pendingOpenRecord = MutableStateFlow(false)

    /** MiniMax 语音合成 / 家长声音复刻 */
    val miniMaxApi = NetworkClient.minimaxApi(AppSettings.MINIMAX_BASE_URL)

    /** MiniMax 语音合成封装（孩子端「读出来」/ AI 播报共用） */
    val speechSynthesizer = SpeechSynthesizer(miniMaxApi, settingsStore)

    /** 苏格拉底辅导对话（问老师） */
    val chatRepository = ChatRepository(
        chatDao = db.chatDao(),
        childDao = db.childDao(),
        weakPointDao = db.weakPointDao(),
        settingsStore = settingsStore,
        speechSynthesizer = speechSynthesizer,
    )

    /** 孩子端星星激励 */
    val kidReward = KidReward(db.kidStarDao())
}

class AsrApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
        applicationScope.launch {
            val settings = container.settingsStore.settings.first()
            DailyReviewWorker.schedule(this@AsrApplication, settings.reminderHour, settings.reminderMinute)
        }
        applicationScope.launch { autoBackupOnStart() }
    }

    /** 启动时：已配置 WebDAV 且 WiFi 下自动静默备份；超过 3 天未备份则弹提醒 */
    private suspend fun autoBackupOnStart() {
        val s = container.settingsStore.settings.first()
        if (s.webdavUser.isBlank() || s.webdavPassword.isBlank()) return
        val overdue = System.currentTimeMillis() - s.lastBackupAt > REMIND_AFTER_MS
        if (s.autoBackupOnWifi && isOnWifi(this)) {
            container.backupController.backupNow(silent = true)
        } else if (overdue) {
            container.backupReminder.value = true
        }
    }

    private companion object {
        const val REMIND_AFTER_MS = 3L * 24 * 3600 * 1000 // 3 天
    }
}
