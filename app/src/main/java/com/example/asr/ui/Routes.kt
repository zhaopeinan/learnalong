package com.example.asr.ui

object Routes {
    const val TODAY = "today"
    const val RECORDINGS = "recordings"
    const val WEAK_POINTS = "weak_points"
    const val MINE = "mine"
    const val RECORD = "record"
    const val CHAT = "chat"
    const val CHILDREN = "children"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val DETAIL = "detail/{recordingId}"
    const val EXERCISE = "exercise/{weakPointId}"

    fun detail(recordingId: Long) = "detail/$recordingId"
    fun exercise(weakPointId: Long) = "exercise/$weakPointId"
}
