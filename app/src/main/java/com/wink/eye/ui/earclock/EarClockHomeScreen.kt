package com.wink.eye.ui.earclock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.wink.eye.R
import com.wink.eye.data.EarClockAlarm
import com.wink.eye.data.EarClockFrequency
import com.wink.eye.ui.components.WinkConfigCard
import com.wink.eye.ui.components.WinkConfigList
import com.wink.eye.ui.components.WinkEmptyState
import com.wink.eye.ui.components.WinkGlassTopBar
import com.wink.eye.ui.components.WinkGlassTopBarDefaults
import com.wink.eye.ui.components.winkGlassSource
import dev.chrisbanes.haze.rememberHazeState
import com.wink.eye.ui.theme.ThemeManager
import com.wink.eye.ui.theme.ThemeMode
import java.util.Calendar
import java.util.Locale

/** EarClock 首页：闹钟列表、空状态、新增入口，风格与 Wink 首页一致 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarClockHomeScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (String) -> Unit,
    viewModel: EarClockHomeViewModel
) {
    val alarms by viewModel.alarms.collectAsState()
    val context = LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsState(initial = ThemeMode.LIGHT)

    var alarmToDelete by remember { mutableStateOf<EarClockAlarm?>(null) }
    alarmToDelete?.let { alarm ->
        AlertDialog(
            onDismissRequest = { alarmToDelete = null },
            title = { Text(stringResource(R.string.earclock_dialog_delete_title)) },
            text = { Text(stringResource(R.string.earclock_dialog_delete_message, alarm.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlarm(alarm.id)
                    alarmToDelete = null
                }) {
                    Text(stringResource(R.string.earclock_dialog_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { alarmToDelete = null }) {
                    Text(stringResource(R.string.earclock_dialog_delete_cancel))
                }
            }
        )
    }

    // 顶栏玻璃的采样源：由列表内容提供被模糊的画面
    val topBarHazeState = rememberHazeState()
    val topBarHeight = WinkGlassTopBarDefaults.totalHeight()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 页面内容自行处理状态栏留白，以便滚动内容能从悬浮玻璃顶栏下方穿过
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (alarms.isEmpty()) {
                WinkEmptyState(
                    title = stringResource(R.string.earclock_empty_title),
                    subtitle = stringResource(R.string.earclock_empty_subtitle),
                    modifier = Modifier.padding(top = topBarHeight)
                )
            } else {
                WinkConfigList(
                    list = alarms,
                    key = { it.id },
                    modifier = Modifier
                        .fillMaxSize()
                        .winkGlassSource(topBarHazeState),
                    extraTopPadding = topBarHeight
                ) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onToggle = { viewModel.toggleEnabled(alarm) },
                        onDelete = { alarmToDelete = alarm },
                        onClick = { onEditAlarm(alarm.id) }
                    )
                }
            }

            // 悬浮液态玻璃顶栏：叠在内容之上，滚动时内容会从其下方穿过
            WinkGlassTopBar(
                title = stringResource(R.string.earclock_home_title),
                hazeState = topBarHazeState,
                modifier = Modifier.align(Alignment.TopCenter),
                actions = {
                    IconButton(onClick = { ThemeManager.toggle(context) }) {
                        Icon(
                            imageVector = when (themeMode) {
                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                ThemeMode.DARK -> Icons.Default.ModeNight
                            },
                            contentDescription = when (themeMode) {
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            }
                        )
                    }
                    IconButton(onClick = onAddAlarm) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.earclock_add)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: EarClockAlarm,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    WinkConfigCard(
        title = alarm.name,
        mainValue = timeLabel(alarm.hour, alarm.minute),
        badge = frequencyBadge(alarm),
        subtitle = frequencyDetail(alarm),
        leadingIcon = Icons.Default.Headphones,
        enabled = alarm.enabled,
        onToggle = onToggle,
        onDelete = onDelete,
        deleteContentDescription = stringResource(R.string.earclock_delete),
        onClick = onClick
    )
}

private fun timeLabel(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

/** 卡片底部徽标：重复频率类型 */
@Composable
private fun frequencyBadge(alarm: EarClockAlarm): String {
    return when (alarm.frequency) {
        EarClockFrequency.ONCE -> stringResource(R.string.earclock_freq_once)
        EarClockFrequency.WORKDAYS -> stringResource(R.string.earclock_freq_workdays)
        EarClockFrequency.CUSTOM -> stringResource(R.string.earclock_freq_custom)
    }
}

/** 卡片底部描述：仅自定义重复时展示具体星期，其余为空串 */
@Composable
private fun frequencyDetail(alarm: EarClockAlarm): String {
    if (alarm.frequency != EarClockFrequency.CUSTOM || alarm.daysOfWeek.isEmpty()) return ""
    val names = listOf(
        stringResource(R.string.day_sun), stringResource(R.string.day_mon),
        stringResource(R.string.day_tue), stringResource(R.string.day_wed),
        stringResource(R.string.day_thu), stringResource(R.string.day_fri),
        stringResource(R.string.day_sat)
    )
    return alarm.daysOfWeek.sorted().joinToString(" ") { names[it - Calendar.SUNDAY] }
}
