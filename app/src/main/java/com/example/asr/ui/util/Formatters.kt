package com.example.asr.ui.util

import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.SpeakerRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toDateTimeString(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))

fun Long.toDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(this))

fun Int.toDurationString(): String {
    val m = this / 60
    val s = this % 60
    return "%02d:%02d".format(m, s)
}

fun recordingStatusText(status: String): String = when (status) {
    RecordingStatus.RECORDED -> "已录"
    RecordingStatus.TRANSCRIBING -> "转写中"
    RecordingStatus.TRANSCRIBED -> "已转写"
    RecordingStatus.ANALYZED -> "已分析"
    RecordingStatus.FAILED -> "失败"
    else -> status
}

fun speakerRoleText(role: String): String = when (role) {
    SpeakerRole.PARENT -> "家长"
    SpeakerRole.CHILD -> "孩子"
    else -> "未标注"
}
