package com.example.asr.data.local

import android.content.Context
import com.example.asr.data.local.dao.ChildDao
import com.example.asr.data.local.dao.RecordingDao
import com.example.asr.data.local.dao.WorkRecordingDao
import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.repository.RecordingRepository
import com.example.asr.data.settings.SettingsStore
import com.example.asr.data.sync.SyncManager
import com.example.asr.data.sync.WebDavClient
import com.example.asr.domain.StorageCleanup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地媒体存储管理（对应小程序 audio.ts storageUsage/cleanupOrphanAudioFiles +
 * tutor.ts listCleanupCandidates/removeRecordingAudio）：
 * 录音/照片占用统计、已分析录音的音频清理、孤儿文件清理。
 * 纯规则在 domain/StorageCleanup，这里只做文件系统与 DB 访问。
 */
class MediaStorage(
    private val context: Context,
    private val recordingDao: RecordingDao,
    private val childDao: ChildDao,
    private val workRecordingDao: WorkRecordingDao,
    private val settingsStore: SettingsStore,
) {

    private val recordingsDir get() = File(context.filesDir, "recordings")
    private val photosDir get() = File(context.filesDir, "photos")

    /** 本地媒体占用：录音 + 错题照片（备份页存储管理展示用） */
    suspend fun storageUsage(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        dirSizeBytes(recordingsDir) to dirSizeBytes(photosDir)
    }

    data class CleanupCandidate(val id: Long, val title: String, val bytes: Long)

    /** 可清理的记录：已完成分析、音频仍在本地（对齐小程序 listCleanupCandidates） */
    suspend fun listCleanupCandidates(): List<CleanupCandidate> = withContext(Dispatchers.IO) {
        val childNames = childDao.getAll().associate { it.id to it.name }
        recordingDao.getAll()
            .filter { it.status == RecordingStatus.ANALYZED && it.filePath.isNotBlank() && !it.audioRemoved }
            .map { r ->
                CleanupCandidate(
                    id = r.id,
                    title = "${childNames[r.childId] ?: ""} · ${r.subject}",
                    bytes = audioFilesOf(r.filePath, r.segments).sumOf { fileSizeOf(it) },
                )
            }
            .filter { it.bytes > 0 }
            .sortedByDescending { it.bytes }
    }

    /** 单条录音的音频占用字节数（对应小程序 tutor.ts recordingAudioBytes） */
    suspend fun recordingAudioBytes(recordingId: Long): Long = withContext(Dispatchers.IO) {
        val rec = recordingDao.getById(recordingId) ?: return@withContext 0L
        if (rec.filePath.isBlank() || rec.audioRemoved) return@withContext 0L
        audioFilesOf(rec.filePath, rec.segments).sumOf { fileSizeOf(it) }
    }

    /**
     * 删除录音的音频文件释放空间（文本内容保留）。
     * uploadToCloud=true 时先上传到坚果云 recordings/ 目录（要求已配置 WebDAV）。
     * 返回释放的字节数。
     */
    suspend fun removeRecordingAudio(recordingId: Long, uploadToCloud: Boolean): Long =
        withContext(Dispatchers.IO) {
            val rec = recordingDao.getById(recordingId) ?: return@withContext 0L
            if (rec.filePath.isBlank() || rec.audioRemoved) return@withContext 0L
            val files = audioFilesOf(rec.filePath, rec.segments)
            if (uploadToCloud) {
                val s = settingsStore.settings.first()
                require(s.webdavUser.isNotBlank() && s.webdavPassword.isNotBlank()) {
                    "未配置坚果云 WebDAV，无法备份到云端"
                }
                val client = WebDavClient(s.webdavUrl, s.webdavUser, s.webdavPassword)
                client.ensureDir("${SyncManager.ROOT_DIR}/recordings")
                for (f in files) {
                    val file = File(f)
                    if (file.exists()) client.upload("${SyncManager.ROOT_DIR}/recordings/${file.name}", file)
                }
            }
            val freed = files.sumOf { fileSizeOf(it) }
            files.forEach { File(it).delete() }
            recordingDao.updateAudioFlags(recordingId, removed = true, backedUp = uploadToCloud)
            freed
        }

    /**
     * 清理未被任何记录引用的孤儿音频（历史 bug 遗留的重复拷贝、取消导入的残留等）。
     * 只动 rec_、import_、chunk_ 前缀的文件，返回清理出的字节数。
     */
    suspend fun cleanOrphanAudioFiles(): Long = withContext(Dispatchers.IO) {
        val referenced = referencedAudioNames()
        val now = System.currentTimeMillis()
        var freed = 0L
        recordingsDir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (!StorageCleanup.isOrphanCandidate(f.name, referenced, f.lastModified(), now)) {
                return@forEach
            }
            val size = f.length()
            if (f.delete()) freed += size
        }
        freed
    }

    /** 被引用的音频文件名集合：辅导录音（含分段）+ 工作端录音（含分段） */
    private suspend fun referencedAudioNames(): Set<String> {
        val names = mutableSetOf<String>()
        recordingDao.getAll().forEach { r ->
            audioFilesOf(r.filePath, r.segments).forEach { names.add(File(it).name) }
        }
        workRecordingDao.getAll().forEach { r ->
            audioFilesOf(r.filePath, r.segments).forEach { names.add(File(it).name) }
        }
        return names
    }

    /** 录音涉及的全部音频文件：分段录音返回全部分段，否则仅主文件 */
    private fun audioFilesOf(filePath: String, segments: String?): List<String> {
        val decoded = RecordingRepository.decodeSegments(segments)
        val paths = if (decoded != null && decoded.size > 1) decoded else listOf(filePath)
        return paths.filter { it.isNotBlank() }
    }

    private fun fileSizeOf(path: String): Long = try {
        File(path).takeIf { it.exists() }?.length() ?: 0L
    } catch (_: Exception) {
        0L
    }

    private fun dirSizeBytes(dir: File): Long {
        var total = 0L
        try {
            dir.listFiles()?.forEach { f -> if (f.isFile) total += f.length() }
        } catch (_: Exception) { /* 目录不存在等情况忽略 */ }
        return total
    }
}
