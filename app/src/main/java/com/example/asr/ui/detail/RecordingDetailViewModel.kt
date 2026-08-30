package com.example.asr.ui.detail

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.data.local.entity.RecordingEntity
import com.example.asr.data.local.entity.RecordingPhotoEntity
import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.TranscriptSegmentEntity
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.repository.RecordingRepository
import com.example.asr.data.repository.TutorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    val error: String? = null,
    val notice: String? = null,
)

class RecordingDetailViewModel(
    private val recordingId: Long,
    private val recordingRepository: RecordingRepository,
    private val tutorRepository: TutorRepository,
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
                it.copy(notice = "分析完成，但未提取到薄弱点（可在设置页用「测试分析模型」检查模型是否可用）")
            }
        } else {
            _ui.update { it.copy(pendingAnalysis = candidates) }
        }
    }

    /** 用户确认后入库：新增 + 合并重复项 */
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
    }

    fun dismissAnalysis() = _ui.update { it.copy(pendingAnalysis = null) }

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

    /** 播放对应片段：seek 到 start，播放到 end 自动停 */
    fun playSegment(segment: TranscriptSegmentEntity) {
        val path = _ui.value.recording?.filePath ?: return
        stopPlayback()
        try {
            val p = MediaPlayer()
            p.setDataSource(path)
            p.prepare()
            p.seekTo((segment.startSec * 1000).toInt())
            p.start()
            player = p
            _ui.update { it.copy(playingSegmentId = segment.id) }
            val playMillis = ((segment.endSec - segment.startSec).coerceAtLeast(0.5f) * 1000).toLong()
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
        _ui.update { it.copy(playingSegmentId = null) }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    fun clearNotice() = _ui.update { it.copy(notice = null) }

    fun canTranscribe(): Boolean {
        val status = _ui.value.recording?.status
        return status == RecordingStatus.RECORDED || status == RecordingStatus.FAILED ||
            status == RecordingStatus.TRANSCRIBED || status == RecordingStatus.ANALYZED
    }

    override fun onCleared() = stopPlayback()
}
