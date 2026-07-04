package com.wink.eye.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wink.eye.MainActivity
import com.wink.eye.R
import com.wink.eye.ReminderActivity
import com.wink.eye.data.ReminderMode
import com.wink.eye.data.Rule

object ReminderHelper {

    private const val CHANNEL_ID = "wink_reminder"
    private const val ALARM_CHANNEL_ID = "wink_alarm"
    private const val TAG = "ReminderHelper"

    // #14 懒加载 channels
    @Volatile
    private var channelsCreated = false

    fun triggerReminder(context: Context, rule: Rule) {
        // 锁屏或息屏状态下不允许提醒
        if (isScreenOffOrLocked(context)) {
            Log.d(TAG, "锁屏或息屏状态，跳过提醒: ${rule.name}")
            return
        }

        Log.d(TAG, "触发提醒: ${rule.name}, 模式: ${rule.reminderMode}")

        ensureChannelsIfNeeded(context)

        when (rule.reminderMode) {
            ReminderMode.ALARM -> triggerAlarm(context, rule)
            ReminderMode.NOTIFICATION -> triggerNotification(context, rule)
        }
    }

    // 屏幕息屏或设备锁屏时不允许提醒（覆盖亮屏时长触发与定时触发两条路径）
    private fun isScreenOffOrLocked(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive != true) return true
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isKeyguardLocked == true
    }

    private fun triggerAlarm(context: Context, rule: Rule) {
        Log.d(TAG, "触发全屏提醒")

        val fullScreenIntent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RULE_ID, rule.id)
            putExtra(EXTRA_RULE_NAME, rule.name)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            rule.id.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notificationId = rule.id.hashCode()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "全屏提醒通知已发送，ID: $notificationId")
    }

    private fun triggerNotification(context: Context, rule: Rule) {
        Log.d(TAG, "触发通知提醒")

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(context, ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RULE_ID, rule.id)
            putExtra(EXTRA_RULE_NAME, rule.name)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            rule.id.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, DismissReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_ID, rule.id.hashCode())
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            rule.id.hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notificationId = rule.id.hashCode()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "通知已发送，ID: $notificationId")
    }

    private fun ensureChannelsIfNeeded(context: Context) {
        if (channelsCreated) return
        synchronized(this) {
            if (channelsCreated) return
            ensureChannels(context)
            channelsCreated = true
        }
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // 通知提醒渠道
        val reminderChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(reminderChannel)

        // #15 闹钟提醒渠道使用字符串资源
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(alarmChannel)
        Log.d(TAG, "通知渠道已创建")
    }

    const val EXTRA_RULE_ID = "rule_id"
    const val EXTRA_RULE_NAME = "rule_name"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
}

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderHelper.EXTRA_NOTIFICATION_ID, 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }
}
