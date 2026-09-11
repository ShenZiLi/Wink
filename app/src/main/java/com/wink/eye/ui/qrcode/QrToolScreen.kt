package com.wink.eye.ui.qrcode

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wink.eye.R
import com.wink.eye.ui.components.WinkGlassTopBar
import com.wink.eye.ui.components.WinkGlassTopBarDefaults
import com.wink.eye.ui.components.winkGlassSource
import com.wink.eye.ui.theme.ThemeManager
import com.wink.eye.ui.theme.ThemeMode
import com.wink.eye.ui.theme.WinkLayoutOverlay
import dev.chrisbanes.haze.rememberHazeState

/**
 * 二维码页：上半相机取景、下半编辑面板的单页沉浸式布局。
 *
 * 液态玻璃规范（见 agent.md）：顶栏作为悬浮层叠在内容之上，内容层打 `winkGlassSource`，
 * 页面 `Scaffold` 的 `contentWindowInsets` 清零由内容自行处理状态栏留白。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrToolScreen(viewModel: QrToolViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsState(initial = ThemeMode.LIGHT)

    // 相机权限只在进入本页时申请，不放进 MainActivity 的启动流程
    val activity = remember(context) { context.findActivity() }
    // 用 rememberSaveable：切 Tab 再回来仍是同一个目的地状态，
    // 否则每次进页都当成「首次」重新申请一次权限
    var hasRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
    }

    val requestOrGuideCamera = {
        when {
            hasCameraPermission(context) -> viewModel.onCameraPermissionResult(true)
            // 首次进入时 shouldShowRequestPermissionRationale 同样是 false，
            // 必须配合「是否已申请过」才能区分「没问过」与「被永久拒绝」
            hasRequested && activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.CAMERA
            ) != true -> openAppSettings(context)

            else -> {
                hasRequested = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasCameraPermission(context)) viewModel.onCameraPermissionResult(true)
        else requestOrGuideCamera()
    }

    // 从系统设置页返回时重新校验权限，授权后立刻恢复取景
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasCameraPermission(context)) {
                viewModel.onCameraPermissionResult(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 相册识别：系统照片选择器，无需任何权限
    val pickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onDecodeFromGallery(context, uri)
    }

    // 一次性提示。按 messageId 而非 messageRes 触发：
    // 连续两条相同提示（例如连续两次保存失败）文案不变，只监听文案不会重新执行副作用。
    val messageId = state.messageId
    LaunchedEffect(messageId) {
        state.messageRes?.let { resId ->
            Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val topBarHazeState = rememberHazeState()
    val topBarHeight = WinkGlassTopBarDefaults.totalHeight()

    // 键盘可见时收起取景框，把空间留给编辑面板，避免相机被压成一条窄缝。
    // WindowInsets.ime 是 @Composable 取值，必须先拿到实例；
    // 再套 derivedStateOf 收口：键盘动画期间 insets 逐帧变化，
    // 直接在组合里比较布尔值会让整个页面每帧重组。
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .winkGlassSource(topBarHazeState)
                    .imePadding()
                    .padding(top = topBarHeight)
            ) {
                QrCameraCard(
                    // 键盘弹出时暂停取景（释放相机）但保留组件，避免重建
                    active = !imeVisible,
                    cameraEnabled = state.cameraEnabled,
                    hasCameraPermission = state.hasCameraPermission,
                    scanSuccess = state.scanSuccess,
                    generatedQr = state.generatedQr,
                    showGeneratedQr = state.showGeneratedQr,
                    onHideQr = viewModel::onHideQr,
                    onSaveQr = { viewModel.onSaveQrToGallery(context) },
                    onScanResult = viewModel::onScanSuccess,
                    onRequestPermission = requestOrGuideCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        // 键盘弹出时压成 0 高而不是移出组合树：
                        // 移出会连带关闭 ML Kit 分析器与执行器，键盘一开一关就重建一次相机
                        .then(
                            if (imeVisible) Modifier.height(0.dp) else Modifier.weight(1f)
                        )
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (imeVisible) 0.dp else 8.dp,
                            bottom = if (imeVisible) 0.dp else 8.dp
                        )
                )

                // 取景框让位后，用弹性占位把编辑面板压到键盘正上方
                if (imeVisible) Spacer(Modifier.weight(1f))

                QrEditPanel(
                    text = state.text,
                    copied = state.copied,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    // 键盘弹出时底部导航栏被遮挡，无需再留白
                    bottomReservedHeight = if (imeVisible) {
                        0.dp
                    } else {
                        WinkLayoutOverlay.BottomBarOverlayHeight - 16.dp
                    },
                    onTextChanged = viewModel::onTextChanged,
                    onClear = viewModel::onClear,
                    onBackspace = viewModel::onBackspace,
                    onCopy = { viewModel.onCopy(context) },
                    onUndo = viewModel::onUndo,
                    onRedo = viewModel::onRedo,
                    onPickFromGallery = {
                        pickMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onGenerate = viewModel::onGenerateQr
                )
            }

            WinkGlassTopBar(
                title = stringResource(R.string.qr_home_title),
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
                    if (state.hasCameraPermission) {
                        IconButton(onClick = viewModel::onToggleCamera) {
                            Icon(
                                imageVector = if (state.cameraEnabled) {
                                    Icons.Default.NoPhotography
                                } else {
                                    Icons.Default.CameraAlt
                                },
                                contentDescription = if (state.cameraEnabled) {
                                    stringResource(R.string.qr_camera_off)
                                } else {
                                    stringResource(R.string.qr_camera_on)
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}

/** 跳到本应用的系统设置页，用于权限被永久拒绝后的引导 */
private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }
}

/** 从 Context 链上找到宿主 Activity，用于查询权限申请状态 */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
