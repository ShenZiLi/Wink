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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wink.eye.R
import com.wink.eye.data.ReminderMode
import com.wink.eye.data.Rule
import com.wink.eye.data.RuleType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    existingRule: Rule?,
    onSave: (Rule) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(existingRule?.name ?: "") }
    var ruleTypeIndex by remember { mutableStateOf(if (existingRule?.type is RuleType.ScreenTime) 1 else 0) }
    var cronExpression by remember { mutableStateOf((existingRule?.type as? RuleType.Cron)?.expression ?: "0 */30 * * * ?") }
    var screenOnMinutes by remember { mutableFloatStateOf((existingRule?.type as? RuleType.ScreenTime)?.screenOnMinutes?.toFloat() ?: 30f) }
    var screenOffResetMinutes by remember { mutableFloatStateOf((existingRule?.type as? RuleType.ScreenTime)?.screenOffResetMinutes?.toFloat() ?: 5f) }
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
                    label = { Text(stringResource(R.string.rule_type_cron)) }
                )
                FilterChip(
                    selected = ruleTypeIndex == 1,
                    onClick = { ruleTypeIndex = 1 },
                    label = { Text(stringResource(R.string.rule_type_screen)) }
                )
            }

            // Cron 表达式
            if (ruleTypeIndex == 0) {
                OutlinedTextField(
                    value = cronExpression,
                    onValueChange = { cronExpression = it },
                    label = { Text(stringResource(R.string.edit_cron_label)) },
                    placeholder = { Text(stringResource(R.string.edit_cron_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 常用预设
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = { cronExpression = "0 */30 * * * ?" },
                        label = { Text(stringResource(R.string.edit_cron_preset_30min)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { cronExpression = "0 0 * * * ?" },
                        label = { Text(stringResource(R.string.edit_cron_preset_1h)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { cronExpression = "0 0 */2 * * ?" },
                        label = { Text(stringResource(R.string.edit_cron_preset_2h)) }
                    )
                }
            }

            // 亮屏时长设置
            if (ruleTypeIndex == 1) {
                Column {
                    Text(
                        text = "${stringResource(R.string.edit_screen_on_label)}: ${screenOnMinutes.toInt()} 分钟",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = screenOnMinutes,
                        onValueChange = { screenOnMinutes = it },
                        valueRange = 5f..120f,
                        steps = 22,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Text(
                        text = "${stringResource(R.string.edit_screen_off_reset_label)}: ${screenOffResetMinutes.toInt()} 分钟",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = screenOffResetMinutes,
                        onValueChange = { screenOffResetMinutes = it },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        RuleType.Cron(cronExpression)
                    } else {
                        RuleType.ScreenTime(
                            screenOnMinutes = screenOnMinutes.toInt(),
                            screenOffResetMinutes = screenOffResetMinutes.toInt()
                        )
                    }
                    val rule = Rule(
                        id = existingRule?.id ?: UUID.randomUUID().toString(),
                        name = name.ifBlank { "未命名规则" },
                        type = ruleType,
                        reminderMode = reminderMode,
                        enabled = existingRule?.enabled ?: true
                    )
                    onSave(rule)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.edit_save))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
