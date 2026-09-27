package com.example.asr.ui.util

import com.example.asr.data.local.entity.RecordingStatus
import com.example.asr.data.local.entity.SpeakerRole
import com.example.asr.data.local.entity.WorkStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toDateTimeString(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))

fun Long.toDateString(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(this))

fun Long.toMinuteString(): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(this))

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

/** 工作端状态文案（对应小程序 format.ts recordingStatusText，含 ANALYZING） */
fun workStatusText(status: String): String = when (status) {
    WorkStatus.RECORDED -> "已录"
    WorkStatus.TRANSCRIBING -> "转写中"
    WorkStatus.TRANSCRIBED -> "已转写"
    WorkStatus.ANALYZING -> "分析中"
    WorkStatus.ANALYZED -> "已分析"
    WorkStatus.FAILED -> "失败"
    else -> status
}
