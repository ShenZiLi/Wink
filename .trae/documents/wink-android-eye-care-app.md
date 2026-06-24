# Wink - 护眼提醒 Android App 实现计划

## 概述

构建一个轻量级 Android 应用 **Wink**，用于定时提醒护眼。支持两种规则类型（Cron 定时规则、亮屏时长规则）和两种提醒方式（持续响铃、消息弹窗）。

## 技术选型

| 项目 | 选择 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 构建 | Gradle Kotlin DSL |
| 最低 SDK | API 34 (Android 14) |
| 目标 SDK | API 36 (Android 16) |
| 数据存储 | SharedPreferences + Kotlin Serialization JSON |
| 后台任务 | Foreground Service + Handler 定时检测 |
| Cron 解析 | com.cronutils:cron-utils 库 |

## 功能规格

### 规则类型
1. **Cron 规则**：按 cron 表达式定时触发提醒（如 `0 */30 * * * ?` 每30分钟）
2. **亮屏时长规则**：
   - 设置亮屏累计时长阈值（如 30 分钟）
   - 设置暗屏重置时长（如 5 分钟，暗屏超过此时长则重置累计）
   - 检测屏幕亮灭状态，累计亮屏时间

### 提醒方式
1. **持续响铃**：播放系统闹铃音，直到用户手动关闭
2. **消息弹窗**：显示系统通知，点击可进入应用

### 首页
- 纵向列表展示所有规则
- 每条规则卡片显示：规则名称、类型标签、规则摘要、启用开关
- 支持左滑删除（或长按菜单删除）
- 点击卡片进入编辑页
- 右上角 "+" 按钮新增规则
- 右上角主题切换按钮（亮色/暗色/跟随系统）

## 项目结构

```
app/
├── src/main/
│   ├── java/com/wink/eye/
│   │   ├── MainActivity.kt                    # 主 Activity
│   │   ├── WinkApp.kt                         # Application 类
│   │   ├── data/
│   │   │   ├── Rule.kt                        # 规则数据模型
│   │   │   └── RuleRepository.kt              # 规则存储仓库
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   │   ├── Theme.kt                   # Material 3 主题（含亮/暗色切换）
│   │   │   │   └── Color.kt                   # 亮色/暗色颜色定义
│   │   │   ├── home/
│   │   │   │   └── HomeScreen.kt              # 首页（规则列表）
│   │   │   └── edit/
│   │   │       └── EditScreen.kt              # 新增/编辑规则页
│   │   ├── service/
│   │   │   ├── ScreenMonitorService.kt        # 亮屏检测前台服务
│   │   │   └── ReminderService.kt             # 提醒服务
│   │   ├── receiver/
│   │   │   └── ScreenReceiver.kt              # 屏幕亮灭广播接收器
│   │   └── util/
│   │       └── CronHelper.kt                  # Cron 解析工具
│   ├── res/
│   │   ├── values/
│   │   │   └── strings.xml
│   │   └── drawable/
│   │       └── ic_launcher.xml
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro
```

## 数据模型

```kotlin
@Serializable
sealed class RuleType {
    @Serializable
    data class Cron(val expression: String) : RuleType()

    @Serializable
    data class ScreenTime(
        val screenOnMinutes: Int,    // 亮屏阈值（分钟）
        val screenOffResetMinutes: Int // 暗屏重置阈值（分钟）
    ) : RuleType()
}

@Serializable
enum class ReminderMode {
    ALARM,    // 持续响铃
    NOTIFICATION  // 消息弹窗
}

@Serializable
data class Rule(
    val id: String,          // UUID
    val name: String,        // 规则名称
    val type: RuleType,      // 规则类型
    val reminderMode: ReminderMode, // 提醒方式
    val enabled: Boolean = true
)
```

## 实现步骤

### Step 1: 项目初始化
- 使用 Android Studio 项目模板创建 Kotlin + Compose 项目
- 包名：`com.wink.eye`
- 配置 `build.gradle.kts`：添加依赖（compose, serialization, cron-utils）
- 配置 `AndroidManifest.xml`：声明权限和组件

**关键依赖：**
```kotlin
implementation(platform("androidx.compose:compose-bom:2024.12.01"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.activity:activity-compose:1.9.3")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("com.cronutils:cron-utils:9.2.1")
```

### Step 2: 数据层
- 创建 `Rule.kt` 数据模型（如上）
- 创建 `RuleRepository.kt`：基于 SharedPreferences + JSON 的 CRUD 操作
  - `getAll(): List<Rule>`
  - `getById(id: String): Rule?`
  - `save(rule: Rule)`
  - `delete(id: String)`

### Step 3: 主题与 UI 基础
- 创建 Material 3 主题，支持亮色/暗色/跟随系统三种模式
- `Theme.kt`：使用 `isSystemInDarkTheme()` + 自定义 `ThemeMode` 枚举
- `Color.kt`：定义亮色和暗色两套颜色（护眼绿色调为主色）
- 主题偏好持久化到 SharedPreferences
- 首页 TopAppBar 提供主题切换图标按钮（点击循环切换：亮→暗→跟随系统）

### Step 4: 首页 - 规则列表
- `HomeScreen.kt`：LazyColumn 纵向列表
- 每条规则是一个 Card：
  - 规则名称（粗体）
  - 类型标签（Cron / 亮屏时长）
  - 规则摘要文字
  - 启用/禁用 Switch
  - 删除按钮（IconButton）
- 点击卡片导航到编辑页
- 右上角 TopAppBar 中有主题切换按钮 + "+" 新增按钮
- 使用 ViewModel 管理状态

### Step 5: 编辑/新增规则页
- `EditScreen.kt`：表单页面
- 规则名称输入框
- 规则类型选择（Tab 或 Radio）：Cron / 亮屏时长
- Cron 类型：显示 cron 表达式输入框 + 常用预设（每30分钟、每小时等）
- 亮屏时长类型：两个 NumberPicker/Slider（亮屏阈值、暗屏重置阈值）
- 提醒方式选择：持续响铃 / 消息弹窗
- 保存按钮

### Step 6: 屏幕状态检测服务
- `ScreenReceiver.kt`：注册 `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` 广播
- `ScreenMonitorService.kt`：前台服务
  - 维护一个亮屏累计计时器
  - 屏幕亮时开始计时
  - 屏幕灭时记录灭屏时间
  - 屏幕再次亮时，判断灭屏时长是否超过重置阈值
    - 超过：重置累计时间
    - 未超过：继续累计
  - 累计时间达到阈值时触发提醒
- 前台服务通知（Android 8+ 必需）

### Step 7: Cron 定时提醒
- `CronHelper.kt`：使用 cron-utils 解析 cron 表达式，计算下次触发时间
- 使用 `AlarmManager.setExactAndAllowWhileIdle()` 设置精确闹钟
- 在 `BroadcastReceiver` 中接收闹钟触发，执行提醒

### Step 8: 提醒功能
- **持续响铃**：
  - 使用 `RingtoneManager` 获取系统闹铃 URI
  - 使用 `MediaPlayer` 循环播放
  - 弹出全屏 Activity 带关闭按钮
- **消息弹窗**：
  - 使用 `NotificationCompat` 发送通知
  - 设置 `fullScreenIntent`（锁屏时弹出）
  - 点击通知打开应用

### Step 9: 权限与适配
- AndroidManifest 声明权限：
  - `FOREGROUND_SERVICE`
  - `FOREGROUND_SERVICE_SPECIAL_USE`（API 34+ 必需）
  - `POST_NOTIFICATIONS`（运行时请求）
  - `USE_EXACT_ALARM`（Cron 精确闹钟必需）
  - `WAKE_LOCK`
- 运行时权限请求：
  - 通知权限 `POST_NOTIFICATIONS`
  - 精确闹钟权限（检查 `canScheduleExactAlarms()`）
- 引导用户关闭电池优化（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）
- NotificationChannel 创建
- 前台服务类型声明（`specialUse`）

## 关键设计决策

1. **前台服务而非 WorkManager**：亮屏时间检测需要实时性，WorkManager 不适合分钟级精度
2. **SharedPreferences 而非 Room**：规则数量极少（通常 <20），JSON 序列化足够
3. **cron-utils 库**：成熟稳定的 cron 解析库，避免自己实现
4. **AlarmManager 精确闹钟**：Cron 规则需要精确触发
5. **全屏 Activity 关闭响铃**：持续响铃需要用户主动关闭，全屏 Activity 是最可靠方式

## 验证步骤

1. 构建项目无编译错误
2. 首页能正确展示空状态和规则列表
3. 新增 Cron 规则，保存后出现在列表中
4. 新增亮屏时长规则，保存后出现在列表中
5. 编辑已有规则，修改后保存成功
6. 删除规则，列表更新
7. Cron 规则到时间后触发提醒（响铃/通知）
8. 亮屏累计时间达标后触发提醒
9. 暗屏超过重置时长后累计时间被重置
10. 持续响铃模式下，响铃播放直到用户点击关闭
