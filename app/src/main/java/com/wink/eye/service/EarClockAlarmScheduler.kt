package com.wink.eye.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wink.eye.MainActivity
import com.wink.eye.data.EarClockAlarm
import com.wink.eye.data.EarClockFrequency
import com.wink.eye.data.WorkdayType
import java.util.Calendar

/** EarClock 闹钟调度器：基于 AlarmManager + PendingIntent，按频率/稍后提醒计算触发时间 */
object EarClockAlarmScheduler {
    private const val TAG = "EarClockAlarmScheduler"

    /** 正常触发（含频率触发的正常闹钟） */
    fun scheduleNext(context: Context, alarm: EarClockAlarm) {
        val triggerAtMs = nextTriggerMillis(alarm, System.currentTimeMillis())
        schedule(context, alarm, triggerAtMs, isSnooze = false, snoozeCount = 0)
        Log.d(TAG, "已调度下一次触发: id=${alarm.id}, at=$triggerAtMs")
    }

    /** 稍后提醒：在当前时间基础上延迟 snoozeMinutes 分钟 */
    fun scheduleSnooze(context: Context, alarm: EarClockAlarm, snoozeCount: Int) {
        val triggerAtMs = System.currentTimeMillis() + alarm.snoozeMinutes * 60 * 1000L
        schedule(context, alarm, triggerAtMs, isSnooze = true, snoozeCount = snoozeCount)
        Log.d(TAG, "已调度稍后提醒: id=${alarm.id}, count=$snoozeCount, at=$triggerAtMs")
    }

    private fun schedule(
        context: Context,
        alarm: EarClockAlarm,
        triggerAtMs: Long,
        isSnooze: Boolean,
        snoozeCount: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(context, alarm, isSnooze, snoozeCount)

        // 检查精确闹钟权限，必要时降级为非精确
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "没有精确闹钟权限，使用非精确调度")
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                10 * 60 * 1000L,
                pendingIntent
            )
            return
        }
        // 用 setAlarmClock 而不是 setExactAndAllowWhileIdle：
        // 前者是系统最高优先级的闹钟通道，不受 Doze / 各厂商省电策略的窗口延迟影响，
        // 并会在状态栏展示闹钟图标（符合闹钟语义）。
        // 实测 setExactAndAllowWhileIdle 在 ColorOS 上会被塞进约 24 秒的触发窗口。
        alarmManager.setAlarmClock(
            // showIntent：用户点击状态栏闹钟图标时回到应用（原先传 null，图标点了没反应）
            AlarmManager.AlarmClockInfo(triggerAtMs, buildShowIntent(context)),
            pendingIntent
        )
    }

    private fun buildPendingIntent(
        context: Context,
        alarm: EarClockAlarm,
        isSnooze: Boolean,
        snoozeCount: Int
    ): PendingIntent {
        val intent = Intent(context, EarClockAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 状态栏闹钟图标被点击时的入口 */
    private fun buildShowIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(context: Context, alarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EarClockAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "已取消闹钟: $alarmId")
        } else {
            Log.w(TAG, "取消失败，未找到闹钟: $alarmId")
        }
    }

    /**
     * 计算下一次触发时间（毫秒）。
     * - ONCE：下一次到达该时刻
     * - WORKDAYS：按工作日类型匹配的周一至五下一时刻
     * - CUSTOM：下一个匹配的已选星期时刻
     */
    fun nextTriggerMillis(alarm: EarClockAlarm, fromMillis: Long): Long {
        val base = Calendar.getInstance().apply { timeInMillis = fromMillis }
        val currentDayOfWeek = base.get(Calendar.DAY_OF_WEEK)

        // 构造今天的设定时刻
        val today = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var daysAhead = 0
        if (today.timeInMillis <= fromMillis) {
            // 今天该时刻已过，向后找
            daysAhead = findNextDayAhead(alarm, currentDayOfWeek)
            today.add(Calendar.DAY_OF_YEAR, daysAhead)
        } else {
            // 今天该时刻未到，但需确认今天是否匹配频率
            if (!matchesDay(alarm, currentDayOfWeek)) {
                daysAhead = findNextDayAhead(alarm, currentDayOfWeek)
                today.add(Calendar.DAY_OF_YEAR, daysAhead)
            }
        }

        return today.timeInMillis
    }

    /** 返回需要向后偏移的天数，保证偏移后的星期匹配频率 */
    private fun findNextDayAhead(alarm: EarClockAlarm, todayDayOfWeek: Int): Int {
        for (offset in 1..7) {
            if (matchesDay(alarm, dayOfWeekAfter(todayDayOfWeek, offset))) return offset
        }
        // 理论不可达：CUSTOM 至少一天、WORKDAYS 必有、ONCE 任何天都匹配，这里兜底取 1
        return 1
    }

    /** Calendar.DAY_OF_WEEK（1=周日..7=周六）基础上向后偏移 offset 天后的 DAY_OF_WEEK 值 */
    private fun dayOfWeekAfter(fromDayOfWeek: Int, offset: Int): Int {
        val v = fromDayOfWeek + offset
        return if (v > Calendar.SATURDAY) v - 7 else v
    }

    private fun matchesDay(alarm: EarClockAlarm, dayOfWeek: Int): Boolean {
        return when (alarm.frequency) {
            EarClockFrequency.ONCE -> true
            EarClockFrequency.WORKDAYS -> when (alarm.workdayType) {
                // 本期仅 WEEKDAYS：周一至五；LEGAL（法定工作日，依赖节假日表）暂同周一至五逻辑
                WorkdayType.WEEKDAYS, WorkdayType.LEGAL ->
                    dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
            }
            EarClockFrequency.CUSTOM -> dayOfWeek in alarm.daysOfWeek
        }
    }

    const val EXTRA_ALARM_ID = "alarmId"
    const val EXTRA_IS_SNOOZE = "isSnooze"
    const val EXTRA_SNOOZE_COUNT = "snoozeCount"
}