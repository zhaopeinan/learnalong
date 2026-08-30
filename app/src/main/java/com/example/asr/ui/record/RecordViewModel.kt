package com.example.asr.ui.record

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.AsrApplication
import com.example.asr.audio.AudioRecorder
import com.example.asr.data.local.entity.ChildEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class RecordUiState(
    val selectedChildId: Long? = null,
    val subject: String = "",
    val isRecording: Boolean = false,
    val elapsedSec: Int = 0,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

class RecordViewModel(application: Application) : ViewModel() {

    private val recorder = AudioRecorder(application)
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

    private var currentFile: File? = null
    private var timerJob: Job? = null

    fun selectChild(id: Long) = _ui.update { it.copy(selectedChildId = id) }

    fun setSubject(subject: String) = _ui.update { it.copy(subject = subject) }

    fun startRecording() {
        if (_ui.value.isRecording) return
        try {
            currentFile = recorder.start()
            _ui.update { it.copy(isRecording = true, elapsedSec = 0, error = null) }
            timerJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _ui.update { it.copy(elapsedSec = it.elapsedSec + 1) }
                }
            }
        } catch (e: Exception) {
            _ui.update { it.copy(error = "录音启动失败：${e.message}") }
        }
    }

    fun stopAndSave() {
        if (!_ui.value.isRecording) return
        timerJob?.cancel()
        val file = recorder.stop()
        val state = _ui.value
        _ui.update { it.copy(isRecording = false) }

        val childId = state.selectedChildId
        if (file == null || childId == null || state.subject.isBlank()) {
            file?.delete()
            _ui.update { it.copy(error = "请选择孩子并填写科目后再录音") }
            return
        }
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                recordingRepository.saveRecording(childId, state.subject, file, state.elapsedSec)
                _ui.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, error = "保存失败：${e.message}") }
            }
        }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    override fun onCleared() {
        timerJob?.cancel()
        if (recorder.isRecording) recorder.stop()
    }
}
