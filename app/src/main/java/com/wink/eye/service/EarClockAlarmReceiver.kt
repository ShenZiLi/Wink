package com.wink.eye.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wink.eye.WinkApp
import com.wink.eye.data.EarClockFrequency

/** EarClock 闹钟触发接收器：校验耳机连接后启动响铃前台服务 */
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

        EarClockRingingService.start(context, alarm.id, isSnooze, snoozeCount)
    }

    companion object {
        private const val TAG = "EarClockAlarmReceiver"
    }
}
