package com.wink.eye.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenTimeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到亮屏时长闹钟触发")

        // 通过 Service 的 action 触发检查，Service 内部会 checkScreenTimeRules 并重新调度
        val serviceIntent = Intent(context, ScreenMonitorService::class.java).apply {
            action = ACTION_CHECK_SCREEN_TIME
        }
        context.startService(serviceIntent)
    }

    companion object {
        private const val TAG = "ScreenTimeAlarmReceiver"
        const val ACTION_CHECK_SCREEN_TIME = "com.wink.eye.CHECK_SCREEN_TIME"
    }
}
