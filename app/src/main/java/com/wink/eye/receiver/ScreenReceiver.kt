package com.wink.eye.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wink.eye.service.ScreenMonitorService

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                ScreenMonitorService.startScreenOn(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                ScreenMonitorService.startScreenOff(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                ScreenMonitorService.startUserPresent(context)
            }
        }
    }
}
