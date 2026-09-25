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
    /** 设置分组子页：模型服务 / 语音合成与音色 / 学习与复习 / 备份与存储 / 通用 */
    const val SETTINGS_MODEL = "settings_model"
    const val SETTINGS_VOICE = "settings_voice"
    const val SETTINGS_STUDY = "settings_study"
    const val SETTINGS_BACKUP = "settings_backup"
    const val SETTINGS_GENERAL = "settings_general"
    const val BACKUP = "backup"
    const val GUIDE = "guide"
    const val GUIDE_ARTICLE = "guide_article/{articleId}"
    const val ABOUT = "about"
    const val DETAIL = "detail/{recordingId}"
    const val EXERCISE = "exercise/{weakPointId}"
    const val KID_PROGRESS = "kid_progress"
    /** 工作端首页（记录列表 / 待办清单两视图，页内切换） */
    const val WORK_HOME = "work_home"
    const val WORK_RECORD = "work_record"
    /** 工作端详情：auto=true 时（录音页保存后跳入）自动开始转写并分析 */
    const val WORK_DETAIL = "work_detail/{recordingId}?auto={auto}"

    fun detail(recordingId: Long) = "detail/$recordingId"
    fun exercise(weakPointId: Long) = "exercise/$weakPointId"
    fun guideArticle(articleId: String) = "guide_article/$articleId"
    fun workDetail(recordingId: Long, auto: Boolean = false) = "work_detail/$recordingId?auto=$auto"

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
