package com.example.asr.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.asr.MainActivity
import com.example.asr.R
import com.example.asr.worker.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 录音前台服务（对应小程序 audio.ts 的 startSegmentedRecording 可靠性逻辑）：
 * - foregroundServiceType="microphone" + 常驻通知（计时/暂停状态，点击回录音页）
 * - PARTIAL_WAKE_LOCK：录音期间持有，防止 CPU 休眠断录（对应小程序 setKeepScreenOn）
 * - 分段续录：单段满 SEGMENT_DURATION_SEC(600s) 自动无缝切下一段，段文件 rec_<ts>_sN.m4a
 * - 音频焦点：被来电/其他应用抢占时自动暂停；永久丢失则按系统中断收尾并标记 interrupted
 * - 看门狗：录音中段文件 4s 无字节增长（对应小程序 4s 无音频帧心跳）判定被系统杀掉，
 *   保住已录段落正常收尾，避免"计时还在走、实际没在录"的假录音
 *
 * 与 UI 通信：Binder + StateFlow（RecordViewModel bindService 后收集 state）。
 * 录音在服务内独立存活，页面退出/切后台不中断录音。
 */
class RecordingService : Service() {

    enum class Phase { IDLE, RECORDING, PAUSED, FINISHED }

    data class RecordingState(
        val phase: Phase = Phase.IDLE,
        val elapsedSec: Int = 0,
        val segmentCount: Int = 0,
        /** 实时振幅 0~1（波形/音量展示） */
        val amplitude: Float = 0f,
        /** 录音曾被系统中断（来电/被杀等），实际时长可能小于计时 */
        val interrupted: Boolean = false,
        /** 中断或被自动暂停的原因，UI 提示用 */
        val message: String? = null,
    )

    data class RecordResult(
        val files: List<File>,
        val durationSec: Int,
        val interrupted: Boolean,
        val message: String?,
    )

    inner class LocalBinder : Binder() {
        val service: RecordingService get() = this@RecordingService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state

    private var recorder: AudioRecorder? = null
    private val segmentFiles = mutableListOf<File>()
    private var baseTimestamp = 0L

    // 计时：已完成分段累计 + 当前段（不含暂停）
    private var completedSegMs = 0L
    private var segStartMs = 0L
    private var segPausedMs = 0L
    private var pauseStartMs = 0L

    // 看门狗：段文件字节增长心跳
    private var lastFileSize = 0L
    private var lastGrowthAtMs = 0L
    private var growthEverSeen = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var pendingResult: RecordResult? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // 永久丢失（被其他录音/通话应用接管）：保不住麦克风，按系统中断收尾
            AudioManager.AUDIOFOCUS_LOSS ->
                markInterrupted("检测到系统音频中断（来电/闹钟/其他应用），已保留实际录到的内容")
            // 临时被抢（来电响铃等）：自动暂停并标记，由用户点继续恢复
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> pauseBySystem("系统音频被占用（来电/其他应用），已自动暂停")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) startRecording()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cleanupRecorder()
        releaseWakeLock()
        abandonAudioFocus()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- 对外操作（Binder 调用方在 UI 层） ----------

    @Synchronized
    fun startRecording() {
        if (_state.value.phase == Phase.RECORDING || _state.value.phase == Phase.PAUSED) return
        segmentFiles.clear()
        completedSegMs = 0L
        pendingResult = null
        baseTimestamp = System.currentTimeMillis()

        acquireWakeLock()
        requestAudioFocus()
        NotificationHelper.ensureRecordingChannel(this)
        startForegroundWithNotification("准备录音…")

        try {
            startSegment()
        } catch (e: Exception) {
            cleanupRecorder()
            releaseWakeLock()
            abandonAudioFocus()
            stopForeground(STOP_FOREGROUND_REMOVE)
            _state.value = RecordingState(message = "录音启动失败：${e.message}")
            return
        }
        _state.value = RecordingState(phase = Phase.RECORDING, segmentCount = 1)
        startTicker()
    }

    @Synchronized
    fun pauseRecording() {
        if (_state.value.phase != Phase.RECORDING) return
        recorder?.pause()
        pauseStartMs = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(phase = Phase.PAUSED, amplitude = 0f, message = null)
        updateNotification()
    }

    @Synchronized
    fun resumeRecording() {
        if (_state.value.phase != Phase.PAUSED) return
        try {
            recorder?.resume()
        } catch (e: Exception) {
            markInterrupted("录音恢复失败（${e.message}），已保留实际录到的内容")
            return
        }
        segPausedMs += SystemClock.elapsedRealtime() - pauseStartMs
        // 恢复后重新武装看门狗：以恢复时刻为基准给宽限，避免暂停前的旧时间戳误判
        if (growthEverSeen) lastGrowthAtMs = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(phase = Phase.RECORDING, message = null)
        updateNotification()
    }

    /** 正常停止；若录音已被系统中断自动收尾，则返回保留的部分结果 */
    @Synchronized
    fun stopRecording(): RecordResult {
        if (_state.value.phase == Phase.FINISHED) {
            val pending = pendingResult ?: RecordResult(emptyList(), 0, true, _state.value.message)
            pendingResult = null
            _state.value = RecordingState()
            stopSelf()
            return pending
        }
        val interrupted = _state.value.interrupted
        val message = _state.value.message
        finishSegments()
        teardown()
        val result = RecordResult(
            files = segmentFiles.toList(),
            durationSec = if (segmentFiles.isEmpty()) 0 else maxOf(1, (completedSegMs / 1000).toInt()),
            interrupted = interrupted,
            message = message,
        )
        _state.value = RecordingState()
        stopSelf()
        return result
    }

    // ---------- 内部实现 ----------

    private fun startSegment() {
        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${baseTimestamp}_s${segmentFiles.size + 1}.m4a")
        val r = recorder ?: AudioRecorder(applicationContext).also { recorder = it }
        r.start(file)
        segStartMs = SystemClock.elapsedRealtime()
        segPausedMs = 0L
        lastFileSize = 0L
        // 续段重新武装看门狗：以段起始时刻为基准给宽限（仅 growthEverSeen 后生效）
        lastGrowthAtMs = segStartMs
    }

    private fun currentSegMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val pausedExtra = if (_state.value.phase == Phase.PAUSED) now - pauseStartMs else 0L
        return now - segStartMs - segPausedMs - pausedExtra
    }

    private var tickerRunning = false

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        scope.launch {
            var tickCount = 0
            while (true) {
                delay(250)
                val phase = _state.value.phase
                if (phase != Phase.RECORDING && phase != Phase.PAUSED) break
                val amplitude = if (phase == Phase.RECORDING) {
                    (recorder?.maxAmplitude() ?: 0) / 32767f
                } else 0f
                _state.value = _state.value.copy(
                    elapsedSec = ((completedSegMs + currentSegMs()) / 1000).toInt(),
                    amplitude = amplitude.coerceIn(0f, 1f),
                )
                tickCount++
                if (tickCount % 4 == 0) { // 每秒
                    if (phase == Phase.RECORDING) {
                        if (currentSegMs() >= SEGMENT_DURATION_SEC * 1000L) rotateSegment()
                        runWatchdog()
                    }
                    updateNotification()
                }
            }
            tickerRunning = false
        }
    }

    /** 到 10 分钟自动续录下一段（对用户表现为一段连续录音；段间存在 <0.5s 接缝） */
    private fun rotateSegment() {
        completedSegMs += currentSegMs()
        val r = recorder ?: return
        r.stop()?.let { segmentFiles.add(it) }
        try {
            startSegment()
        } catch (e: Exception) {
            // 续段启动失败（录音会话被系统回收等）：保住已录段落并标记中断
            markInterrupted("录音启动失败（${e.message ?: "可能被系统回收"}），已保留实际录到的内容")
            return
        }
        _state.value = _state.value.copy(segmentCount = segmentFiles.size + 1)
    }

    /** 文件增长心跳看门狗：录音中 4s 无字节写入 = 录音已被系统杀掉，立即收尾 */
    private fun runWatchdog() {
        val file = currentSegmentFile() ?: return
        val size = try { file.length() } catch (_: Exception) { return }
        val now = SystemClock.elapsedRealtime()
        if (size > lastFileSize) {
            lastFileSize = size
            lastGrowthAtMs = now
            growthEverSeen = true
            return
        }
        if (growthEverSeen && now - lastGrowthAtMs > 4000) {
            markInterrupted("录音被系统中断（可能因麦克风被其他应用占用），已保留实际录到的内容")
        }
    }

    private fun currentSegmentFile(): File? {
        val dir = File(filesDir, "recordings")
        val f = File(dir, "rec_${baseTimestamp}_s${segmentFiles.size + 1}.m4a")
        return if (f.exists()) f else null
    }

    /** 系统临时抢占：自动暂停并标记（对齐小程序中断提示，只提示一次由 state.message 承载） */
    private fun pauseBySystem(reason: String) {
        if (_state.value.phase != Phase.RECORDING) return
        recorder?.pause()
        pauseStartMs = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(phase = Phase.PAUSED, amplitude = 0f, message = reason)
        updateNotification()
    }

    /** 录音被系统中断：保住已录段落正常收尾，状态置 FINISHED 等页面来取结果 */
    private fun markInterrupted(reason: String) {
        val phase = _state.value.phase
        if (phase != Phase.RECORDING && phase != Phase.PAUSED) return
        finishSegments()
        val totalMs = completedSegMs
        teardown()
        val result = RecordResult(
            files = segmentFiles.toList(),
            durationSec = if (segmentFiles.isEmpty()) 0 else maxOf(1, (totalMs / 1000).toInt()),
            interrupted = true,
            message = reason,
        )
        if (segmentFiles.isEmpty()) {
            // 没录到任何内容：直接回到 IDLE，由页面提示
            _state.value = RecordingState(interrupted = true, message = reason)
        } else {
            pendingResult = result
            _state.value = RecordingState(
                phase = Phase.FINISHED,
                elapsedSec = result.durationSec,
                segmentCount = segmentFiles.size,
                interrupted = true,
                message = reason,
            )
        }
    }

    /** 停止当前段并累计时长（成功段入列表，失败段丢弃但保留已录段落） */
    private fun finishSegments() {
        val r = recorder ?: return
        completedSegMs += currentSegMs()
        r.stop()?.let { file ->
            // 空段（启动即停）丢弃
            if (file.length() > 0) segmentFiles.add(file) else file.delete()
        }
        recorder = null
    }

    private fun cleanupRecorder() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder = null
    }

    private fun teardown() {
        recorder = null
        releaseWakeLock()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ---------- 焦点 / 唤醒锁 / 通知 ----------

    private fun requestAudioFocus() {
        val am = audioManager ?: (getSystemService(Context.AUDIO_SERVICE) as AudioManager).also {
            audioManager = it
        }
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        am.requestAudioFocus(req)
        focusRequest = req
    }

    private fun abandonAudioFocus() {
        val req = focusRequest ?: return
        try { audioManager?.abandonAudioFocusRequest(req) } catch (_: Exception) {}
        focusRequest = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "asr:recording").apply {
            setReferenceCounted(false)
            acquire(MAX_RECORDING_MS)
        }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun startForegroundWithNotification(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN_RECORD, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationHelper.RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.mic_white)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(intent)
            .build()
    }

    private fun updateNotification() {
        val s = _state.value
        if (s.phase != Phase.RECORDING && s.phase != Phase.PAUSED) return
        val time = formatElapsed(s.elapsedSec)
        val text = when (s.phase) {
            Phase.PAUSED -> "录音已暂停 $time"
            else -> if (s.segmentCount > 1) "录音中 $time · 第 ${s.segmentCount} 段" else "录音中 $time"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun formatElapsed(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    companion object {
        const val ACTION_START = "com.example.asr.action.START_RECORDING"
        const val EXTRA_OPEN_RECORD = "com.example.asr.extra.OPEN_RECORD"

        /** 分段时长（秒）：与小程序 SEGMENT_DURATION_SEC 一致，转写时按段序 * 600s 计时间偏移 */
        const val SEGMENT_DURATION_SEC = 600

        /** 看门狗唤醒锁兜底上限：3 小时，防止异常路径泄漏 */
        private const val MAX_RECORDING_MS = 3L * 3600 * 1000

        private const val NOTIFICATION_ID = 1002
    }
}
