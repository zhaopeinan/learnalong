package com.example.asr.ui.work

import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.data.local.entity.WorkRecordingEntity
import com.example.asr.data.local.entity.WorkStatus
import com.example.asr.data.local.entity.WorkTodoWithRecording
import com.example.asr.data.repository.RecordingRepository
import com.example.asr.data.repository.WorkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkDetailUiState(
    /** 首次查询完成（此前 recording 为 null 只是加载中，不显示「不存在」） */
    val loaded: Boolean = false,
    val busy: Boolean = false,
    /** 阶段文案（准备上传…/上传转写中…/识别中…/AI 分析中…） */
    val stageText: String = "",
    /** 转写上传进度 0-1；-1 表示识别中（不确定进度）；null 表示不在转写 */
    val uploadPercent: Int? = null,
    /** 播放中（分段连续播放） */
    val playing: Boolean = false,
    val playText: String = "",
    val showTranscript: Boolean = false,
    /** 一次性提示（snackbar） */
    val notice: String? = null,
    /** 失败弹窗内容（可重试） */
    val failure: String? = null,
    val deleted: Boolean = false,
)

/** 工作端详情 ViewModel（对应小程序 work/detail）：转写 → AI 分析 → 纪要 + 待办 + 回放 */
class WorkDetailViewModel(
    private val recordingId: Long,
    private val workRepository: WorkRepository,
    autoStart: Boolean,
) : ViewModel() {

    val recording: StateFlow<WorkRecordingEntity?> = workRepository.observeRecording(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todos: StateFlow<List<WorkTodoWithRecording>> = workRepository.observeTodos(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ui = MutableStateFlow(WorkDetailUiState())
    val ui: StateFlow<WorkDetailUiState> = _ui

    private var player: MediaPlayer? = null

    init {
        viewModelScope.launch {
            workRepository.getRecording(recordingId)
            _ui.update { it.copy(loaded = true) }
        }
        // 录音页保存后跳入（auto=1）：已录状态自动跑完整流程
        if (autoStart) {
            viewModelScope.launch {
                val rec = workRepository.getRecording(recordingId)
                if (rec?.status == WorkStatus.RECORDED) runPipeline()
            }
        }
    }

    /** 完整流程：转写（如需要）→ 分析（对应小程序 runPipeline） */
    fun runPipeline() {
        if (_ui.value.busy) return
        viewModelScope.launch {
            val needTranscribe = workRepository.getRecording(recordingId)?.transcriptText == null
            _ui.update {
                it.copy(
                    busy = true,
                    stageText = if (needTranscribe) "准备上传…" else "准备分析…",
                    uploadPercent = null,
                    failure = null,
                )
            }
            try {
                if (needTranscribe) {
                    workRepository.transcribe(recordingId) { p ->
                        _ui.update {
                            it.copy(
                                stageText = if (p == null) "识别中…" else "上传转写中…",
                                uploadPercent = if (p == null) -1 else (p * 100).toInt(),
                            )
                        }
                    }
                }
                workRepository.analyze(recordingId) { i, t ->
                    _ui.update {
                        it.copy(
                            stageText = if (t > 1) "AI 分析中（$i/$t）…" else "AI 分析中…",
                            uploadPercent = null,
                        )
                    }
                }
                _ui.update { it.copy(busy = false, stageText = "", notice = "分析完成") }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(busy = false, stageText = "", failure = e.message ?: "请稍后重试")
                }
            }
        }
    }

    fun toggleTodo(todoId: Long, done: Boolean) {
        viewModelScope.launch { workRepository.toggleTodo(todoId, done) }
    }

    fun toggleTranscript() = _ui.update { it.copy(showTranscript = !it.showTranscript) }

    /** 音频回放：分段录音按顺序连续播放（对齐小程序 onPlayTap） */
    fun togglePlay() {
        if (_ui.value.playing) {
            stopPlay()
            return
        }
        viewModelScope.launch {
            val rec = workRepository.getRecording(recordingId) ?: return@launch
            if (rec.filePath.isBlank()) return@launch
            val segments = RecordingRepository.decodeSegments(rec.segments)
            val files = if (segments != null && segments.size > 1) segments else listOf(rec.filePath)
            _ui.update { it.copy(playing = true) }
            playAt(files, 0)
        }
    }

    private fun playAt(files: List<String>, index: Int) {
        if (index >= files.size) {
            stopPlay()
            return
        }
        _ui.update {
            it.copy(playText = if (files.size > 1) "播放中（${index + 1}/${files.size}）" else "播放中")
        }
        try {
            val p = MediaPlayer()
            player = p
            p.setDataSource(files[index])
            p.setOnCompletionListener { playAt(files, index + 1) }
            p.setOnErrorListener { _, _, _ ->
                stopPlay()
                true
            }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            stopPlay()
            _ui.update { it.copy(notice = "播放失败：${e.message}") }
        }
    }

    private fun stopPlay() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
        _ui.update { it.copy(playing = false, playText = "") }
    }

    fun deleteRecording() {
        if (_ui.value.busy) return
        viewModelScope.launch {
            workRepository.deleteRecording(recordingId)
            _ui.update { it.copy(deleted = true) }
        }
    }

    fun clearNotice() = _ui.update { it.copy(notice = null) }

    fun dismissFailure() = _ui.update { it.copy(failure = null) }

    override fun onCleared() {
        stopPlay()
    }
}
