package com.wink.eye.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wink.eye.WinkApp
import com.wink.eye.data.RuleType
import com.wink.eye.service.ReminderHelper
import com.wink.eye.util.CronHelper
import java.time.ZonedDateTime

class CronAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: return
        val rule = WinkApp.instance.ruleRepository.getById(ruleId) ?: return

        if (rule.enabled && rule.type is RuleType.Cron) {
            ReminderHelper.triggerReminder(context, rule)
        }

        // 调度下一次触发
        if (rule.enabled) {
            scheduleNext(context, ruleId, (rule.type as RuleType.Cron).expression)
        }
    }

    companion object {
        private const val EXTRA_RULE_ID = "rule_id"

        fun schedule(context: Context, ruleId: String, expression: String) {
            val nextTime = CronHelper.nextExecutionTime(expression) ?: return
            scheduleAt(context, ruleId, nextTime)
        }

        fun scheduleNext(context: Context, ruleId: String, expression: String) {
            schedule(context, ruleId, expression)
        }

        fun cancel(context: Context, ruleId: String) {
            val intent = Intent(context, CronAlarmReceiver::class.java).apply {
                putExtra(EXTRA_RULE_ID, ruleId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ruleId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.cancel()
        }

        private fun scheduleAt(context: Context, ruleId: String, time: ZonedDateTime) {
            val intent = Intent(context, CronAlarmReceiver::class.java).apply {
                putExtra(EXTRA_RULE_ID, ruleId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ruleId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerMs = time.toInstant().toEpochMilli()

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMs,
                pendingIntent
            )
        }
    }
}
