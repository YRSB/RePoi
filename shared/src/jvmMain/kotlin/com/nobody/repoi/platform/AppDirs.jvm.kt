package com.nobody.repoi.platform

import java.io.File

private fun appBaseDir(): File =
    File(System.getProperty("user.home"), ".poishelf").apply { mkdirs() }

/**
 * 应用目录（S12）JVM 实际：用户主目录 `.poishelf/` 下 `cache/` + `files/`。
 * DataStore 文件落 `files/`，文件名与 Android 侧同名（升级连续仅针对 Android）。
 */
actual fun cacheDirPath(): String =
    File(appBaseDir(), "cache").apply { mkdirs() }.absolutePath

actual fun filesDirPath(): String =
    File(appBaseDir(), "files").apply { mkdirs() }.absolutePath

/**
 * 图片字节写入系统相册（S4）JVM 实际：桌面无系统相册 API，
 * 写入用户 `Pictures/PoiShelf` 目录（与 android 侧相对路径同名）。
 */
actual suspend fun saveImageBytesToGallery(bytes: ByteArray, fileName: String): Result<Unit> = runCatching {
    val dir = File(File(System.getProperty("user.home"), "Pictures"), "PoiShelf").apply { mkdirs() }
    File(dir, fileName).writeBytes(bytes)
}
