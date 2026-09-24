package com.example.asr.domain

import com.example.asr.data.local.dao.KidStarDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.random.Random

/**
 * 孩子端星星激励（对应小程序 utils/kidReward.ts + kid-progress 页 onMark 结算）：
 * 「我会啦」完成复习 +1 星，星星数按孩子持久化在 kid_stars 表。
 */
class KidReward(private val kidStarDao: KidStarDao) {

    suspend fun getStars(childId: Long): Int = kidStarDao.getStars(childId) ?: 0

    fun observeStars(childId: Long): Flow<Int> = kidStarDao.observeStars(childId).map { it ?: 0 }

    /** 加一颗星，返回新总数 */
    suspend fun addStar(childId: Long): Int {
        kidStarDao.addStar(childId)
        return getStars(childId)
    }

    /** 复习反馈的奖励结算（对应小程序 kid-progress onMark） */
    enum class Outcome { STAR, DEFEATED, TRY_AGAIN }

    companion object {
        /** 掌握度满 = 已消灭小怪兽（小程序 defeated: wp.mastery >= 100） */
        const val DEFEAT_MASTERY = 100

        /** 「快消灭啦」徽章阈值（小程序 almostDone: 80 <= mastery < 100） */
        const val ALMOST_DONE_MASTERY = 80

        val PRAISES = listOf("太棒了！", "你真厉害！", "又消灭一个小怪兽！", "继续保持哦！")

        const val DEFEATED_TOAST = "🏆 消灭了一只小怪兽！"
        const val TRY_AGAIN_TOAST = "没关系，明天再练一次"

        fun isDefeated(mastery: Int): Boolean = mastery >= DEFEAT_MASTERY

        fun isAlmostDone(mastery: Int): Boolean = mastery >= ALMOST_DONE_MASTERY && !isDefeated(mastery)

        /** 结算：未掌握只鼓励不加星；掌握了得 1 星，若掌握度满则消灭小怪兽 */
        fun outcomeFor(mastered: Boolean, newMastery: Int): Outcome = when {
            !mastered -> Outcome.TRY_AGAIN
            isDefeated(newMastery) -> Outcome.DEFEATED
            else -> Outcome.STAR
        }

        fun randomPraise(random: Random = Random.Default): String =
            PRAISES[random.nextInt(PRAISES.size)]

        /** 得星提示：随机表扬语 + ⭐+1（小程序 toast 文案） */
        fun starToast(random: Random = Random.Default): String = "${randomPraise(random)} ⭐+1"
    }
}
