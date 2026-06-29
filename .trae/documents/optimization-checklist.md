# Wink App 优化清单

## Critical（必须修复）

### #1 数据迁移 Bug - 旧规则静默丢失
- **问题**：`RuleType.ScreenTime` 字段从 `screenOnMinutes`/`screenOffResetMinutes` 改为 `screenOnDuration`/`screenOffResetDuration`，旧数据反序列化时找不到新字段，`catch` 块返回 `emptyList()`，所有用户规则被静默删除
- **修复**：添加 `@Deprecated` 旧字段保留兼容，新增 `effectiveScreenOnDuration`/`effectiveScreenOffResetDuration` 属性优先使用新字段、回退旧字段
- **文件**：`app/src/main/java/com/wink/eye/data/Rule.kt`

### #2 服务重启后状态丢失
- **问题**：`ScreenMonitorService` 的 `accumulatedScreenOnMs`、`screenOnTime`、`isScreenOn` 全在内存中，进程被杀后 `START_STICKY` 重启服务，所有状态归零
- **修复**：添加 `saveState()`/`restoreState()` 持久化到 `SharedPreferences("wink_screen_state")`，`onCreate` 时恢复
- **文件**：`app/src/main/java/com/wink/eye/service/ScreenMonitorService.kt`

### #3 onTaskRemoved 在 Android 12+ 崩溃
- **问题**：`startForegroundService` 在 App 被杀后触发 `ForegroundServiceStartNotAllowedException`
- **修复**：Android 12+ 改用 `AlarmManager.setExactAndAllowWhileIdle` 延迟 1s 重启服务
- **文件**：`app/src/main/java/com/wink/eye/service/ScreenMonitorService.kt`

---

## High（重要优化）

### #4 DismissReceiver 未在 Manifest 注册
- **问题**：`ReminderHelper` 中定义的 `DismissReceiver` 未注册，通知删除 Intent 无法工作
- **修复**：在 `AndroidManifest.xml` 中添加 `<receiver android:name=".service.DismissReceiver" android:exported="false" />`
- **文件**：`app/src/main/AndroidManifest.xml`

### #5 DebugInfoPanel 丢失
- **问题**：之前添加的实时亮屏时长显示代码不在 HomeScreen 中
- **修复**：在 `HomeScreen.kt` 中恢复 `DebugInfoPanel`，接收 `tick` 参数每 5 秒刷新，实时计算亮屏时长 + 上次暗屏时间戳
- **文件**：`app/src/main/java/com/wink/eye/ui/home/HomeScreen.kt`

### #6 ScreenReceiver 仅动态注册
- **问题**：`ScreenReceiver` 仅在 `onCreate` 动态注册，服务被杀重启期间遗漏 SCREEN_ON/OFF 事件
- **修复**：`onCreate` 中通过 `DisplayManager` 主动查询当前屏幕实际状态
- **文件**：`app/src/main/java/com/wink/eye/service/ScreenMonitorService.kt`

### #7 checkScreenTimeRules 多规则时提前重置
- **问题**：匹配到第一个规则后就 `accumulatedScreenOnMs = 0L`，后续规则阈值判断被跳过
- **修复**：先收集所有需触发的规则到 `triggeredRules` 列表，统一触发后再重置一次
- **文件**：`app/src/main/java/com/wink/eye/service/ScreenMonitorService.kt`

### #8 scheduleScreenTimeAlarm 递归风险
- **问题**：`minRemainingMs <= 0` 时递归调用自身，若 `checkScreenTimeRules` 因异常未重置，导致 `StackOverflow`
- **修复**：添加 `scheduleRecursionCount` 计数器，超过 `MAX_SCHEDULE_RECURSION(5)` 次终止
- **文件**：`app/src/main/java/com/wink/eye/service/ScreenMonitorService.kt`

### #9 cron-utils 依赖未使用
- **问题**：`CronAlarmReceiver` 已删除但 `com.cronutils:cron-utils:9.2.1` 仍在依赖列表，`CronHelper.kt` 也残留
- **修复**：移除依赖，删除 `CronHelper.kt`
- **文件**：`app/build.gradle.kts`、`app/src/main/java/com/wink/eye/util/CronHelper.kt`

---

## Medium（建议优化）

### #10 ViewModel 传入 Activity Context
- **问题**：`HomeViewModel` 中 `deleteRule(context)` / `toggleEnabled(context)` 接收 Context 参数，容易误传 Activity Context 导致泄漏
- **修复**：改用 `AndroidViewModel`，内部使用 `applicationContext`
- **文件**：`app/src/main/java/com/wink/eye/ui/home/HomeViewModel.kt`

### #11 秒级 Slider 无步长
- **问题**：秒级 Slider 范围 5~300 没有 `steps` 参数，拖动时很难选到精确值
- **修复**：添加 `steps = 58`（步长约 5 秒）
- **文件**：`app/src/main/java/com/wink/eye/ui/edit/EditScreen.kt`

### #12 硬编码中文字符串
- **问题**：HomeScreen / EditScreen 中大量中文文本硬编码，不支持多语言
- **修复**：全部移至 `strings.xml`，使用 `stringResource()` 引用
- **文件**：`app/src/main/res/values/strings.xml`、`HomeScreen.kt`、`EditScreen.kt`

### #14 ensureChannels 每次触发都调用
- **问题**：每次 `triggerReminder` 都调用 `ensureChannels`，有性能开销
- **修复**：改为 `@Volatile channelsCreated` 双重检查锁懒加载
- **文件**：`app/src/main/java/com/wink/eye/service/ReminderHelper.kt`

### #15 通知 Channel 名称硬编码
- **问题**：`"持续响铃提醒"` / `"全屏闹钟提醒，使用闹钟声音"` 硬编码
- **修复**：改用 `context.getString(R.string.alarm_channel_name)` 等
- **文件**：`app/src/main/java/com/wink/eye/service/ReminderHelper.kt`

### #16 服务前台通知无实时信息
- **问题**：前台服务通知始终显示固定文本，用户不知道服务是否正常工作
- **修复**：`updateForegroundNotification()` 显示当前累计亮屏时长（如 "亮屏时长监测中 · 5m30s"）
- **文件**：`app/src/main/java/com/wink/eye/service/ScreenMonitorService.kt`

---

## Low（体验优化）

### #17 删除规则无确认弹窗
- **问题**：删除按钮直接执行删除，破坏性操作无确认
- **修复**：添加 `AlertDialog` 确认弹窗
- **文件**：`app/src/main/java/com/wink/eye/ui/home/HomeScreen.kt`

### #18 亮屏时长规则标签写死"分钟"
- **问题**：`strings.xml` 中 `edit_screen_on_label` 仍写"分钟"，但已支持秒级单位
- **修复**：标签根据当前单位动态显示（"亮屏时长阈值: 30 分钟" 或 "亮屏时长阈值: 30 秒"）
- **文件**：`app/src/main/java/com/wink/eye/ui/edit/EditScreen.kt`

### #19 定时提醒预设快捷选项始终未选中
- **问题**：三个预设 FilterChip 的 `selected` 始终为 `false`
- **修复**：根据 `intervalValue`/`intervalUnit` 计算 `isPreset15`/`isPreset30`/`isPreset1h` 状态
- **文件**：`app/src/main/java/com/wink/eye/ui/edit/EditScreen.kt`

### #20 缺少 About/设置页面
- **问题**：没有版本信息、权限说明、反馈入口
- **状态**：待实现

---

## 修复状态

| # | 优先级 | 状态 |
|---|--------|------|
| 1 | Critical | ✅ 已修复 |
| 2 | Critical | ✅ 已修复 |
| 3 | Critical | ✅ 已修复 |
| 4 | High | ✅ 已修复 |
| 5 | High | ✅ 已修复 |
| 6 | High | ✅ 已修复 |
| 7 | High | ✅ 已修复 |
| 8 | High | ✅ 已修复 |
| 9 | High | ✅ 已修复 |
| 10 | Medium | ✅ 已修复 |
| 11 | Medium | ✅ 已修复 |
| 12 | Medium | ✅ 已修复 |
| 14 | Medium | ✅ 已修复 |
| 15 | Medium | ✅ 已修复 |
| 16 | Medium | ✅ 已修复 |
| 17 | Low | ✅ 已修复 |
| 18 | Low | ✅ 已修复 |
| 19 | Low | ✅ 已修复 |
| 20 | Low | ⏳ 待实现 |
