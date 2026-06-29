package com.wink.eye.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import com.wink.eye.R
import com.wink.eye.WinkApp
import com.wink.eye.data.RuleType
import com.wink.eye.data.ScreenTimeUnit
import com.wink.eye.receiver.ScreenReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScreenDebugInfo(
    val accumulatedScreenOnMs: Long = 0L,
    val isScreenOn: Boolean = true,
    val screenOnStartMs: Long = 0L,
    val lastScreenOffTimestamp: Long = 0L
)

class ScreenMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var screenOnTime: Long = 0L
    private var screenOffTime: Long = 0L
    private var accumulatedScreenOnMs: Long = 0L
    private var isScreenOn: Boolean = true
    private var screenReceiver: ScreenReceiver? = null

    // #8 递归防护
    private var scheduleRecursionCount = 0

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isScreenOn) {
                val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
                checkScreenTimeRules(currentAccumulated)
                updateDebugInfo()
                updateForegroundNotification()
            }
            handler.postDelayed(this, checkIntervalMs)
        }
    }

    private val checkIntervalMs: Long
        get() {
            val minThresholdMs = getMinScreenOnThresholdMs()
            if (minThresholdMs <= 0) return 60_000L
            return (minThresholdMs / 10).coerceIn(1_000L, 60_000L)
        }

    // #2 状态持久化
    private val statePrefs: android.content.SharedPreferences
        get() = getSharedPreferences("wink_screen_state", Context.MODE_PRIVATE)

    private fun saveState() {
        statePrefs.edit()
            .putLong(KEY_ACCUMULATED_MS, accumulatedScreenOnMs)
            .putLong(KEY_SCREEN_ON_TIME, if (isScreenOn) screenOnTime else 0L)
            .putLong(KEY_SCREEN_OFF_TIME, screenOffTime)
            .putBoolean(KEY_IS_SCREEN_ON, isScreenOn)
            .putLong(KEY_LAST_SCREEN_OFF_TIMESTAMP, lastScreenOffTimestamp)
            .apply()
    }

    private fun restoreState() {
        val prefs = statePrefs
        // 如果没有保存过状态，使用默认值
        if (!prefs.contains(KEY_IS_SCREEN_ON)) return

        accumulatedScreenOnMs = prefs.getLong(KEY_ACCUMULATED_MS, 0L)
        isScreenOn = prefs.getBoolean(KEY_IS_SCREEN_ON, true)
        screenOffTime = prefs.getLong(KEY_SCREEN_OFF_TIME, 0L)
        lastScreenOffTimestamp = prefs.getLong(KEY_LAST_SCREEN_OFF_TIMESTAMP, 0L)

        val savedScreenOnTime = prefs.getLong(KEY_SCREEN_ON_TIME, 0L)
        if (isScreenOn && savedScreenOnTime > 0) {
            // 重启前是亮屏状态，累计从保存的 screenOnTime 开始
            screenOnTime = savedScreenOnTime
        } else {
            screenOnTime = System.currentTimeMillis()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // 注册屏幕广播接收器
        screenReceiver = ScreenReceiver().also {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(it, filter)
        }

        // #2 恢复持久化状态
        restoreState()

        // #6 查询实际屏幕状态（避免重启后状态不准）
        isScreenOn = isScreenCurrentlyOn().also { actualScreenOn ->
            if (actualScreenOn) {
                screenOnTime = System.currentTimeMillis()
            } else {
                screenOffTime = System.currentTimeMillis()
                lastScreenOffTimestamp = screenOffTime
            }
        }

        // 启动定时检查
        handler.post(checkRunnable)

        // 亮屏时调度 AlarmManager 闹钟
        if (isScreenOn) {
            scheduleScreenTimeAlarm()
        }

        updateDebugInfo()
        saveState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        cancelScreenTimeAlarm()
        screenReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    // #3 onTaskRemoved 安全重启（Android 12+ 不能直接 startForegroundService）
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 使用 AlarmManager 延迟重启
            val restartIntent = Intent(this, ScreenMonitorService::class.java)
            val pendingIntent = PendingIntent.getService(
                this, RESTART_REQUEST_CODE, restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
        } else {
            val restartIntent = Intent(this, ScreenMonitorService::class.java)
            startForegroundService(restartIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    fun handleScreenOn() {
        val now = System.currentTimeMillis()
        if (!isScreenOn) {
            val offDuration = now - screenOffTime
            val minResetMs = getMinScreenOffResetMs()
            if (minResetMs > 0 && offDuration >= minResetMs) {
                accumulatedScreenOnMs = 0L
            }
            isScreenOn = true
            screenOnTime = now

            val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
            checkScreenTimeRules(currentAccumulated)
            scheduleScreenTimeAlarm()
            updateDebugInfo()
            saveState()
        }
    }

    fun handleScreenOff() {
        if (isScreenOn) {
            accumulatedScreenOnMs += System.currentTimeMillis() - screenOnTime
            isScreenOn = false
            screenOffTime = System.currentTimeMillis()
            lastScreenOffTimestamp = screenOffTime

            cancelScreenTimeAlarm()
            updateDebugInfo()
            saveState()
        }
    }

    private fun updateDebugInfo() {
        _debugInfo.value = ScreenDebugInfo(
            accumulatedScreenOnMs = accumulatedScreenOnMs,
            isScreenOn = isScreenOn,
            screenOnStartMs = if (isScreenOn) screenOnTime else 0L,
            lastScreenOffTimestamp = lastScreenOffTimestamp
        )
    }

    // #16 前台通知显示实时亮屏时长
    private fun updateForegroundNotification() {
        val currentOnMs = if (isScreenOn) {
            accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
        } else {
            accumulatedScreenOnMs
        }
        val text = "${getString(R.string.service_notification_text)} · ${formatDuration(currentOnMs)}"
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "${hours}h${minutes}m${seconds}s" else "${minutes}m${seconds}s"
    }

    // ---- AlarmManager 调度 ----

    private fun scheduleScreenTimeAlarm() {
        if (!isScreenOn) return

        // #8 递归防护
        scheduleRecursionCount++
        if (scheduleRecursionCount > MAX_SCHEDULE_RECURSION) {
            Log.w(TAG, "scheduleScreenTimeAlarm 递归超过 $MAX_SCHEDULE_RECURSION 次，终止")
            scheduleRecursionCount = 0
            return
        }

        val minRemainingMs = getMinRemainingScreenOnMs()
        if (minRemainingMs <= 0) {
            val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
            checkScreenTimeRules(currentAccumulated)
            scheduleScreenTimeAlarm()
            return
        }

        // 成功调度后重置计数
        scheduleRecursionCount = 0

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMs = System.currentTimeMillis() + minRemainingMs

        val intent = Intent(this, ScreenTimeAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            SCREEN_TIME_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
                Log.d(TAG, "已调度亮屏闹钟: ${minRemainingMs}ms 后触发")
                return
            }
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMs,
            pendingIntent
        )
        Log.d(TAG, "已调度非精确亮屏闹钟: ${minRemainingMs}ms 后触发")
    }

    private fun cancelScreenTimeAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ScreenTimeAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            SCREEN_TIME_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "已取消亮屏闹钟")
        }
    }

    // ---- 规则检查 ----

    // #7 多规则时先收集再重置
    private fun checkScreenTimeRules(currentAccumulatedMs: Long) {
        val rules = WinkApp.instance.ruleRepository.getAll()
        val triggeredRules = mutableListOf<com.wink.eye.data.Rule>()
        var minThresholdMs = Long.MAX_VALUE

        rules.filter { it.enabled && it.type is RuleType.ScreenTime }.forEach { rule ->
            val screenTimeType = rule.type as RuleType.ScreenTime
            val thresholdMs = screenTimeType.effectiveScreenOnDuration * if (screenTimeType.screenOnUnit == ScreenTimeUnit.MINUTES) 60_000L else 1_000L
            if (currentAccumulatedMs >= thresholdMs) {
                triggeredRules.add(rule)
                minThresholdMs = minOf(minThresholdMs, thresholdMs)
            }
        }

        // 统一触发所有匹配的规则
        triggeredRules.forEach { rule ->
            ReminderHelper.triggerReminder(this, rule)
        }

        // 全部触发后重置一次
        if (triggeredRules.isNotEmpty()) {
            accumulatedScreenOnMs = 0L
            screenOnTime = System.currentTimeMillis()
        }
    }

    private fun getMinScreenOnThresholdMs(): Long {
        val rules = WinkApp.instance.ruleRepository.getAll()
        return rules
            .filter { it.enabled && it.type is RuleType.ScreenTime }
            .minOfOrNull {
                val st = it.type as RuleType.ScreenTime
                st.effectiveScreenOnDuration * if (st.screenOnUnit == ScreenTimeUnit.MINUTES) 60_000L else 1_000L
            }
            ?: 0L
    }

    private fun getMinRemainingScreenOnMs(): Long {
        if (!isScreenOn) return Long.MAX_VALUE
        val minThresholdMs = getMinScreenOnThresholdMs()
        if (minThresholdMs <= 0) return Long.MAX_VALUE
        val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
        return minThresholdMs - currentAccumulated
    }

    private fun getMinScreenOffResetMs(): Long {
        val rules = WinkApp.instance.ruleRepository.getAll()
        return rules
            .filter { it.enabled && it.type is RuleType.ScreenTime }
            .minOfOrNull {
                val st = it.type as RuleType.ScreenTime
                st.effectiveScreenOffResetDuration * if (st.screenOffResetUnit == ScreenTimeUnit.MINUTES) 60_000L else 1_000L
            }
            ?: 0L
    }

    // #6 查询当前屏幕实际状态
    private fun isScreenCurrentlyOn(): Boolean {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.displays.any { it.state != Display.STATE_OFF }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "ScreenMonitorService"
        private const val CHANNEL_ID = "wink_screen_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val SCREEN_TIME_ALARM_REQUEST_CODE = 2001
        private const val RESTART_REQUEST_CODE = 3001

        // #8 递归防护
        private const val MAX_SCHEDULE_RECURSION = 5

        // #2 状态持久化 key
        private const val KEY_ACCUMULATED_MS = "accumulated_ms"
        private const val KEY_SCREEN_ON_TIME = "screen_on_time"
        private const val KEY_SCREEN_OFF_TIME = "screen_off_time"
        private const val KEY_IS_SCREEN_ON = "is_screen_on"
        private const val KEY_LAST_SCREEN_OFF_TIMESTAMP = "last_screen_off_timestamp"

        private var lastScreenOffTimestamp: Long = 0L
        private val _debugInfo = MutableStateFlow(ScreenDebugInfo())
        val debugInfo: StateFlow<ScreenDebugInfo> = _debugInfo

        fun start(context: Context) {
            val intent = Intent(context, ScreenMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenMonitorService::class.java)
            context.stopService(intent)
        }

        fun startScreenOn(context: Context) {
            val intent = Intent(context, ScreenMonitorService::class.java).apply {
                action = ACTION_SCREEN_ON
            }
            context.startService(intent)
        }

        fun startScreenOff(context: Context) {
            val intent = Intent(context, ScreenMonitorService::class.java).apply {
                action = ACTION_SCREEN_OFF
            }
            context.startService(intent)
        }

        const val ACTION_SCREEN_ON = "com.wink.eye.SCREEN_ON"
        const val ACTION_SCREEN_OFF = "com.wink.eye.SCREEN_OFF"
        const val ACTION_CHECK_SCREEN_TIME = "com.wink.eye.CHECK_SCREEN_TIME"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCREEN_ON -> handleScreenOn()
            ACTION_SCREEN_OFF -> handleScreenOff()
            ACTION_CHECK_SCREEN_TIME -> handleCheckScreenTime()
        }
        return START_STICKY
    }

    private fun handleCheckScreenTime() {
        if (isScreenOn) {
            val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
            checkScreenTimeRules(currentAccumulated)
            scheduleScreenTimeAlarm()
            updateDebugInfo()
        }
    }
}
