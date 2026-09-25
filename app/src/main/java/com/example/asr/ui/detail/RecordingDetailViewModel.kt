package com.example.asr.ui.detail

import android.media.MediaPlayer
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.audio.RecordingService
import com.example.asr.data.local.MediaStorage
import com.example.asr.data.local.entity.RecordingEntity
import com.example.asr.data.local.entity.RecordingPhotoEntity
import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.repository.RecordingRepository
import com.example.asr.data.repository.TutorRepository
import com.example.asr.data.settings.AppSettings
import com.example.asr.data.settings.SettingsStore
import com.example.asr.domain.StorageCleanup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val recording: RecordingEntity? = null,
    val busy: Boolean = false,
    /** 转写上传进度 0-1；null 表示不在上传（识别中或未开始） */
    val uploadProgress: Float? = null,
    /** 待确认的候选薄弱点（含查重结果）；null 表示无待确认分析 */
    val pendingAnalysis: List<TutorRepository.AnalysisCandidate>? = null,
    val playingSegmentId: Long? = null,
    val pausedSegmentId: Long? = null,
    /** 分析完成后的音频清理询问（ask 模式）：含文件大小与 WebDAV 可用性 */
    val cleanupPrompt: CleanupPrompt? = null,
    val error: String? = null,
    val notice: String? = null,
)

/** 音频清理询问弹窗数据（对应小程序 maybeCleanupAudio 的 ActionSheet） */
data class CleanupPrompt(
    val bytes: Long,
    /** 已配置坚果云 WebDAV，可提供「备份到云端后删除」选项 */
    val webdavOk: Boolean,
)

class RecordingDetailViewModel(
    private val recordingId: Long,
    private val recordingRepository: RecordingRepository,
    private val tutorRepository: TutorRepository,
    private val mediaStorage: MediaStorage,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui

    val segments: StateFlow<List<TranscriptSegmentEntity>> =
        recordingRepository.observeSegments(recordingId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weakPoints: StateFlow<List<WeakPointEntity>> =
        tutorRepository.observeWeakPointsByRecording(recordingId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 该记录附带的错题照片 */
    val photos: StateFlow<List<RecordingPhotoEntity>> =
        tutorRepository.observePhotos(recordingId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var player: MediaPlayer? = null
    private var stopJob: Job? = null
    /** 暂停/继续用的墙钟余量（毫秒） */
    private var remainingMs: Long = 0L
    private var endAtMs: Long = 0L

    init {
        viewModelScope.launch {
            _ui.update { it.copy(recording = recordingRepository.getRecording(recordingId)) }
        }
    }

    fun transcribe() = runAction {
        val count = recordingRepository.transcribe(recordingId) { progress ->
            _ui.update { it.copy(uploadProgress = progress) }
        }
        _ui.update { it.copy(uploadProgress = null, notice = "转写完成，共 $count 段") }
    }

    fun polish() = runAction {
        tutorRepository.polishRecording(recordingId)
        _ui.update { it.copy(notice = "文稿润色完成，点「查看文稿」可对照原文") }
    }

    /** 分析：提取 + 查重后进入待确认状态，由用户确认后才入库 */
    fun analyze() = runAction {
        val candidates = tutorRepository.analyzeRecording(recordingId)
        if (candidates.isEmpty()) {
            _ui.update {
                it.copy(notice = "分析完成，但未提取到薄弱点（可在 设置 → 模型服务 用「测试分析模型」检查模型是否可用）")
            }
        } else {
            _ui.update { it.copy(pendingAnalysis = candidates) }
        }
    }

    /** 用户确认后入库：新增 + 合并重复项；随后按清理模式处理录音文件 */
    fun confirmAnalysis() = runAction {
        val candidates = _ui.value.pendingAnalysis ?: return@runAction
        val result = tutorRepository.applyAnalysis(recordingId, candidates)
        _ui.update {
            it.copy(
                pendingAnalysis = null,
                notice = buildString {
                    append("已添加 ${result.added} 个薄弱点")
                    if (result.merged > 0) append("，合并 ${result.merged} 个重复项")
                },
            )
        }
        maybeCleanupAudio()
    }

    fun dismissAnalysis() = _ui.update { it.copy(pendingAnalysis = null) }

    /** 分析完成后按设置处理录音文件（对应小程序 maybeCleanupAudio：释放存储空间，文本保留） */
    private suspend fun maybeCleanupAudio() {
        val bytes = mediaStorage.recordingAudioBytes(recordingId)
        if (bytes <= 0) return
        val s = settingsStore.settings.first()
        val webdavOk = s.webdavUser.isNotBlank() && s.webdavPassword.isNotBlank()
        when (s.audioCleanupMode) {
            AppSettings.CLEANUP_KEEP -> return
            AppSettings.CLEANUP_DELETE -> removeAudio(toCloud = false)
            AppSettings.CLEANUP_BACKUP_DELETE ->
                if (webdavOk) removeAudio(toCloud = true) else askCleanup(bytes, webdavOk)
            // ask（含 backup_delete 但未配置坚果云的兜底）
            else -> askCleanup(bytes, webdavOk)
        }
    }

    private fun askCleanup(bytes: Long, webdavOk: Boolean) {
        _ui.update { it.copy(cleanupPrompt = CleanupPrompt(bytes, webdavOk)) }
    }

    fun dismissCleanupPrompt() = _ui.update { it.copy(cleanupPrompt = null) }

    /** 清理询问弹窗的选择：删除 / 备份到云端后删除 */
    fun confirmCleanupAudio(toCloud: Boolean) {
        _ui.update { it.copy(cleanupPrompt = null) }
        runAction { removeAudio(toCloud) }
    }

    private suspend fun removeAudio(toCloud: Boolean) {
        val freed = mediaStorage.removeRecordingAudio(recordingId, toCloud)
        if (freed > 0) {
            _ui.update {
                val msg = "已删除录音文件，释放 ${StorageCleanup.mbText(freed)}"
                it.copy(notice = if (it.notice != null) "${it.notice}；$msg" else msg)
            }
        }
    }

    /** 附加已导入的错题照片文件（导入由 PhotoCapture 组件完成） */
    fun addPhotoFiles(files: List<java.io.File>) = runAction {
        tutorRepository.addPhotos(recordingId, files)
        _ui.update { it.copy(notice = "已添加 ${files.size} 张照片") }
    }

    fun notifyError(message: String) = _ui.update { it.copy(error = message) }

    fun deletePhoto(photo: RecordingPhotoEntity) = runAction {
        tutorRepository.deletePhoto(photo)
        _ui.update { it.copy(notice = "照片已删除") }
    }

    /** 分析错题照片：多模态模型提取薄弱点，走与录音分析相同的确认流程 */
    fun analyzePhotos() = runAction {
        val outcome = tutorRepository.analyzePhotos(recordingId)
        if (outcome.candidates.isEmpty()) {
            // 把模型原始返回带给用户，便于区分「照片无法辨认」和「识别/解析失败」
            val raw = outcome.rawText.trim().take(200)
            _ui.update {
                it.copy(
                    notice = "未识别出可分析的题目内容。请确认照片清晰、包含题目内容。" +
                        if (raw.isNotEmpty()) "\n模型原始返回：$raw" else "",
                )
            }
        } else {
            _ui.update { it.copy(pendingAnalysis = outcome.candidates) }
        }
    }

    private fun runAction(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            try {
                block()
                _ui.update { it.copy(recording = recordingRepository.getRecording(recordingId)) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "操作失败") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    fun assignRole(speakerLabel: String, role: String) {
        viewModelScope.launch {
            recordingRepository.assignSpeakerRole(recordingId, speakerLabel, role)
        }
    }

    /**
     * 播放/暂停切换（对齐小程序 onPlaySegment）：正在播该段 → 暂停；已暂停该段 → 继续；
     * 否则从头播放该段。
     */
    fun togglePlaySegment(segment: TranscriptSegmentEntity) {
        when {
            _ui.value.playingSegmentId == segment.id && player != null -> {
                stopJob?.cancel()
                stopJob = null
                remainingMs = (endAtMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                try {
                    player?.pause()
                } catch (_: Exception) {}
                _ui.update { it.copy(playingSegmentId = null, pausedSegmentId = segment.id) }
            }
            _ui.value.pausedSegmentId == segment.id && player != null -> {
                try {
                    player?.start()
                } catch (_: Exception) {}
                endAtMs = SystemClock.elapsedRealtime() + remainingMs
                stopJob = viewModelScope.launch {
                    delay(remainingMs)
                    stopPlayback()
                }
                _ui.update { it.copy(playingSegmentId = segment.id, pausedSegmentId = null) }
            }
            else -> playSegment(segment)
        }
    }

    /**
     * 播放对应片段：seek 到 start，播放到 end 自动停。
     * 分段录音（>10 分钟自动续录产生）按全局时间轴定位所在分段文件，
     * 换算段内偏移（对齐小程序 playSegment）。
     */
    fun playSegment(segment: TranscriptSegmentEntity) {
        val rec = _ui.value.recording ?: return
        if (rec.filePath.isBlank() || rec.audioRemoved) return
        stopPlayback()
        try {
            val decoded = RecordingRepository.decodeSegments(rec.segments)
            val files = if (decoded != null && decoded.size > 1) decoded else listOf(rec.filePath)
            val fileIdx = minOf(
                files.size - 1,
                (segment.startSec / RecordingService.SEGMENT_DURATION_SEC).toInt(),
            )
            val offsetInFileSec =
                (segment.startSec - fileIdx * RecordingService.SEGMENT_DURATION_SEC).coerceAtLeast(0f)
            val p = MediaPlayer()
            p.setDataSource(files[fileIdx])
            p.setOnCompletionListener { stopPlayback() }
            p.setOnErrorListener { _, _, _ ->
                stopPlayback()
                true
            }
            p.prepare()
            p.seekTo((offsetInFileSec * 1000).toInt())
            p.start()
            player = p
            _ui.update { it.copy(playingSegmentId = segment.id, pausedSegmentId = null) }
            val playMillis = ((segment.endSec - segment.startSec).coerceAtLeast(0.5f) * 1000).toLong()
            remainingMs = playMillis
            endAtMs = SystemClock.elapsedRealtime() + playMillis
            stopJob = viewModelScope.launch {
                delay(playMillis)
                stopPlayback()
            }
        } catch (e: Exception) {
            _ui.update { it.copy(error = "播放失败：${e.message}") }
        }
    }

    fun stopPlayback() {
        stopJob?.cancel()
        stopJob = null
        try {
            player?.stop()
        } catch (_: Exception) {}
        player?.release()
        player = null
        _ui.update { it.copy(playingSegmentId = null, pausedSegmentId = null) }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    fun clearNotice() = _ui.update { it.copy(notice = null) }

    fun canTranscribe(): Boolean {
        val rec = _ui.value.recording ?: return false
        if (rec.audioRemoved) return false
        val status = rec.status
        return status == RecordingStatus.RECORDED || status == RecordingStatus.FAILED ||
            status == RecordingStatus.TRANSCRIBED || status == RecordingStatus.ANALYZED
    }

    override fun onCleared() = stopPlayback()
}
