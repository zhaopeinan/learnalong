package com.example.asr.ui

import com.example.asr.data.local.entity.ChatMode

object Routes {
    const val SPLASH = "splash"
    /** 协议页：gate=true 为强制确认门；type=terms|privacy 为初始文档 */
    const val AGREEMENT = "agreement?gate={gate}&type={type}"
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
    const val GUIDE = "guide"
    const val GUIDE_ARTICLE = "guide_article/{articleId}"
    const val ABOUT = "about"
    const val DETAIL = "detail/{recordingId}"
    const val EXERCISE = "exercise/{weakPointId}"
    const val KID_PROGRESS = "kid_progress"

    fun detail(recordingId: Long) = "detail/$recordingId"
    fun exercise(weakPointId: Long) = "exercise/$weakPointId"
    fun guideArticle(articleId: String) = "guide_article/$articleId"

    /** 协议确认门（首次启动） */
    fun agreementGate() = "agreement?gate=true"

    /** 协议只读页（设置/关于页入口），type=terms|privacy */
    fun agreementReadonly(type: String = "terms") = "agreement?gate=false&type=$type"

    /** 问老师入口：free=自由提问；weakpoint/exercise 需带 refId=weakPointId */
    fun chat(mode: String = ChatMode.FREE, childId: Long? = null, refId: Long? = null): String {
        var route = "chat?mode=$mode"
        if (childId != null && childId > 0) route += "&childId=$childId"
        if (refId != null && refId > 0) route += "&refId=$refId"
        return route
    }
}
