package com.nobody.poishelf.platform

/**
 * 应用目录（S12）。Android = Context.cacheDir/filesDir；JVM = 应用数据目录。
 */
expect fun cacheDirPath(): String
expect fun filesDirPath(): String

/**
 * 图片字节写入系统相册/图片目录（S4 字节版跨平台写入）。
 *
 * Android 实际经 MediaStore（RELATIVE_PATH Pictures/PoiShelf）；
 * JVM 写入用户 Pictures/PoiShelf 目录。返回成功结果。
 */
expect suspend fun saveImageBytesToGallery(bytes: ByteArray, fileName: String): Result<Unit>
