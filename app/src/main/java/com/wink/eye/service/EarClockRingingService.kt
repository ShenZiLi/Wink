package com.wink.eye.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wink.eye.EarClockAlarmActivity
import com.wink.eye.R
import com.wink.eye.WinkApp

/**
 * 闹钟响铃的前台服务。
 *
 * 音频播放不能依赖全屏 Activity：锁屏时后台 Activity 可能被系统拦截，
 * 而由 setAlarmClock 触发的前台服务可以独立、持续地播放闹铃。
 */
class EarClockRingingService : Service() {

    private var player: MediaPlayer? = null
    private var ringingAlarmId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getStringExtra(EarClockAlarmScheduler.EXTRA_ALARM_ID)
            ?: return START_NOT_STICKY
        val isSnooze = intent.getBooleanExtra(EarClockAlarmScheduler.EXTRA_IS_SNOOZE, false)
        val snoozeCount = intent.getIntExtra(EarClockAlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
        val alarm = WinkApp.instance.earClockRepository.getById(alarmId)
            ?: return START_NOT_STICKY

        stopRinging()
        ringingAlarmId = alarmId
        startForeground(
            alarmId.hashCode(),
            buildNotification(alarmId, alarm.name, isSnooze, snoozeCount),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        player = EarClockAudioHelper.playAlarm(
            this,
            alarm.ringtoneUri?.let(android.net.Uri::parse),
            alarm.vibrationMode
        )
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun buildNotification(
        alarmId: String,
        alarmName: String,
        isSnooze: Boolean,
        snoozeCount: Int
    ): Notification {
        ensureChannel()
        val alarmIntent = Intent(this, EarClockAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EarClockAlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(EarClockAlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EarClockAlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.earclock_notification_title))
            .setContentText(getString(R.string.earclock_notification_text, alarmName))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .build()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.earclock_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.earclock_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopRinging() {
        player?.run {
            runCatching { if (isPlaying) stop() }
            release()
        }
        player = null
        EarClockAudioHelper.cancelVibration(this)
        ringingAlarmId?.let { getSystemService(NotificationManager::class.java).cancel(it.hashCode()) }
        ringingAlarmId = null
    }

    companion object {
        private const val CHANNEL_ID = "wink_earclock"

        fun start(context: Context, alarmId: String, isSnooze: Boolean, snoozeCount: Int) {
            val intent = Intent(context, EarClockRingingService::class.java).apply {
                putExtra(EarClockAlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(EarClockAlarmScheduler.EXTRA_IS_SNOOZE, isSnooze)
                putExtra(EarClockAlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EarClockRingingService::class.java))
        }
    }
}
