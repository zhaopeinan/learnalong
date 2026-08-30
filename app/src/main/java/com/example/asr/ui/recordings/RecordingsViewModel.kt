package com.example.asr.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.audio.AudioImporter
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.RecordingWithChild
import com.example.asr.data.repository.ChildRepository
import com.example.asr.data.repository.RecordingRepository
import com.example.asr.data.repository.TutorRepository
import com.example.asr.data.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModel(
    private val recordingRepository: RecordingRepository,
    private val tutorRepository: TutorRepository,
    childRepository: ChildRepository,
    settingsStore: SettingsStore,
) : ViewModel() {

    val childFilter = MutableStateFlow<Long?>(null)
    val subjectFilter = MutableStateFlow<String?>(null)

    val children: StateFlow<List<ChildEntity>> = childRepository.children
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjects: StateFlow<List<String>> = recordingRepository.observeSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 可选科目（设置页可编辑），供导入弹窗下拉选择 */
    val configSubjects: StateFlow<List<String>> = settingsStore.settings
        .map { it.subjects }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordings: StateFlow<List<RecordingWithChild>> =
        combine(childFilter, subjectFilter) { c, s -> c to s }
            .flatMapLatest { (c, s) -> recordingRepository.observeRecordings(c, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage

    /** 把导入的音频文件登记为一条辅导记录 */
    fun saveImported(file: File, childId: Long, subject: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val durationSec = AudioImporter.probeDurationSec(file)
                recordingRepository.saveRecording(childId, subject, file, durationSec)
                _importMessage.value = "导入成功"
                onDone()
            } catch (e: Exception) {
                _importMessage.value = "导入失败：${e.message}"
            }
        }
    }

    /** 拍错题：创建纯照片记录并附带照片，完成后回调新记录 id（跳转详情页直接分析） */
    fun savePhotoRecord(childId: Long, subject: String, photoFiles: List<File>, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = recordingRepository.savePhotoRecord(childId, subject)
                tutorRepository.addPhotos(id, photoFiles)
                onDone(id)
            } catch (e: Exception) {
                _importMessage.value = "保存失败：${e.message}"
            }
        }
    }

    /** 删除记录（连同本地音频和错题照片文件） */
    fun delete(recordingId: Long) {
        viewModelScope.launch {
            tutorRepository.deletePhotosForRecording(recordingId)
            recordingRepository.deleteRecording(recordingId)
            _importMessage.value = "已删除"
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }
}
