package com.wink.eye.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.wink.eye.R
import com.wink.eye.WinkApp
import com.wink.eye.data.RuleType
import com.wink.eye.data.ScreenTimeUnit
import com.wink.eye.receiver.ScreenReceiver

class ScreenMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var screenOnTime: Long = 0L
    private var screenOffTime: Long = 0L
    private var accumulatedScreenOnMs: Long = 0L
    private var isScreenOn: Boolean = true
    private var screenReceiver: ScreenReceiver? = null

    private val checkIntervalMs = 60_000L // 每分钟检查一次

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (isScreenOn) {
                val currentAccumulated = accumulatedScreenOnMs + (System.currentTimeMillis() - screenOnTime)
                checkScreenTimeRules(currentAccumulated)
            }
            handler.postDelayed(this, checkIntervalMs)
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

        // 初始化屏幕状态
        isScreenOn = true
        screenOnTime = System.currentTimeMillis()

        // 启动定时检查
        handler.post(checkRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(checkRunnable)
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
        }
    }

    fun handleScreenOff() {
        if (isScreenOn) {
            accumulatedScreenOnMs += System.currentTimeMillis() - screenOnTime
            isScreenOn = false
            screenOffTime = System.currentTimeMillis()
        }
    }

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
        private const val CHANNEL_ID = "wink_screen_monitor"
        private const val NOTIFICATION_ID = 1001

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCREEN_ON -> handleScreenOn()
            ACTION_SCREEN_OFF -> handleScreenOff()
        }
        return START_STICKY
    }
}
