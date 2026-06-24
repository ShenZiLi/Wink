package com.wink.eye.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class RuleType {
    @Serializable
    @SerialName("interval")
    data class Interval(
        val value: Int,
        val unit: IntervalUnit
    ) : RuleType()

    @Serializable
    @SerialName("screen_time")
    data class ScreenTime(
        val screenOnMinutes: Int,
        val screenOffResetMinutes: Int
    ) : RuleType()
}

@Serializable
enum class IntervalUnit {
    @SerialName("seconds") SECONDS,
    @SerialName("minutes") MINUTES
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
