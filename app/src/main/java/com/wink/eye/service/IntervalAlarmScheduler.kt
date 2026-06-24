package com.wink.eye.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wink.eye.ReminderActivity

object IntervalAlarmScheduler {
    private const val ACTION_INTERVAL_REMINDER = "com.wink.eye.INTERVAL_REMINDER"

    fun schedule(context: Context, ruleId: String, intervalMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, IntervalAlarmReceiver::class.java).apply {
            action = ACTION_INTERVAL_REMINDER
            putExtra("ruleId", ruleId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用 setRepeating 实现间隔提醒
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + intervalMs,
            intervalMs,
            pendingIntent
        )
    }

    fun cancel(context: Context, ruleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, IntervalAlarmReceiver::class.java).apply {
            action = ACTION_INTERVAL_REMINDER
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }
}
