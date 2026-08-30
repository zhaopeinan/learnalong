package com.example.asr.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** MediaRecorder 封装：AAC / .m4a，输出到 App 私有目录 recordings/ */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${System.currentTimeMillis()}.m4a")

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
        return file
    }

    /** 停止录音并返回输出文件；失败时返回 null 并清理半成品文件 */
    fun stop(): File? {
        val r = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null
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
