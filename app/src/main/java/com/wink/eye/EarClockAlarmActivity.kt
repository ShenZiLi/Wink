package com.wink.eye

import android.app.NotificationManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wink.eye.data.EarClockFrequency
import com.wink.eye.data.VibrationMode
import com.wink.eye.service.EarClockAlarmScheduler
import com.wink.eye.service.EarClockAudioHelper
import java.util.Locale

/** EarClock 全屏闹钟页：连接耳机时循环播放闹铃，提供「立即关闭」与「稍后提醒」 */
class EarClockAlarmActivity : ComponentActivity() {

    /** 当前响铃的闹钟 ID，用于退出时清理通知 */
    private var ringingAlarmId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alarmId = intent.getStringExtra(EarClockAlarmScheduler.EXTRA_ALARM_ID).also {
            ringingAlarmId = it
        }
        val alarm = alarmId?.let { WinkApp.instance.earClockRepository.getById(it) }
        if (alarm == null) {
            finish()
            return
        }

        val isSnooze = intent.getBooleanExtra(EarClockAlarmScheduler.EXTRA_IS_SNOOZE, false)
        val snoozeCount = intent.getIntExtra(EarClockAlarmScheduler.EXTRA_SNOOZE_COUNT, 0)

        // 锁屏上显示
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val player = EarClockAudioHelper.playAlarm(
            this,
            alarm.ringtoneUri?.let(Uri::parse),
            alarm.vibrationMode
        )

        val timeLabel = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
        val canSnooze = alarm.snoozeEnabled && snoozeCount < alarm.snoozeRepeatLimit

        setContent {
            val context = LocalContext.current
            DisposableEffect(Unit) {
                onDispose {
                    releasePlayer(player)
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = alarm.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = getString(R.string.earclock_playing_in_headphone),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSnooze) {
                    Text(
                        text = getString(R.string.earclock_snooze_times, snoozeCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(40.dp))
                if (canSnooze) {
                    OutlinedButton(
                        onClick = {
                            releasePlayer(player)
                            EarClockAlarmScheduler.scheduleSnooze(context, alarm, snoozeCount + 1)
                            finish()
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(
                            text = getString(R.string.earclock_snooze),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Button(
                    onClick = {
                        releasePlayer(player)
                        dismiss(context, alarm, isSnooze)
                    },
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(
                        text = getString(R.string.earclock_dismiss),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }

    /** 关闭闹钟；ONCE 且非稍后提醒时自动停用该闹钟 */
    private fun dismiss(context: android.content.Context, alarm: com.wink.eye.data.EarClockAlarm, isSnooze: Boolean) {
        if (alarm.frequency == EarClockFrequency.ONCE && !isSnooze) {
            val updated = alarm.copy(enabled = false)
            WinkApp.instance.earClockRepository.save(updated)
            EarClockAlarmScheduler.cancel(context, alarm.id)
        }
        finish()
    }

    private fun releasePlayer(player: android.media.MediaPlayer?) {
        player?.apply {
            try {
                if (isPlaying) stop()
            } catch (_: Exception) {}
            release()
        }
        // 震动是循环播放的，没有这一步会在铃声停止后继续震
        EarClockAudioHelper.cancelVibration(this)
    }

    override fun onDestroy() {
        // 无论用户以何种方式退出，都必须清掉闹钟通知并停止震动
        ringingAlarmId?.let {
            getSystemService(NotificationManager::class.java).cancel(it.hashCode())
        }
        EarClockAudioHelper.cancelVibration(this)
        super.onDestroy()
    }
}