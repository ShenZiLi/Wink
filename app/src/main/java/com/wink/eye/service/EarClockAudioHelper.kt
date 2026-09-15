package com.wink.eye.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.wink.eye.data.VibrationMode

/** EarClock 音频辅助：检测耳机连接、将闹铃路由到耳机播放并可选振动 */
object EarClockAudioHelper {
    private const val TAG = "EarClockAudioHelper"

    /**
     * 耳机设备类型。
     *
     * 覆盖有线、蓝牙经典（A2DP/SCO）、蓝牙 LE Audio、助听器与 USB 音频。
     * 只列 A2DP 会漏掉 LE Audio 耳机（TYPE_BLE_HEADSET，Android 12+）
     * 和 USB-C 直连/转接头（TYPE_USB_DEVICE）。
     */
    private val HEADPHONE_DEVICE_TYPES = listOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY
    )

    /** 当前是否有耳机连接（仅强依赖有线/USB；蓝牙检测做 best-effort，缺权限属少见情况） */
    fun isHeadphoneConnected(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type in HEADPHONE_DEVICE_TYPES && it.isSink }
        } catch (_: SecurityException) {
            Log.w(TAG, "读取音频输出设备被拒，视为未连接")
            false
        }
    }

    /**
     * 播放闹铃并路由到耳机；返回 MediaPlayer 实例供调用方停止/释放，全部失败时返回 null。
     *
     * 依次尝试「用户自定义铃声 → 系统默认闹铃 → 默认来电 → 默认通知音」，
     * 任一环节失败（例如自定义铃声的 URI 授权已失效）都能降级，不会整个闹铃静默。
     *
     * @param ringtoneUri 自定义铃声 URI，null 时用系统默认闹铃
     * @param vibrationMode 振动模式，OFF 时不振动
     */
    fun playAlarm(
        context: Context,
        ringtoneUri: Uri?,
        vibrationMode: VibrationMode
    ): MediaPlayer? {
        val device = findHeadphoneDevice(context)
        Log.d(
            TAG,
            "首选输出设备=${device?.let(::deviceLabel) ?: "未找到耳机"}; 当前输出=${describeOutputs(context)}"
        )

        val candidates = listOfNotNull(
            ringtoneUri,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).distinct()

        for (uri in candidates) {
            val player = MediaPlayer()
            try {
                // 有耳机时用 USAGE_MEDIA：媒体流天然跟随耳机路由。
                // USAGE_ALARM 在多数厂商策略里被强制走扬声器（闹钟必须能叫醒人），
                // setPreferredDevice 常被忽略，这正是「插着耳机却从扬声器出声」的原因。
                // 无耳机时回退 USAGE_ALARM，走闹钟通道、不受静音模式影响。
                val usage = if (device != null) {
                    AudioAttributes.USAGE_MEDIA
                } else {
                    AudioAttributes.USAGE_ALARM
                }
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(context, uri)
                player.isLooping = true
                player.setVolume(1f, 1f)
                Log.d(TAG, "audioUsage=${if (device != null) "MEDIA(耳机)" else "ALARM(扬声器)"}")
                // 必须在 prepare() 之前设置首选设备，否则路由偏好不生效，
                // 闹铃会从扬声器出声而不是耳机
                if (device != null) {
                    val applied = player.setPreferredDevice(device)
                    Log.d(TAG, "setPreferredDevice(${deviceLabel(device)}) -> $applied")
                }
                player.prepare()
                player.start()
                Log.d(TAG, "开始播放闹铃: $uri")

                if (vibrationMode != VibrationMode.OFF) {
                    vibrate(context)
                }
                return player
            } catch (t: Throwable) {
                Log.e(TAG, "播放闹铃失败，尝试下一个候选铃声: $uri", t)
                runCatching { player.release() }
            }
        }

        Log.e(TAG, "所有候选铃声均播放失败，本次仅振动")
        if (vibrationMode != VibrationMode.OFF) {
            vibrate(context)
        }
        return null
    }

    /** 设备类型标签，便于日志定位路由问题 */
    private fun deviceLabel(device: AudioDeviceInfo): String {
        val name = when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳麦"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙A2DP"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙SCO"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙LE耳机"
            AudioDeviceInfo.TYPE_HEARING_AID -> "助听器"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB耳麦"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB音频"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB配件音频"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
            else -> "其他(${device.type})"
        }
        return "$name#${device.id}"
    }

    /** 当前全部输出设备（仅日志用） */
    private fun describeOutputs(context: Context): String = try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).joinToString { deviceLabel(it) }
    } catch (_: SecurityException) {
        "读取被拒"
    }

    /**
     * 耳机设备优先级，越靠前越优先。
     *
     * 关键的坑：蓝牙耳机同时暴露 A2DP 与 SCO 两个输出设备，
     * SCO 是双向通话通道，把媒体/铃声路由过去往往完全无声，
     * 因此必须让 A2DP 排在前面，SCO 兜底到底。
     */
    private fun headphonePriority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> 0
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 0
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 1
        AudioDeviceInfo.TYPE_BLE_HEADSET -> 2
        AudioDeviceInfo.TYPE_USB_HEADSET -> 3
        AudioDeviceInfo.TYPE_USB_DEVICE -> 4
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> 5
        AudioDeviceInfo.TYPE_HEARING_AID -> 6
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 7
        else -> Int.MAX_VALUE
    }

    /** 按优先级挑选输出设备，无可用耳机时返回 null（不强制路由） */
    private fun findHeadphoneDevice(context: Context): AudioDeviceInfo? = runCatching {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in HEADPHONE_DEVICE_TYPES && it.isSink }
            .minByOrNull { headphonePriority(it.type) }
    }.getOrNull()

    /**
     * 启动震动。
     *
     * 第二个参数 `repeat = 0` 表示**从索引 0 开始循环**（`-1` 才是只播一次）。
     * 闹钟需要持续震动直到用户处理，因此必须先启动循环、再在关闭时调用 [cancelVibration]；
     * 漏掉 cancel 会导致铃声停了震动还在响。
     */
    private fun vibrate(context: Context) {
        vibrator(context).vibrate(longArrayOf(0, 500, 200, 500), 0)
    }

    /** 停止震动。关闭闹钟、稍后提醒、页面销毁时都必须调用 */
    fun cancelVibration(context: Context) {
        runCatching { vibrator(context).cancel() }
    }

    private fun vibrator(context: Context): Vibrator =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
}