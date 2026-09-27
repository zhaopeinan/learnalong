package com.example.asr.media

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.PI

/**
 * 程序合成音效（项目无音频资源，纯代码生成 PCM，AudioTrack 流式播放）：
 * - 加分：上行三音琶音 C5-E5-G5（~0.6s）
 * - 兑换：上扬号角式旋律 C5-E5-G5-C6 + 五度/泛音和声（~1.5s）
 * PCM 生成与 Android 播放分离，生成逻辑可单测。
 */
object SoundEffects {

    const val SAMPLE_RATE = 22050

    /** 单个音符：频率 / 起始时刻 / 时长（秒） */
    data class Note(val freq: Float, val startSec: Float, val durationSec: Float)

    /** 加分琶音 PCM */
    fun earnPcm(): ShortArray = render(
        notes = listOf(
            Note(523.25f, 0.00f, 0.22f),  // C5
            Note(659.25f, 0.09f, 0.22f),  // E5
            Note(783.99f, 0.18f, 0.40f),  // G5 尾音稍长
        ),
        totalSec = 0.6f,
    )

    /** 兑换号角 PCM：主旋律 + 五度/高八度泛音和声 */
    fun redeemPcm(): ShortArray = render(
        notes = listOf(
            Note(523.25f, 0.00f, 0.18f),   // C5
            Note(659.25f, 0.14f, 0.18f),   // E5
            Note(783.99f, 0.28f, 0.18f),   // G5
            Note(1046.50f, 0.42f, 0.95f),  // C6 长音
            Note(783.99f, 0.42f, 0.95f),   // 五度和声
            Note(1318.51f, 0.42f, 0.95f),  // 泛音
        ),
        totalSec = 1.5f,
    )

    /**
     * 渲染 PCM：正弦波 + 快攻慢衰指数包络；多音符叠加后按峰值归一化（0.85 封顶防爆音），
     * 结尾 20ms 淡出避免咔哒声。返回 16bit 单声道 PCM。
     */
    fun render(notes: List<Note>, totalSec: Float): ShortArray {
        val total = (totalSec * SAMPLE_RATE).toInt()
        val mix = FloatArray(total)
        val attackSec = 0.005f
        val decaySec = 0.12f
        for (note in notes) {
            val start = (note.startSec * SAMPLE_RATE).toInt()
            val length = (note.durationSec * SAMPLE_RATE).toInt()
            for (i in 0 until length) {
                val idx = start + i
                if (idx >= total) break
                val t = i.toFloat() / SAMPLE_RATE
                val attack = if (t < attackSec) t / attackSec else 1f
                val envelope = attack * exp(-t / decaySec)
                mix[idx] += (sin(2.0 * PI * note.freq * t).toFloat() * envelope)
            }
        }
        // 峰值归一化：多音符叠加不溢出
        var peak = 0f
        for (v in mix) {
            val a = kotlin.math.abs(v)
            if (a > peak) peak = a
        }
        val scale = if (peak > 0f) (0.85f / peak).coerceAtMost(1f) else 1f
        val fadeOut = (0.02f * SAMPLE_RATE).toInt()
        return ShortArray(total) { i ->
            val fade = if (i >= total - fadeOut) (total - i).toFloat() / fadeOut else 1f
            (mix[i] * scale * fade * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    fun playEarn() = play(earnPcm())

    fun playRedeem() = play(redeemPcm())

    /** 后台线程静态模式播放，播完自动 release */
    private fun play(pcm: ShortArray) {
        Thread {
            val track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                pcm.size * 2,
                AudioTrack.MODE_STATIC,
            )
            try {
                track.write(pcm, 0, pcm.size)
                track.play()
                // 静态模式写完即播，等待播完后释放
                Thread.sleep(pcm.size * 1000L / SAMPLE_RATE + 100)
            } catch (_: Exception) {
                // 播放失败静默忽略（音效不阻断功能）
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }.apply { isDaemon = true }.start()
    }
}
