package com.example.asr

import com.example.asr.domain.StorageCleanup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCleanupTest {

    @Test
    fun `storageText 对齐小程序格式（MB 四舍五入 + 200MB 上限提示）`() {
        assertEquals(
            "录音 0MB · 照片 0MB（本地上限 200MB）",
            StorageCleanup.storageText(0, 0),
        )
        assertEquals(
            "录音 2MB · 照片 2MB（本地上限 200MB）",
            StorageCleanup.storageText(2L * 1048576, 1048576L + 600000), // 1.57MB → 2MB
        )
    }

    @Test
    fun `mbText 保留一位小数`() {
        assertEquals("0.5MB", StorageCleanup.mbText(524288))
        assertEquals("12.3MB", StorageCleanup.mbText((12.3 * 1048576).toLong()))
    }

    @Test
    fun `孤儿候选只看 rec_、import_、chunk_ 前缀且未被引用`() {
        val referenced = setOf("rec_1_s1.m4a")
        assertTrue(StorageCleanup.isOrphanCandidate("rec_2_s1.m4a", referenced, 0L, NOW))
        assertTrue(StorageCleanup.isOrphanCandidate("import_20260101_120000.mp3", referenced, 0L, NOW))
        assertFalse(StorageCleanup.isOrphanCandidate("rec_1_s1.m4a", referenced, 0L, NOW))
        assertFalse(StorageCleanup.isOrphanCandidate("voice_sample.m4a", referenced, 0L, NOW))
        assertFalse(StorageCleanup.isOrphanCandidate("random.txt", referenced, 0L, NOW))
    }

    @Test
    fun `chunk_ 切块文件 10 分钟宽限期内不清理`() {
        val referenced = emptySet<String>()
        // 5 分钟前的 chunk：宽限期内，不动
        assertFalse(
            StorageCleanup.isOrphanCandidate(
                "chunk_a.m4a", referenced,
                NOW - 5 * 60 * 1000, NOW,
            )
        )
        // 11 分钟前的 chunk：可清理
        assertTrue(
            StorageCleanup.isOrphanCandidate(
                "chunk_a.m4a", referenced,
                NOW - 11 * 60 * 1000, NOW,
            )
        )
    }

    @Test
    fun `cleanupTotalText 对齐小程序`() {
        assertEquals("已选 2 项，共 3.0MB", StorageCleanup.cleanupTotalText(2, 3L * 1048576))
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
