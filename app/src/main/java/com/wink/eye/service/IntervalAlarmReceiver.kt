package com.wink.eye.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wink.eye.WinkApp
import com.wink.eye.data.RuleType
import com.wink.eye.service.ReminderHelper

class IntervalAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra("ruleId") ?: return
        val intervalMs = intent.getLongExtra("intervalMs", 0)
        
        Log.d(TAG, "收到闹钟触发: ruleId=$ruleId, intervalMs=$intervalMs")
        
        val rule = WinkApp.instance.ruleRepository.getById(ruleId) ?: return

        if (rule.enabled && rule.type is RuleType.Interval) {
            Log.d(TAG, "触发提醒: ${rule.name}")
            ReminderHelper.triggerReminder(context, rule)
            
            // 重新调度下一次闹钟
            if (intervalMs > 0) {
                IntervalAlarmScheduler.schedule(context, ruleId, intervalMs)
                Log.d(TAG, "已重新调度下一次提醒")
            }
        } else {
            Log.w(TAG, "规则已禁用或类型不匹配: enabled=${rule.enabled}, type=${rule.type}")
        }
    }
    
    companion object {
        private const val TAG = "IntervalAlarmReceiver"
    }
}
