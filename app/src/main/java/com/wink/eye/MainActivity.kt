package com.wink.eye

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.compose.liquidglassnav.LiquidGlassBottomNavBar
import com.compose.liquidglassnav.NavItem
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wink.eye.data.IntervalUnit
import com.wink.eye.data.Rule
import com.wink.eye.data.RuleRepository
import com.wink.eye.data.RuleType
import com.wink.eye.service.IntervalAlarmScheduler
import com.wink.eye.service.ScreenMonitorService
import com.wink.eye.ui.components.winkGlassSource
import com.wink.eye.ui.edit.EditScreen
import com.wink.eye.ui.earclock.EarClockEditScreen
import com.wink.eye.ui.earclock.EarClockHomeScreen
import com.wink.eye.ui.earclock.EarClockHomeViewModel
import com.wink.eye.ui.home.HomeScreen
import com.wink.eye.ui.home.HomeViewModel
import com.wink.eye.ui.theme.ThemeManager
import com.wink.eye.ui.theme.WinkTheme
import dev.chrisbanes.haze.rememberHazeState

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "通知权限结果: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeManager.init(this)
        requestPermissions()

        setContent {
            WinkTheme {
                WinkNavHost(repository = WinkApp.instance.ruleRepository)
            }
        }
    }

    private fun requestPermissions() {
        // 请求通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 检查精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "没有精确闹钟权限，引导用户到设置页面")
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun WinkNavHost(repository: RuleRepository) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory())
    val earClockViewModel: EarClockHomeViewModel = viewModel(factory = EarClockHomeViewModel.Factory())
    val earClockRepository = WinkApp.instance.earClockRepository

    // 底部悬浮导航栏的玻璃采样源：由 NavHost 内的页面内容提供被模糊的画面
    val bottomBarHazeState = rememberHazeState()

    // 底部导航栏仅在主页面（home/earclock）显示
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == "home" || currentRoute == "earclock"

    val navItems = remember {
        listOf(
            NavItem(
                icon = Icons.Outlined.Visibility,
                activeIcon = Icons.Filled.Visibility,
                label = "Wink",
                route = "home"
            ),
            NavItem(
                icon = Icons.Outlined.Headphones,
                activeIcon = Icons.Filled.Headphones,
                label = "EarClock",
                route = "earclock"
            )
        )
    }

    val selectedIndex = when (currentRoute) {
        "home" -> 0
        "earclock" -> 1
        else -> 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .padding(innerPadding)
                    // 页面内容作为底部导航栏玻璃的采样源（不产生视觉变化）
                    .winkGlassSource(bottomBarHazeState),
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(250)) },
                popEnterTransition = { fadeIn(tween(300)) },
                popExitTransition = { fadeOut(tween(250)) }
            ) {
                composable("home") {
                    LaunchedEffect(Unit) { homeViewModel.loadRules() }
                    HomeScreen(
                        onAddRule = { navController.navigate("edit/new") },
                        onEditRule = { id -> navController.navigate("edit/$id") },
                        viewModel = homeViewModel
                    )
                }
                composable("edit/new") {
                    EditScreen(
                        existingRule = null,
                        onSave = { rule ->
                            repository.save(rule)
                            onRuleSaved(context, rule)
                            homeViewModel.loadRules()
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("edit/{ruleId}") { backStackEntry ->
                    val ruleId = backStackEntry.arguments?.getString("ruleId") ?: return@composable
                    val rule = repository.getById(ruleId)
                    EditScreen(
                        existingRule = rule,
                        onSave = { updatedRule ->
                            repository.save(updatedRule)
                            onRuleSaved(context, updatedRule)
                            homeViewModel.loadRules()
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("earclock") {
                    LaunchedEffect(Unit) { earClockViewModel.loadAlarms() }
                    EarClockHomeScreen(
                        onAddAlarm = { navController.navigate("earclock/edit/new") },
                        onEditAlarm = { alarmId -> navController.navigate("earclock/edit/$alarmId") },
                        viewModel = earClockViewModel
                    )
                }
                composable("earclock/edit/new") {
                    EarClockEditScreen(
                        existingAlarm = null,
                        onSave = { alarm ->
                            earClockViewModel.saveAlarm(alarm)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable("earclock/edit/{alarmId}") { backStackEntry ->
                    val alarmId = backStackEntry.arguments?.getString("alarmId") ?: return@composable
                    val alarm = earClockRepository.getById(alarmId)
                    EarClockEditScreen(
                        existingAlarm = alarm,
                        onSave = { updatedAlarm ->
                            earClockViewModel.saveAlarm(updatedAlarm)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
            }
        }

        // 悬浮液态玻璃底部导航栏
        if (showBottomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                LiquidGlassBottomNavBar(
                    items = navItems,
                    selectedIndex = selectedIndex,
                    onItemSelected = { index ->
                        val route = navItems[index].route
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedColor = MaterialTheme.colorScheme.onSurface,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    activeColor = MaterialTheme.colorScheme.primary,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    barHeight = 70.dp,
                    cornerRadius = 30.dp,
                    showBorder = true,
                    hazeState = bottomBarHazeState
                )
            }
        }
    }
}

private fun onRuleSaved(context: android.content.Context, rule: Rule) {
    if (!rule.enabled) return

    when (rule.type) {
        is RuleType.Interval -> {
            val intervalMs = when (rule.type.unit) {
                IntervalUnit.SECONDS -> rule.type.value * 1000L
                IntervalUnit.MINUTES -> rule.type.value * 60 * 1000L
            }
            IntervalAlarmScheduler.schedule(context, rule.id, intervalMs)
        }
        is RuleType.ScreenTime -> {
            ScreenMonitorService.start(context)
        }
    }

    syncServices(context)
}

private fun syncServices(context: android.content.Context) {
    val rules = WinkApp.instance.ruleRepository.getAll()

    // 如果有任何启用的亮屏时长规则，启动前台服务
    val hasScreenTimeRule = rules.any { it.enabled && it.type is RuleType.ScreenTime }
    if (hasScreenTimeRule) {
        ScreenMonitorService.start(context)
    } else {
        ScreenMonitorService.stop(context)
    }

    // 取消所有已禁用间隔规则的闹钟
    rules.filter { !it.enabled && it.type is RuleType.Interval }.forEach {
        IntervalAlarmScheduler.cancel(context, it.id)
    }
}
