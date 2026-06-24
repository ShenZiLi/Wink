package com.wink.eye

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wink.eye.data.IntervalUnit
import com.wink.eye.data.Rule
import com.wink.eye.data.RuleRepository
import com.wink.eye.data.RuleType
import com.wink.eye.service.IntervalAlarmScheduler
import com.wink.eye.service.ScreenMonitorService
import com.wink.eye.ui.edit.EditScreen
import com.wink.eye.ui.home.HomeScreen
import com.wink.eye.ui.home.HomeViewModel
import com.wink.eye.ui.theme.ThemeManager
import com.wink.eye.ui.theme.WinkTheme

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
                // 引导用户开启精确闹钟权限
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

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            // 每次进入首页时重新加载规则
            LaunchedEffect(Unit) {
                homeViewModel.loadRules()
            }
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
            val ruleId = backStackEntry.arguments?.getString("ruleId") ?: return@composable Unit
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
