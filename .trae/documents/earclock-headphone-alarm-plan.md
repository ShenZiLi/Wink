# 耳机闹钟 EarClock 实现计划

> 依据 UI 设计文档 `docs/earclock-alarm-design.md` 对齐调整后的实现计划。

## 概述（Summary）
在现有 Android 护眼应用 **Wink**（Kotlin + Jetpack Compose + Material3）中新增独立功能模块 **耳机闹钟 EarClock**：仅在检测到耳机连接时才通过耳机播放闹铃的全屏闹钟。用户从 Wink 首页进入 EarClock 首页；闹钟的「设置闹钟」页按 UI 设计文档实现（iOS 系统闹钟风格深色布局，落地时用 Material3 组件 + Wink 护眼绿主题适配）。

已确认的产品决策：
- **耳机播放策略**：必须连接耳机才响；未连接耳机时本次触发静默，但仍调度下一次触发。
- **触发形态**：全屏闹钟页面（类似现有 `ReminderActivity`），含「立即关闭」与「稍后提醒」按钮，闹铃循环播放。
- **稍后提醒**：可配置间隔（分钟）与最大重复次数；次数用尽后不再稍后提醒。

功能清单（对齐 UI 设计文档）：
1. 连接耳机时播放闹铃（有线 + 蓝牙，best-effort 检测）。
2. 铃声自定义（系统铃声选择器）。
3. 频率设置：响一次 / 工作日 / 自定义（一周自选）。【UI 稿第 4 项「秒抢闹钟」标注“待定”，本期不做，见决策】
4. 工作日类型（UI 设置分组卡片含此项）。
5. 振动设置（开/默认/关闭）。
6. 稍后提醒（间隔 + 次数上限可配置）。
7. 设置页含实时「距离下次响铃」倒计时提示。

## 当前状态分析（Current State Analysis）
- 应用为单模块 Android 工程：`app/build.gradle.kts`，`namespace=com.wink.eye`，minSdk 34，Compose + Material3，kotlinx-serialization。
- 入口 [MainActivity.kt](file:///Users/shen/Studio/Code/Wink/app/src/main/java/com/wink/eye/MainActivity.kt)：`WinkNavHost` 用 `NavHost`，路由 `home` / `edit/new` / `edit/{ruleId}`；首页每次进入重新 `loadRules()`。
- 数据层 [Rule.kt](file:///Users/shen/Studio/Code/Wink/app/src/main/java/com/wink/eye/data/Rule.kt) + [RuleRepository.kt](file:///Users/shen/Studio/Code/Wink/app/src/main/java/com/wink/eye/data/RuleRepository.kt)：SharedPreferences + kotlinx-serialization，CRUD。`WinkApp` 懒加载单例 `ruleRepository`。
- ViewModel 模式参考 [HomeViewModel.kt](file:///Users/shen/Studio/Code/Wink/app/src/main/java/com/wink/eye/ui/home/HomeViewModel.kt)：`MutableStateFlow` + `StateFlow` + `Factory`。
- 调度层 [IntervalAlarmScheduler.kt](file:///Users/shen/Studio/Code/Wink/app/src/main/java/com/wink/eye/service/IntervalAlarmScheduler.kt)：`AlarmManager.setExactAndAllowWhileIdle` + `PendingIntent.getBroadcast`，Receiver 触发后再调度下一次。已有 `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` 权限。
- 提醒层 [ReminderHelper.kt](file:///Users/shen/Studio/Code/Wink/app/src/main/java/com/wink/eye/service/ReminderHelper.kt)：ALARM 模式用 fullScreenIntent 通知唤起全屏页面；`ReminderActivity` 用 `MediaPlayer`（`USAGE_ALARM`）循环播放系统闹铃。
- UI 风格：Home 用 `Card` + `Switch` + `IconButton` + 删除确认 `AlertDialog` + 空状态；Edit 用 `TopAppBar(primaryContainer)` + `FilterChip` + `OutlinedTextField` + 底部 `Button`。页面内自刷新手表参考 [HomeScreen.kt](file:///Users/shen/Studio/Code/Wink/app/src/main/java/com/wink/eye/ui/home/HomeScreen.kt#L281-L298) 的 `DebugInfoPanel`（`mutableLongStateOf` + `LaunchedEffect` 每秒更新）。
- 主题 [Theme.kt / Color.kt](file:///Users/shen/Studio/Code/Wink/app/src/main/java/com/wink/eye/ui/theme/Color.kt)：护眼绿 `primary=#2E7D32`(亮)/`#6BFF8E`(暗)。
- Manifest：已有 `VIBRATE`、`USE_FULL_SCREEN_INTENT`、`FOREGROUND_SERVICE`、精确闹钟等权限，无 `BLUETOOTH_CONNECT`。

关键技术点/约束：
- 后台唤起：BroadcastReceiver 无法直接稳定启动 Activity（Android 10+ 后台启动限制），沿用现有 `fullScreenIntent` 通知模式唤起全屏闹钟页面。
- 耳机检测与路由：`AudioManager.getDevices(GET_DEVICES_ALL)` 过滤 `TYPE_WIRED_HEADSET/TYPE_WIRED_HEADPHONES/TYPE_BLUETOOTH_A2DP/TYPE_USB_HEADSET`；`MediaPlayer.setPreferredDevice(AudioDeviceInfo)` 将闹铃路由到耳机（标准 API），蓝牙 A2DP 检测做 best-effort。
- UI 计时：设置页「距离下次响铃」用 `mutableLongStateOf` + `LaunchedEffect { while(true){ delay(1000); now=... } }` 每秒刷新（复刻 `DebugInfoPanel` 思路）。

## 建议改动（Proposed Changes）

### 1. 数据层
**新建 `data/EarClockAlarm.kt`**
```kotlin
@Serializable
enum class EarClockFrequency { @SerialName("once") ONCE,
    @SerialName("workdays") WORKDAYS, @SerialName("custom") CUSTOM }

/** 工作日类型：WEEKDAYS=周一至周五；LEGAL=法定工作日（需节假日表，本期延后，仅预留） */
@Serializable
enum class WorkdayType { @SerialName("weekdays") WEEKDAYS,
    @SerialName("legal") LEGAL }

@Serializable
data class EarClockAlarm(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val hour: Int,            // 触发时间 0-23
    val minute: Int,          // 0-59
    val frequency: EarClockFrequency,
    val workdayType: WorkdayType = WorkdayType.WEEKDAYS, // 频率=WORKDAYS 时有效
    val daysOfWeek: Set<Int> = emptySet(), // CUSTOM 下一周自选，值为 Calendar.DAY_OF_WEEK
    val ringtoneUri: String? = null,        // 自定义铃声，null=系统默认闹铃
    val vibrationMode: VibrationMode = VibrationMode.DEFAULT, // 见下
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,      // 稍后提醒间隔（分钟）
    val snoozeRepeatLimit: Int = 3   // 稍后提醒最大次数
)

@Serializable
enum class VibrationMode {
    @SerialName("default") DEFAULT,   // 默认振动
    @SerialName("off") OFF,           // 关闭
    @SerialName("none") CUSTOM        // 自定义（延后）—— 与 UI「默认/关闭」对齐
}
```
- 独立数据模型（字段与现有 `Rule` 差异大，不并入 RuleType）。
- kotlinx-serialization 对 enum 的未知值：`Json { ignoreUnknownKeys = true }` + enum 需容忍未知值（`ignoreUnknownKeys` 不覆盖 enum 未知成员，反序列化未知 `VibrationMode` 会抛错——因本期只新增、无历史数据回读风险，导入时用 try/catch 兜底为空列表，逻辑与 `RuleRepository` 一致）。
- **新建 `data/EarClockRepository.kt`**：镜像 `RuleRepository`（SharedPreferences `earclock_alarms` + JSON），提供 `getAll/getById/save(toggle)/delete`。
- **修改 `WinkApp.kt`**：新增懒加载 `val earClockRepository by lazy { EarClockRepository(this) }`。

### 2. 调度层
**新建 `service/EarClockAlarmScheduler.kt`**（模仿 `IntervalAlarmScheduler`）
- `scheduleNext(context, alarm)`：计算下一次触发 `nextTriggerMillis(alarm, now)` 并 `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)`；PendingIntent 统一 requestCode=`abs(alarm.id.hashCode())` + `FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE`，extra 携带 `alarmId`、`frequency`、`isSnooze=false`、`snoozeCount=0`。
- `scheduleSnooze(context, alarm, snoozeCount)`：在 `now + snoozeMinutes` 触发，extra 带 `isSnooze=true`、`snoozeCount`；复用同一 PendingIntent（extras 参与投递，`FLAG_UPDATE_CURRENT` 更新属性）。
- `cancel(context, alarmId)`。
- `nextTriggerMillis(alarm, now)`：按频率取下一个匹配 `hour:minute`（及匹配的星期）的时间点，返回远超 now 的最小时间：
  - `ONCE`：下一次到达该时刻（今天未过则今天，否则明天）。
  - `WORKDAYS`：`workdayType` 决定匹配日——`WEEKDAYS` 匹配周一至五；`LEGAL` 本期落回周一至五（节假日表延后）。
  - `CUSTOM`：下一个匹配的已选星期时刻。

**新建 `service/EarClockAlarmReceiver.kt`**：`onReceive`
1. 读 `alarmId`，从 repository 加载；不存在或 `enabled=false` 直接忽略。
2. `isSnooze` 为 false（正常触发）：
   - **ONCE**：本次为一次性触发，不自动再调下一次；「关闭后停用」交由 Activity 处理，Receiver 仅触发展示。
   - **否则（WORKDAYS/CUSTOM）**：触发后 `scheduleNext` 重排下一次。
3. 调 `EarClockAudioHelper.isHeadphoneConnected(context)`：
   - **未连接**：本次静默不响（不启动页面），但仍按第 2 步规则重排下一次。
   - **已连接**：沿用 fullScreenIntent 通知（高优、`CATEGORY_ALARM`）唤起 `EarClockAlarmActivity`，extra 传 `alarmId`、`ringtoneUri`、`vibrationMode`、`isSnooze`、`frequency`、`snoozeCount`。

### 3. 音频辅助
**新建 `service/EarClockAudioHelper.kt`**
- `isHeadphoneConnected(context): Boolean`：枚举 `AudioManager.getDevices(GET_DEVICES_ALL)`，命中 `TYPE_WIRED_HEADSET / TYPE_WIRED_HEADPHONES / TYPE_BLUETOOTH_A2DP / TYPE_USB_HEADSET` 返回 true（蓝牙检测 try/catch 处理缺失权限 best-effort）。
- `playAlarm(context, ringtoneUri, vibrationMode): MediaPlayer`：`MediaPlayer` + `AudioAttributes(USAGE_ALARM)`，`setDataSource`（uri 或默认 `TYPE_ALARM` 兜底），`isLooping=true`，`setPreferredDevice(已连接耳机设备)` 强制路由至耳机，`start()`；`vibrationMode` 非 `OFF` 时用 `Vibrator`/`VibratorManager` 触发 `longArrayOf(0,500,200,500)` pattern。返回实例供 Stop。

### 4. 全屏闹钟页
**新建 `EarClockAlarmActivity.kt`**（参考 `ReminderActivity`）
- Manifest `android:showWhenLocked|turnScreenOn|excludeFromRecents`；窗口 `FLAG_SHOW_WHEN_LOCKED|FLAG_TURN_SCREEN_ON|FLAG_KEEP_SCREEN_ON`。
- 进入即 `EarClockAudioHelper.playAlarm(...)`，`DisposableEffect` 退出时 `stop`。
- 界面：耳机图标 + 闹钟名 + 时刻 + 已连接耳机提示；两个按钮：
  - **「立即关闭」**：`stopAlarm()`；若 `frequency==ONCE && !isSnooze` → repository 将该 alarm `enabled=false`，并 `cancel`；finish。
  - **「稍后提醒」（可选显示）**：`snoozeEnabled && snoozeCount < snoozeRepeatLimit` 时显示；点击 → `scheduleSnooze(alarm, snoozeCount+1)`，`stopAlarm()`，finish。

### 5. UI：EarClock 首页 + 设置闹钟页（对齐 UI 设计文档）
**修改 `MainActivity.kt`**：在 `WinkNavHost` 增加路由 `earclock`、`earclock/edit/new`、`earclock/edit/{alarmId}`；首页参数透传 `earClockRepository`。

**修改 `HomeScreen.kt`**：在规则列表顶部新增**耳机闹钟入口卡片**——耳机图标 + 「耳机闹钟 EarClock」+ 副标题「通过耳机响铃的闹钟」，点击导航到 `earclock`；沿用现有 `Card` 风格与主题色，与 Wink 保持一致。

**新建 `ui/earclock/EarClockHomeScreen.kt` + `EarClockHomeViewModel.kt`**
- 镜像 `HomeScreen`：`TopAppBar("EarClock", primaryContainer)`、卡片列表（名称 + 频率/时间摘要 + `Switch` + 删除 `IconButton` + 删除确认 `AlertDialog`）、空状态、新增按钮（TopAppBar `+`）。风格与 Wink 一致。
- ViewModel：`loadAlarms / deleteAlarm / toggleEnabled /`，保存后按 `enabled` 调 `scheduleNext` 或 `cancel`。

**新建 `ui/earclock/EarClockEditScreen.kt`** —— 按 UI 设计文档 `docs/earclock-alarm-design.md` 实现，自上而下：
1. **顶部导航栏**：可编辑起止/确认导航。落地用 `TopAppBar`：左侧「取消」文字按钮（返回丢弃未保存）、标题「设置闹钟」（或新建/编辑复用同一标题，按设计稿统一为「设置闹钟」）、右侧「完成」文字按钮（校验通过则保存并返回，否则不响应并提示）。
2. **倒计时提示**：标题下方居中 `距离下次响铃还有 X 小时 X 分钟`。用 `mutableLongStateOf` + `LaunchedEffect` 每秒刷新（复刻 `DebugInfoPanel`）；`nextTriggerMillis` 计算剩余时长。新建未设时刻时显示占位。
3. **时间选择器**：双列滚轮（小时 00–23 / 分钟 00–59）。以**自绘双列 `LazyColumn`** 实现（居中选中行高亮加粗、上下分隔线、上下半透明淡出），还原 iOS 滚轮视觉；选中值映射到 `hour/minute`。
4. **频率切换**：`FilterChip` 单选项【响一次 / 工作日 / 自定义】。UI 稿第 4 项「秒抢闹钟」标注“待定”，**本期不展示**（见决策）。选「工作日」时启用设置卡片中的「工作日类型」；选「自定义」时下方展开周一到周日 7 个可选 `FilterChip`（至少选一天）。
5. **设置分组卡片**（单一 `Card` + 若干设置行，行用 `Row` + 细分隔线，右侧 `>` 箭头）：
   - **工作日类型**：副标题显示当前值「工作日（周一至五）」。`frequency==WORKDAYS` 时可见；本期仅 `WEEKDAYS`（LEGAL 延后）——点击弹 `AlertDialog` 选择（本期仅一项可选，占位后续扩展）。
   - **闹钟名称**：`OutlinedTextField`（inline 编辑，UI 稿为跳转子页，落地用行内输入框简化）。
   - **铃声**：行显示当前铃声名（默认「系统默认闹铃」）；点击 → 系统 `RingtoneManager.ACTION_RINGTONE_PICKER` 选择器，保存选中 URI 与名称。
   - **振动**：`Switch`（开=默认振动 / 关=关闭），对应 `VibrationMode.DEFAULT/OFF`。
   - **稍后提醒**：`Switch` 启用后展开「间隔（分钟）」与「最大次数」两个 `FilterChip` 组（间隔 5/10/15、次数 1/3/5）。
6. 交互流程与 UI 稿第 8 节一致：取消→返回丢弃；完成→保存、写库并 `scheduleNext/cancel`。

> 注意：UI 稿为 iOS 深色视觉，落地按「与 Wink 风格一致性」章节适配为 Material3 + 护眼绿，不直接照抄黑底/系统蓝。

### 6. Manifest / 资源配置
**修改 `AndroidManifest.xml`**：
- 新增 `<activity android:name=".EarClockAlarmActivity" android:showWhenLocked="true" android:turnScreenOn="true" android:excludeFromRecents="true" android:theme="@style/Theme.Wink.Reminder" />`。
- 新增 `<receiver android:name=".service.EarClockAlarmReceiver" android:exported="false" />`。
- 可选：`<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />` 以 best-effort 检测蓝牙耳机（缺失时跳过，不崩溃）。

**修改 `res/values/strings.xml`**：新增 `earclock_*` 文案，覆盖：页面标题「设置闹钟」、取消/完成、倒计时提示「距离下次响铃还有 %1$d 小时 %2$d 分钟」、频率（响一次/工作日/自定义）、工作日类型、闹钟名称、铃声、振动（默认/关闭）、稍后提醒（间隔/最大次数）、保存校验等。

## 假设与决策（Assumptions & Decisions）
- **独立数据模型**：EarClock 字段与 `Rule` 差异大，独立 `EarClockAlarm` + `EarClockRepository`，不耦合既有规则系统。
- **「响一次」语义**：一次性闹钟 = 到达设定时刻响一次；用户点击「立即关闭」后自动停用（`enabled=false` 落库），不再调度。
- **后台唤起**：沿用现有 `fullScreenIntent` 通知方式（受 Android 后台启动限制），比在 Receiver 直接 `startActivity` 更可靠。
- **耳机检测**：有线必中；蓝牙 best-effort（有 `BLUETOOTH_CONNECT` 则检测，否则忽略蓝牙）。未连接耳机时本次触发静默但继续调度下一次。
- **「秒抢闹钟」本期不做**：UI 设计稿将其标注为“待定”，为避免范围膨胀（YAGNI），本期仅实现响应/工作日/自定义三档，UI 不展示该项；后续需求明确再扩展频率枚举。
- **「工作日类型」默认仅「工作日（周一至五）」**：`WorkdayType.LEGAL`（法定工作日，依赖节假日表）预留枚举位但本期不实现；设置项保留，值固定为周一至五。
- **振动三态**：`VibrationMode` 供 UI「默认/关闭」两态，`CUSTOM`（自定义振动）预留延后。
- **时间选择器**：为还原 UI 稿 iOS 双列滚轮，采用自绘双列 `LazyColumn`；若实现中发现吸附/性能问题，降级为 Material3 `TimePickerDialog`。
- **入口**：在 Wink 首页顶部加一张耳机闹钟入口卡片（风格与 Wink 卡片一致），导航到 EarClock 首页。
- **不做**：不做复杂多闹钟提醒改动、不动既有 `Rule` / `ReminderActivity` 逻辑，仅新增独立模块。

## 验证步骤（Verification）
1. 构建：在项目根目录执行 `./gradlew assembleDebug`，确保编译通过、无 lint 阻塞错误。
2. 手测流程：
   - Wink 首页出现耳机闹钟入口卡片，点击进入 EarClock 首页，空状态正常。
   - 进入「设置闹钟」页：顶部三栏（取消/标题/完成）、倒计时提示实时刷新、双列滚轮可选时分、频率 Chip 可切换、设置分组卡片（工作日类型/名称/铃声/振动/稍后提醒）交互正常。
   - 新增闹钟：设置时刻、频率（响一次）、默认铃声、振动开、稍后提醒开；点「完成」保存，返回首页列表出现且 Switch 为开。
   - 到点（缩短时间便于测试）：未连接耳机 → 静默不响、无 crash；连接耳机（有线/蓝牙）→ 弹出全屏闹钟，耳机中循环响铃 + 振动。
   - 「稍后提醒」：点击后按设置的间隔重响；达到次数上限后「稍后提醒」不再显示。
   - 「立即关闭」：停止响铃；ONCE 闹钟关闭后 Switch 自动变为关。
   - 频率切换：工作日（工作日类型）/自定义（至少选一天）下到点正常重复触发；工作日类型本期仅周一至五。
   - 振动开关：关振动时仅响铃不振动。
   - 铃声自定义：选择系统铃声后到点播放所选铃声。
   - 深浅色主题下 UI 与 Wink 一致（护眼绿适配正确）。