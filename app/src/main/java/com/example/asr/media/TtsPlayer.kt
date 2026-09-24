package com.example.asr.media

import android.media.MediaPlayer

/** TTS 播报状态 */
enum class TtsState { IDLE, PREPARING, PLAYING, COMPLETED, ERROR }

/**
 * AI 回复语音播报：MediaPlayer 播放 MiniMax 返回的远程音频 URL。
 * URL 24h 有效，过期导致播放失败（ERROR）由调用方重新合成后重试。
 */
class TtsPlayer(
    private val onStateChanged: (TtsState) -> Unit = {},
) {

    private var player: MediaPlayer? = null

    var state: TtsState = TtsState.IDLE
        private set

    /** 播放远程音频；重复调用会先停掉上一段 */
    fun play(url: String) {
        stopInternal()
        val p = MediaPlayer()
        player = p
        setState(TtsState.PREPARING)
        runCatching {
            p.setDataSource(url)
            p.setOnPreparedListener {
                it.start()
                setState(TtsState.PLAYING)
            }
            p.setOnCompletionListener {
                stopInternal()
                setState(TtsState.COMPLETED)
            }
            p.setOnErrorListener { _, _, _ ->
                stopInternal()
                setState(TtsState.ERROR)
                true
            }
            p.prepareAsync()
        }.onFailure {
            stopInternal()
            setState(TtsState.ERROR)
        }
    }

    fun stop() {
        stopInternal()
        setState(TtsState.IDLE)
    }

    fun release() {
        stopInternal()
        state = TtsState.IDLE
    }

    private fun stopInternal() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
    }

    private fun setState(value: TtsState) {
        state = value
        onStateChanged(value)
    }
}
