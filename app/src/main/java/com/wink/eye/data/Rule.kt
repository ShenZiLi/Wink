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
        @SerialName("screenOnDuration") val screenOnDuration: Int = 0,
        @SerialName("screenOffResetDuration") val screenOffResetDuration: Int = 0,
        @SerialName("screenOnUnit") val screenOnUnit: ScreenTimeUnit = ScreenTimeUnit.MINUTES,
        @SerialName("screenOffResetUnit") val screenOffResetUnit: ScreenTimeUnit = ScreenTimeUnit.MINUTES,
        @Deprecated("Legacy field, migrated to screenOnDuration")
        @SerialName("screenOnMinutes") val screenOnMinutes: Int = 0,
        @Deprecated("Legacy field, migrated to screenOffResetDuration")
        @SerialName("screenOffResetMinutes") val screenOffResetMinutes: Int = 0
    ) : RuleType() {
        /** 兼容旧数据：优先使用新字段，旧字段非零时自动迁移 */
        val effectiveScreenOnDuration: Int
            get() = if (screenOnDuration > 0) screenOnDuration else screenOnMinutes
        val effectiveScreenOffResetDuration: Int
            get() = if (screenOffResetDuration > 0) screenOffResetDuration else screenOffResetMinutes
    }
}

@Serializable
enum class IntervalUnit {
    @SerialName("seconds") SECONDS,
    @SerialName("minutes") MINUTES
}

@Serializable
enum class ScreenTimeUnit {
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
