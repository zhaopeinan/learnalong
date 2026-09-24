package com.example.asr.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.asr.media.PhotoImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 照片采集器：「拍照（可连拍多张）」或「从相册选择（可多选）」。
 * 调用方拿到已复制进私有目录的文件列表（onResult）。
 */
class PhotoCapture internal constructor(
    /** 弹出来源选择（拍照 / 相册） */
    val launch: () -> Unit,
    /** 直接调起拍照（可连拍多张） */
    val launchCamera: () -> Unit,
    /** 直接调起相册多选 */
    val launchGallery: () -> Unit,
)

@Composable
fun rememberPhotoCapture(
    onResult: (List<File>) -> Unit,
    onError: (String) -> Unit,
): PhotoCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    // 连拍已采集的照片
    var captured by remember { mutableStateOf<List<File>>(emptyList()) }
    var showContinueDialog by remember { mutableStateOf(false) }
    var capturedCount by remember { mutableIntStateOf(0) }

    fun importUris(uris: List<Uri>) {
        scope.launch(Dispatchers.IO) {
            try {
                onResult(uris.mapIndexed { i, uri -> PhotoImporter.import(context, uri, i) })
            } catch (e: Exception) {
                onError(e.message ?: "添加照片失败")
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (!uris.isNullOrEmpty()) importUris(uris)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (ok && uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val file = PhotoImporter.import(context, uri, capturedCount)
                    captured = captured + file
                    capturedCount = captured.size
                    showContinueDialog = true // 问是否继续拍
                } catch (e: Exception) {
                    onError(e.message ?: "添加照片失败")
                }
            }
        }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val name = "shot_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val file = File(dir, name)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    fun launchGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // 来源选择弹窗
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("添加错题照片") },
            text = { Text("可以直接拍照（支持连拍多张），或从相册选择已有照片") },
            confirmButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    launchCamera()
                }) { Text("拍照") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourceDialog = false
                    launchGallery()
                }) { Text("从相册选择") }
            },
        )
    }

    // 连拍确认弹窗：每拍完一张问一次
    if (showContinueDialog) {
        AlertDialog(
            onDismissRequest = { /* 强制二选一，点空白不处理 */ },
            title = { Text("已拍 $capturedCount 张") },
            text = { Text("继续拍下一页，还是完成添加？") },
            confirmButton = {
                TextButton(onClick = {
                    showContinueDialog = false
                    launchCamera()
                }) { Text("继续拍") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showContinueDialog = false
                    val done = captured
                    captured = emptyList()
                    capturedCount = 0
                    onResult(done)
                }) { Text("完成") }
            },
        )
    }

    return remember {
        PhotoCapture(
            launch = { showSourceDialog = true },
            launchCamera = { launchCamera() },
            launchGallery = { launchGallery() },
        )
    }
}
