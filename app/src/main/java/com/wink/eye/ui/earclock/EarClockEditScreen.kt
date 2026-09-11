package com.wink.eye.ui.earclock

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wink.eye.R
import com.wink.eye.data.EarClockAlarm
import com.wink.eye.data.EarClockFrequency
import com.wink.eye.data.VibrationMode
import com.wink.eye.service.EarClockAlarmScheduler
import com.wink.eye.ui.components.WinkGlassTopBar
import com.wink.eye.ui.components.WinkGlassTopBarDefaults
import com.wink.eye.ui.components.winkGlassSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import java.util.Calendar
import java.util.UUID

/** 一周自定义顺序：周一..周日 */
private val CUSTOM_DAYS = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

/** 设置闹钟页：对齐 UI 设计文档（导航 + 倒计时 + 滚轮时间 + 频率 + 设置分组卡片） */
@Composable
fun EarClockEditScreen(
    existingAlarm: EarClockAlarm?,
    onSave: (EarClockAlarm) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(existingAlarm?.name ?: context.getString(R.string.earclock_default_name)) }
    // 新增闹钟默认滚轮时间为当前时间
    var hour by remember { mutableIntStateOf(existingAlarm?.hour ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(existingAlarm?.minute ?: Calendar.getInstance().get(Calendar.MINUTE)) }
    var frequency by remember { mutableStateOf(existingAlarm?.frequency ?: EarClockFrequency.ONCE) }
    var daysOfWeek by remember { mutableStateOf(existingAlarm?.daysOfWeek ?: emptySet()) }
    var ringtoneUri by remember { mutableStateOf(existingAlarm?.ringtoneUri) }
    var ringtoneName by remember { mutableStateOf<String?>(null) }
    var vibrationOn by remember { mutableStateOf(existingAlarm?.vibrationMode != VibrationMode.OFF) }
    var snoozeEnabled by remember { mutableStateOf(existingAlarm?.snoozeEnabled ?: true) }
    var snoozeMinutes by remember { mutableIntStateOf(existingAlarm?.snoozeMinutes ?: 5) }
    var snoozeRepeatLimit by remember { mutableIntStateOf(existingAlarm?.snoozeRepeatLimit ?: 3) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            ringtoneUri = uri.toString()
            ringtoneName = RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }
    }

    // 顶栏玻璃的采样源：由表单内容提供被模糊的画面
    val topBarHazeState = rememberHazeState()
    val topBarHeight = WinkGlassTopBarDefaults.contentTopPadding()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 内容自行处理状态栏留白，使内容能从悬浮玻璃顶栏下方穿过
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .winkGlassSource(topBarHazeState)
                // 正常情况下整页无需滚动；小屏或大字体时作为兜底，避免内容被裁切
                .verticalScroll(rememberScrollState())
                .padding(top = topBarHeight)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CountdownSection(hour, minute, frequency, daysOfWeek)

            WheelTimePicker(
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it }
            )

            // 频率切换
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = frequency == EarClockFrequency.ONCE,
                    onClick = { frequency = EarClockFrequency.ONCE },
                    label = { Text(stringResource(R.string.earclock_freq_once)) }
                )
                FilterChip(
                    selected = frequency == EarClockFrequency.WORKDAYS,
                    onClick = { frequency = EarClockFrequency.WORKDAYS },
                    label = { Text(stringResource(R.string.earclock_freq_workdays)) }
                )
                FilterChip(
                    selected = frequency == EarClockFrequency.CUSTOM,
                    onClick = { frequency = EarClockFrequency.CUSTOM },
                    label = { Text(stringResource(R.string.earclock_freq_custom)) }
                )
            }

            // 自定义频率：一周自选（一行等分铺满）
            if (frequency == EarClockFrequency.CUSTOM) {
                val dayNames = listOf(
                    stringResource(R.string.day_mon), stringResource(R.string.day_tue),
                    stringResource(R.string.day_wed), stringResource(R.string.day_thu),
                    stringResource(R.string.day_fri), stringResource(R.string.day_sat),
                    stringResource(R.string.day_sun)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CUSTOM_DAYS.forEachIndexed { index, day ->
                        val sel = day in daysOfWeek
                        Surface(
                            onClick = {
                                daysOfWeek = if (sel) daysOfWeek - day else daysOfWeek + day
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (sel) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = if (sel) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            } else {
                                null
                            }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNames[index],
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (sel) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            SettingsCard(
                // 与上方星期选择/频率切换拉开间距；两种频率下都能保证不贴在一起
                modifier = Modifier.padding(top = 16.dp),
                name = name,
                onNameChange = { name = it },
                workdayLabel = stringResource(R.string.earclock_edit_workday_weekdays),
                ringtoneName = ringtoneName,
                onPickRingtone = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri)
                    }
                    ringtoneLauncher.launch(intent)
                },
                vibrationOn = vibrationOn,
                onVibrationChange = { vibrationOn = it },
                snoozeEnabled = snoozeEnabled,
                onSnoozeEnabledChange = { snoozeEnabled = it },
                snoozeMinutes = snoozeMinutes,
                onSnoozeMinutesChange = { snoozeMinutes = it },
                snoozeRepeatLimit = snoozeRepeatLimit,
                onSnoozeRepeatLimitChange = { snoozeRepeatLimit = it },
                frequency = frequency
            )
        }

        // 悬浮液态玻璃顶栏：叠在表单之上，内容会从其下方穿过
        WinkGlassTopBar(
            title = stringResource(R.string.earclock_edit_title),
            hazeState = topBarHazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            navigationIcon = {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.edit_back), color = MaterialTheme.colorScheme.primary)
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        onSave(buildAlarm(existingAlarm, name, hour, minute, frequency,
                            daysOfWeek, ringtoneUri, vibrationOn, snoozeEnabled, snoozeMinutes, snoozeRepeatLimit))
                    },
                    enabled = isValid(frequency, daysOfWeek)
                ) {
                    Text(stringResource(R.string.edit_save), color = MaterialTheme.colorScheme.primary)
                }
            },
            // 项目规范：编辑页标题居中
            centeredTitle = true
        )
        }
    }
}

/** 距离下次响铃倒计时：每秒刷新，单行展示 */
@Composable
private fun CountdownSection(hour: Int, minute: Int, frequency: EarClockFrequency, daysOfWeek: Set<Int>) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Text(
        text = "${stringResource(R.string.earclock_countdown_prefix)} ${remainingLabel(now, hour, minute, frequency, daysOfWeek)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun remainingLabel(now: Long, hour: Int, minute: Int,
                           frequency: EarClockFrequency, daysOfWeek: Set<Int>): String {
    val dummy = EarClockAlarm(
        id = "dummy", name = "", enabled = true,
        hour = hour, minute = minute, frequency = frequency, daysOfWeek = daysOfWeek
    )
    val diffMs = (EarClockAlarmScheduler.nextTriggerMillis(dummy, now) - now).coerceAtLeast(0)
    val totalMin = diffMs / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}小时${m}分钟" else "${m}分钟"
}

@Composable
private fun isValid(frequency: EarClockFrequency, daysOfWeek: Set<Int>): Boolean {
    return when (frequency) {
        EarClockFrequency.ONCE, EarClockFrequency.WORKDAYS -> true
        EarClockFrequency.CUSTOM -> daysOfWeek.isNotEmpty()
    }
}

private fun buildAlarm(
    existingAlarm: EarClockAlarm?,
    name: String,
    hour: Int,
    minute: Int,
    frequency: EarClockFrequency,
    daysOfWeek: Set<Int>,
    ringtoneUri: String?,
    vibrationOn: Boolean,
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
    snoozeRepeatLimit: Int
): EarClockAlarm = EarClockAlarm(
    id = existingAlarm?.id ?: UUID.randomUUID().toString(),
    name = name,
    enabled = existingAlarm?.enabled ?: true,
    hour = hour,
    minute = minute,
    frequency = frequency,
    daysOfWeek = daysOfWeek,
    ringtoneUri = ringtoneUri,
    vibrationMode = if (vibrationOn) VibrationMode.DEFAULT else VibrationMode.OFF,
    snoozeEnabled = snoozeEnabled,
    snoozeMinutes = snoozeMinutes,
    snoozeRepeatLimit = snoozeRepeatLimit
)

/** 双列滚轮时间选择器 */
@Composable
private fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WheelColumn(0..23, hour, onHourChange)
        Text(text = ":", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 4.dp))
        WheelColumn(0..59, minute, onMinuteChange)
    }
}

/** 单列滚轮：以穿过中线的值确定选中，切换项时触发触觉反馈，支持循环滚动 */
@Composable
private fun WheelColumn(range: IntRange, selected: Int, onSelect: (Int) -> Unit) {
    val itemHeight = 48.dp
    val totalItems = range.last - range.first + 1
    // 循环滚动：remember 缓存列表，避免每次重组重新创建 24000 个元素
    val items = remember(totalItems) {
        List(totalItems * 1000) { (it + range.first) % (totalItems) + range.first }
    }
    val initialIndex = (items.size / 2) + (selected - range.first)
    val listState: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = (initialIndex - 1).coerceAtLeast(0))
    val haptic = LocalHapticFeedback.current
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    // 记住最新 selected，避免 LaunchedEffect(listState) 闭包捕获旧值导致回到初始值(如当前小时)时被判定为“未变化”而漏选。
    val currentSelected by rememberUpdatedState(selected)

    // 跳到首帧：避免初始定位误触发选中/振动
    var skipInit by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        // 每帧发射，确保高刷下无延迟
        .distinctUntilChanged()
        .collect { (i0, scrollOffset) ->
            if (skipInit) {
                skipInit = false
                return@collect
            }
            val v = centeredValueFrom(i0, scrollOffset, itemHeightPx, range)
            if (v != currentSelected) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect(v)
            }
        }
    }

    // 松手吸附（权威）：滚动停滞后，先就近解析命中的值并强制选中，再将其对齐到中线。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val v = centeredValueFrom(
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset,
                        itemHeightPx,
                        range
                    )
                    if (v != currentSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(v)
                    }
                    // 保持滚动位置在中间区域，实现无限循环
                    val vIndex = (items.size / 2) + (v - range.first)
                    listState.scrollToItem((vIndex - 1).coerceAtLeast(0))
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .height(itemHeight * 3)
            .width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(items) { index, value ->
            val isSel = value == selected
            Box(
                modifier = Modifier
                    .height(itemHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.toString(),
                    style = if (isSel) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(if (isSel) 1f else 0.45f)
                )
            }
        }
    }
}

/**
 * 纯算术计算当前穿过可视区垂直中线的值，支持循环范围映射。
 * @param i0 第一个可见项索引
 * @param scrollOffset 第一个可见项已被滚过的像素
 * @param itemHeightPx 单项高度（像素）
 * @param range 循环范围（如 0..23, 0..59）
 * viewport 高度 = 3 * itemHeight，中线在 1.5 * itemHeight 处。
 */
private fun centeredValueFrom(i0: Int, scrollOffset: Int, itemHeightPx: Float, range: IntRange): Int {
    val totalItems = range.last - range.first + 1
    val raw = i0 + ((1.5f * itemHeightPx + scrollOffset) / itemHeightPx).toInt()
    // 模运算实现循环映射：处理正负索引
    val normalized = (raw - range.first) % totalItems
    val mapped = if (normalized < 0) normalized + totalItems else normalized
    return mapped + range.first
}

/** 设置分组卡片 */
@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    name: String,
    onNameChange: (String) -> Unit,
    workdayLabel: String,
    ringtoneName: String?,
    onPickRingtone: () -> Unit,
    vibrationOn: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    snoozeEnabled: Boolean,
    onSnoozeEnabledChange: (Boolean) -> Unit,
    snoozeMinutes: Int,
    onSnoozeMinutesChange: (Int) -> Unit,
    snoozeRepeatLimit: Int,
    onSnoozeRepeatLimitChange: (Int) -> Unit,
    frequency: EarClockFrequency
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            // 工作日类型（仅 WORKDAYS 可见）
            if (frequency == EarClockFrequency.WORKDAYS) {
                SettingRow(title = stringResource(R.string.earclock_edit_workday_type), value = workdayLabel, chevron = false)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // 闹钟名称
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.earclock_edit_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    // 留出浮动 label 的空间，避免「闹钟名称」被卡片上缘压住
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 铃声
            ClickableSettingRow(
                title = stringResource(R.string.earclock_edit_ringtone),
                value = ringtoneName ?: stringResource(R.string.earclock_edit_ringtone_default),
                onClick = onPickRingtone
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 振动
            SwitchRow(
                title = stringResource(R.string.earclock_edit_vibration),
                checked = vibrationOn,
                onCheckedChange = onVibrationChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 稍后提醒
            SwitchRow(
                title = stringResource(R.string.earclock_edit_snooze),
                checked = snoozeEnabled,
                onCheckedChange = onSnoozeEnabledChange
            )
            if (snoozeEnabled) {
                // 两组参数压进同一行：改用比 FilterChip 更矮更窄的紧凑块，
                // 省下约 40dp 纵向空间，换取整页一屏展示（时间轮保持原生 48dp 高度）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左右两段等分，标签与选项块对齐
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.earclock_edit_snooze_minutes_short),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        listOf(5, 10, 15).forEach { v ->
                            SnoozeOptionChip(
                                text = v.toString(),
                                selected = snoozeMinutes == v,
                                onClick = { onSnoozeMinutesChange(v) }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.earclock_edit_snooze_times_short),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        listOf(1, 3, 5).forEach { v ->
                            SnoozeOptionChip(
                                text = v.toString(),
                                selected = snoozeRepeatLimit == v,
                                onClick = { onSnoozeRepeatLimitChange(v) }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 稍后提醒板块内的紧凑选项块。
 *
 * 比 Material3 的 FilterChip 更矮更窄（30dp / 横向 9dp），
 * 用于把「间隔」与「次数」两组选项压进同一行，避免各占一行。
 */
@Composable
private fun SnoozeOptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // 注意：不能用 Surface(onClick = ...) 重载 —— 它会把最小点击尺寸强制为 48dp，
    // 六块并排后会超出屏幕宽度，导致最右侧选项被裁切
    Surface(
        modifier = Modifier
            .height(30.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, chevron: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (chevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClickableSettingRow(title: String, value: String, onClick: () -> Unit) {
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}