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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
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

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isScreenOn) {
                val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
                checkScreenTimeRules(currentAccumulated)
                updateDebugInfo()
            }
            handler.postDelayed(this, checkIntervalMs)
        }
    }

    private val checkIntervalMs: Long
        get() {
            val minThresholdMs = getMinScreenOnThresholdMs()
            if (minThresholdMs <= 0) return 60_000L
            // 检查间隔为最小阈值的 1/10，最低 1 秒
            return (minThresholdMs / 10).coerceIn(1_000L, 60_000L)
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

        // 初始化屏幕状态
        isScreenOn = true
        screenOnTime = System.currentTimeMillis()

        // 启动定时检查
        handler.post(checkRunnable)

        // 亮屏时调度 AlarmManager 闹钟
        scheduleScreenTimeAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
        cancelScreenTimeAlarm()
        screenReceiver?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 服务被杀后重启
        val restartIntent = Intent(this, ScreenMonitorService::class.java)
        startForegroundService(restartIntent)
        super.onTaskRemoved(rootIntent)
    }

    fun handleScreenOn() {
        val now = System.currentTimeMillis()
        if (!isScreenOn) {
            val offDuration = now - screenOffTime
            // 检查所有亮屏时长规则，取最小重置时间
            val minResetMs = getMinScreenOffResetMs()
            if (minResetMs > 0 && offDuration >= minResetMs) {
                // 暗屏超过重置阈值，重置累计时间
                accumulatedScreenOnMs = 0L
            }
            isScreenOn = true
            screenOnTime = now

            // 亮屏时立即检查并调度闹钟
            val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
            checkScreenTimeRules(currentAccumulated)
            scheduleScreenTimeAlarm()
            updateDebugInfo()
        }
    }

    fun handleScreenOff() {
        if (isScreenOn) {
            accumulatedScreenOnMs += System.currentTimeMillis() - screenOnTime
            isScreenOn = false
            screenOffTime = System.currentTimeMillis()
            lastScreenOffTimestamp = screenOffTime

            // 暗屏时取消待触发的闹钟
            cancelScreenTimeAlarm()
            updateDebugInfo()
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

    // ---- AlarmManager 调度（保证后台/Doze 下可靠触发）----

    private fun scheduleScreenTimeAlarm() {
        if (!isScreenOn) return

        val minRemainingMs = getMinRemainingScreenOnMs()
        if (minRemainingMs <= 0) {
            // 已经超过阈值，立即检查
            val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
            checkScreenTimeRules(currentAccumulated)
            // 检查后可能需要再调度
            scheduleScreenTimeAlarm()
            return
        }

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

        // 无精确闹钟权限时用非精确闹钟
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

    private fun checkScreenTimeRules(currentAccumulatedMs: Long) {
        val rules = WinkApp.instance.ruleRepository.getAll()
        rules.filter { it.enabled && it.type is RuleType.ScreenTime }.forEach { rule ->
            val screenTimeType = rule.type as RuleType.ScreenTime
            val thresholdMs = screenTimeType.screenOnDuration * if (screenTimeType.screenOnUnit == ScreenTimeUnit.MINUTES) 60_000L else 1_000L
            if (currentAccumulatedMs >= thresholdMs) {
                ReminderHelper.triggerReminder(this, rule)
                // 触发后重置累计时间
                accumulatedScreenOnMs = 0L
                screenOnTime = System.currentTimeMillis()
            }
        }
    }

    /** 获取所有启用的亮屏时长规则中最小的阈值（毫秒） */
    private fun getMinScreenOnThresholdMs(): Long {
        val rules = WinkApp.instance.ruleRepository.getAll()
        return rules
            .filter { it.enabled && it.type is RuleType.ScreenTime }
            .minOfOrNull {
                val st = it.type as RuleType.ScreenTime
                st.screenOnDuration * if (st.screenOnUnit == ScreenTimeUnit.MINUTES) 60_000L else 1_000L
            }
            ?: 0L
    }

    /** 获取当前亮屏累计时间距离最小阈值还剩多少毫秒 */
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
                st.screenOffResetDuration * if (st.screenOffResetUnit == ScreenTimeUnit.MINUTES) 60_000L else 1_000L
            }
            ?: 0L
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
            // 重新调度下一次闹钟
            scheduleScreenTimeAlarm()
            updateDebugInfo()
        }
    }
}
