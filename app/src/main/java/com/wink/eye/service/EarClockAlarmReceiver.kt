package com.wink.eye.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wink.eye.EarClockAlarmActivity
import com.wink.eye.R
import com.wink.eye.WinkApp
import com.wink.eye.data.EarClockAlarm
import com.wink.eye.data.EarClockFrequency

/** EarClock 闹钟触发接收器：校验耳机连接后唤起全屏闹钟页 */
class EarClockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EarClockAlarmScheduler.EXTRA_ALARM_ID) ?: return
        val isSnooze = intent.getBooleanExtra(EarClockAlarmScheduler.EXTRA_IS_SNOOZE, false)
        val snoozeCount = intent.getIntExtra(EarClockAlarmScheduler.EXTRA_SNOOZE_COUNT, 0)

        Log.d(TAG, "收到闹钟触发: id=$alarmId, isSnooze=$isSnooze, snoozeCount=$snoozeCount")

        val alarm = WinkApp.instance.earClockRepository.getById(alarmId) ?: return
        if (!alarm.enabled) {
            Log.w(TAG, "闹钟已禁用，忽略: $alarmId")
            return
        }

        // 正常触发（非稍后提醒）：WORKDAYS/CUSTOM 重排下一次；ONCE 一次性不重排
        if (!isSnooze && alarm.frequency != EarClockFrequency.ONCE) {
            EarClockAlarmScheduler.scheduleNext(context, alarm)
        }

        // 必须连接耳机才响；未连接时本次静默（已按频率重排下一次）
        if (!EarClockAudioHelper.isHeadphoneConnected(context)) {
            Log.d(TAG, "未连接耳机，本次静默不响: ${alarm.name}")
            return
        }

        launchAlarm(context, alarm, isSnooze, snoozeCount)
    }

    /**
     * 唤起全屏闹钟页。
     *
     * 走两条路径，任一成功即可（避免「只弹出一条普通通知、必须点一下才全屏」）：
     * 1. 直接 `startActivity` —— 持有「显示在其他应用上层」权限时，
     *    后台启动 Activity 属于系统豁免场景，可立即全屏；
     * 2. 高优通知 + `fullScreenIntent` —— Google 推荐的闹钟路径，
     *    但依赖「全屏通知」权限，Android 14+ 该权限默认只自动授予闹钟/通话类应用，
     *    厂商 ROM 常不认（实测 ColorOS 上直接被拒，通知降级）。
     */
    private fun launchAlarm(
        context: Context,
        alarm: EarClockAlarm,
        isSnooze: Boolean,
        snoozeCount: Int
    ) {
        ensureChannel(context)

        val alarmIntent = Intent(context, EarClockAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EarClockAlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            putExtra(EarClockAlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EarClockAlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
        }

        // 路径 1：直接启动
        val startedDirectly = runCatching { context.startActivity(alarmIntent) }
            .onFailure { Log.w(TAG, "直接启动闹钟页被拒，回退到全屏通知", it) }
            .isSuccess
        Log.d(TAG, "直接启动闹钟页: $startedDirectly")

        // 路径 2：通知兜底（同时也是用户手动重新进入闹钟页的入口）
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.earclock_notification_title))
            .setContentText(context.getString(R.string.earclock_notification_text, alarm.name))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            // 闹钟未处理前不允许划掉，避免误清除后错过提醒
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(alarm.id.hashCode(), notification)
        Log.d(TAG, "已发出闹钟通知（含全屏意图）: ${alarm.name}")
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.earclock_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.earclock_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "EarClockAlarmReceiver"
        const val CHANNEL_ID = "wink_earclock"
    }
}