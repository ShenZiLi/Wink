# Agent 规则（Wink 项目）

## 编辑页 UI 规范
- **所有编辑/设置页的顶部导航栏统一为：左上角"返回"文字按钮，右上角"保存"文字按钮，标题居中。**
- 不许使用导航图标（如箭头图标），统一使用 `TextButton` + 文字。
- 左侧文字：`stringResource(R.string.edit_back)`
- 右侧文字：`stringResource(R.string.edit_save)`
- 布局方式：`Row` 包裹三个元素（左按钮、居中标题、右按钮），`Modifier.weight(1f)` 让标题撑满中间。

## Git 提交约定
- **每次功能改动完成后，立即将改动提交到本地 git。**（commit 到本地仓库，无需推送远端）
- 提交信息用中文、简洁描述本次改动内容与目的。
- 提交前先 `git add` 本次改动涉及的具体文件，避免误提交无关文件或敏感文件（如密钥）。
- 除非用户明确要求，否则不主动 push 到远端。

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