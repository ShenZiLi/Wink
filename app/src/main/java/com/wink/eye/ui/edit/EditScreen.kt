package com.wink.eye.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wink.eye.R
import com.wink.eye.data.IntervalUnit
import com.wink.eye.data.ReminderMode
import com.wink.eye.data.Rule
import com.wink.eye.data.RuleType
import com.wink.eye.data.ScreenTimeUnit
import java.util.UUID

/** Debug 开关：允许亮屏时长/暗屏重置使用秒级单位，正式上线时设为 false */
private const val DEBUG_SECONDS_ENABLED = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    existingRule: Rule?,
    onSave: (Rule) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(existingRule?.name ?: "护眼规则") }
    var ruleTypeIndex by remember { mutableStateOf(if (existingRule?.type is RuleType.ScreenTime) 1 else 0) }

    // 间隔时间设置
    val existingInterval = existingRule?.type as? RuleType.Interval
    var intervalValue by remember { mutableIntStateOf(existingInterval?.value ?: 30) }
    var intervalValueText by remember { mutableStateOf((existingInterval?.value ?: 30).toString()) }
    var intervalUnit by remember { mutableStateOf(existingInterval?.unit ?: IntervalUnit.MINUTES) }

    // 亮屏时长设置
    val existingScreenTime = existingRule?.type as? RuleType.ScreenTime
    var screenOnDuration by remember { mutableFloatStateOf(existingScreenTime?.screenOnDuration?.toFloat() ?: 30f) }
    var screenOffResetDuration by remember { mutableFloatStateOf(existingScreenTime?.screenOffResetDuration?.toFloat() ?: 5f) }
    var screenOnUnit by remember { mutableStateOf(existingScreenTime?.screenOnUnit ?: ScreenTimeUnit.MINUTES) }
    var screenOffResetUnit by remember { mutableStateOf(existingScreenTime?.screenOffResetUnit ?: ScreenTimeUnit.MINUTES) }
    var reminderMode by remember { mutableStateOf(existingRule?.reminderMode ?: ReminderMode.NOTIFICATION) }

    val isEditing = existingRule != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditing) stringResource(R.string.edit_title_edit)
                        else stringResource(R.string.edit_title_new)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 规则名称
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.edit_name_label)) },
                placeholder = { Text(stringResource(R.string.edit_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 规则类型选择
            Text(
                text = stringResource(R.string.edit_type_label),
                style = MaterialTheme.typography.titleSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ruleTypeIndex == 0,
                    onClick = { ruleTypeIndex = 0 },
                    label = { Text(stringResource(R.string.rule_type_interval)) }
                )
                FilterChip(
                    selected = ruleTypeIndex == 1,
                    onClick = { ruleTypeIndex = 1 },
                    label = { Text(stringResource(R.string.rule_type_screen)) }
                )
            }

            // 间隔时间设置
            if (ruleTypeIndex == 0) {
                // 自定义间隔：每 X 分钟/秒
                Text(
                    text = stringResource(R.string.edit_interval_custom_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("每", style = MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(
                        value = intervalValueText,
                        onValueChange = { text ->
                            intervalValueText = text.filter { it.isDigit() }
                            intervalValue = text.filter { it.isDigit() }.toIntOrNull() ?: 0
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    // 单位切换
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = intervalUnit == IntervalUnit.MINUTES,
                            onClick = { intervalUnit = IntervalUnit.MINUTES },
                            label = { Text("分钟") }
                        )
                        FilterChip(
                            selected = intervalUnit == IntervalUnit.SECONDS,
                            onClick = { intervalUnit = IntervalUnit.SECONDS },
                            label = { Text("秒") }
                        )
                    }
                }

                // 固定可选项
                Text(
                    text = stringResource(R.string.edit_interval_preset_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            intervalValue = 15
                            intervalValueText = "15"
                            intervalUnit = IntervalUnit.MINUTES
                        },
                        label = { Text("每 15 分钟") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            intervalValue = 30
                            intervalValueText = "30"
                            intervalUnit = IntervalUnit.MINUTES
                        },
                        label = { Text("每 30 分钟") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            intervalValue = 1
                            intervalValueText = "1"
                            intervalUnit = IntervalUnit.MINUTES
                        },
                        label = { Text("每 1 小时") }
                    )
                }
            }

            // 亮屏时长设置
            if (ruleTypeIndex == 1) {
                val screenOnUnitLabel = if (screenOnUnit == ScreenTimeUnit.MINUTES) "分钟" else "秒"
                val screenOffResetUnitLabel = if (screenOffResetUnit == ScreenTimeUnit.MINUTES) "分钟" else "秒"

                Column {
                    Text(
                        text = "${stringResource(R.string.edit_screen_on_label)}: ${screenOnDuration.toInt()} $screenOnUnitLabel",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = screenOnDuration,
                        onValueChange = { screenOnDuration = it },
                        valueRange = if (screenOnUnit == ScreenTimeUnit.MINUTES) 5f..120f else 5f..300f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (DEBUG_SECONDS_ENABLED) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = screenOnUnit == ScreenTimeUnit.MINUTES,
                                onClick = {
                                    screenOnUnit = ScreenTimeUnit.MINUTES
                                    screenOnDuration = 30f
                                },
                                label = { Text("分钟") }
                            )
                            FilterChip(
                                selected = screenOnUnit == ScreenTimeUnit.SECONDS,
                                onClick = {
                                    screenOnUnit = ScreenTimeUnit.SECONDS
                                    screenOnDuration = 30f
                                },
                                label = { Text("秒") }
                            )
                        }
                    }
                }

                Column {
                    Text(
                        text = "${stringResource(R.string.edit_screen_off_reset_label)}: ${screenOffResetDuration.toInt()} $screenOffResetUnitLabel",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = screenOffResetDuration,
                        onValueChange = { screenOffResetDuration = it },
                        valueRange = if (screenOffResetUnit == ScreenTimeUnit.MINUTES) 1f..30f else 5f..300f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (DEBUG_SECONDS_ENABLED) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = screenOffResetUnit == ScreenTimeUnit.MINUTES,
                                onClick = {
                                    screenOffResetUnit = ScreenTimeUnit.MINUTES
                                    screenOffResetDuration = 5f
                                },
                                label = { Text("分钟") }
                            )
                            FilterChip(
                                selected = screenOffResetUnit == ScreenTimeUnit.SECONDS,
                                onClick = {
                                    screenOffResetUnit = ScreenTimeUnit.SECONDS
                                    screenOffResetDuration = 30f
                                },
                                label = { Text("秒") }
                            )
                        }
                    }
                }
            }

            // 提醒方式
            Text(
                text = stringResource(R.string.edit_reminder_label),
                style = MaterialTheme.typography.titleSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = reminderMode == ReminderMode.ALARM,
                    onClick = { reminderMode = ReminderMode.ALARM },
                    label = { Text(stringResource(R.string.edit_reminder_alarm)) }
                )
                FilterChip(
                    selected = reminderMode == ReminderMode.NOTIFICATION,
                    onClick = { reminderMode = ReminderMode.NOTIFICATION },
                    label = { Text(stringResource(R.string.edit_reminder_notification)) }
                )
            }

            Spacer(Modifier.height(8.dp))

            // 保存按钮
            Button(
                onClick = {
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
                        name = name.ifBlank { "护眼规则" },
                        type = ruleType,
                        reminderMode = reminderMode,
                        enabled = existingRule?.enabled ?: true
                    )
                    onSave(rule)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && (ruleTypeIndex == 1 || intervalValue > 0)
            ) {
                Text(stringResource(R.string.edit_save))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
