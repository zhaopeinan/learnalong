package com.example.asr.ui.record

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.AsrApplication
import com.example.asr.audio.RecordingService
import com.example.asr.data.local.entity.ChildEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordUiState(
    val selectedChildId: Long? = null,
    val subject: String = "",
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    /** 录音已被系统中断自动收尾，结果待保存 */
    val interruptedFinished: Boolean = false,
    val elapsedSec: Int = 0,
    val segmentCount: Int = 1,
    /** 实时振幅 0~1（音量条展示） */
    val amplitude: Float = 0f,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/**
 * 录音页 ViewModel：录音实体在 RecordingService（前台服务）中存活，
 * 页面退出/切后台不中断录音；这里只负责绑定服务、映射状态、保存结果。
 */
class RecordViewModel(application: Application) : ViewModel() {

    private val app = application
    private val container = (application as AsrApplication).container
    private val recordingRepository = container.recordingRepository
    private val childRepository = container.childRepository

    val children: StateFlow<List<ChildEntity>> = childRepository.children
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 可选科目（设置页可编辑） */
    val subjects: StateFlow<List<String>> = container.settingsStore.settings
        .map { it.subjects }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ui = MutableStateFlow(RecordUiState())
    val ui: StateFlow<RecordUiState> = _ui

    private var service: RecordingService? = null
    private var lastServiceMessage: String? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as? RecordingService.LocalBinder)?.service ?: return
            service = s
            viewModelScope.launch {
                s.state.collect { state -> mapServiceState(state) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    init {
        application.bindService(
            Intent(application, RecordingService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun mapServiceState(state: RecordingService.RecordingState) {
        _ui.update {
            it.copy(
                isRecording = state.phase == RecordingService.Phase.RECORDING ||
                    state.phase == RecordingService.Phase.PAUSED,
                isPaused = state.phase == RecordingService.Phase.PAUSED,
                interruptedFinished = state.phase == RecordingService.Phase.FINISHED,
                elapsedSec = state.elapsedSec,
                segmentCount = maxOf(1, state.segmentCount),
                amplitude = state.amplitude,
            )
        }
        // 中断/自动暂停原因只提示一次（对齐小程序 onInterrupted 回调）
        val msg = state.message
        if (msg != null && msg != lastServiceMessage) {
            lastServiceMessage = msg
            _ui.update { it.copy(error = msg) }
        }
        if (msg == null) lastServiceMessage = null
    }

    fun selectChild(id: Long) = _ui.update { it.copy(selectedChildId = id) }

    fun setSubject(subject: String) = _ui.update { it.copy(subject = subject) }

    fun startRecording() {
        if (_ui.value.isRecording) return
        try {
            lastServiceMessage = null
            ContextCompat.startForegroundService(
                app,
                Intent(app, RecordingService::class.java).setAction(RecordingService.ACTION_START),
            )
        } catch (e: Exception) {
            _ui.update { it.copy(error = "录音启动失败：${e.message}") }
        }
    }

    fun pauseRecording() = service?.pauseRecording()

    fun resumeRecording() = service?.resumeRecording()

    fun stopAndSave() {
        val s = _ui.value
        if (!s.isRecording && !s.interruptedFinished) return
        val svc = service
        if (svc == null) {
            _ui.update { it.copy(error = "录音服务未连接，请重试") }
            return
        }
        val result = svc.stopRecording()
        _ui.update {
            it.copy(
                isRecording = false,
                isPaused = false,
                interruptedFinished = false,
                amplitude = 0f,
            )
        }

        val childId = s.selectedChildId
        if (result.files.isEmpty() || childId == null || s.subject.isBlank()) {
            _ui.update {
                it.copy(
                    error = if (result.files.isEmpty()) "没有录到内容"
                    else "请选择孩子并填写科目后再录音",
                )
            }
            return
        }
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                recordingRepository.saveRecording(
                    childId = childId,
                    subject = s.subject,
                    file = result.files.first(),
                    durationSec = result.durationSec,
                    segmentFiles = result.files,
                )
                _ui.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = "保存失败：${e.message}") }
            }
        }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    override fun onCleared() {
        // 只解绑：录音在服务里继续（页面退出不中断），重进页面后重新绑定恢复展示
        try { app.unbindService(connection) } catch (_: Exception) {}
    }
}
