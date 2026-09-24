package com.example.asr.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.asr.MainActivity

object NotificationHelper {

    const val CHANNEL_ID = "daily_review"
    const val RECORDING_CHANNEL_ID = "recording"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "每日复习提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "提醒今天到期的薄弱点复习任务" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** 录音常驻通知渠道：低重要性，不响铃不震动，避免每秒刷新打扰 */
    fun ensureRecordingChannel(context: Context) {
        val channel = NotificationChannel(
            RECORDING_CHANNEL_ID,
            "录音状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "录音期间的常驻计时通知" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showDailyReminder(context: Context, dueCount: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("今日复习提醒")
            .setContentText("今天有 $dueCount 个薄弱点待复习，点开开始吧")
            .setAutoCancel(true)
            .setContentIntent(intent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
