package com.wink.eye.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 耳机闹钟频率 */
@Serializable
enum class EarClockFrequency {
    @SerialName("once") ONCE,
    @SerialName("workdays") WORKDAYS,
    @SerialName("custom") CUSTOM
}

/** 工作日类型：WEEKDAYS=周一至周五；LEGAL=法定工作日（依赖节假日表，本期延后，仅预留） */
@Serializable
enum class WorkdayType {
    @SerialName("weekdays") WEEKDAYS,
    @SerialName("legal") LEGAL
}

/** 振动模式：DEFAULT=默认振动；OFF=关闭；CUSTOM=自定义振动（本期限留延后） */
@Serializable
enum class VibrationMode {
    @SerialName("default") DEFAULT,
    @SerialName("off") OFF,
    @SerialName("custom") CUSTOM
}

/** 耳机闹钟实体：仅在连接耳机时才通过耳机播放闹铃的全屏闹钟 */
@Serializable
data class EarClockAlarm(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    /** 触发时间 0-23 */
    val hour: Int,
    /** 触发分钟 0-59 */
    val minute: Int,
    /** 频率 */
    val frequency: EarClockFrequency,
    /** 频率=WORKDAYS 时有效的工作日类型 */
    val workdayType: WorkdayType = WorkdayType.WEEKDAYS,
    /** 频率=CUSTOM 时一周自选的天数，值为 java.util.Calendar.DAY_OF_WEEK */
    val daysOfWeek: Set<Int> = emptySet(),
    /** 自定义铃声 URI，null=系统默认闹铃 */
    val ringtoneUri: String? = null,
    /** 振动模式 */
    val vibrationMode: VibrationMode = VibrationMode.DEFAULT,
    /** 是否开启稍后提醒 */
    val snoozeEnabled: Boolean = true,
    /** 稍后提醒间隔（分钟） */
    val snoozeMinutes: Int = 5,
    /** 稍后提醒最大次数 */
    val snoozeRepeatLimit: Int = 3
)