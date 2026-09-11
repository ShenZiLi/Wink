package com.wink.eye.ui.qrcode

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wink.eye.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 取景卡圆角，与 Wink 的卡片圆角规范保持一致 */
private val CameraCardShape = RoundedCornerShape(20.dp)

/**
 * 相机取景卡：CameraX 预览 + 四角扫框 + 扫描成功动效 + 生成二维码叠加层。
 *
 * 相机的绑定与释放完全由本组件负责：
 * [ProcessCameraProvider.bindToLifecycle] 绑定的是 Activity 的生命周期，切到别的 Tab 不会触发
 * onStop，因此必须在离开组合时手动 `unbindAll()`，否则相机会一直占用。
 *
 * @param active 页面当前是否需要取景。键盘弹出等场景传 false —— 只暂停取景释放相机，
 *   组件本身**不能**从组合树移除：那会连带 `BarcodeAnalyzer.close()` 与执行器 `shutdown()`，
 *   键盘一开一关就重建一次相机，既闪烁又有提交到已关闭执行器的风险。
 */
@Composable
fun QrCameraCard(
    active: Boolean,
    cameraEnabled: Boolean,
    hasCameraPermission: Boolean,
    scanSuccess: Boolean,
    generatedQr: Bitmap?,
    showGeneratedQr: Boolean,
    onHideQr: () -> Unit,
    onSaveQr: () -> Unit,
    onScanResult: (String) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            // 必须用 COMPATIBLE（TextureView）。默认的 PERFORMANCE 走 SurfaceView，
            // 画面由系统合成器输出、不进 View 绘制树：圆角裁剪会失效，
            // 并且会穿透 Compose 的层级盖住悬浮的玻璃底部导航栏，Haze 也采样不到画面。
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // 用 rememberUpdatedState 包一层，避免 remember 里捕获到过期的回调
    val currentOnScanResult by rememberUpdatedState(onScanResult)
    val analyzer = remember { BarcodeAnalyzer { text -> currentOnScanResult(text) } }

    val previewUseCase = remember { Preview.Builder().build() }
    val analysisUseCase = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    // 生成二维码叠加层显示期间不取景：既省电，也避免扫码结果与生成结果互相干扰
    val shouldRunCamera = active && hasCameraPermission && cameraEnabled && !showGeneratedQr

    DisposableEffect(shouldRunCamera) {
        var disposed = false
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            // 组合已销毁时不能再绑定，否则会绑到已释放的 PreviewView 上
            if (disposed) return@addListener
            val provider = runCatching { providerFuture.get() }.getOrNull() ?: return@addListener
            runCatching {
                provider.unbindAll()
                if (shouldRunCamera) {
                    previewUseCase.setSurfaceProvider(previewView.surfaceProvider)
                    analysisUseCase.setAnalyzer(analysisExecutor, analyzer)
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase,
                        analysisUseCase
                    )
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    // ML Kit 与线程池的生命周期与页面绑定，不能泄漏
    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    // 扫码成功触发一次短震动，作为「识别到了」的触觉反馈
    LaunchedEffect(scanSuccess) {
        if (scanSuccess) vibrateOnce(context)
    }

    Box(
        modifier = modifier
            .clip(CameraCardShape)
            .background(Color.Black)
    ) {
        // 预览视图始终留在组合树里，否则 surfaceProvider 无法附着
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (shouldRunCamera) {
            ScanFrameOverlay(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = stringResource(R.string.qr_scan_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            )
        }

        when {
            !hasCameraPermission -> QrCameraPermissionPlaceholder(onClick = onRequestPermission)
            showGeneratedQr && generatedQr != null -> QrGeneratedOverlay(
                bitmap = generatedQr,
                onHide = onHideQr,
                onSave = onSaveQr
            )

            !cameraEnabled -> QrCameraOffPlaceholder()
        }

        ScanSuccessOverlay(visible = scanSuccess)
    }
}

/**
 * 四角扫框角标，主色描边，提示有效识别区域。
 *
 * 源项目没有这个元素，属于移植时补充的可用性设计。
 */
@Composable
private fun ScanFrameOverlay(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = size.minDimension * 0.62f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val right = left + side
        val bottom = top + side
        val arm = side * 0.24f
        val stroke = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val corners = listOf(
            Path().apply {
                moveTo(left, top + arm)
                lineTo(left, top)
                lineTo(left + arm, top)
            },
            Path().apply {
                moveTo(right - arm, top)
                lineTo(right, top)
                lineTo(right, top + arm)
            },
            Path().apply {
                moveTo(right, bottom - arm)
                lineTo(right, bottom)
                lineTo(right - arm, bottom)
            },
            Path().apply {
                moveTo(left + arm, bottom)
                lineTo(left, bottom)
                lineTo(left, bottom - arm)
            }
        )
        corners.forEach { drawPath(path = it, color = color, style = stroke) }
    }
}

/** 相机关闭占位：黑底 + 划掉的相机图标 */
@Composable
private fun QrCameraOffPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.NoPhotography,
                contentDescription = stringResource(R.string.qr_camera_off_placeholder),
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.qr_camera_off_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 相机权限缺失占位。
 *
 * 与源项目不同：受限于「只申请一次」的系统行为，这里点击后进入系统设置页，
 * 由调用方决定走申请还是走设置引导。
 */
@Composable
private fun QrCameraPermissionPlaceholder(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.qr_permission_denied),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.qr_permission_denied_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 扫描成功动效：主色边框闪烁 + 中心对勾，总时长约 800ms。
 *
 * 源项目用的是固定的绿色，移植后改用主题主色，符合 Wink「染色只给主要操作与选中态」的规范。
 */
@Composable
private fun ScanSuccessOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        val accent = MaterialTheme.colorScheme.primary
        val transition = rememberInfiniteTransition(label = "scan_success")
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scan_success_pulse"
        )
        val checkScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(300),
            label = "scan_success_scale"
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 6.dp, color = accent.copy(alpha = pulse), shape = CameraCardShape)
            )
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(accent.copy(alpha = pulse * 0.22f), CircleShape)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.qr_scan_success),
                    tint = accent,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

/**
 * 生成二维码叠加层：半透明蒙层 + 白色圆角卡 + 二维码 + 保存按钮。
 *
 * 二维码本体必须保持纯黑白（染色会降低识别率），因此这里用白色卡片承载，
 * 在亮色与暗色主题下都是同一种呈现。
 */
@Composable
private fun QrGeneratedOverlay(
    bitmap: Bitmap,
    onHide: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onHide
            ),
        contentAlignment = Alignment.Center
    ) {
        // 二维码边长跟随卡片尺寸收缩，小屏或压缩布局下也不会溢出
        val qrSide = (minOf(maxWidth.value, maxHeight.value) * 0.55f).dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.qr_generated),
                    modifier = Modifier.size(qrSide)
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSave) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.qr_save_to_gallery),
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = onHide) {
                Text(text = stringResource(R.string.qr_close), color = Color.White)
            }
        }
    }
}

/**
 * 扫码成功震动反馈。
 *
 * 无马达设备静默跳过；API 31+ 走 [VibratorManager]，低版本回退废弃的 [Vibrator]。
 */
private fun vibrateOnce(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    if (vibrator?.hasVibrator() == true) {
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }
}

/** 相机权限是否已授予 */
fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
