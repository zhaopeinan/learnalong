package com.example.asr.data.local

import com.example.asr.data.local.dao.ChildDao
import com.example.asr.data.local.dao.MasteryHistoryDao
import com.example.asr.data.local.dao.ReviewTaskDao
import com.example.asr.data.local.dao.WeakPointDao
import com.example.asr.data.local.entity.ChildEntity
import com.example.asr.data.local.entity.MasteryHistoryEntity
import com.example.asr.data.local.entity.ReviewTaskEntity
import com.example.asr.data.local.entity.WeakPointEntity
import com.example.asr.data.settings.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * 演示数据播种（对应小程序 tour.ts 的 seedDemoDataIfEmpty）：
 * 若库还是空的（全新安装），放入一套示例孩子/薄弱点/今日任务/练习题，
 * 让新手引导和新用户第一眼就能看到真实数据长什么样。只执行一次。
 * 触发时机对齐小程序：复习页首进（onShow）时调用。
 */
class DemoSeeder(
    private val childDao: ChildDao,
    private val weakPointDao: WeakPointDao,
    private val reviewTaskDao: ReviewTaskDao,
    private val masteryHistoryDao: MasteryHistoryDao,
    private val settingsStore: SettingsStore,
) {

    suspend fun seedIfEmpty() {
        if (settingsStore.settings.first().demoSeededV1) return
        runCatching {
            if (childDao.getAll().isNotEmpty()) {
                settingsStore.setDemoSeededV1(true)
                return
            }
            val now = System.currentTimeMillis()
            val childId = childDao.insert(ChildEntity(name = "示例·小明", grade = "二年级"))

            val wp1 = weakPointDao.insert(
                WeakPointEntity(
                    childId = childId,
                    subject = "拼音",
                    knowledgePoint = "声母 b 和 p 混淆",
                    description = "把「婆婆」读成「伯伯」，听写时 b、p 写反。",
                    mastery = 45,
                    reviewStage = 0,
                    nextReviewAt = now,
                    createdAt = now - 2 * DAY_MS,
                    sourceRecordingId = null,
                    exerciseCache = DEMO_PINYIN_CONTENT,
                )
            )
            reviewTaskDao.insert(
                ReviewTaskEntity(weakPointId = wp1, dueDate = now, completedAt = null, content = DEMO_PINYIN_CONTENT)
            )
            masteryHistoryDao.insert(MasteryHistoryEntity(weakPointId = wp1, mastery = 30, recordedAt = now - 2 * DAY_MS))
            masteryHistoryDao.insert(MasteryHistoryEntity(weakPointId = wp1, mastery = 45, recordedAt = now))

            val wp2 = weakPointDao.insert(
                WeakPointEntity(
                    childId = childId,
                    subject = "数学",
                    knowledgePoint = "20 以内进位加法不熟练",
                    description = "算 8+7 要数手指，「凑十法」没有形成条件反射。",
                    mastery = 55,
                    reviewStage = 0,
                    nextReviewAt = now,
                    createdAt = now - DAY_MS,
                    sourceRecordingId = null,
                    exerciseCache = DEMO_MATH_CONTENT,
                )
            )
            reviewTaskDao.insert(
                ReviewTaskEntity(weakPointId = wp2, dueDate = now, completedAt = null, content = DEMO_MATH_CONTENT)
            )
            masteryHistoryDao.insert(MasteryHistoryEntity(weakPointId = wp2, mastery = 40, recordedAt = now - DAY_MS))
            masteryHistoryDao.insert(MasteryHistoryEntity(weakPointId = wp2, mastery = 55, recordedAt = now))

            settingsStore.setDemoSeededV1(true)
        } // 示例数据失败不影响主流程
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        val DEMO_PINYIN_CONTENT = """
            {"exercises":[{"question":"「婆婆」这个词，两个字的声母分别是什么？","answer":"都是 p（pó po）","hint":"发 p 时手心能感觉到明显气流"},{"question":"听写：白菜。注意「白」的声母。","answer":"bái cài（声母 b）","hint":"b 不送气，p 送气"},{"question":"读一读对比词：拔河、爬坡。","answer":"bá hé、pá pō","hint":""},{"question":"「跑步」的「跑」，声母是 b 还是 p？","answer":"p（pǎo）","hint":""},{"question":"拼一拼：b—à→？p—à→？","answer":"bà（爸）、pà（怕）","hint":"声调相同，区别只在声母"}],"tips":"用手心感受气流：发 p 时有明显气流，发 b 没有。每天对比朗读 5 分钟。"}
        """.trimIndent()

        val DEMO_MATH_CONTENT = """
            {"exercises":[{"question":"8 + 7 = ？说说你是怎么算的。","answer":"15（把 7 分成 2 和 5，8+2=10，10+5=15）","hint":"凑十法：先把 8 凑成 10"},{"question":"9 + 6 = ？","answer":"15","hint":"把 6 分成 1 和 5"},{"question":"7 + 8 和 8 + 7 哪个好算？结果是多少？","answer":"一样，都是 15","hint":"交换加数位置，结果不变"},{"question":"6 + 9 = ？","answer":"15","hint":""},{"question":"17 - 9 = ？（用刚才的进位加法反过来想）","answer":"8","hint":"想：9 加几等于 17？"}],"tips":"进位加法先练「凑十」：看到 8 想 2，看到 9 想 1。每天口头练 5 题，坚持一周。"}
        """.trimIndent()
    }
}
