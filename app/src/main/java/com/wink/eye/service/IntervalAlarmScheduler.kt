package com.wink.eye.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object IntervalAlarmScheduler {
    private const val TAG = "IntervalAlarmScheduler"

    fun schedule(context: Context, ruleId: String, intervalMs: Long) {
        Log.d(TAG, "开始调度提醒: ruleId=$ruleId, interval=${intervalMs}ms")
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 检查精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "没有精确闹钟权限，使用非精确闹钟")
                scheduleInexact(context, ruleId, intervalMs)
                return
            }
        }

        val intent = Intent(context, IntervalAlarmReceiver::class.java).apply {
            putExtra("ruleId", ruleId)
            putExtra("intervalMs", intervalMs)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用精确闹钟，首次触发后在 Receiver 中重新调度下一次
        val triggerAtMs = System.currentTimeMillis() + intervalMs
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            pendingIntent
        )
        Log.d(TAG, "✓ 闹钟已调度: ruleId=$ruleId, interval=${intervalMs}ms, triggerAt=$triggerAtMs (约 ${intervalMs/1000} 秒后)")
    }

    private fun scheduleInexact(context: Context, ruleId: String, intervalMs: Long) {
        Log.d(TAG, "使用非精确闹钟调度: ruleId=$ruleId, interval=${intervalMs}ms")
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, IntervalAlarmReceiver::class.java).apply {
            putExtra("ruleId", ruleId)
            putExtra("intervalMs", intervalMs)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + intervalMs,
            intervalMs,
            pendingIntent
        )
    }

    fun cancel(context: Context, ruleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, IntervalAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "✓ 已取消提醒: ruleId=$ruleId")
        } else {
            Log.w(TAG, "取消失败，未找到闹钟: ruleId=$ruleId")
        }
    }
}
