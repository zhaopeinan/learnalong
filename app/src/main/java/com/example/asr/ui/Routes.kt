package com.example.asr.ui

import com.example.asr.data.local.entity.ChatMode

object Routes {
    const val SPLASH = "splash"
    const val AGREEMENT = "agreement"
    const val TODAY = "today"
    const val RECORDINGS = "recordings"
    const val WEAK_POINTS = "weak_points"
    const val MINE = "mine"
    const val RECORD = "record"
    /** 问老师（AI 语音辅导）：mode=free|weakpoint|exercise，childId 缺省在页内解析，refId=weakPointId */
    const val CHAT = "chat?mode={mode}&childId={childId}&refId={refId}"
    const val CHILDREN = "children"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val DETAIL = "detail/{recordingId}"
    const val EXERCISE = "exercise/{weakPointId}"
    const val KID_PROGRESS = "kid_progress"

    fun detail(recordingId: Long) = "detail/$recordingId"
    fun exercise(weakPointId: Long) = "exercise/$weakPointId"

    /** 问老师入口：free=自由提问；weakpoint/exercise 需带 refId=weakPointId */
    fun chat(mode: String = ChatMode.FREE, childId: Long? = null, refId: Long? = null): String {
        var route = "chat?mode=$mode"
        if (childId != null && childId > 0) route += "&childId=$childId"
        if (refId != null && refId > 0) route += "&refId=$refId"
        return route
    }
}
