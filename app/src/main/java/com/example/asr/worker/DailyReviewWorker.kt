package com.example.asr.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.asr.AsrApplication
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 每天一次：统计今日到期复习任务数并发系统通知 */
class DailyReviewWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AsrApplication).container
        val count = container.tutorRepository.countDueTasks(System.currentTimeMillis())
        if (count > 0) {
            NotificationHelper.showDailyReminder(applicationContext, count)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_review_reminder"

        /** 排程每日提醒，首次触发时间为当天（或次日）的 hour:minute */
        fun schedule(context: Context, hour: Int, minute: Int) {
            val delay = initialDelayMillis(hour, minute)
            val request = PeriodicWorkRequestBuilder<DailyReviewWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        internal fun initialDelayMillis(hour: Int, minute: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
