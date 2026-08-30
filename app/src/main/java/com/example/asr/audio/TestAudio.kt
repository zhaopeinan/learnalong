package com.example.asr.audio

import java.io.ByteArrayOutputStream

/** 生成静音 WAV（16bit PCM 单声道），用于测试 ASR 接口连通性 */
object TestAudio {

    fun silentWav(seconds: Int = 1, sampleRate: Int = 16000): ByteArray {
        val dataSize = seconds * sampleRate * 2 // 16bit = 2 字节/采样
        val out = ByteArrayOutputStream(44 + dataSize)
        fun int32(v: Int) = out.write(
            byteArrayOf(
                (v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(),
                (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte(),
            ), 0, 4,
        )
        fun int16(v: Int) =
            out.write(byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte()), 0, 2)

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        int32(36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        int32(16) // PCM fmt 块大小
        int16(1) // PCM
        int16(1) // 单声道
        int32(sampleRate)
        int32(sampleRate * 2) // 字节率
        int16(2) // 块对齐
        int16(16) // 位深
        out.write("data".toByteArray(Charsets.US_ASCII))
        int32(dataSize)
        out.write(ByteArray(dataSize)) // 静音
        return out.toByteArray()
    }
}
