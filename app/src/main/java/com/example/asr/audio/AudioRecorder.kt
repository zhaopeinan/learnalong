package com.example.asr.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * MediaRecorder 封装：AAC / .m4a，单段录音。
 * 长录音的分段续录由 RecordingService 持有多个段来编排（对齐小程序 audio.ts 的分段逻辑）。
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var paused = false

    val isRecording: Boolean get() = recorder != null
    val isPaused: Boolean get() = paused

    /** 开始录制到指定文件（目录需已存在） */
    fun start(file: File) {
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()

        recorder = r
        outputFile = file
        paused = false
    }

    fun pause() {
        val r = recorder ?: return
        if (paused) return
        r.pause()
        paused = true
    }

    fun resume() {
        val r = recorder ?: return
        if (!paused) return
        r.resume()
        paused = false
    }

    /** 当前最大振幅（波形/音量展示用），未在录或暂停时返回 0 */
    fun maxAmplitude(): Int {
        val r = recorder ?: return 0
        if (paused) return 0
        return try { r.maxAmplitude } catch (_: Exception) { 0 }
    }

    /** 停止录音并返回输出文件；失败时返回 null 并清理半成品文件 */
    fun stop(): File? {
        val r = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null
        paused = false
        return try {
            r.stop()
            file
        } catch (e: Exception) {
            file?.delete()
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }
}
