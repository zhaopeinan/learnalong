package com.example.asr.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File

/**
 * 各品牌系统录音机的默认存储目录（相对内部存储根）。
 * 「导入音频」时按 Build.MANUFACTURER 识别手机品牌，把系统文件选择器
 * 直接定位到该机录音机文件夹（EXTRA_INITIAL_URI），找不到目录时回退默认位置。
 */
object RecorderFolders {

    /** 厂商（小写）→ 候选目录，按优先级排列 */
    private val byManufacturer: List<Pair<Regex, List<String>>> = listOf(
        Regex("xiaomi|redmi|poco|blackshark") to listOf(
            "MIUI/sound_recorder", "sound_recorder",
        ),
        Regex("huawei|honor") to listOf(
            "Sounds", "Recordings",
        ),
        Regex("oppo|realme|oneplus") to listOf(
            "Recordings", "Music/Recordings",
        ),
        Regex("vivo|iqoo") to listOf(
            "Recordings", "录音",
        ),
        Regex("samsung") to listOf(
            "Recordings", "Voice Recorder",
        ),
        Regex("meizu") to listOf(
            "Recorder", "Recordings",
        ),
        Regex("lenovo|motorola|nubia|zte") to listOf(
            "Recordings",
        ),
    )

    /** 未识别品牌时的通用候选 */
    private val generic = listOf("Recordings", "Sounds", "MIUI/sound_recorder", "sound_recorder", "录音")

    /** 本机第一个实际存在的录音目录（相对路径），无则 null */
    fun findExisting(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val candidates =
            (byManufacturer.firstOrNull { it.first.containsMatchIn(manufacturer) }?.second
                ?: emptyList()) + generic
        val root = Environment.getExternalStorageDirectory()
        return candidates.distinct().firstOrNull { File(root, it).isDirectory }
    }

    /** 系统文件选择器初始目录的 document Uri（无匹配目录时 null） */
    fun initialUri(): Uri? {
        val dir = findExisting() ?: return null
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$dir",
        )
    }
}

/**
 * 与 OpenDocument 相同，但自动把选择器初始位置定位到本机系统录音机文件夹。
 */
class OpenDocumentInRecorderFolder : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            RecorderFolders.initialUri()?.let {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, it)
            }
        }
}
