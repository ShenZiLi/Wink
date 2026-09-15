package com.wink.eye.ui.qrcode

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 取景叠加层里展示用的二维码边长 */
const val QrDisplaySize = 512

/**
 * 导出到相册用的二维码边长。
 *
 * 比展示尺寸大一倍，保证存成 PNG 后仍能被远距离或低分辨率相机稳定识别。
 */
const val QrExportSize = 1024

/** 二维码生成结果。内容为空或超过二维码容量时返回对应的失败态，而不是抛异常。 */
sealed interface QrRenderResult {
    /** 生成成功 */
    data class Success(val bitmap: Bitmap) : QrRenderResult

    /** 内容为空，没有可编码的数据 */
    data object Empty : QrRenderResult

    /** 内容超出二维码容量上限（M 级纠错约 2.3KB 字节） */
    data object TooLong : QrRenderResult
}

/**
 * 基于 ZXing 的二维码渲染器。
 *
 * 与源项目的差异（性能相关，必须保留）：
 * - 位图在 [Dispatchers.Default] 上生成，不阻塞主线程
 * - 用 `IntArray` + `setPixels` 一次写入像素，替代逐像素 `setPixel`
 *   （512×512 逐像素会产生 26 万次 JNI 调用，明显掉帧）
 */
object QrCodeRenderer {

    /** 生成指定边长的二维码位图，黑白两色以保证可扫描性 */
    suspend fun render(text: String, size: Int = QrDisplaySize): QrRenderResult =
        withContext(Dispatchers.Default) { renderBlocking(text, size) }

    /** 同步渲染，调用方需自行保证不在主线程执行 */
    fun renderBlocking(text: String, size: Int): QrRenderResult {
        if (text.isEmpty()) return QrRenderResult.Empty
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                // 静默区 4 模块，二维码四周留白，保证扫描器能定位
                EncodeHintType.MARGIN to 4,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    pixels[rowOffset + x] = if (matrix.get(x, y)) COLOR_DARK else COLOR_LIGHT
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            QrRenderResult.Success(bitmap)
        } catch (_: Exception) {
            // 内容超出容量时 ZXing 抛 WriterException / IllegalArgumentException，
            // 统一降级成 TooLong 由界面提示，不能让异常冒到主线程
            QrRenderResult.TooLong
        }
    }

    /** 二维码前景色，必须为深色 */
    private const val COLOR_DARK = 0xFF000000.toInt()

    /** 二维码背景色，必须为浅色；导出与展示都保持纯黑白，染色会降低识别率 */
    private const val COLOR_LIGHT = 0xFFFFFFFF.toInt()
}
