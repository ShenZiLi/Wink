package com.wink.eye.ui.qrcode

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ML Kit 二维码分析器，实现 CameraX 的 [ImageAnalysis.Analyzer]。
 *
 * 只识别 [Barcode.FORMAT_QR_CODE]。分析期间用 [busy] 串行化，避免同一帧重复提交；
 * 无论如何都保证 `ImageProxy.close()` 被调用，否则预览会卡死。
 */
class BarcodeAnalyzer(
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer, Closeable {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @Volatile
    private var busy = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (busy) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        busy = true
        // rotationDegrees 必须交给 ML Kit，否则竖屏下识别率会大幅下降
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                    ?.rawValue
                    ?.let(onResult)
            }
            .addOnCompleteListener {
                busy = false
                imageProxy.close()
            }
    }

    /** 释放 ML Kit 资源；页面离开组合时必须调用 */
    override fun close() {
        runCatching { scanner.close() }
    }
}

/**
 * 静态图片二维码解码器，供「从相册识别」使用。
 *
 * 与实时分析器分开持有 scanner 实例，避免与取景框的生命周期互相干扰。
 */
object QrImageDecoder {

    private val scanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    /**
     * 解码本地图片中的二维码。
     *
     * @return `Result.failure` 表示图片读取失败；`Result.success(null)` 表示图中没有二维码
     */
    suspend fun decode(context: Context, uri: Uri): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                // ML Kit 需要能直接读像素的软件位图；默认可能返回 HARDWARE 位图导致解码失败
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
            decodeBitmap(bitmap)
        }
    }

    /** ImageDecoder 已自动应用 EXIF 方向，故旋转角传 0 */
    private suspend fun decodeBitmap(bitmap: Bitmap): String? = suspendCoroutine { cont ->
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes ->
                cont.resume(barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }?.rawValue)
            }
            .addOnFailureListener { cont.resume(null) }
    }
}
