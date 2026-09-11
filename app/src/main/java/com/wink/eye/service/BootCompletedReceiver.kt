package com.wink.eye.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wink.eye.WinkApp

/**
 * 开机后重新调度所有已启用的耳机闹钟。
 *
 * AlarmManager 中已注册的闹钟在设备重启后会被系统全部清除，
 * 若不在开机时重排，闹钟会静默失效（表现为「设了闹钟但从来没响过」）。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val enabledAlarms = WinkApp.instance.earClockRepository.getAll().filter { it.enabled }
        enabledAlarms.forEach { EarClockAlarmScheduler.scheduleNext(context, it) }
        Log.d(TAG, "开机/更新后重排耳机闹钟: ${enabledAlarms.size} 个")
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"
    }
}
