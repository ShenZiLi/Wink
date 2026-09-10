package com.wink.eye.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.wink.eye.ui.components.WinkConfigCard
import com.wink.eye.ui.components.WinkConfigList
import com.wink.eye.ui.components.WinkEmptyState
import com.wink.eye.ui.components.WinkGlassTopBar
import com.wink.eye.ui.components.WinkGlassTopBarDefaults
import com.wink.eye.ui.components.winkGlassSource
import dev.chrisbanes.haze.rememberHazeState
import com.wink.eye.ui.theme.ThemeManager
import com.wink.eye.ui.theme.ThemeMode
import com.wink.eye.ui.theme.WinkLayoutOverlay
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

    // 顶栏玻璃的采样源：由列表内容提供被模糊的画面
    val topBarHazeState = rememberHazeState()
    val topBarHeight = WinkGlassTopBarDefaults.totalHeight()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 页面内容自行处理状态栏留白，以便滚动内容能从悬浮玻璃顶栏下方穿过
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (rules.isEmpty()) {
                WinkEmptyState(
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle),
                    modifier = Modifier.padding(top = topBarHeight)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    WinkConfigList(
                        list = rules,
                        key = { it.id },
                        modifier = Modifier
                            .weight(1f)
                            .winkGlassSource(topBarHazeState),
                        extraTopPadding = topBarHeight
                    ) { rule ->
                        RuleCard(
                            rule = rule,
                            onToggle = { viewModel.toggleEnabled(rule) },
                            onDelete = { ruleToDelete = rule },
                            onClick = { onEditRule(rule.id) }
                        )
                    }

                    // 【Wink 页专属面板】亮屏时长调试面板，仅本页有对应数据源，
                    // EarClock 页不存在同类内容，两页此处不做统一。
                    if (hasScreenTimeRule) {
                        // 底部留白略小于列表的遮挡高度，让面板整体下移贴近悬浮菜单栏
                        Box(Modifier.padding(bottom = WinkLayoutOverlay.DebugPanelBottomPadding)) {
                            DebugInfoPanel(debugInfo)
                        }
                    }
                }
            }

            // 悬浮液态玻璃顶栏：叠在内容之上，滚动时内容会从其下方穿过
            WinkGlassTopBar(
                title = "Wink",
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
                    IconButton(onClick = onAddRule) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.home_add_rule)
                        )
                    }
                }
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
    val typeLabel = when (rule.type) {
        is RuleType.Interval -> stringResource(R.string.rule_type_interval)
        is RuleType.ScreenTime -> stringResource(R.string.rule_type_screen)
    }
    WinkConfigCard(
        title = rule.name,
        mainValue = ruleMainValue(rule),
        badge = typeLabel,
        subtitle = ruleDetail(rule),
        enabled = rule.enabled,
        onToggle = onToggle,
        onDelete = onDelete,
        deleteContentDescription = stringResource(R.string.home_delete_rule),
        onClick = onClick
    )
}

/** 卡片主值大字：规则的核心数值 + 单位 */
@Composable
private fun ruleMainValue(rule: Rule): String {
    return when (rule.type) {
        is RuleType.Interval -> stringResource(
            R.string.summary_value,
            rule.type.value,
            intervalUnitLabel(rule.type.unit)
        )
        is RuleType.ScreenTime -> stringResource(
            R.string.summary_value,
            rule.type.effectiveScreenOnDuration,
            screenUnitLabel(rule.type.screenOnUnit)
        )
    }
}

/** 卡片底部描述：放次要参数，无次要参数时返回空串 */
@Composable
private fun ruleDetail(rule: Rule): String {
    return when (rule.type) {
        is RuleType.Interval -> ""
        is RuleType.ScreenTime -> stringResource(
            R.string.summary_screen_off_reset,
            rule.type.effectiveScreenOffResetDuration,
            screenUnitLabel(rule.type.screenOffResetUnit)
        )
    }
}

@Composable
private fun intervalUnitLabel(unit: IntervalUnit): String = when (unit) {
    IntervalUnit.MINUTES -> stringResource(R.string.unit_minutes)
    IntervalUnit.SECONDS -> stringResource(R.string.unit_seconds)
}

@Composable
private fun screenUnitLabel(unit: ScreenTimeUnit): String =
    if (unit == ScreenTimeUnit.MINUTES) stringResource(R.string.unit_minutes)
    else stringResource(R.string.unit_seconds)

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

/**
 * 【Wink 页专属面板】亮屏时长实时统计。
 *
 * 数据来自 [ScreenMonitorService]，只有 Wink 页（亮屏时长规则）才有对应概念，
 * EarClock 页没有同类面板，因此两页列表高度的一致性不包含此面板占位。
 */
@Composable
private fun DebugInfoPanel(debugInfo: ScreenDebugInfo) {
    // 内部自驱动：每秒更新 now，now 是 Compose state，变化必然触发重组
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // now 直接参与计算，Compose 检测到 now 变化会重组此组件
    val currentScreenOnMs = if (debugInfo.isScreenOn && debugInfo.screenOnStartMs > 0) {
        debugInfo.accumulatedScreenOnMs + (now - debugInfo.screenOnStartMs)
    } else {
        debugInfo.accumulatedScreenOnMs
    }

    val totalSeconds = currentScreenOnMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 已亮屏
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
                           else String.format("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (debugInfo.isScreenOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.debug_screen_on_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 分隔线
            androidx.compose.material3.VerticalDivider(
                modifier = Modifier.height(48.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 上次暗屏
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTimestamp(debugInfo.lastScreenOffTimestamp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.debug_last_screen_off_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
