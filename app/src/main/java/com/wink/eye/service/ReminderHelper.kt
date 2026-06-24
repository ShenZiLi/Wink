package com.wink.eye.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.wink.eye.MainActivity
import com.wink.eye.R
import com.wink.eye.data.ReminderMode
import com.wink.eye.data.Rule

object ReminderHelper {

    private const val CHANNEL_ID = "wink_reminder"
    private const val CHANNEL_NAME = "护眼提醒"

    fun triggerReminder(context: Context, rule: Rule) {
        ensureChannel(context)

        when (rule.reminderMode) {
            ReminderMode.ALARM -> triggerAlarm(context, rule)
            ReminderMode.NOTIFICATION -> triggerNotification(context, rule)
        }
    }

    private fun triggerAlarm(context: Context, rule: Rule) {
        val intent = Intent(context, com.wink.eye.ReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RULE_ID, rule.id)
            putExtra(EXTRA_RULE_NAME, rule.name)
        }
        context.startActivity(intent)
    }

    private fun triggerNotification(context: Context, rule: Rule) {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
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
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(rule.id.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_desc)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    const val EXTRA_RULE_ID = "rule_id"
    const val EXTRA_RULE_NAME = "rule_name"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
}

class DismissReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderHelper.EXTRA_NOTIFICATION_ID, 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }
}
