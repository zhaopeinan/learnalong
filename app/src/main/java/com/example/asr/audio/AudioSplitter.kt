package com.example.asr.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * ASR 上传前的本地音频切块（移植自小程序 miniprogram/src/utils/audioSplit.ts）。
 * 背景：转写接口单文件上限 25MB，长录音/导入音频容易超限。
 * 本地无损切成若干小于上限的有效音频文件，逐块上传后按时间偏移合并：
 *   - wav：解析 RIFF 头后按 PCM 数据字节切（每块重写文件头）
 *   - mp3：扫描帧头，按帧边界切
 *   - m4a/mp4：解析采样表（stts/stsc/stsz/stco）得到切块点，materialize 用
 *     MediaExtractor + MediaMuxer 按 sample 边界重封装（对应小程序的手工 box 重建）
 *   - aac(ADTS)：按帧切并用 MediaMuxer 封装成 m4a（接口不认裸 ADTS 流）
 * sniffFormat / sniffReader / planSplit / materializeChunkBytes 为纯函数，可在 JVM 单测；
 * 涉及 MediaExtractor/MediaMuxer 的部分只在设备上运行。
 */
object AudioSplitter {

    /** 转写接口单文件上限 25MB，留 0.5MB 给 multipart 头 */
    const val ASR_MAX_BYTES: Long = (24.5 * 1024 * 1024).toLong()

    /** 单块目标大小：兼顾切块时的内存占用（需整块读入再写盘） */
    private const val CHUNK_TARGET_BYTES = 20L * 1024 * 1024

    /** 单块时长上限：避免单次识别任务过长导致服务端超时 */
    private const val CHUNK_TARGET_SEC = 30.0 * 60

    /** 切块临时文件前缀（deleteChunkFile 据此防误删） */
    private const val CHUNK_TMP_PREFIX = "chunk_"

    interface RangeReader {
        val size: Long
        fun read(pos: Long, len: Int): ByteArray
    }

    /** 文件版 RangeReader：每次读独立打开，避免句柄生命周期问题 */
    class FileRangeReader(private val file: File) : RangeReader {
        override val size: Long
            get() = if (file.exists()) file.length() else
                throw IllegalArgumentException("录音文件不存在或已被清理，请重新导入或录制")

        override fun read(pos: Long, len: Int): ByteArray {
            val p = pos.coerceIn(0, size)
            val l = len.toLong().coerceIn(0, size - p).toInt()
            if (l <= 0) return ByteArray(0)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(p)
                val buf = ByteArray(l)
                raf.readFully(buf)
                return buf
            }
        }
    }

    enum class AudioFmt { WAV, MP3, AAC_ADTS, MP4, UNKNOWN }

    data class ChunkInfo(val offsetSec: Double, val durationSec: Double)

    data class PlanOptions(
        /** 单块大小上限（默认 20MB） */
        val targetBytes: Long = CHUNK_TARGET_BYTES,
        /** 单块时长上限秒（默认 30 分钟） */
        val targetSec: Double = CHUNK_TARGET_SEC,
    )

    // ---------- 基础字节工具 ----------

    private fun str4(b: ByteArray, o: Int): String {
        if (o < 0 || o + 4 > b.size) return ""
        return String(b, o, 4, Charsets.US_ASCII)
    }

    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) * 0x1000000L + (b[o + 1].toLong() and 0xFF) * 0x10000L +
            (b[o + 2].toLong() and 0xFF) * 0x100L + (b[o + 3].toLong() and 0xFF)

    private fun u64(b: ByteArray, o: Int): Long = u32(b, o) * 0x100000000L + u32(b, o + 4)

    private fun le16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) + (b[o + 1].toInt() and 0xFF) * 0x100

    private fun le32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) + (b[o + 1].toLong() and 0xFF) * 0x100L +
            (b[o + 2].toLong() and 0xFF) * 0x10000L + (b[o + 3].toLong() and 0xFF) * 0x1000000L

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

    // ---------- 格式嗅探 ----------

    fun sniffFormat(head: ByteArray): AudioFmt {
        if (head.size >= 12 && str4(head, 0) == "RIFF" && str4(head, 8) == "WAVE") return AudioFmt.WAV
        if (head.size >= 8 && str4(head, 4) == "ftyp") return AudioFmt.MP4
        if (head.size >= 3 && str4(head, 0).substring(0, 3) == "ID3") return AudioFmt.MP3
        if (head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) {
            val layer = (head[1].toInt() shr 1) and 3
            return if (layer == 0) AudioFmt.AAC_ADTS else AudioFmt.MP3
        }
        return AudioFmt.UNKNOWN
    }

    private fun skipId3v2(head: ByteArray): Int {
        if (head.size < 10 || str4(head, 0).substring(0, 3) != "ID3") return 0
        val size = ((head[6].toInt() and 0x7F) shl 21) or ((head[7].toInt() and 0x7F) shl 14) or
            ((head[8].toInt() and 0x7F) shl 7) or (head[9].toInt() and 0x7F)
        return 10 + size + (if (head[5].toInt() and 0x10 != 0) 10 else 0)
    }

    /** 带 ID3v2 跳过的嗅探：ID3 开头的可能是 mp3，也可能是带标签的 ADTS 流 */
    fun sniffReader(reader: RangeReader): AudioFmt {
        val head = reader.read(0, minOf(64L, reader.size).toInt())
        val fmt = sniffFormat(head)
        if (fmt != AudioFmt.MP3 || str4(head, 0).substring(0, 3) != "ID3") return fmt
        val skip = skipId3v2(reader.read(0, minOf(4096L, reader.size).toInt()))
        if (skip <= 0 || skip >= reader.size - 4) return fmt
        val after = sniffFormat(reader.read(skip.toLong(), minOf(64L, reader.size - skip).toInt()))
        return if (after == AudioFmt.UNKNOWN) fmt else after
    }

    // ---------- 帧扫描（mp3 / ADTS 通用滑动窗口） ----------

    class FrameHeader(
        val frameLength: Int,
        val sampleRate: Int,
        val samplesPerFrame: Int,
        val headerLen: Int,
    )

    class FrameScan(
        val offsets: MutableList<Long>,
        val lengths: MutableList<Int>,
        val headerLens: MutableList<Int>,
        var sampleRate: Int,
        var samplesPerFrame: Int,
    )

    private fun scanFrames(
        reader: RangeReader,
        startPos: Long,
        parseHeader: (ByteArray) -> FrameHeader?,
    ): FrameScan? {
        val size = reader.size
        val win = 1024 * 1024
        var winStart = -1L
        var winBytes = ByteArray(0)
        fun headerAt(pos: Long): FrameHeader? {
            if (pos + 4 > size) return null
            if (winStart < 0 || pos < winStart || pos + 16 > winStart + winBytes.size) {
                val from = pos
                val len = minOf(maxOf(win.toLong(), 16), size - from).toInt()
                winBytes = reader.read(from, len)
                winStart = from
            }
            val off = (pos - winStart).toInt()
            return parseHeader(winBytes.copyOfRange(off, minOf(off + 16, winBytes.size)))
        }
        val scan = FrameScan(mutableListOf(), mutableListOf(), mutableListOf(), 0, 0)
        var covered = 0L
        var pos = startPos
        var misses = 0
        while (pos + 4 <= size) {
            val h = headerAt(pos)
            if (h != null && h.frameLength >= h.headerLen && pos + h.frameLength <= size) {
                if (scan.offsets.isEmpty()) {
                    scan.sampleRate = h.sampleRate
                    scan.samplesPerFrame = h.samplesPerFrame
                }
                scan.offsets.add(pos)
                scan.lengths.add(h.frameLength)
                scan.headerLens.add(h.headerLen)
                covered += h.frameLength
                pos += h.frameLength
                misses = 0
            } else {
                pos++
                misses++
                // 连续大段无效字节说明文件已损坏或格式不对，避免全文件逐字节扫描
                if (misses > 65536) break
            }
        }
        val span = size - startPos
        if (scan.offsets.size < 3 || span <= 0 || covered < span * 0.6) return null
        return scan
    }

    /** 按大小与时长把帧序列分组为块，返回字节区间与时间信息 */
    private fun planFrameChunks(
        scan: FrameScan,
        targetBytes: Long,
        targetSec: Double,
    ): Pair<List<LongRange>, List<ChunkInfo>> {
        val frameDur = scan.samplesPerFrame.toDouble() / scan.sampleRate
        val ranges = mutableListOf<LongRange>()
        val chunks = mutableListOf<ChunkInfo>()
        var i = 0
        var cumSec = 0.0
        while (i < scan.offsets.size) {
            var bytes = 0L
            var j = i
            while (
                j < scan.offsets.size &&
                (j == i || bytes + scan.lengths[j] <= targetBytes) &&
                (j - i) * frameDur < targetSec
            ) {
                bytes += scan.lengths[j]
                j++
            }
            ranges.add(scan.offsets[i]..(scan.offsets[j - 1] + scan.lengths[j - 1] - 1))
            val dur = (j - i) * frameDur
            chunks.add(ChunkInfo(cumSec, dur))
            cumSec += dur
            i = j
        }
        return ranges to chunks
    }

    // ---------- mp3 ----------

    private val MP3_BITRATES_V1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val MP3_BITRATES_V2L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val MP3_SAMPLE_RATES = arrayOf(
        intArrayOf(11025, 12000, 8000), // MPEG 2.5
        intArrayOf(0, 0, 0),
        intArrayOf(22050, 24000, 16000), // MPEG 2
        intArrayOf(44100, 48000, 32000), // MPEG 1
    )

    private fun parseMp3Header(b: ByteArray): FrameHeader? {
        if (b.size < 4) return null
        if (b[0] != 0xFF.toByte() || (b[1].toInt() and 0xE0) != 0xE0) return null
        val version = (b[1].toInt() shr 3) and 3 // 0=2.5 2=2 3=1
        val layer = (b[1].toInt() shr 1) and 3
        if (version == 1 || layer != 1) return null // 只处理 Layer III
        val bitrateIdx = (b[2].toInt() shr 4) and 15
        if (bitrateIdx == 0 || bitrateIdx == 15) return null
        val srIdx = (b[2].toInt() shr 2) and 3
        if (srIdx == 3) return null
        val bitrate = (if (version == 3) MP3_BITRATES_V1L3 else MP3_BITRATES_V2L3)[bitrateIdx] * 1000
        val sampleRate = MP3_SAMPLE_RATES[version][srIdx]
        val padding = (b[2].toInt() shr 1) and 1
        val samplesPerFrame = if (version == 3) 1152 else 576
        val frameLength = ((if (version == 3) 144 else 72) * bitrate) / sampleRate + padding
        return FrameHeader(frameLength, sampleRate, samplesPerFrame, 4)
    }

    private fun planMp3(reader: RangeReader, opts: PlanOptions): SplitPlan? {
        val head = reader.read(0, minOf(4096L, reader.size).toInt())
        val scan = scanFrames(reader, skipId3v2(head).toLong(), ::parseMp3Header) ?: return null
        // 编码器常把 Xing/Info 全局元信息写在首帧（含整个源文件的总时长），
        // 切块时必须丢弃该帧，否则块 0 会带上错误的总时长元数据
        if (scan.offsets.size > 1) {
            val firstBytes = reader.read(scan.offsets[0], minOf(scan.lengths[0], 512))
            var isMeta = false
            var i = 0
            while (i + 4 <= firstBytes.size) {
                val tag = str4(firstBytes, i)
                if (tag == "Xing" || tag == "Info") { isMeta = true; break }
                i++
            }
            if (isMeta) {
                scan.offsets.removeAt(0)
                scan.lengths.removeAt(0)
                scan.headerLens.removeAt(0)
            }
        }
        val (ranges, chunks) = planFrameChunks(scan, opts.targetBytes, opts.targetSec)
        return SplitPlan(AudioFmt.MP3, chunks, ranges = ranges)
    }

    // ---------- wav ----------

    class WavFmt(
        val format: Int,
        val channels: Int,
        val sampleRate: Int,
        val byteRate: Int,
        val blockAlign: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSize: Long,
    )

    private fun parseWav(reader: RangeReader): WavFmt? {
        if (reader.size < 44) return null
        val head = reader.read(0, 12)
        if (str4(head, 0) != "RIFF" || str4(head, 8) != "WAVE") return null
        var pos = 12L
        var format = 0
        var channels = 0
        var sampleRate = 0
        var byteRate = 0
        var blockAlign = 0
        var bitsPerSample = 0
        var fmtFound = false
        while (pos + 8 <= reader.size) {
            val h = reader.read(pos, 8)
            val id = str4(h, 0)
            val size = le32(h, 4)
            if (id == "fmt ") {
                val f = reader.read(pos + 8, minOf(size, 40L).toInt())
                format = le16(f, 0)
                channels = le16(f, 2)
                sampleRate = le32(f, 4).toInt()
                byteRate = le32(f, 8).toInt()
                blockAlign = le16(f, 12)
                bitsPerSample = le16(f, 14)
                // WAVE_FORMAT_EXTENSIBLE：真实编码在 SubFormat 前两字节
                if (format == 0xFFFE && f.size >= 26) format = le16(f, 24)
                fmtFound = true
            } else if (id == "data") {
                if (!fmtFound) return null
                val dataOffset = pos + 8
                var dataSize = size
                if (dataSize == 0xFFFFFFFFL || dataOffset + dataSize > reader.size) {
                    dataSize = reader.size - dataOffset
                }
                if (format != 1 && format != 3) return null // 只支持 PCM / float
                if (byteRate <= 0 || blockAlign <= 0) return null
                return WavFmt(format, channels, sampleRate, byteRate, blockAlign, bitsPerSample, dataOffset, dataSize)
            }
            pos += 8 + size + (size % 2)
        }
        return null
    }

    private fun buildWavHeader(fmt: WavFmt, dataSize: Int): ByteArray {
        val b = ByteArray(44)
        wStr(b, 0, "RIFF")
        wLe32(b, 4, 36L + dataSize)
        wStr(b, 8, "WAVE")
        wStr(b, 12, "fmt ")
        wLe32(b, 16, 16)
        wLe16(b, 20, fmt.format)
        wLe16(b, 22, fmt.channels)
        wLe32(b, 24, fmt.sampleRate.toLong())
        wLe32(b, 28, fmt.byteRate.toLong())
        wLe16(b, 32, fmt.blockAlign)
        wLe16(b, 34, fmt.bitsPerSample)
        wStr(b, 36, "data")
        wLe32(b, 40, dataSize.toLong())
        return b
    }

    private fun planWav(reader: RangeReader, opts: PlanOptions): SplitPlan? {
        val fmt = parseWav(reader) ?: return null
        val maxByBytes = opts.targetBytes / fmt.blockAlign * fmt.blockAlign
        val maxBySec = (opts.targetSec * fmt.byteRate).toLong() / fmt.blockAlign * fmt.blockAlign
        val perChunk = maxOf(fmt.blockAlign.toLong(), minOf(maxByBytes, maxBySec))
        val ranges = mutableListOf<LongRange>()
        val chunks = mutableListOf<ChunkInfo>()
        var off = 0L
        var cumSec = 0.0
        while (off < fmt.dataSize) {
            val len = minOf(perChunk, fmt.dataSize - off)
            ranges.add((fmt.dataOffset + off)..(fmt.dataOffset + off + len - 1))
            val dur = len.toDouble() / fmt.byteRate
            chunks.add(ChunkInfo(cumSec, dur))
            cumSec += dur
            off += len
        }
        return SplitPlan(AudioFmt.WAV, chunks, ranges = ranges, wavFmt = fmt)
    }

    // ---------- m4a / mp4 ----------

    private class Mp4Box(val type: String, val start: Long, val size: Long, val headerSize: Int)

    private fun childBoxes(buf: ByteArray, start: Int, end: Int): List<Mp4Box> {
        val out = mutableListOf<Mp4Box>()
        var pos = start
        while (pos + 8 <= end) {
            var size = u32(buf, pos)
            val type = str4(buf, pos + 4)
            var headerSize = 8
            if (size == 1L) {
                size = u64(buf, pos + 8)
                headerSize = 16
            } else if (size == 0L) {
                size = (end - pos).toLong()
            }
            if (size < headerSize || pos + size > end) break
            out.add(Mp4Box(type, pos.toLong(), size, headerSize))
            pos += size.toInt()
        }
        return out
    }

    private fun topLevelBoxes(reader: RangeReader): List<Mp4Box> {
        val out = mutableListOf<Mp4Box>()
        var pos = 0L
        while (pos + 8 <= reader.size) {
            val h = reader.read(pos, minOf(16L, reader.size - pos).toInt())
            var size = u32(h, 0)
            val type = str4(h, 4)
            var headerSize = 8
            if (size == 1L && h.size >= 16) {
                size = u64(h, 8)
                headerSize = 16
            } else if (size == 0L) {
                size = reader.size - pos
            }
            if (size < headerSize || pos + size > reader.size) break
            out.add(Mp4Box(type, pos, size, headerSize))
            pos += size
        }
        return out
    }

    private class TableEntry(val count: Long, val delta: Long)
    private class StscEntry(val firstChunk: Long, val samplesPerChunk: Long, val descIdx: Long)

    private class Mp4Info(
        val mediaTimescale: Long,
        val stts: List<TableEntry>,
        val stsc: List<StscEntry>,
        val stszUniform: Long,
        val stszList: List<Long>?,
        val chunkOffsets: List<Long>,
        val totalSamples: Int,
    )

    class SampleMap(
        val sizes: LongArray,
        val cumBytes: LongArray, // 长度 n+1
        val cumDur: LongArray,   // 长度 n+1，单位 mediaTimescale
    )

    private fun findBox(buf: ByteArray, start: Int, end: Int, type: String): Mp4Box? =
        childBoxes(buf, start, end).firstOrNull { it.type == type }

    private fun boxBytes(buf: ByteArray, b: Mp4Box): ByteArray =
        buf.copyOfRange(b.start.toInt(), (b.start + b.size).toInt())

    private fun parseStts(b: ByteArray): List<TableEntry> {
        val n = u32(b, 12).toInt()
        return (0 until n).map { TableEntry(u32(b, 16 + it * 8), u32(b, 20 + it * 8)) }
    }

    private fun parseStsc(b: ByteArray): List<StscEntry> {
        val n = u32(b, 12).toInt()
        return (0 until n).map {
            StscEntry(u32(b, 16 + it * 12), u32(b, 20 + it * 12), u32(b, 24 + it * 12))
        }
    }

    /** 从 mvhd/mdhd 原始字节读 timescale（v0/v1 均处理） */
    private fun boxTimescale(raw: ByteArray): Long {
        if (raw.size < 24) return 0
        return if (raw[8].toInt() == 0) u32(raw, 20) else u32(raw, 28)
    }

    /** 解析 m4a/mp4：定位音频轨并取出切块所需的采样表 */
    private fun parseMp4(reader: RangeReader): Mp4Info? {
        val tops = topLevelBoxes(reader)
        val moovTop = tops.firstOrNull { it.type == "moov" } ?: return null
        val moov = reader.read(moovTop.start, moovTop.size.toInt())
        val traks = childBoxes(moov, 8, moov.size).filter { it.type == "trak" }
        for (trak in traks) {
            val trakEnd = (trak.start + trak.size).toInt()
            val mdiaBox = findBox(moov, trak.start.toInt() + 8, trakEnd, "mdia") ?: continue
            val mdiaEnd = (mdiaBox.start + mdiaBox.size).toInt()
            val mdiaStart = mdiaBox.start.toInt() + 8
            val mdhdBox = findBox(moov, mdiaStart, mdiaEnd, "mdhd") ?: continue
            val hdlrBox = findBox(moov, mdiaStart, mdiaEnd, "hdlr") ?: continue
            val minfBox = findBox(moov, mdiaStart, mdiaEnd, "minf") ?: continue
            // hdlr payload：version/flags(4) + pre_defined(4) + handler_type(4)
            if (str4(moov, hdlrBox.start.toInt() + 8 + 8) != "soun") continue
            val minfEnd = (minfBox.start + minfBox.size).toInt()
            val stblBox = findBox(moov, minfBox.start.toInt() + 8, minfEnd, "stbl") ?: continue
            val stblEnd = (stblBox.start + stblBox.size).toInt()
            val stblStart = stblBox.start.toInt() + 8
            val stsdBox = findBox(moov, stblStart, stblEnd, "stsd") ?: continue
            val sttsBox = findBox(moov, stblStart, stblEnd, "stts") ?: continue
            val stscBox = findBox(moov, stblStart, stblEnd, "stsc") ?: continue
            val stszBox = findBox(moov, stblStart, stblEnd, "stsz") ?: continue
            val stcoBox = findBox(moov, stblStart, stblEnd, "stco")
            val co64Box = if (stcoBox == null) findBox(moov, stblStart, stblEnd, "co64") else null
            if (stcoBox == null && co64Box == null) continue
            val stsd = boxBytes(moov, stsdBox)
            // stsd 内第一个采样 entry 的类型必须是 mp4a（AAC）；alac/enca 等不支持
            if (stsd.size < 24 || str4(stsd, 20) != "mp4a") return null
            val stsz = boxBytes(moov, stszBox)
            val stszUniform = u32(stsz, 12)
            val totalSamples = u32(stsz, 16).toInt()
            if (totalSamples == 0) return null
            var stszList: List<Long>? = null
            if (stszUniform == 0L) {
                stszList = (0 until totalSamples).map { u32(stsz, 20 + it * 4) }
            }
            val chunkOffsets = mutableListOf<Long>()
            if (stcoBox != null) {
                val stco = boxBytes(moov, stcoBox)
                val n = u32(stco, 12).toInt()
                for (i in 0 until n) chunkOffsets.add(u32(stco, 16 + i * 4))
            } else if (co64Box != null) {
                val co64 = boxBytes(moov, co64Box)
                val n = u32(co64, 12).toInt()
                for (i in 0 until n) chunkOffsets.add(u64(co64, 16 + i * 8))
            }
            return Mp4Info(
                mediaTimescale = boxTimescale(boxBytes(moov, mdhdBox)),
                stts = parseStts(boxBytes(moov, sttsBox)),
                stsc = parseStsc(boxBytes(moov, stscBox)),
                stszUniform = stszUniform,
                stszList = stszList,
                chunkOffsets = chunkOffsets,
                totalSamples = totalSamples,
            )
        }
        return null
    }

    private fun buildSampleMap(info: Mp4Info): SampleMap {
        val n = info.totalSamples
        val sizes = LongArray(n) { i -> if (info.stszUniform > 0) info.stszUniform else info.stszList?.get(i) ?: 0 }
        val cumBytes = LongArray(n + 1)
        for (i in 0 until n) cumBytes[i + 1] = cumBytes[i] + sizes[i]
        val cumDur = LongArray(n + 1)
        var idx = 0
        var lastDelta = 1024L
        for (e in info.stts) {
            lastDelta = e.delta
            var k = 0L
            while (k < e.count && idx < n) {
                cumDur[idx + 1] = cumDur[idx] + e.delta
                idx++
                k++
            }
        }
        while (idx < n) {
            cumDur[idx + 1] = cumDur[idx] + lastDelta
            idx++
        }
        return SampleMap(sizes, cumBytes, cumDur)
    }

    /** 按大小与时长把 sample 序列分组为块，返回 [sampleStart, sampleEndExclusive] 区间 */
    private fun planSampleChunks(
        map: SampleMap,
        timescale: Long,
        targetBytes: Long,
        targetSec: Double,
    ): Pair<List<IntRange>, List<ChunkInfo>> {
        val n = map.sizes.size
        val ts = if (timescale > 0) timescale else 1
        val sampleRanges = mutableListOf<IntRange>()
        val chunks = mutableListOf<ChunkInfo>()
        var i = 0
        while (i < n) {
            var j = i + 1
            while (
                j < n &&
                map.cumBytes[j + 1] - map.cumBytes[i] <= targetBytes &&
                (map.cumDur[j + 1] - map.cumDur[i]).toDouble() / ts <= targetSec
            ) {
                j++
            }
            sampleRanges.add(i..j)
            chunks.add(
                ChunkInfo(
                    map.cumDur[i].toDouble() / ts,
                    (map.cumDur[j] - map.cumDur[i]).toDouble() / ts,
                )
            )
            i = j
        }
        return sampleRanges to chunks
    }

    private fun planMp4(reader: RangeReader, opts: PlanOptions): SplitPlan? {
        val info = parseMp4(reader) ?: return null
        if (info.mediaTimescale <= 0) return null
        val map = buildSampleMap(info)
        val (sampleRanges, chunks) = planSampleChunks(map, info.mediaTimescale, opts.targetBytes, opts.targetSec)
        return SplitPlan(AudioFmt.MP4, chunks, sampleRanges = sampleRanges, sampleMap = map, mediaTimescale = info.mediaTimescale)
    }

    // ---------- AAC ADTS → m4a ----------

    private val ADTS_SAMPLE_RATES = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350, 0, 0, 0,
    )

    private fun parseAdtsHeader(b: ByteArray): FrameHeader? {
        if (b.size < 7) return null
        if (b[0] != 0xFF.toByte() || (b[1].toInt() and 0xF6) != 0xF0) return null
        val protectionAbsent = b[1].toInt() and 1
        val freqIdx = (b[2].toInt() shr 2) and 15
        val sampleRate = ADTS_SAMPLE_RATES[freqIdx]
        if (sampleRate == 0) return null
        val headerLen = if (protectionAbsent != 0) 7 else 9
        val frameLength = ((b[3].toInt() and 3) shl 11) or ((b[4].toInt() and 0xFF) shl 3) or
            ((b[5].toInt() shr 5) and 7)
        if (frameLength < headerLen) return null
        return FrameHeader(frameLength, sampleRate, 1024, headerLen)
    }

    private fun planAdts(reader: RangeReader, opts: PlanOptions): SplitPlan? {
        val scan = scanFrames(reader, 0, ::parseAdtsHeader) ?: return null
        // 从首帧取声道与编码参数，合成 AudioSpecificConfig
        val first = reader.read(scan.offsets[0], 16)
        val profile = (first[2].toInt() shr 6) and 3
        val freqIdx = (first[2].toInt() shr 2) and 15
        val chanCfg = ((first[2].toInt() and 1) shl 2) or ((first[3].toInt() shr 6) and 3)
        if (chanCfg == 0) return null // PCE 声道配置不支持
        val objectType = profile + 1
        val asc = byteArrayOf(
            ((objectType shl 3) or (freqIdx shr 1)).toByte(),
            (((freqIdx and 1) shl 7) or (chanCfg shl 3)).toByte(),
        )

        val n = scan.offsets.size
        // 每帧视为一个 sample：payload 大小 = 帧长 - 头长，时长 1024 个采样
        val sizes = LongArray(n) { (scan.lengths[it] - scan.headerLens[it]).toLong() }
        val cumBytes = LongArray(n + 1)
        val cumDur = LongArray(n + 1)
        for (i in 0 until n) {
            cumBytes[i + 1] = cumBytes[i] + sizes[i]
            cumDur[i + 1] = cumDur[i] + 1024
        }
        val map = SampleMap(sizes, cumBytes, cumDur)
        val (sampleRanges, chunks) = planSampleChunks(map, scan.sampleRate.toLong(), opts.targetBytes, opts.targetSec)
        return SplitPlan(
            AudioFmt.AAC_ADTS, chunks,
            sampleRanges = sampleRanges, sampleMap = map,
            adts = AdtsInfo(scan.offsets, scan.headerLens, scan.sampleRate, chanCfg, asc),
        )
    }

    class AdtsInfo(
        val frameOffsets: List<Long>,
        val headerLens: List<Int>,
        val sampleRate: Int,
        val channels: Int,
        val asc: ByteArray,
    )

    // ---------- 统一入口 ----------

    class SplitPlan(
        val kind: AudioFmt,
        val chunks: List<ChunkInfo>,
        internal val ranges: List<LongRange>? = null,       // wav / mp3 字节区间
        internal val wavFmt: WavFmt? = null,
        internal val sampleRanges: List<IntRange>? = null,  // mp4 / adts sample 区间 [a, bExclusive]
        internal val sampleMap: SampleMap? = null,
        internal val mediaTimescale: Long = 0,
        internal val adts: AdtsInfo? = null,
    )

    private const val UNSUPPORTED_MSG =
        "该音频格式暂不支持自动切块（支持 mp3、m4a、wav、aac，且 m4a 需为 AAC 编码），请分段或压缩到 25MB 以内后重新导入"

    /**
     * 对超过 ASR_MAX_BYTES 的音频规划切块（纯函数，可在 JVM 单测）。
     * hintExt：文件扩展名（小写，不含点），嗅探失败时按扩展名再试一次。
     */
    fun planSplit(reader: RangeReader, hintExt: String = "", options: PlanOptions = PlanOptions()): SplitPlan {
        var fmt = sniffReader(reader)
        if (fmt == AudioFmt.UNKNOWN) {
            fmt = when (hintExt) {
                "m4a", "mp4", "mov" -> AudioFmt.MP4
                "mp3" -> AudioFmt.MP3
                "wav" -> AudioFmt.WAV
                "aac" -> AudioFmt.AAC_ADTS
                else -> AudioFmt.UNKNOWN
            }
        }
        val plan = when (fmt) {
            AudioFmt.WAV -> planWav(reader, options)
            AudioFmt.MP3 -> planMp3(reader, options)
            AudioFmt.AAC_ADTS -> planAdts(reader, options)
            AudioFmt.MP4 -> planMp4(reader, options)
            AudioFmt.UNKNOWN -> null
        }
        if (plan == null || plan.chunks.isEmpty()) throw IllegalArgumentException(UNSUPPORTED_MSG)
        return plan
    }

    /** 生成第 index 块的文件字节（纯函数，仅 wav/mp3 走这条路；mp4/adts 用 MediaMuxer） */
    fun materializeChunkBytes(reader: RangeReader, plan: SplitPlan, index: Int): Pair<ByteArray, String> {
        val range = plan.ranges?.getOrNull(index) ?: throw IllegalArgumentException("切块索引越界")
        val data = reader.read(range.first, (range.last - range.first + 1).toInt())
        if (plan.kind == AudioFmt.MP3) return data to "mp3"
        val fmt = plan.wavFmt ?: throw IllegalStateException("wav 参数缺失")
        return (buildWavHeader(fmt, data.size) + data) to "wav"
    }

    // ---------- 设备侧：切块计划与临时文件 ----------

    /** 内容格式对应的标准扩展名 */
    private val CONTENT_EXT = mapOf(AudioFmt.MP4 to ".m4a", AudioFmt.MP3 to ".mp3", AudioFmt.WAV to ".wav")

    class AsrSplitPlan(
        val needsSplit: Boolean,
        val chunks: List<ChunkInfo>,
        private val materializer: (Int) -> File,
    ) {
        /** 生成第 index 块：无需切块时返回原文件，否则写临时文件并返回 */
        fun materialize(index: Int): File = materializer(index)
    }

    /**
     * 规划某个音频文件的转写切块。文件不超过 ASR 上限时 needsSplit=false，直接用原文件；
     * 例外：ADTS 裸流（.aac）服务端不认，无论大小都先封装成 m4a 再上传；
     * 扩展名与内容不符的（如 .aac 实为 m4a）：复制为正确扩展名再上传。
     * 临时块写到 recordings 目录（chunk_ 前缀），上传完用 deleteChunkFile 清理。
     */
    fun planAsrSplit(file: File): AsrSplitPlan {
        val reader = FileRangeReader(file)
        val fmt = sniffReader(reader)
        val actualExt = file.extension.let { if (it.isEmpty()) "" else ".${it.lowercase()}" }
        val expectExt = CONTENT_EXT[fmt]
        val dir = file.parentFile ?: file.absoluteFile.parentFile!!
        if (reader.size <= ASR_MAX_BYTES && fmt != AudioFmt.AAC_ADTS && expectExt != null && expectExt != actualExt) {
            // 扩展名与内容不符：复制一个正确扩展名的临时文件上传
            return AsrSplitPlan(true, listOf(ChunkInfo(0.0, 0.0))) {
                val out = File(dir, "${CHUNK_TMP_PREFIX}${System.currentTimeMillis()}_0$expectExt")
                file.copyTo(out, overwrite = true)
                out
            }
        }
        if (reader.size <= ASR_MAX_BYTES && fmt != AudioFmt.AAC_ADTS) {
            return AsrSplitPlan(false, listOf(ChunkInfo(0.0, 0.0))) { file }
        }
        val core = planSplit(reader, file.extension.lowercase())
        return AsrSplitPlan(true, core.chunks) { index ->
            val ext = when (core.kind) {
                AudioFmt.WAV, AudioFmt.MP3 -> {
                    val (bytes, e) = materializeChunkBytes(reader, core, index)
                    val out = File(dir, "${CHUNK_TMP_PREFIX}${System.currentTimeMillis()}_$index.$e")
                    out.writeBytes(bytes)
                    return@AsrSplitPlan out
                }
                AudioFmt.MP4 -> {
                    remuxMp4Chunk(file, core, index, File(dir, "${CHUNK_TMP_PREFIX}${System.currentTimeMillis()}_$index.m4a"))
                }
                AudioFmt.AAC_ADTS -> {
                    muxAdtsChunk(reader, core, index, File(dir, "${CHUNK_TMP_PREFIX}${System.currentTimeMillis()}_$index.m4a"))
                }
                AudioFmt.UNKNOWN -> throw IllegalArgumentException(UNSUPPORTED_MSG)
            }
            ext
        }
    }

    /** 删除切块临时文件（带前缀守卫，避免误删录音） */
    fun deleteChunkFile(file: File) {
        if (!file.name.startsWith(CHUNK_TMP_PREFIX)) return
        try { file.delete() } catch (_: Exception) { /* 已不存在等情况忽略 */ }
    }

    /** m4a 切块：MediaExtractor 定位 sample 边界，MediaMuxer 无损重封装 */
    private fun remuxMp4Chunk(src: File, plan: SplitPlan, index: Int, out: File): File {
        val range = plan.sampleRanges?.getOrNull(index) ?: throw IllegalArgumentException("切块索引越界")
        val map = plan.sampleMap ?: throw IllegalStateException("mp4 采样表缺失")
        val timescale = plan.mediaTimescale.takeIf { it > 0 } ?: 1
        val sampleCount = range.last - range.first
        if (sampleCount <= 0) throw IllegalArgumentException("切块内容为空")
        // 块起始时间（µs）：muxer 输出从 0 起排，需要减掉
        val startUs = map.cumDur[range.first] * 1_000_000L / timescale

        val extractor = MediaExtractor()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            extractor.setDataSource(src.absolutePath)
            var trackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIdx = i
                    format = f
                    break
                }
            }
            if (trackIdx < 0 || format == null) throw IllegalArgumentException("找不到音频轨")
            extractor.selectTrack(trackIdx)
            val muxTrack = muxer.addTrack(format)
            muxer.start()
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val buffer = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            var written = 0
            while (written < sampleCount) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime - startUs
                info.flags = 0
                muxer.writeSampleData(muxTrack, buffer, info)
                extractor.advance()
                written++
            }
            if (written == 0) throw IllegalArgumentException("切块内容为空")
            muxer.stop()
            return out
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            try { muxer.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    /** ADTS 切块：剥掉帧头，把裸 AAC 帧用 MediaMuxer 封装成 m4a */
    private fun muxAdtsChunk(reader: RangeReader, plan: SplitPlan, index: Int, out: File): File {
        val range = plan.sampleRanges?.getOrNull(index) ?: throw IllegalArgumentException("切块索引越界")
        val adts = plan.adts ?: throw IllegalStateException("adts 参数缺失")
        val sampleCount = range.last - range.first
        if (sampleCount <= 0) throw IllegalArgumentException("切块内容为空")
        val startUs = range.first.toLong() * 1024 * 1_000_000L / adts.sampleRate

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, adts.sampleRate, adts.channels)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(adts.asc))

        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val muxTrack = muxer.addTrack(format)
            muxer.start()
            val info = MediaCodec.BufferInfo()
            for (s in range.first until range.last) {
                val pos = adts.frameOffsets[s] + adts.headerLens[s]
                val payloadLen = ((plan.sampleMap?.sizes?.get(s)) ?: 0).toInt()
                if (payloadLen <= 0) continue
                val payload = reader.read(pos, payloadLen)
                info.offset = 0
                info.size = payload.size
                info.presentationTimeUs = s.toLong() * 1024 * 1_000_000L / adts.sampleRate - startUs
                info.flags = 0
                muxer.writeSampleData(muxTrack, ByteBuffer.wrap(payload), info)
            }
            muxer.stop()
            return out
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            try { muxer.release() } catch (_: Exception) {}
        }
    }
}
