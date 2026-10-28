package com.nobody.poishelf.platform

import android.content.ContentValues
import android.provider.MediaStore

/**
 * 应用目录（S12）：`Context.cacheDir/filesDir`。
 * 与 `PoiShelfApplication` 时代一致；DataStore 文件名保持 `<name>.preferences_pb`。
 */
actual fun cacheDirPath(): String = poiShelfAppContext().cacheDir.absolutePath

actual fun filesDirPath(): String = poiShelfAppContext().filesDir.absolutePath

/**
 * 图片字节写入系统相册（S4）：`MediaStore` insert + 输出流写入，
 * `RELATIVE_PATH` = `Pictures/PoiShelf`（调用方已切到 IO 上下文，见 Gallery VM）。
 */
actual suspend fun saveImageBytesToGallery(bytes: ByteArray, fileName: String): Result<Unit> = runCatching {
    val resolver = poiShelfAppContext().contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, guessImageMimeType(fileName))
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PoiShelf")
    }
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values) ?: error("MediaStore insert 返回空 Uri")
    try {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法打开相册输出流")
    } catch (e: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
}

private fun guessImageMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/jpeg"
}
