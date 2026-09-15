# Wink

An Android toolkit: **eye-care reminders**, **earphone alarm clock**, and a **QR code utility**. Built entirely with Jetpack Compose and a Liquid Glass visual language.

## Screenshots

<table>
  <tr>
    <td align="center"><b>Wink · Home</b></td>
    <td align="center"><b>Rule Editor</b></td>
    <td align="center"><b>Reminder</b></td>
    <td align="center"><b>Dark Mode</b></td>
  </tr>
  <tr>
    <td><img src="docs/images/home-light.jpg" width="200"/></td>
    <td><img src="docs/images/rule-edit.jpg" width="200"/></td>
    <td><img src="docs/images/reminder-popup.jpg" width="200"/></td>
    <td><img src="docs/images/home-dark.jpg" width="200"/></td>
  </tr>
  <tr>
    <td align="center"><b>EarClock · Alarms</b></td>
    <td align="center"><b>Alarm Setup</b></td>
    <td align="center"><b>Scan</b></td>
    <td align="center"><b>Generate</b></td>
  </tr>
  <tr>
    <td><img src="docs/images/earclock-home.jpg" width="200"/></td>
    <td><img src="docs/images/earclock-edit.jpg" width="200"/></td>
    <td><img src="docs/images/qrcode-scan.jpg" width="200"/></td>
    <td><img src="docs/images/qrcode-generate.jpg" width="200"/></td>
  </tr>
</table>

## Modules

A floating glass bottom bar switches between three tabs. Every page shares the same shell: glass top bar, content cards, floating navigation layer.

### 1. Wink · Screen time tracking and eye-care reminders

| Rule type | Trigger |
|-----------|---------|
| Interval | Repeats at a fixed interval (seconds/minutes), with quick presets |
| Screen time | Fires once accumulated screen-on time reaches the threshold; the counter resets after the screen stays off long enough |

- Each rule card can be toggled, tapped to edit, or swiped to delete
- The home panel shows **current screen-on time** and **last screen-off time**, refreshed every second
- Reminder styles: **continuous ringing** (full screen with looping alarm sound) or **notification popup** (high-priority notification with vibration)
- Screen-time tracking is backed by a foreground service plus AlarmManager, so it stays accurate in the background and under Doze

### 2. EarClock · Earphone alarm

Plays the alarm through earphones when they are connected, so it never blasts the room.

- Selects the Bluetooth output by priority (A2DP over SCO) and plays on the media volume channel when earphones are present
- Frequency: once / workdays / custom weekdays
- Ringtone: system default or a local audio file
- Vibration switch and snooze (interval and maximum repeat count)
- Exact scheduling via `AlarmManager.setAlarmClock()`; alarms are re-scheduled after reboot
- Brings up the full-screen ringing page directly from the background, without relying on vendor notification policies

### 3. QRCode · QR utility

- Live scanning with CameraX preview and ML Kit, with the model bundled in the APK (no Google Play services required)
- Decode from a gallery image
- Generate a QR code from text and save it to `Pictures/Wink`
- Typed content survives tab switches; no local history is kept

## Tech Stack

| Item | Version |
|------|---------|
| Kotlin | 2.1.0 |
| Android Gradle Plugin | 8.10.0 |
| Gradle | 8.13 |
| compileSdk / targetSdk | 36 |
| minSdk | 34 (Android 14+) |
| Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Navigation Compose | 2.8.5 |
| kotlinx-serialization | 1.7.3 |
| Haze (backdrop blur) | 1.6.10 |
| CameraX | 1.4.0 |
| ML Kit barcode-scanning | 17.3.0 |
| ZXing core | 3.5.3 |

Notes on the choices:

- **Haze stays on 1.x**: the 2.x refractive glass requires Kotlin 2.4 + AGP 9.1 + compileSdk 37, which this build chain does not support; refraction and highlights are drawn by the custom glass layer instead
- **Bundled ML Kit**: the scanning model ships inside the APK (~10MB) so recognition works offline and without GMS

## Project Structure

```
app/src/main/java/com/wink/eye/
├── MainActivity.kt                 # Entry point, navigation host, permissions
├── ReminderActivity.kt             # Full-screen ringing reminder
├── EarClockAlarmActivity.kt        # Full-screen alarm ringing
├── WinkApp.kt                      # Application
│
├── data/                           # Rule / EarClockAlarm models and repositories
├── receiver/                       # Screen on/off broadcast receiver
├── service/                        # Foreground services, schedulers, receivers, audio routing
└── ui/
    ├── home/        Wink screen
    ├── edit/        Rule editor
    ├── earclock/    Alarm list and setup
    ├── qrcode/      Camera preview, scanner, renderer, gallery saver
    ├── components/  Shared list card and glass layer
    └── theme/       Theme and colors

com/compose/liquidglassnav/          # Reusable floating glass bottom bar
```

## Build & Run

Requirements: JDK 17 and Android SDK (compileSdk 36).

```bash
./gradlew :app:assembleDebug     # output: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug      # install to a connected device
```

Logs:

```bash
adb logcat -s ScreenMonitorService IntervalAlarmReceiver EarClockAlarmReceiver EarClockRingingService ReminderHelper
```

## Release

Push a `v*` tag and GitHub Actions builds the signed APK and publishes a release:

```bash
git tag -a v1.2.0 -m "Wink v1.2.0" && git push origin v1.2.0
```

- Version name comes from the tag (without `v`); the version code is the workflow run number
- Output: release `vX.Y.Z` with the `Wink-X.Y.Z.apk` asset

## Permissions

| Group | Permissions |
|-------|-------------|
| Reminders and scheduling | `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW` |
| Foreground services | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| Earphone alarm | `BLUETOOTH_CONNECT`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE` |
| QR code | `CAMERA` |
