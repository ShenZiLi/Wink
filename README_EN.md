# Wink — Eye Care Reminder

An Android eye care reminder app that helps you develop healthy eye habits through periodic reminders and screen-on time monitoring.

## 📱 Screenshots

<table>
  <tr>
    <td align="center"><b>Home</b></td>
    <td align="center"><b>Rule: Interval</b></td>
    <td align="center"><b>Rule: Screen Time</b></td>
    <td align="center"><b>Alert: Popup</b></td>
    <td align="center"><b>Alert: Fullscreen</b></td>
    <td align="center"><b>Dark Mode</b></td>
  </tr>
  <tr>
    <td><img src="docs/images/首页.jpg" width="200"/></td>
    <td><img src="docs/images/规则-定时提醒.jpg" width="200"/></td>
    <td><img src="docs/images/规则-亮屏时长.jpg" width="200"/></td>
    <td><img src="docs/images/提醒-弹窗提醒.jpg" width="200"/></td>
    <td><img src="docs/images/提醒-全屏提醒.jpg" width="200"/></td>
    <td><img src="docs/images/暗色模式.jpg" width="200"/></td>
  </tr>
</table>

## Features

### 1. Interval Reminder

Periodically remind you to rest your eyes at a fixed interval.

- **Custom Interval**: Set X seconds/minutes, manual input
- **Quick Presets**: Every 15 min / 30 min / 1 hour
- Uses `AlarmManager.setExactAndAllowWhileIdle` for precise scheduling — works reliably even when the app is in the background or Doze mode

### 2. Screen-On Time Monitoring

Continuously monitors cumulative screen-on time and triggers reminders when a threshold is reached.

- **Screen-On Threshold**: How long of continuous use before reminding (supports minutes/seconds)
- **Screen-Off Reset Duration**: How long the screen must be off before resetting the timer (supports minutes/seconds)
- Dual protection: foreground service + AlarmManager for accurate timing and reminders even in background/locked state
- Real-time display of screen-on duration and last screen-off time on the home screen

### 3. Reminder Modes

| Mode | Behavior |
|------|----------|
| Alarm | Full-screen popup + alarm ringtone loop, stops only when dismissed manually |
| Notification | High-priority notification + lock-screen popup + vibration, swipeable to dismiss |

Both modes support lock-screen display and vibration.

### 4. Themes

Light/Dark theme toggle with persistent preference.

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.1.0 |
| Android Gradle Plugin | 8.10.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 34 |
| Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Navigation Compose | 2.8.5 |
| kotlinx-serialization | 1.7.3 |
| Gradle | 9.5.1 (local install, no wrapper) |

## Project Structure

```
app/src/main/java/com/wink/eye/
├── MainActivity.kt              # Main entry, navigation host, permission requests
├── ReminderActivity.kt          # Full-screen alarm reminder page
├── WinkApp.kt                   # Application class
│
├── data/
│   ├── Rule.kt                  # Data models (Rule, RuleType, IntervalUnit, ScreenTimeUnit, ReminderMode)
│   └── RuleRepository.kt        # SharedPreferences + JSON serialization persistence
│
├── receiver/
│   └── ScreenReceiver.kt        # Screen on/off broadcast receiver
│
├── service/
│   ├── ScreenMonitorService.kt  # Screen-on monitoring foreground service + AlarmManager scheduling
│   ├── ScreenTimeAlarmReceiver.kt   # Screen time alarm trigger receiver
│   ├── IntervalAlarmScheduler.kt    # Interval reminder alarm scheduler
│   ├── IntervalAlarmReceiver.kt     # Interval reminder alarm trigger receiver
│   └── ReminderHelper.kt        # Notification/full-screen reminder sender
│
└── ui/
    ├── home/
    │   ├── HomeScreen.kt        # Home: rule list + real-time screen info panel
    │   └── HomeViewModel.kt     # Home state management
    ├── edit/
    │   └── EditScreen.kt        # Rule editor page
    └── theme/
        ├── Theme.kt             # Theme management (ThemeManager, WinkTheme)
        └── Color.kt             # Light/dark color definitions
```

## Permissions

| Permission | Purpose |
|------------|---------|
| `FOREGROUND_SERVICE` | Screen-on monitoring foreground service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ foreground service type declaration |
| `POST_NOTIFICATIONS` | Android 13+ notification sending |
| `SCHEDULE_EXACT_ALARM` | Android 12+ exact alarm scheduling |
| `USE_EXACT_ALARM` | Alarm-type app usage declaration |
| `WAKE_LOCK` | Wake device for reminders |
| `VIBRATE` | Reminder vibration |
| `USE_FULL_SCREEN_INTENT` | Lock-screen full-screen reminder |

## Build & Run

### Requirements

- Android SDK (compileSdk 36)
- Gradle 9.5.1 (local install)
- JDK 17

### Build

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## License

```
MIT License

Copyright (c) 2026
```

---

> Take care of your eyes, one reminder at a time.
