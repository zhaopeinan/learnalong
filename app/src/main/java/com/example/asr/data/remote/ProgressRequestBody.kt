package com.example.asr.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** 包装 RequestBody，回调上传进度（0f-1f），用于转写上传进度条 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val countingSink = object : ForwardingSink(sink) {
            private var written = 0L
            private var lastReported = -1

            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                if (total > 0) {
                    val percent = (written * 100 / total).toInt()
                    if (percent != lastReported) {
                        lastReported = percent
                        onProgress(written.toFloat() / total)
                    }
                }
            }
        }
        val buffered = countingSink.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
