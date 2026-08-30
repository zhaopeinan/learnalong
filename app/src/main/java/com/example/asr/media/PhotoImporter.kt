package com.example.asr.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 错题照片：复制进 App 私有目录、按需压缩编码为 base64 data URL */
object PhotoImporter {

    private val timeFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** 复制 uri 内容到 photos 目录，返回新文件；失败抛异常 */
    fun import(context: Context, uri: Uri, index: Int = 0): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val ext = when {
            context.contentResolver.getType(uri)?.contains("png") == true -> "png"
            else -> "jpg"
        }
        val target = File(dir, "photo_${timeFormat.format(Date())}_$index.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("无法读取所选照片")
        if (target.length() == 0L) {
            target.delete()
            throw IllegalArgumentException("所选照片为空")
        }
        return target
    }

    /**
     * 压缩并编码为 base64 data URL。
     * 策略（保清晰度优先，控制请求体大小）：
     * - 最长边 > 1600px 才降采样（错题文字在 1600px 下清晰可辨，再高对 VLM 收益递减）
     * - 已是小图（≤1600px 且 < 800KB）直接原样编码，不做有损压缩
     * - 其余按 JPEG 质量 85 压缩
     */
    fun toDataUrl(file: File, maxSide: Int = 1600, quality: Int = 85): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val alreadySmall =
            maxOf(bounds.outWidth, bounds.outHeight) <= maxSide && file.length() < 800 * 1024
        if (alreadySmall) {
            val mime = if (file.extension.equals("png", ignoreCase = true)) "image/png" else "image/jpeg"
            val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            return "data:$mime;base64,$b64"
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IllegalArgumentException("照片解码失败")
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }
}
