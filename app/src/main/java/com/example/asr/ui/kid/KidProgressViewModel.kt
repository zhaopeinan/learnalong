package com.example.asr.ui.kid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.ReviewTaskWithWeakPoint
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.repository.ChildRepository
import com.example.asr.data.repository.TutorRepository
import com.example.asr.data.settings.SettingsStore
import com.example.asr.domain.KidReward
import com.example.asr.media.SpeechSynthesizer
import com.example.asr.media.TtsPlayer
import com.example.asr.media.TtsState
import com.example.asr.ui.components.TaskContentState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 小怪兽图鉴条目（对应小程序 kid-progress MonsterView） */
data class MonsterView(
    val weakPoint: WeakPointEntity,
    /** 掌握度 >= 100：已消灭（翻面成勋章） */
    val defeated: Boolean,
    /** 掌握度 >= 80 且未消灭：快消灭啦徽章 */
    val almostDone: Boolean,
    /** 今天有到期任务 */
    val dueToday: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class KidProgressViewModel(
    private val tutorRepository: TutorRepository,
    childRepository: ChildRepository,
    private val kidReward: KidReward,
    private val speechSynthesizer: SpeechSynthesizer,
    settingsStore: SettingsStore,
) : ViewModel() {

    /** 孩子端绑定的孩子 id（未绑定为 null，页面显示引导空态） */
    val boundChildId: StateFlow<Long?> = settingsStore.settings
        .map { it.kidChildId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 设置是否已加载（避免进页面时「未绑定孩子」空态闪烁） */
    val ready: StateFlow<Boolean> = boundChildId
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** AI 语音播报开关：关闭时隐藏所有「读出来」按钮 */
    val voiceEnabled: StateFlow<Boolean> = settingsStore.settings
        .map { it.chatVoiceEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val child: StateFlow<ChildEntity?> = combine(boundChildId, childRepository.children) { id, children ->
        children.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 今日到期复习任务（仅当前孩子） */
    val dueTasks: StateFlow<List<ReviewTaskWithWeakPoint>> = boundChildId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else tutorRepository.observeDueTasks(System.currentTimeMillis())
                .map { tasks -> tasks.filter { it.childId == id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stars: StateFlow<Int> = boundChildId
        .flatMapLatest { id -> if (id == null) flowOf(0) else kidReward.observeStars(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 小怪兽图鉴：今天要练的排最前，已消灭垫底，其余按消灭进度升序（同小程序排序） */
    val monsters: StateFlow<List<MonsterView>> = combine(
        boundChildId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else tutorRepository.observeWeakPoints(id, null)
        },
        dueTasks,
    ) { weakPoints, due ->
        val dueIds = due.map { it.weakPointId }.toSet()
        weakPoints.map { wp ->
            MonsterView(
                weakPoint = wp,
                defeated = KidReward.isDefeated(wp.mastery),
                almostDone = KidReward.isAlmostDone(wp.mastery),
                dueToday = wp.id in dueIds,
            )
        }.sortedWith(compareBy({ !it.dueToday }, { it.defeated }, { it.weakPoint.mastery }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** taskId → 练习内容状态 */
    private val _contents = MutableStateFlow<Map<Long, TaskContentState>>(emptyMap())
    val contents: StateFlow<Map<Long, TaskContentState>> = _contents

    /** 正在播放/合成的播报 key（空串 = 无；一次只播一条，同小程序 playingKey） */
    private val _playingKey = MutableStateFlow("")
    val playingKey: StateFlow<String> = _playingKey

    /** 一次性提示（snackbar 展示后调 consumeMessage 清除） */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val ttsPlayer = TtsPlayer { state ->
        if (state == TtsState.COMPLETED || state == TtsState.ERROR) _playingKey.value = ""
    }

    /** 展开任务时调用：加载/生成练习内容（task.content 即缓存，没有才请求生成） */
    fun loadContent(item: ReviewTaskWithWeakPoint) {
        if (_contents.value[item.taskId] is TaskContentState.Loading) return
        if (_contents.value[item.taskId] is TaskContentState.Ready) return
        viewModelScope.launch {
            _contents.update { it + (item.taskId to TaskContentState.Loading) }
            _contents.update {
                try {
                    it + (item.taskId to TaskContentState.Ready(tutorRepository.getTaskContent(item)))
                } catch (e: Exception) {
                    it + (item.taskId to TaskContentState.Failed(e.message ?: "生成失败"))
                }
            }
        }
    }

    /** 「读出来」：再点一次停止；播放中忽略其他播报（同小程序 onSpeak） */
    fun onSpeak(key: String, text: String) {
        if (!voiceEnabled.value || text.isBlank()) return
        if (_playingKey.value == key) {
            ttsPlayer.stop()
            _playingKey.value = ""
            return
        }
        if (_playingKey.value.isNotEmpty()) return
        viewModelScope.launch {
            _playingKey.value = key
            try {
                ttsPlayer.play(speechSynthesizer.synthesize(text, child.value?.voiceId))
            } catch (e: Exception) {
                _playingKey.value = ""
                _message.value = e.message ?: "语音生成失败"
            }
        }
    }

    /** 「还要练」/「我会啦」：推进复习；掌握了 +1 星，掌握度满触发消灭庆祝（同小程序 onMark） */
    fun mark(item: ReviewTaskWithWeakPoint, mastered: Boolean) {
        viewModelScope.launch {
            tutorRepository.applyReview(item.taskId, item.weakPointId, mastered)
            _contents.update { it - item.taskId }
            if (mastered) {
                kidReward.addStar(item.childId)
                val defeated = KidReward.isDefeated(
                    tutorRepository.getWeakPoint(item.weakPointId)?.mastery ?: 0
                )
                _message.value = if (defeated) KidReward.DEFEATED_TOAST else KidReward.starToast()
            } else {
                _message.value = KidReward.TRY_AGAIN_TOAST
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** 离开页面时停止播报（对应小程序 onHide stopCurrent） */
    fun stopSpeaking() {
        ttsPlayer.stop()
        _playingKey.value = ""
    }

    override fun onCleared() {
        ttsPlayer.release()
    }
}
