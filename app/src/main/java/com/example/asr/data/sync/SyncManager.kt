package com.example.asr.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.example.asr.data.local.AppDatabase
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.MasteryHistoryEntity
import com.example.asr.data.local.entity.RecordingEntity
import com.example.asr.data.local.entity.RecordingPhotoEntity
import com.example.asr.data.local.entity.ReviewTaskEntity
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.local.entity.WorkRecordingEntity
import com.example.asr.data.local.entity.WorkTodoEntity
import com.example.asr.data.repository.RecordingRepository
import com.example.asr.data.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 云端 backup.json 结构；字段名与实体字段一致，容忍未知字段/缺省值以兼容旧版本 */
@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val children: List<ChildEntity> = emptyList(),
    val recordings: List<RecordingEntity> = emptyList(),
    val segments: List<TranscriptSegmentEntity> = emptyList(),
    val weakPoints: List<WeakPointEntity> = emptyList(),
    val reviewTasks: List<ReviewTaskEntity> = emptyList(),
    val masteryHistory: List<MasteryHistoryEntity> = emptyList(),
    val photos: List<RecordingPhotoEntity> = emptyList(),
    val workRecordings: List<WorkRecordingEntity> = emptyList(),
    val workTodos: List<WorkTodoEntity> = emptyList(),
)

data class BackupResult(
    val recordCount: Int,
    val audioUploaded: Int,
    val audioSkipped: Int,   // 远端已存在且大小一致，跳过
    val configUploaded: Boolean = false,
)

/** 备份进度：current/total 为条目序号，itemName 为正在处理的文件名 */
data class SyncProgress(val current: Int, val total: Int, val itemName: String)

/** 备份模式：全部（配置+数据+音频）/ 仅配置 / 仅数据（数据+音频） */
enum class BackupMode { ALL, CONFIG_ONLY, DATA_ONLY }

/** 云端 config.json：App 配置快照（科目、模型、提醒时间等），恢复后无需重新设置 */
@Serializable
data class ConfigFile(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val apiKey: String = "",
    val baseUrl: String = "",
    val asrModel: String = "",
    val llmModel: String = "",
    val vlmModel: String = "",
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPassword: String = "",
    val autoBackupOnWifi: Boolean = true,
    val subjects: List<String> = emptyList(),
)

data class RestoreResult(
    val children: Int,
    val recordings: Int,
    val segments: Int,
    val weakPoints: Int,
    val reviewTasks: Int,
    val audioDownloaded: Int,
    val audioMissing: Int,
    val photos: Int = 0,
    val configRestored: Boolean = false,
)

/**
 * 坚果云 WebDAV 备份/恢复。
 *
 * 云端结构（webdavUrl 下）：
 *   ASRTutor/backup.json          全量数据快照
 *   ASRTutor/recordings/<文件名>  音频文件
 *
 * 恢复冲突策略：所有表按主键 REPLACE 插入，即"云端覆盖本地同 id 数据"，
 * 本地存在而云端没有的记录保持不动（不做全量清空）。
 */
class SyncManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settingsStore: SettingsStore,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 增量备份：先列出云端已有文件比对，只上传本地新增或大小不一致的音频，
     * 最后上传 backup.json 数据快照（保证快照引用的音频都已在云端）。
     * mode 决定备份范围：全部 / 仅配置 / 仅数据。onProgress 回调：SyncProgress(当前第几项, 总项数, 文件名)。
     */
    suspend fun backup(
        url: String,
        user: String,
        password: String,
        mode: BackupMode = BackupMode.ALL,
        onProgress: ((SyncProgress) -> Unit)? = null,
    ): BackupResult = withContext(Dispatchers.IO) {
        val client = WebDavClient(url, user, password)
        client.ensureDir(ROOT_DIR)

        // 配置快照（科目、模型、提醒时间等）
        if (mode != BackupMode.DATA_ONLY) {
            val s = settingsStore.settings.first()
            val config = ConfigFile(
                exportedAt = System.currentTimeMillis(),
                apiKey = s.apiKey,
                baseUrl = s.baseUrl,
                asrModel = s.asrModel,
                llmModel = s.llmModel,
                vlmModel = s.vlmModel,
                reminderHour = s.reminderHour,
                reminderMinute = s.reminderMinute,
                webdavUrl = s.webdavUrl,
                webdavUser = s.webdavUser,
                webdavPassword = s.webdavPassword,
                autoBackupOnWifi = s.autoBackupOnWifi,
                subjects = s.subjects,
            )
            onProgress?.invoke(SyncProgress(1, 1, "config.json"))
            client.uploadBytes(
                "$ROOT_DIR/config.json",
                json.encodeToString(config).toByteArray(Charsets.UTF_8),
                "application/json",
            )
            if (mode == BackupMode.CONFIG_ONLY) {
                return@withContext BackupResult(0, 0, 0, configUploaded = true)
            }
        }

        client.ensureDir("$ROOT_DIR/recordings")
        client.ensureDir("$ROOT_DIR/photos")

        val data = BackupFile(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            children = db.childDao().getAll(),
            recordings = db.recordingDao().getAll(),
            segments = db.transcriptDao().getAll(),
            weakPoints = db.weakPointDao().getAll(),
            reviewTasks = db.reviewTaskDao().getAll(),
            masteryHistory = db.masteryHistoryDao().getAll(),
            photos = db.recordingPhotoDao().getAll(),
            workRecordings = db.workRecordingDao().getAll(),
            workTodos = db.workTodoDao().getAll(),
        )

        // 比对云端已有文件，算出增量清单（音频 + 错题照片）
        val remoteAudio = client.listFiles("$ROOT_DIR/recordings")
        val remotePhotos = client.listFiles("$ROOT_DIR/photos")

        /** 本地文件去重：云端已存在且大小一致的跳过 */
        fun collectToUpload(paths: List<String>, remote: Map<String, Long>, skippedRef: IntArray): List<File> {
            val result = mutableListOf<File>()
            for (path in paths) {
                val file = File(path)
                if (!file.exists()) continue
                if (remote[file.name] == file.length()) skippedRef[0]++ else result.add(file)
            }
            return result
        }

        // 辅导录音 + 工作端录音的全部音频文件（分段录音取全部分段）
        val allAudioPaths = data.recordings.flatMap { audioPathsOf(it.filePath, it.segments) } +
            data.workRecordings.flatMap { audioPathsOf(it.filePath, it.segments) }

        val skippedCounter = intArrayOf(0)
        val audioUploads = collectToUpload(allAudioPaths, remoteAudio, skippedCounter)
            .map { it to "recordings" }
        val photoUploads = collectToUpload(data.photos.map { it.filePath }, remotePhotos, skippedCounter)
            .map { it to "photos" }
        val toUpload = audioUploads + photoUploads
        val skipped = skippedCounter[0]

        val total = toUpload.size + 1 // +1 为最后的 backup.json
        var done = 0
        for ((file, dir) in toUpload) {
            done++
            onProgress?.invoke(SyncProgress(done, total, file.name))
            client.upload("$ROOT_DIR/$dir/${file.name}", file)
        }

        onProgress?.invoke(SyncProgress(total, total, "backup.json"))
        client.uploadBytes(
            "$ROOT_DIR/backup.json",
            json.encodeToString(data).toByteArray(Charsets.UTF_8),
            "application/json",
        )
        BackupResult(
            recordCount = data.recordings.size,
            audioUploaded = toUpload.size,
            audioSkipped = skipped,
            configUploaded = mode != BackupMode.DATA_ONLY,
        )
    }

    /** 恢复：下载 backup.json 按外键顺序 REPLACE upsert，缺失的音频逐个下载；若云端有 config.json 则同时恢复配置（科目、模型等） */
    suspend fun restore(url: String, user: String, password: String): RestoreResult =
        withContext(Dispatchers.IO) {
            val client = WebDavClient(url, user, password)
            val bytes = client.download("$ROOT_DIR/backup.json")
            val data = json.decodeFromString<BackupFile>(bytes.toString(Charsets.UTF_8))

            db.withTransaction {
                db.childDao().upsertAll(data.children)
                db.recordingDao().upsertAll(data.recordings)
                db.transcriptDao().upsertAll(data.segments)
                db.weakPointDao().upsertAll(data.weakPoints)
                db.reviewTaskDao().upsertAll(data.reviewTasks)
                db.masteryHistoryDao().upsertAll(data.masteryHistory)
                db.recordingPhotoDao().upsertAll(data.photos)
                db.workRecordingDao().upsertAll(data.workRecordings)
                db.workTodoDao().upsertAll(data.workTodos)
            }

            var downloaded = 0
            var missing = 0
            // 音频在 recordings/ 目录，照片在 photos/ 目录；
            // 音频已被用户主动清理的记录不再从云端拉回，避免刚释放的空间被占回
            val filesWithDir =
                data.recordings.filter { !it.audioRemoved }
                    .flatMap { r -> audioPathsOf(r.filePath, r.segments) }
                    .map { it to "recordings" } +
                    data.workRecordings
                        .flatMap { r -> audioPathsOf(r.filePath, r.segments) }
                        .map { it to "recordings" } +
                    data.photos.map { it.filePath to "photos" }
            for ((path, dir) in filesWithDir) {
                val target = File(path)
                if (target.exists()) continue
                try {
                    val bytes = client.download("$ROOT_DIR/$dir/${target.name}")
                    // 写回 filePath 指向的位置（正常即 filesDir 下的子目录）
                    target.parentFile?.mkdirs()
                    target.writeBytes(bytes)
                    downloaded++
                } catch (e: WebDavException) {
                    if (e.code == 404) missing++ else throw e
                }
            }
            RestoreResult(
                children = data.children.size,
                recordings = data.recordings.size,
                segments = data.segments.size,
                weakPoints = data.weakPoints.size,
                reviewTasks = data.reviewTasks.size,
                audioDownloaded = downloaded,
                audioMissing = missing,
                photos = data.photos.size,
                configRestored = restoreConfig(client),
            )
        }

    /** 恢复配置快照；云端没有 config.json（旧备份）时跳过，返回 false */
    private suspend fun restoreConfig(client: WebDavClient): Boolean {
        val bytes = try {
            client.download("$ROOT_DIR/config.json")
        } catch (e: WebDavException) {
            if (e.code == 404) return false else throw e
        }
        val config = json.decodeFromString<ConfigFile>(bytes.toString(Charsets.UTF_8))
        if (config.apiKey.isNotBlank()) settingsStore.setApiKey(config.apiKey)
        if (config.baseUrl.isNotBlank()) settingsStore.setBaseUrl(config.baseUrl)
        if (config.asrModel.isNotBlank()) settingsStore.setAsrModel(config.asrModel)
        if (config.llmModel.isNotBlank()) settingsStore.setLlmModel(config.llmModel)
        if (config.vlmModel.isNotBlank()) settingsStore.setVlmModel(config.vlmModel)
        settingsStore.setReminderTime(config.reminderHour, config.reminderMinute)
        if (config.webdavUrl.isNotBlank()) settingsStore.setWebdavUrl(config.webdavUrl)
        if (config.webdavUser.isNotBlank()) settingsStore.setWebdavUser(config.webdavUser)
        if (config.webdavPassword.isNotBlank()) settingsStore.setWebdavPassword(config.webdavPassword)
        settingsStore.setAutoBackupOnWifi(config.autoBackupOnWifi)
        if (config.subjects.isNotEmpty()) settingsStore.setSubjects(config.subjects)
        return true
    }

    companion object {
        const val ROOT_DIR = "ASRTutor"

        /** 录音涉及的全部音频文件：分段录音返回全部分段，否则仅主文件（辅导录音与工作录音通用） */
        fun audioPathsOf(filePath: String, segments: String?): List<String> {
            val decoded = RecordingRepository.decodeSegments(segments)
            val paths = if (decoded != null && decoded.size > 1) decoded else listOf(filePath)
            return paths.filter { it.isNotBlank() }
        }
    }
}
