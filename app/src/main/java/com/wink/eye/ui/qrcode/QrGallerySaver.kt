package com.wink.eye.ui.qrcode

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把二维码位图保存到系统相册。
 *
 * 目标 API 34（minSdk 34）下写 [MediaStore] 无需任何存储权限，
 * 通过 `IS_PENDING` 标记保证写入完成前其它应用读不到半成品文件。
 */
object QrGallerySaver {

    /** 相册子目录名，最终落在 `Pictures/Wink` */
    private const val ALBUM_NAME = "Wink"

    /**
     * 保存位图为 PNG。
     *
     * @return 成功返回媒体库 Uri；插入或写入失败返回 null，由界面提示用户
     */
    suspend fun save(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "wink_qr_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@withContext null

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: false
        }.getOrDefault(false)

        if (!written) {
            // 写入失败要把占位记录删掉，否则相册里会留下一个空文件
            runCatching { resolver.delete(uri, null, null) }
            return@withContext null
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        runCatching { resolver.update(uri, values, null, null) }
        uri
    }
}
