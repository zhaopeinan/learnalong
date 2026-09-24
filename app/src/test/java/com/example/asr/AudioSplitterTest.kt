package com.example.asr

import com.example.asr.audio.AudioSplitter
import com.example.asr.audio.AudioSplitter.AudioFmt
import com.example.asr.audio.AudioSplitter.PlanOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/** AudioSplitter 纯函数（sniff / plan / materializeChunkBytes）单测，不涉及 MediaMuxer */
class AudioSplitterTest {

    /** 内存版 RangeReader，行为与 FileRangeReader 一致（边界裁剪） */
    private class MemReader(private val bytes: ByteArray) : AudioSplitter.RangeReader {
        override val size: Long get() = bytes.size.toLong()
        override fun read(pos: Long, len: Int): ByteArray {
            val p = pos.coerceIn(0, size).toInt()
            val l = len.toLong().coerceIn(0, size - p).toInt()
            return bytes.copyOfRange(p, p + l)
        }
    }

    // ---------- 字节构造工具 ----------

    private fun w32(b: ByteArray, o: Int, v: Long) {
        b[o] = ((v ushr 24) and 255).toByte()
        b[o + 1] = ((v ushr 16) and 255).toByte()
        b[o + 2] = ((v ushr 8) and 255).toByte()
        b[o + 3] = (v and 255).toByte()
    }

    private fun wLe16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v and 255).toByte()
        b[o + 1] = ((v ushr 8) and 255).toByte()
    }

    private fun wLe32(b: ByteArray, o: Int, v: Long) {
        b[o] = (v and 255).toByte()
        b[o + 1] = ((v ushr 8) and 255).toByte()
        b[o + 2] = ((v ushr 16) and 255).toByte()
        b[o + 3] = ((v ushr 24) and 255).toByte()
    }

    private fun wStr(b: ByteArray, o: Int, s: String) {
        for (i in s.indices) b[o + i] = s[i].code.toByte()
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArray(8 + payload.size)
        w32(out, 0, out.size.toLong())
        wStr(out, 4, type)
        payload.copyInto(out, 8)
        return out
    }

    private fun fullBox(type: String, payload: ByteArray): ByteArray =
        box(type, ByteArray(4) + payload)

    /** 合法 PCM wav：44 字节头 + dataSize 个 PCM 字节 */
    private fun buildWav(dataSize: Int, sampleRate: Int = 8000, channels: Int = 1, bits: Int = 16): ByteArray {
        val blockAlign = channels * bits / 8
        val byteRate = sampleRate * blockAlign
        val b = ByteArray(44 + dataSize)
        wStr(b, 0, "RIFF")
        wLe32(b, 4, 36L + dataSize)
        wStr(b, 8, "WAVE")
        wStr(b, 12, "fmt ")
        wLe32(b, 16, 16)
        wLe16(b, 20, 1) // PCM
        wLe16(b, 22, channels)
        wLe32(b, 24, sampleRate.toLong())
        wLe32(b, 28, byteRate.toLong())
        wLe16(b, 32, blockAlign)
        wLe16(b, 34, bits)
        wStr(b, 36, "data")
        wLe32(b, 40, dataSize.toLong())
        return b
    }

    /** MPEG1 Layer3 128kbps 44100 帧，417 字节；payload 首 4 字节可定制（如 "Xing"） */
    private fun mp3Frame(tag: String? = null): ByteArray {
        val f = ByteArray(417)
        f[0] = 0xFF.toByte()
        f[1] = 0xFB.toByte()
        f[2] = 0x90.toByte()
        f[3] = 0x00
        tag?.let { wStr(f, 4, it) }
        return f
    }

    /** ADTS 帧（AAC-LC 44100 stereo），帧长 100 字节（payload 93） */
    private fun adtsFrame(): ByteArray {
        val f = ByteArray(100)
        f[0] = 0xFF.toByte()
        f[1] = 0xF1.toByte()
        f[2] = 0x50 // profile=1(LC) freqIdx=4(44100) chan 高位置 0
        f[3] = 0x80.toByte() // chanCfg=2，frameLength 高位 0
        f[4] = 0x0C // frameLength=100 → (100>>3)=12
        f[5] = 0x9F.toByte() // (100&7)<<5 | 0x1F
        f[6] = 0xFC.toByte()
        return f
    }

    /** 最小可解析的 m4a：ftyp + moov（音频轨采样表），sample 数据区不需要真实存在 */
    private fun buildMp4(sampleCount: Int, sampleSize: Int, timescale: Int = 44100, delta: Int = 1024): ByteArray {
        val ftyp = box("ftyp", "M4A ".toByteArray(Charsets.US_ASCII) + ByteArray(12))

        val mdhdBody = ByteArray(20)
        w32(mdhdBody, 8, timescale.toLong())
        w32(mdhdBody, 12, sampleCount.toLong() * delta)
        val mdhd = fullBox("mdhd", mdhdBody)

        val hdlrBody = ByteArray(8 + 4) // pre_defined(4) + handler_type(4) + 空 name
        wStr(hdlrBody, 4, "soun")
        val hdlr = fullBox("hdlr", hdlrBody)

        // stsd：entry_count=1，entry 类型 mp4a
        val stsdEntry = ByteArray(24)
        w32(stsdEntry, 0, 24)
        wStr(stsdEntry, 4, "mp4a")
        val stsd = fullBox("stsd", ByteArray(4).apply { w32(this, 0, 1) } + stsdEntry)

        val stts = fullBox("stts", ByteArray(12).apply {
            w32(this, 0, 1) // 1 entry
            w32(this, 4, sampleCount.toLong())
            w32(this, 8, delta.toLong())
        })
        val stsc = fullBox("stsc", ByteArray(16).apply {
            w32(this, 0, 1) // 1 entry
            w32(this, 4, 1) // firstChunk
            w32(this, 8, 1) // samplesPerChunk
            w32(this, 12, 1) // descIdx
        })
        val stsz = fullBox("stsz", ByteArray(8 + sampleCount * 4).apply {
            w32(this, 0, 0) // 非均匀
            w32(this, 4, sampleCount.toLong())
            for (i in 0 until sampleCount) w32(this, 8 + i * 4, sampleSize.toLong())
        })
        val stco = fullBox("stco", ByteArray(8).apply {
            w32(this, 0, 1) // 1 chunk
            w32(this, 4, 1024) // 偏移值 plan 阶段不使用
        })

        val stbl = box("stbl", stsd + stts + stsc + stsz + stco)
        val minf = box("minf", stbl)
        val mdia = box("mdia", mdhd + hdlr + minf)
        val trak = box("trak", mdia)
        val moov = box("moov", trak)
        return ftyp + moov
    }

    private fun assertClose(expected: Double, actual: Double, tol: Double = 1e-6) {
        assertTrue("expected $expected but was $actual", kotlin.math.abs(expected - actual) <= tol)
    }

    // ---------- sniff ----------

    @Test
    fun `sniff detects wav mp3 mp4 adts and unknown`() {
        assertEquals(AudioFmt.WAV, AudioSplitter.sniffFormat(buildWav(100)))
        assertEquals(AudioFmt.MP3, AudioSplitter.sniffFormat("ID3\u0004\u0000\u0000\u0000\u0000\u0000\u0000".toByteArray() + mp3Frame()))
        assertEquals(AudioFmt.MP3, AudioSplitter.sniffFormat(mp3Frame()))
        assertEquals(AudioFmt.MP4, AudioSplitter.sniffFormat(buildMp4(10, 100)))
        assertEquals(AudioFmt.AAC_ADTS, AudioSplitter.sniffFormat(adtsFrame()))
        assertEquals(AudioFmt.UNKNOWN, AudioSplitter.sniffFormat(ByteArray(32) { 0x55 }))
    }

    @Test
    fun `sniffReader sees ADTS stream hidden behind ID3 tag`() {
        // ID3v2 头（syncsafe size=4）+ ADTS 帧
        val id3 = ByteArray(14)
        wStr(id3, 0, "ID3")
        id3[9] = 4 // tag 体 4 字节
        val reader = MemReader(id3 + adtsFrame() + adtsFrame() + adtsFrame())
        assertEquals(AudioFmt.AAC_ADTS, AudioSplitter.sniffReader(reader))
    }

    // ---------- 大小边界（真实文件） ----------

    @Test
    fun `file exactly at ASR byte limit needs no split`() {
        val file = File.createTempFile("chunk_test_", ".wav")
        try {
            // 合法 wav 头（声明的 data 大小 = 实际填充大小）+ 填充到恰好 ASR_MAX_BYTES
            val header = buildWav((AudioSplitter.ASR_MAX_BYTES - 44).toInt()).copyOf(44)
            RandomAccessFile(file, "rw").use { raf ->
                raf.write(header)
                raf.setLength(AudioSplitter.ASR_MAX_BYTES)
            }
            val plan = AudioSplitter.planAsrSplit(file)
            assertFalse(plan.needsSplit)
            assertEquals(1, plan.chunks.size)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `file one block over ASR byte limit splits into two chunks`() {
        val file = File.createTempFile("chunk_test_", ".wav")
        try {
            val header = buildWav((AudioSplitter.ASR_MAX_BYTES + 2 - 44).toInt()).copyOf(44)
            RandomAccessFile(file, "rw").use { raf ->
                raf.write(header)
                raf.setLength(AudioSplitter.ASR_MAX_BYTES + 2)
            }
            val plan = AudioSplitter.planAsrSplit(file)
            assertTrue(plan.needsSplit)
            assertEquals(2, plan.chunks.size)
            // 时间偏移累计：第二块从第一块结束处开始
            assertClose(plan.chunks[0].durationSec, plan.chunks[1].offsetSec, 1e-3)
        } finally {
            file.delete()
        }
    }

    // ---------- wav plan ----------

    @Test
    fun `wav plan chunks align to block size and carry cumulative offsets`() {
        // 8000Hz 16bit 单声道：byteRate=16000，blockAlign=2，data 250 字节
        val reader = MemReader(buildWav(250))
        val plan = AudioSplitter.planSplit(reader, options = PlanOptions(targetBytes = 100, targetSec = 3600.0))
        assertEquals(AudioFmt.WAV, plan.kind)
        assertEquals(3, plan.chunks.size)
        assertClose(100.0 / 16000, plan.chunks[0].durationSec)
        assertClose(100.0 / 16000, plan.chunks[1].offsetSec)
        assertClose(50.0 / 16000, plan.chunks[2].durationSec)
        assertClose(250.0 / 16000, plan.chunks.last().let { it.offsetSec + it.durationSec })
    }

    @Test
    fun `wav chunk materialize rewrites header with chunk data size`() {
        val reader = MemReader(buildWav(250))
        val plan = AudioSplitter.planSplit(reader, options = PlanOptions(targetBytes = 100, targetSec = 3600.0))
        val (bytes, ext) = AudioSplitter.materializeChunkBytes(reader, plan, 1)
        assertEquals("wav", ext)
        assertEquals(44 + 100, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        // data 块大小写为切块实际字节数
        val dataSize = (bytes[40].toInt() and 0xFF) or ((bytes[41].toInt() and 0xFF) shl 8) or
            ((bytes[42].toInt() and 0xFF) shl 16) or ((bytes[43].toInt() and 0xFF) shl 24)
        assertEquals(100, dataSize)
    }

    // ---------- mp3 plan ----------

    @Test
    fun `mp3 plan drops Xing metadata frame and chunks by frame boundary`() {
        val frames = listOf(mp3Frame("Xing")) + List(10) { mp3Frame() }
        val reader = MemReader(frames.fold(ByteArray(0)) { acc, f -> acc + f })
        val plan = AudioSplitter.planSplit(reader, options = PlanOptions(targetBytes = 900, targetSec = 3600.0))
        assertEquals(AudioFmt.MP3, plan.kind)
        // 每块 2 帧（2*417=834 ≤ 900，3 帧超限）→ 5 块
        assertEquals(5, plan.chunks.size)
        val frameDur = 1152.0 / 44100
        assertClose(2 * frameDur, plan.chunks[0].durationSec, 1e-4)
        assertClose(2 * frameDur, plan.chunks[1].offsetSec, 1e-4)
        // 首块从第 2 帧开始（Xing 帧被丢弃）
        val (bytes, ext) = AudioSplitter.materializeChunkBytes(reader, plan, 0)
        assertEquals("mp3", ext)
        assertEquals(2 * 417, bytes.size)
        assertEquals(0xFF.toByte(), bytes[0])
    }

    // ---------- adts plan ----------

    @Test
    fun `adts plan chunks by payload bytes with 1024 samples per frame`() {
        val reader = MemReader(ByteArray(0) + List(100) { adtsFrame() }.fold(ByteArray(0)) { acc, f -> acc + f })
        val plan = AudioSplitter.planSplit(reader, options = PlanOptions(targetBytes = 2000, targetSec = 3600.0))
        assertEquals(AudioFmt.AAC_ADTS, plan.kind)
        // 每帧 payload 93 字节：21 帧=1953 ≤ 2000，22 帧=2046 超限 → 21*4+16 = 5 块
        assertEquals(5, plan.chunks.size)
        val frameDur = 1024.0 / 44100
        assertClose(21 * frameDur, plan.chunks[0].durationSec, 1e-4)
        assertClose(21 * frameDur, plan.chunks[1].offsetSec, 1e-4)
        assertClose(16 * frameDur, plan.chunks[4].durationSec, 1e-4)
        assertClose(100 * frameDur, plan.chunks.last().let { it.offsetSec + it.durationSec }, 1e-4)
    }

    // ---------- mp4 plan ----------

    @Test
    fun `mp4 plan splits by sample table with timescale durations`() {
        val reader = MemReader(buildMp4(sampleCount = 100, sampleSize = 200))
        val plan = AudioSplitter.planSplit(reader, options = PlanOptions(targetBytes = 4000, targetSec = 3600.0))
        assertEquals(AudioFmt.MP4, plan.kind)
        // 20 sample = 4000 字节 → 5 块
        assertEquals(5, plan.chunks.size)
        val sampleDur = 1024.0 / 44100
        assertClose(20 * sampleDur, plan.chunks[0].durationSec, 1e-4)
        assertClose(20 * sampleDur, plan.chunks[1].offsetSec, 1e-4)
        assertClose(100 * sampleDur, plan.chunks.last().let { it.offsetSec + it.durationSec }, 1e-4)
    }

    @Test
    fun `mp4 plan respects target duration cap`() {
        val reader = MemReader(buildMp4(sampleCount = 100, sampleSize = 10))
        // 时长上限 10 个 sample 的时长
        val plan = AudioSplitter.planSplit(
            reader,
            options = PlanOptions(targetBytes = Long.MAX_VALUE / 4, targetSec = 10 * 1024.0 / 44100),
        )
        assertEquals(10, plan.chunks.size)
    }

    // ---------- 不支持格式 ----------

    @Test
    fun `unsupported content throws readable error`() {
        val reader = MemReader(ByteArray(4096) { 0x55 })
        try {
            AudioSplitter.planSplit(reader)
            fail("should throw for unsupported content")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不支持"))
        }
    }

    @Test
    fun `unknown content falls back to extension hint`() {
        // 内容嗅探失败（全 0）但扩展名提示 wav：仍因 wav 头不合法而抛错
        val reader = MemReader(ByteArray(4096))
        try {
            AudioSplitter.planSplit(reader, hintExt = "wav")
            fail("should throw")
        } catch (e: IllegalArgumentException) {
            // 预期：wav 解析失败 → 不支持提示
            assertTrue(e.message!!.contains("不支持"))
        }
    }
}
