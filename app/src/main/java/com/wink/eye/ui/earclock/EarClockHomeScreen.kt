package com.wink.eye.ui.earclock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ModeNight

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wink.eye.R
import com.wink.eye.data.EarClockAlarm
import com.wink.eye.data.EarClockFrequency
import com.wink.eye.ui.theme.ThemeManager
import com.wink.eye.ui.theme.ThemeMode
import com.wink.eye.ui.theme.WinkLayoutOverlay
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.earclock_home_title)) },
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (alarms.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // 底部预留悬浮菜单栏高度，与 Wink 页列表底部对齐，切换不跳动
                contentPadding = PaddingValues(bottom = WinkLayoutOverlay.BottomBarOverlayHeight)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(alarms, key = { it.id }) { alarm ->
                    Box(Modifier.animateItem()) {
                        AlarmCard(
                            alarm = alarm,
                            onToggle = { viewModel.toggleEnabled(alarm) },
                            onDelete = { alarmToDelete = alarm },
                            onClick = { onEditAlarm(alarm.id) }
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.earclock_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.earclock_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = if (alarm.enabled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.enabled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeLabel(alarm.hour, alarm.minute),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = frequencyLabel(alarm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.earclock_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun timeLabel(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

/** 生成频率与日期的摘要文字，供首页卡片副标题展示 */
@Composable
private fun frequencyLabel(alarm: EarClockAlarm): String {
    return when (alarm.frequency) {
        EarClockFrequency.ONCE -> stringResource(R.string.earclock_freq_once)
        EarClockFrequency.WORKDAYS -> stringResource(R.string.earclock_freq_workdays)
        EarClockFrequency.CUSTOM -> {
            if (alarm.daysOfWeek.isEmpty()) {
                stringResource(R.string.earclock_freq_custom)
            } else {
                val names = listOf(
                    stringResource(R.string.day_sun), stringResource(R.string.day_mon),
                    stringResource(R.string.day_tue), stringResource(R.string.day_wed),
                    stringResource(R.string.day_thu), stringResource(R.string.day_fri),
                    stringResource(R.string.day_sat)
                )
                alarm.daysOfWeek.sorted().joinToString(" ") {
                    names[it - Calendar.SUNDAY]
                }
            }
        }
    }
}