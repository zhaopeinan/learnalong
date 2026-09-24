package com.example.asr.data.remote

import android.content.Context
import com.example.asr.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试模式日志（对应小程序 utils/debug.ts）：开启后（设置页输入密码）记录失败的模型请求，
 * 含完整提示词与错误信息，支持复制 / 导出 txt 分享排查。
 * 只保留最近 10 条，存 filesDir/debug_log.json。
 */
class DebugLog(
    context: Context,
    private val settingsStore: SettingsStore,
) {

    private val file = File(context.filesDir, "debug_log.json")
    private val json = Json { ignoreUnknownKeys = true }

    /** 记录一条失败请求；调试模式未开启时直接忽略。读取/写入失败均静默（不影响主流程） */
    suspend fun record(action: String, model: String, prompt: String, error: String) {
        if (!settingsStore.settings.first().debugMode) return
        runCatching {
            val list = mutableListOf(DebugEntry(System.currentTimeMillis(), action, model, prompt, error))
            list.addAll(entries())
            file.writeText(json.encodeToString(trimToMax(list, MAX_ENTRIES)))
        }
    }

    fun entries(): List<DebugEntry> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString<List<DebugEntry>>(file.readText())
    }.getOrDefault(emptyList())

    /** 最近一条记录（详情页错误弹窗快捷复制用） */
    fun lastEntry(): DebugEntry? = entries().firstOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    companion object {
        const val MAX_ENTRIES = 10

        /** 调试模式开启密码（对齐小程序 settings.ts DEBUG_PASSWORD） */
        const val DEBUG_PASSWORD = "whosyourdaddy"

        fun trimToMax(list: List<DebugEntry>, max: Int = MAX_ENTRIES): List<DebugEntry> =
            list.take(max)

        fun formatTime(t: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(t))

        /** 拼出用于复制/导出的完整调试文本（格式对齐小程序 formatDebugText） */
        fun formatDebugText(e: DebugEntry): String = listOf(
            "===== 伴学记 调试信息 =====",
            "时间：${formatTime(e.time)}",
            "操作：${e.action}",
            "模型：${e.model}",
            "",
            "----- 错误信息 -----",
            e.error,
            "",
            "----- 请求提示词 -----",
            e.prompt,
            "===========================",
        ).joinToString("\n")

        /** 导出文件名（对齐小程序 exportDebugText：伴学记-调试-yyyyMMdd-HHmmss.txt） */
        fun exportFileName(e: DebugEntry): String =
            "伴学记-调试-${formatTime(e.time).replace(Regex("[-:]"), "").replace(' ', '-')}.txt"
    }
}

@Serializable
data class DebugEntry(
    val time: Long,
    /** 操作名称，如「语音转写」「润色文稿」 */
    val action: String,
    val model: String,
    /** 完整请求内容（system + user；图片以占位符表示） */
    val prompt: String,
    val error: String,
)
