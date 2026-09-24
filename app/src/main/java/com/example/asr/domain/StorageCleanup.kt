package com.example.asr.domain

/**
 * 存储管理纯逻辑（对应小程序 tutor.ts listCleanupCandidates / audio.ts storageUsage、
 * cleanupOrphanAudioFiles）：文件系统访问在 data 层，规则集中在这里便于单测。
 */
object StorageCleanup {

    const val LOCAL_LIMIT_MB = 200

    /** chunk_ 转写切块临时文件：只清理 10 分钟前的残留，避免误删进行中的切块 */
    const val CHUNK_GRACE_MS = 10L * 60 * 1000

    /** 占用展示文案（与小程序 backup 页一致：录音 XMB · 照片 YMB（本地上限 200MB）） */
    fun storageText(audioBytes: Long, photoBytes: Long): String {
        val audioMb = Math.round(audioBytes / 1048576.0)
        val photoMb = Math.round(photoBytes / 1048576.0)
        return "录音 ${audioMb}MB · 照片 ${photoMb}MB（本地上限 ${LOCAL_LIMIT_MB}MB）"
    }

    /** 字节 → MB 展示（保留 1 位小数，与小程序 cleanup sizeText 一致） */
    fun mbText(bytes: Long): String =
        String.format(java.util.Locale.US, "%.1fMB", bytes / 1048576.0)

    /**
     * 是否孤儿音频候选：未被任何记录引用、且文件名是 rec_/import_/chunk_ 前缀
     *（历史 bug 遗留的重复拷贝、取消导入的残留等）；chunk_ 需超过宽限期。
     */
    fun isOrphanCandidate(
        fileName: String,
        referencedNames: Set<String>,
        lastModifiedMs: Long,
        nowMs: Long,
    ): Boolean {
        if (!fileName.startsWith("rec_") && !fileName.startsWith("import_") &&
            !fileName.startsWith("chunk_")
        ) return false
        if (fileName in referencedNames) return false
        if (fileName.startsWith("chunk_") && nowMs - lastModifiedMs < CHUNK_GRACE_MS) return false
        return true
    }

    /** 清理弹窗合计文案（与小程序 refreshCleanupTotal 一致） */
    fun cleanupTotalText(selectedCount: Int, totalBytes: Long): String =
        "已选 ${selectedCount} 项，共 ${mbText(totalBytes)}"
}
