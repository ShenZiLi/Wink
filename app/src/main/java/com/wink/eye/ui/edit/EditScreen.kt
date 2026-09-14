package com.wink.eye.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wink.eye.R
import com.wink.eye.data.IntervalUnit
import com.wink.eye.data.ReminderMode
import com.wink.eye.data.Rule
import com.wink.eye.data.RuleType
import com.wink.eye.data.ScreenTimeUnit
import com.wink.eye.ui.components.WinkGlassTopBar
import com.wink.eye.ui.components.WinkGlassTopBarDefaults
import com.wink.eye.ui.components.winkGlassSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID

/** Debug 开关：允许亮屏时长/暗屏重置使用秒级单位，正式上线时设为 false */
private const val DEBUG_SECONDS_ENABLED = true

@Composable
fun EditScreen(
    existingRule: Rule?,
    onSave: (Rule) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existingRule?.name ?: "护眼规则") }
    var ruleTypeIndex by remember { mutableStateOf(if (existingRule?.type is RuleType.ScreenTime) 1 else 0) }

    // 间隔时间设置
    val existingInterval = existingRule?.type as? RuleType.Interval
    var intervalValue by remember { mutableIntStateOf(existingInterval?.value ?: 30) }
    var intervalValueText by remember { mutableStateOf((existingInterval?.value ?: 30).toString()) }
    var intervalUnit by remember { mutableStateOf(existingInterval?.unit ?: IntervalUnit.MINUTES) }

    // 亮屏时长设置
    val existingScreenTime = existingRule?.type as? RuleType.ScreenTime
    var screenOnDuration by remember { mutableFloatStateOf(existingScreenTime?.effectiveScreenOnDuration?.toFloat() ?: 30f) }
    var screenOffResetDuration by remember { mutableFloatStateOf(existingScreenTime?.effectiveScreenOffResetDuration?.toFloat() ?: 5f) }
    var screenOnUnit by remember { mutableStateOf(existingScreenTime?.screenOnUnit ?: ScreenTimeUnit.MINUTES) }
    var screenOffResetUnit by remember { mutableStateOf(existingScreenTime?.screenOffResetUnit ?: ScreenTimeUnit.MINUTES) }
    var reminderMode by remember { mutableStateOf(existingRule?.reminderMode ?: ReminderMode.NOTIFICATION) }

    val isEditing = existingRule != null

    // 预设选中状态
    val isPreset15 = ruleTypeIndex == 0 && intervalUnit == IntervalUnit.MINUTES && intervalValue == 15
    val isPreset30 = ruleTypeIndex == 0 && intervalUnit == IntervalUnit.MINUTES && intervalValue == 30
    val isPreset1h = ruleTypeIndex == 0 && intervalUnit == IntervalUnit.MINUTES && intervalValue == 60

    val isFormValid = name.isNotBlank() && (ruleTypeIndex == 1 || intervalValue > 0)

    // 顶栏「保存」与底部主按钮共用同一构建逻辑
    val saveRule: () -> Unit = {
        val ruleType = if (ruleTypeIndex == 0) {
            RuleType.Interval(value = intervalValue, unit = intervalUnit)
        } else {
            RuleType.ScreenTime(
                screenOnDuration = screenOnDuration.toInt(),
                screenOffResetDuration = screenOffResetDuration.toInt(),
                screenOnUnit = screenOnUnit,
                screenOffResetUnit = screenOffResetUnit
            )
        }
        val rule = Rule(
            id = existingRule?.id ?: UUID.randomUUID().toString(),
            name = name.ifBlank { context.getString(R.string.default_rule_name) },
            type = ruleType,
            reminderMode = reminderMode,
            enabled = existingRule?.enabled ?: true
        )
        onSave(rule)
    }

    // 顶栏玻璃的采样源：由表单内容提供被模糊的画面
    val topBarHazeState = rememberHazeState()
    val topBarHeight = WinkGlassTopBarDefaults.contentTopPadding()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 内容自行处理状态栏留白，使滚动内容能从悬浮玻璃顶栏下方穿过
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .winkGlassSource(topBarHazeState)
                .verticalScroll(rememberScrollState())
                .padding(top = topBarHeight)
                // 顶部间距已由 contentTopPadding 统一控制，这里只补底部
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 基础设置：规则名称 + 规则类型 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.edit_name_label)) },
                        placeholder = { Text(stringResource(R.string.edit_name_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionLabel(stringResource(R.string.edit_type_label))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SelectPill(
                                text = stringResource(R.string.rule_type_interval),
                                selected = ruleTypeIndex == 0,
                                onClick = { ruleTypeIndex = 0 },
                                modifier = Modifier.weight(1f)
                            )
                            SelectPill(
                                text = stringResource(R.string.rule_type_screen),
                                selected = ruleTypeIndex == 1,
                                onClick = { ruleTypeIndex = 1 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── 间隔时间设置 ──
            if (ruleTypeIndex == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SectionLabel(stringResource(R.string.edit_interval_custom_label))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = intervalValueText,
                                    onValueChange = { input: String ->
                                        val digits = input.filter(Char::isDigit)
                                        intervalValueText = digits
                                        intervalValue = digits.toIntOrNull() ?: 0
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                                    modifier = Modifier.width(140.dp)
                                )
                                SelectPill(
                                    text = stringResource(R.string.unit_minutes),
                                    selected = intervalUnit == IntervalUnit.MINUTES,
                                    onClick = { intervalUnit = IntervalUnit.MINUTES },
                                    modifier = Modifier.weight(1f)
                                )
                                SelectPill(
                                    text = stringResource(R.string.unit_seconds),
                                    selected = intervalUnit == IntervalUnit.SECONDS,
                                    onClick = { intervalUnit = IntervalUnit.SECONDS },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SectionLabel(stringResource(R.string.edit_interval_preset_label))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SelectPill(
                                    text = stringResource(R.string.preset_15min),
                                    selected = isPreset15,
                                    onClick = {
                                        intervalValue = 15; intervalValueText = "15"; intervalUnit = IntervalUnit.MINUTES
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SelectPill(
                                    text = stringResource(R.string.preset_30min),
                                    selected = isPreset30,
                                    onClick = {
                                        intervalValue = 30; intervalValueText = "30"; intervalUnit = IntervalUnit.MINUTES
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SelectPill(
                                    text = stringResource(R.string.preset_1hour),
                                    selected = isPreset1h,
                                    onClick = {
                                        intervalValue = 60; intervalValueText = "60"; intervalUnit = IntervalUnit.MINUTES
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // ── 亮屏时长设置 ──
            if (ruleTypeIndex == 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column {
                        SliderSection(
                            label = stringResource(R.string.edit_screen_on_label),
                            value = screenOnDuration,
                            unit = screenOnUnit,
                            onValueChange = { screenOnDuration = it },
                            minutesRange = 5f..120f,
                            minutesSteps = 22,
                            secondsRange = 5f..300f,
                            secondsSteps = 58,
                            onUnitChange = { unit ->
                                screenOnUnit = unit
                                screenOnDuration = 30f
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SliderSection(
                            label = stringResource(R.string.edit_screen_off_reset_label),
                            value = screenOffResetDuration,
                            unit = screenOffResetUnit,
                            onValueChange = { screenOffResetDuration = it },
                            minutesRange = 1f..30f,
                            minutesSteps = 28,
                            secondsRange = 5f..300f,
                            secondsSteps = 58,
                            onUnitChange = { unit ->
                                screenOffResetUnit = unit
                                screenOffResetDuration = if (unit == ScreenTimeUnit.MINUTES) 5f else 30f
                            }
                        )
                    }
                }
            }

            // ── 提醒方式 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionLabel(stringResource(R.string.edit_reminder_label))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectPill(
                            text = stringResource(R.string.edit_reminder_alarm),
                            selected = reminderMode == ReminderMode.ALARM,
                            onClick = { reminderMode = ReminderMode.ALARM },
                            modifier = Modifier.weight(1f)
                        )
                        SelectPill(
                            text = stringResource(R.string.edit_reminder_notification),
                            selected = reminderMode == ReminderMode.NOTIFICATION,
                            onClick = { reminderMode = ReminderMode.NOTIFICATION },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 保存按钮
            Button(
                onClick = saveRule,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                enabled = isFormValid,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    stringResource(R.string.edit_save),
                    modifier = Modifier.padding(vertical = 4.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 悬浮液态玻璃顶栏：叠在表单之上，滚动时内容会从其下方穿过
        WinkGlassTopBar(
            title = if (isEditing) stringResource(R.string.edit_title_edit)
            else stringResource(R.string.edit_title_new),
            hazeState = topBarHazeState,
            modifier = Modifier.align(Alignment.TopCenter),
            navigationIcon = {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.edit_back), color = MaterialTheme.colorScheme.primary)
                }
            },
            actions = {
                TextButton(onClick = saveRule, enabled = isFormValid) {
                    Text(stringResource(R.string.edit_save), color = MaterialTheme.colorScheme.primary)
                }
            },
            // 项目规范：编辑页标题居中
            centeredTitle = true
        )
        }
    }
}

/** 区块小标题：灰色小字，置于控件组上方 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 单选分段按钮：默认按内容自适应宽度；
 * 传入 [Modifier.weight] 即可等分铺满整行（用法见各选项组）。
 */
@Composable
private fun SelectPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
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
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** 亮屏时长卡片内的一行滑块设置：标题 + 当前值 + 滑杆 + 单位切换 */
@Composable
private fun SliderSection(
    label: String,
    value: Float,
    unit: ScreenTimeUnit,
    onValueChange: (Float) -> Unit,
    minutesRange: ClosedFloatingPointRange<Float>,
    minutesSteps: Int,
    secondsRange: ClosedFloatingPointRange<Float>,
    secondsSteps: Int,
    onUnitChange: (ScreenTimeUnit) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // Slider 使用离散档位；只在跨越档位时触发一次轻触感，避免连续拖动造成震动噪音。
    var lastHapticValue by remember(unit) { mutableIntStateOf(value.toInt()) }
    val unitLabel = if (unit == ScreenTimeUnit.MINUTES) {
        stringResource(R.string.unit_minutes)
    } else {
        stringResource(R.string.unit_seconds)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${value.toInt()} $unitLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = { nextValue ->
                val nextStep = nextValue.toInt()
                if (nextStep != lastHapticValue) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticValue = nextStep
                }
                onValueChange(nextValue)
            },
            valueRange = if (unit == ScreenTimeUnit.MINUTES) minutesRange else secondsRange,
            steps = if (unit == ScreenTimeUnit.MINUTES) minutesSteps else secondsSteps,
            modifier = Modifier.fillMaxWidth()
        )
        if (DEBUG_SECONDS_ENABLED) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectPill(
                    text = stringResource(R.string.unit_minutes),
                    selected = unit == ScreenTimeUnit.MINUTES,
                    onClick = { onUnitChange(ScreenTimeUnit.MINUTES) },
                    modifier = Modifier.weight(1f)
                )
                SelectPill(
                    text = stringResource(R.string.unit_seconds),
                    selected = unit == ScreenTimeUnit.SECONDS,
                    onClick = { onUnitChange(ScreenTimeUnit.SECONDS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
