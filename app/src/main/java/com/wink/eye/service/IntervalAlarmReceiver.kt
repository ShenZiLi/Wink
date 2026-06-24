package com.wink.eye.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wink.eye.WinkApp
import com.wink.eye.data.RuleType
import com.wink.eye.service.ReminderHelper

class IntervalAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra("ruleId") ?: return
        val rule = WinkApp.instance.ruleRepository.getById(ruleId) ?: return

        if (rule.enabled && rule.type is RuleType.Interval) {
            ReminderHelper.triggerReminder(context, rule)
        }
    }
}
