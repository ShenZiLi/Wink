package com.wink.eye.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RuleType {
    @Serializable
    @SerialName("cron")
    data class Cron(val expression: String) : RuleType()

    @Serializable
    @SerialName("screen_time")
    data class ScreenTime(
        val screenOnMinutes: Int,
        val screenOffResetMinutes: Int
    ) : RuleType()
}

@Serializable
enum class ReminderMode {
    @SerialName("alarm") ALARM,
    @SerialName("notification") NOTIFICATION
}

@Serializable
data class Rule(
    val id: String,
    val name: String,
    val type: RuleType,
    val reminderMode: ReminderMode,
    val enabled: Boolean = true
)
