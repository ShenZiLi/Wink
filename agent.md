# Agent 规则（Wink 项目）

## 编辑页 UI 规范
- **所有编辑/设置页的顶部导航栏统一为：左上角"返回"文字按钮，右上角"保存"文字按钮，标题居中。**
- 不许使用导航图标（如箭头图标），统一使用 `TextButton` + 文字。
- 左侧文字：`stringResource(R.string.edit_back)`
- 右侧文字：`stringResource(R.string.edit_save)`
- 布局方式：统一用 `WinkGlassTopBar`，传 `navigationIcon`（返回）、`actions`（保存）、**`centeredTitle = true`**（标题居中）。
  标题由组件内部用 `Modifier.weight(1f)` 撑满中间，不要自行拼 `Row`。

## 液态玻璃导航层（Liquid Glass）

设计依据 Apple WWDC25 Liquid Glass 规范，实现见 `ui/components/WinkGlass.kt`。

### 三条铁律（违反即破坏设计体系）

1. **玻璃只用于导航层**。顶栏、底部导航栏、工具栏可以用玻璃；内容层（卡片、表单、面板）一律用不透明表面色。
2. **不嵌套玻璃**。玻璃元素之上或之内不要再叠玻璃，也不要在一屏内堆多块玻璃。
3. **染色只给主要操作与选中态**，不做整体染色。

### 依赖与版本红线

```kotlin
implementation("dev.chrisbanes.haze:haze:1.6.10")   // 提供 backdrop blur
```

> ⚠️ **不要升级这个依赖。** Haze 1.7.3 及以上要求 Compose 1.12.0，Haze 2 还要求 Kotlin 2.4 + AGP 9.1 + compileSdk 37；
> 本项目是 AGP 8.10 / Kotlin 2.1 / compileSdk 36，升上去等于整套构建链重做。
> Haze 1.x **没有** `haze-glass` 模块（折射玻璃是 2.0 独有），折射与高光由 `glassHighlight` 等自绘层补齐。

### 组件清单

| 组件 | 用途 |
|---|---|
| `WinkGlassVariant` | `Regular`（默认，随主题自适应）/ `Clear`（永久透明，仅用于媒体丰富内容之上）。**两者不混用** |
| `winkGlassStyle(variant, blurRadius, tintBoost)` | 生成主题自适应样式：染色 + 磨砂噪声 + 不支持模糊时的回退底色 |
| `Modifier.winkGlassSource(state)` | 把节点标记为**采样源**（内容层用），无视觉效果 |
| `Modifier.winkGlassSurface(state, shape, blurRadius, drawHighlight)` | 把节点变成玻璃：背景模糊 + 高光描边 |
| `Modifier.glassHighlight(shape, strength)` | 顶部高光描边，模拟光源自上而下，制造薄厚度 |
| `WinkGlassTopBar(title, hazeState, navigationIcon, actions, centeredTitle)` | 悬浮玻璃顶栏，内置状态栏 padding 与主题自适应阴影 |
| `WinkGlassDefaults` | 模糊半径基准：导航栏 `NavigationBarBlur` / 顶栏 `TopBarBlur` |
| `WinkGlassShapes` | `NavigationBar`（30dp 圆角）/ `TopBar`（下方圆角） |
| `WinkGlassTopBarDefaults.totalHeight()` | 顶栏含状态栏的完整高度，供内容层算留白 |

### 用法：内容层与导航层共享同一个 HazeState

```kotlin
val hazeState = rememberHazeState()

Box(Modifier.fillMaxSize()) {
    // 内容层：标记为采样源 + 顶部让出顶栏高度
    LazyColumn(
        modifier = Modifier.fillMaxSize().winkGlassSource(hazeState),
        contentPadding = PaddingValues(top = WinkGlassTopBarDefaults.totalHeight())
    ) { ... }

    // 导航层：悬浮叠在内容之上
    WinkGlassTopBar("标题", hazeState, Modifier.align(Alignment.TopCenter))
}
```

### 新增玻璃导航元素的标准步骤

1. 页面 `Scaffold` 设 `contentWindowInsets = WindowInsets(0, 0, 0, 0)`，状态栏留白由内容自行处理。
2. 顶栏**不要**放进 `Scaffold` 的 `topBar` 槽位 —— 用 `Box` + `align(TopCenter)` **悬浮叠放**。
3. 内容打 `winkGlassSource`，顶部留白设为顶栏完整高度（列表用 `WinkConfigList(extraTopPadding = ...)`）。
4. 玻璃元素自身加 `Modifier.shadow(...)` 建立浮起层次。

> **为什么必须这样**：若顶栏走 `Scaffold` 槽位，内容永远落在顶栏下方，玻璃采样不到任何画面，模糊形同虚设。
> 悬浮叠放后，列表滚动时内容会从玻璃下方穿过，玻璃才有可模糊的对象。

### 参数基准

- 模糊半径：底部导航栏 30dp / 顶栏 24dp
- 玻璃底色 alpha：亮色 0.66 / 暗色 0.58（由 `background.luminance()` 自动判定）
- 顶栏阴影 alpha：亮色 0.16 / 暗色 0.55。**亮色主题下玻璃与背景都很浅，只靠 1px 高光描边看不出边缘，必须靠阴影建立层级**。

### 已玻璃化的元素

底部导航栏（`com.compose.liquidglassnav.LiquidGlassBottomNavBar`）、Wink 首页顶栏、EarClock 首页顶栏、二维码页顶栏、规则编辑页顶栏、耳机闹钟编辑页顶栏。

> 各导航元素持有**独立**的 `HazeState`（采样源不同）；底部导航栏的采样源是 `MainActivity` 中的 `NavHost`。

## 二维码页（QRCode Tab）

`ui/qrcode/` 下 7 个文件，是独立二维码 App（CameraX + ML Kit + ZXing）移植进 Wink 的产物。
单页沉浸式：玻璃顶栏 + 相机取景卡（`weight(1f)`）+ 底部编辑面板，玻璃底部导航栏**叠在编辑面板之上**（面板提供被模糊的内容）。

### 三条硬约束（改这个模块前必读）

1. **`PreviewView` 必须用 `ImplementationMode.COMPATIBLE`**。
   默认的 `PERFORMANCE` 走 SurfaceView，画面由系统合成器输出、不进 View 绘制树：
   圆角裁剪失效、会穿透 Compose 层级**盖住悬浮的玻璃底部导航栏**、Haze 也采样不到画面。

2. **相机必须手动释放**。
   `bindToLifecycle` 绑的是 **Activity** 生命周期，切 Tab 不会触发 `onStop`。
   绑定放在 `DisposableEffect(shouldRunCamera)` 里，`onDispose` 调 `unbindAll()`；
   `BarcodeAnalyzer.close()`（即 `BarcodeScanner.close()`）与 `ExecutorService.shutdown()`
   放在另一个 `DisposableEffect(Unit)` 里。漏掉任何一处都会导致切 Tab 后相机常驻耗电。
   `addListener` 回调里要带 `disposed` 守卫，否则会在组合销毁后才绑定。

3. **相机权限不进 `MainActivity.requestPermissions()`**。
   那会让 App 一启动就弹相机权限。改为在 `QrToolScreen` 内按需申请。
   注意 `shouldShowRequestPermissionRationale` 在「从未申请」与「被永久拒绝」时**都返回 false**，
   必须配合「是否已申请过」的标记才能区分，否则无法正确引导到系统设置页。

### 其他要点

- **ZXing 生成**：位图在 `Dispatchers.Default` 上生成，用 `IntArray` + `setPixels` 一次写入
  （逐像素 `setPixel` 在 512×512 下是 26 万次 JNI 调用）。内容超容量时 ZXing 抛异常，
  必须 catch 后降级成 `QrRenderResult.TooLong` 提示用户，不能让它冒到主线程。
- **二维码必须保持纯黑白**（染色会降低识别率），用白色圆角卡承载，两种主题下同一种呈现。
  展示用 512px，导出到相册用 1024px（含 4 模块静默区）。
- **保存到相册**走 `MediaStore` + `IS_PENDING`（minSdk 34 无需存储权限），落盘位置 `Pictures/Wink`。
  写入失败要 `resolver.delete(uri)` 清掉占位记录，否则相册里会留空文件。
- **从相册识别**用 `ActivityResultContracts.PickVisualMedia`（无需权限），
  `ImageDecoder` 必须设 `allocator = ALLOCATOR_SOFTWARE` —— 默认可能返回 HARDWARE 位图导致 ML Kit 读不到像素；
  且 `ImageDecoder` 已自动应用 EXIF 方向，`InputImage.fromBitmap` 的旋转角传 0。
- **文本内容放在 `QrToolViewModel`** 而不是 `remember`。本页是 NavHost 的一个目的地，
  切 Tab 会离开组合；ViewModel 建于 `WinkNavHost` 顶层（Activity 作用域）才能保留内容。
- **键盘弹出时的三处联动**（`MainActivity` 已设 `windowSoftInputMode="adjustResize"`，实际靠 IME insets 生效）：
  1. `QrToolScreen` 给内容加 `imePadding()`；
  2. 取景卡**压成 0 高而不是移出组合树**，并传 `active = false` 暂停取景。
     移出组合会连带 `BarcodeAnalyzer.close()` 与执行器 `shutdown()`，键盘一开一关就重建一次相机；
     压 0 高则保留组件，只释放相机，由 `QrCameraCard` 的 `shouldRunCamera = active && ...` 控制；
  3. 编辑面板的底部避让高度归零（键盘已挡住导航栏，再留白就是空白），
     同时 `MainActivity` 在键盘可见时**整体隐藏底部导航栏** —— 悬浮导航栏在窗口底部、
     不参与页面内容的 IME 避让，留在原地会被输入法候选栏切掉一截。与 iOS 标签栏行为一致。
- **`WindowInsets.ime` 要先取实例再套 `derivedStateOf`**：它是 `@Composable` 取值，不能直接写在
  `derivedStateOf` 的 lambda 里；而键盘动画期间 insets 逐帧变化，直接比较布尔值会让
  `WinkNavHost` / 整页每帧重组。
- **一次性提示要带自增序号**。若界面只用 `LaunchedEffect(messageRes)`，连续两条相同提示
  （例如连续两次保存失败）文案不变、副作用不重跑，第二条 Toast 就消失了。
- **相册识别不要复用带守卫的扫码回调**。`onScanSuccess` 有「动效期间」「生成叠加层显示中」两个
  守卫，若复用会让相册识别的结果被静默吞掉。追加逻辑抽成 `appendScannedText`，两条路径各自调用；
  进入相册识别前先 `onHideQr()`，否则叠加层开着时用户会以为「选了图片没反应」。
- **「可撤销」要在 `onTextChanged` 立即点亮**，不能等 500ms debounce 入栈，
  否则连续输入期间按钮一直是灰的。快照入栈仍由 debounce 合并，撤销粒度不变。
- 底部导航共 3 项，`LiquidGlassBottomNavBar` 的指示器位置按 `items.size` 通用计算，无需改组件；
  但 `MainActivity` 的 `showBottomBar` 与 `selectedIndex` 两处 `when`/条件必须同步补 `qrcode` 分支。

### 二维码模块的依赖

```kotlin
val cameraX = "1.4.0"
implementation("androidx.camera:camera-core:$cameraX")      // 要求 compileSdk ≥ 35，本项目 36
implementation("androidx.camera:camera-camera2:$cameraX")
implementation("androidx.camera:camera-lifecycle:$cameraX")
implementation("androidx.camera:camera-view:$cameraX")
implementation("com.google.mlkit:barcode-scanning:17.3.0")  // bundled 版：模型进 APK，运行时不依赖 GMS
implementation("com.google.zxing:core:3.5.3")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7") // collectAsStateWithLifecycle / LocalLifecycleOwner
```

> ML Kit 的 bundled AAR 约 9.9MB，APK 因此增大约 13MB。换 unbundled 版会引入运行时 GMS 下载，不划算。

## Git 提交约定
- **每次变更完成后，立即将改动提交到本地 git。**（commit 到本地仓库，无需推送远端）
- 提交信息用中文、简洁描述本次改动内容与目的。
- 提交前先 `git add` 本次改动涉及的具体文件，避免误提交无关文件或敏感文件（如密钥）。
- 除非用户明确要求，否则不主动 push 到远端。

## UI 布局遮挡检查（每次改动后必做）

**凡改动涉及 UI/布局，提交前必须对照以下清单自查，防止组件互相遮挡/重叠。**

### 触发场景（出现任一即需检查）
- 引入了**悬浮、覆盖式组件**（如悬浮底部栏、FAB、弹窗、BottomSheet、覆盖在内容的导航）。
- 修改了**固定定位内容**（屏幕底部/顶部面板、DebugInfoPanel 等）。
- 改动涉及 `Scaffold.bottomBar`、`innerPadding`、`Box` 叠加、`safeDrawing` / 边距留白。

### 检查清单
1. **覆盖式组件是否遮挡内容**：悬浮/覆盖组件占位高度是否已为其下方/上方内容预留（如探测"菜单栏高度 ≈ 玻璃条高 + 悬浮留白 + 间距"）。
2. **完整内容是否被裁剪**：滚动列表（`LazyColumn` 等）末尾项、固定面板是否会被悬浮组件盖住，是否给了 `bottom padding` / `contentPadding`。
3. **深浅色两态均验证**：遮挡问题在亮/暗模式下表现一致（颜色透明度过高也易产生视觉重叠）。
4. **真机目视确认**：部署到真机后截图或目视最终渲染，确认无重叠后方可提交。

### 通用做法
- 新增覆盖式组件时，优先在对应内容侧预留等高的底部留白（用常量表达，避免魔法数）。
- 全屏内容叠加悬浮组件时，走 `Scaffold.innerPadding` 或在内容外层加对应方向 padding，而非硬编码少量偏移。

## 真机部署流程

**每次改动完成后，若已连接真实设备，按以下流程构建、安装并启动验证。** 仅模拟器/无线设备则跳过并说明。

### 精简命令（已验证可用）

```bash
# 0. 环境（adb、gradle 均不在默认 PATH，需指定）
export PATH="$PATH:$HOME/.local/share/mise/installs/android-sdk/21.0/platform-tools"
GDIR=~/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13/bin/gradle

# 1. 确认设备在线（须出现 "xxx device"）
adb devices

# 2. 构建
"$GDIR" assembleDebug --console=plain

# 3. 安装（-r 覆盖安装）
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 4. 启动
adb shell am start -n com.wink.eye/.MainActivity

# 5. 验证进程存活（有 PID 即正常，无则崩溃）
adb shell pidof com.wink.eye
```

### 关键说明
- **adb**：SDK 路径来自 `local.properties` 的 `sdk.dir`，当前为 `$HOME/.local/share/mise/installs/android-sdk/21.0/platform-tools`。
- **gradle**：wrapper 下载受 TLS 证书阻断，暂用已解压的 `<GDIR>` 替代 `./gradlew`。
- 多设备时在 adb 后加 `-s <serial>` 指定目标（如 `adb -s 90d3e7c6 install -r …`）。
- 增量构建走缓存，通常几秒完成。
