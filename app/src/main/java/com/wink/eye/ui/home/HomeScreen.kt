package com.wink.eye.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.wink.eye.data.IntervalUnit
import com.wink.eye.data.Rule
import com.wink.eye.data.RuleType
import com.wink.eye.data.ScreenTimeUnit
import com.wink.eye.service.ScreenDebugInfo
import com.wink.eye.service.ScreenMonitorService
import com.wink.eye.ui.theme.ThemeManager
import com.wink.eye.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddRule: () -> Unit,
    onEditRule: (String) -> Unit,
    viewModel: HomeViewModel
) {
    val rules by viewModel.rules.collectAsState()
    val context = LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsState(initial = ThemeMode.LIGHT)
    val hasScreenTimeRule = rules.any { it.type is RuleType.ScreenTime }

    // #5 实时刷新 tick
    var tick by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            tick++
        }
    }
    val debugInfo by ScreenMonitorService.debugInfo.collectAsState()

    // #17 删除确认弹窗
    var ruleToDelete by remember { mutableStateOf<Rule?>(null) }
    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, rule.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRule(rule.id)
                    ruleToDelete = null
                }) {
                    Text(stringResource(R.string.dialog_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text(stringResource(R.string.dialog_delete_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wink") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
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
                    IconButton(onClick = onAddRule) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.home_add_rule)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (rules.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(
                            rule = rule,
                            onToggle = { viewModel.toggleEnabled(rule) },
                        onDelete = { ruleToDelete = rule },
                            onClick = { onEditRule(rule.id) }
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (hasScreenTimeRule) {
                    DebugInfoPanel(debugInfo, tick)
                }
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
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
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
                    text = rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val typeLabel = when (rule.type) {
                        is RuleType.Interval -> stringResource(R.string.rule_type_interval)
                        is RuleType.ScreenTime -> stringResource(R.string.rule_type_screen)
                    }
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ruleSummary(rule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.home_delete_rule),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ruleSummary(rule: Rule): String {
    return when (rule.type) {
        is RuleType.Interval -> {
            val unitLabel = when (rule.type.unit) {
                IntervalUnit.MINUTES -> stringResource(R.string.unit_minutes)
                IntervalUnit.SECONDS -> stringResource(R.string.unit_seconds)
            }
            stringResource(R.string.summary_interval, rule.type.value, unitLabel)
        }
        is RuleType.ScreenTime -> {
            val onUnitLabel = if (rule.type.screenOnUnit == ScreenTimeUnit.MINUTES) stringResource(R.string.unit_minutes) else stringResource(R.string.unit_seconds)
            val offUnitLabel = if (rule.type.screenOffResetUnit == ScreenTimeUnit.MINUTES) stringResource(R.string.unit_minutes) else stringResource(R.string.unit_seconds)
            stringResource(R.string.summary_screen_time, rule.type.effectiveScreenOnDuration, onUnitLabel, rule.type.effectiveScreenOffResetDuration, offUnitLabel)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "${hours}h${minutes}m${seconds}s" else "${minutes}m${seconds}s"
}

private fun formatTimestamp(ts: Long): String {
    if (ts == 0L) return "—"
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ts))
}

@Composable
private fun DebugInfoPanel(debugInfo: ScreenDebugInfo, @Suppress("UNUSED_PARAMETER") tick: Long) {
    val currentScreenOnMs = if (debugInfo.isScreenOn && debugInfo.screenOnStartMs > 0) {
        debugInfo.accumulatedScreenOnMs + (System.currentTimeMillis() - debugInfo.screenOnStartMs)
    } else {
        debugInfo.accumulatedScreenOnMs
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.debug_screen_on, formatDuration(currentScreenOnMs)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.debug_last_screen_off, formatTimestamp(debugInfo.lastScreenOffTimestamp)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
