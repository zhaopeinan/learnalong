package com.example.asr.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 把外部音频（系统文件选择器 / 其他 App 分享）复制进 App 私有录音目录 */
object AudioImporter {

    private val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** 复制 uri 内容到 recordings 目录，返回新文件；失败抛异常 */
    fun import(context: Context, uri: Uri): File {
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val ext = guessExtension(context, uri)
        val target = File(dir, "import_${timeFormat.format(Date())}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取所选音频文件")
        if (target.length() == 0L) {
            target.delete()
            throw IllegalArgumentException("所选音频文件为空")
        }
        return target
    }

    /** 读取音频时长（秒），读不出来返回 0 */
    fun probeDurationSec(file: File): Int {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            mmr.release()
            ((ms ?: 0L) / 1000L).toInt()
        } catch (_: Exception) {
            0
        }
    }

    private fun guessExtension(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: return "m4a"
        return when {
            mime.contains("mp3") || mime.contains("mpeg") -> "mp3"
            mime.contains("wav") || mime.contains("wave") -> "wav"
            mime.contains("ogg") -> "ogg"
            mime.contains("3gpp") -> "3gp"
            mime.contains("amr") -> "amr"
            else -> "m4a" // audio/mp4、audio/aac 等
        }
    }
}
