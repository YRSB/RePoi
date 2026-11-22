package com.nobody.repoi.platform

import java.io.File

private fun appBaseDir(): File =
    File(System.getProperty("user.home"), ".poishelf").apply { mkdirs() }

actual fun cacheDirPath(): String =
    File(appBaseDir(), "cache").apply { mkdirs() }.absolutePath

actual fun filesDirPath(): String =
    File(appBaseDir(), "files").apply { mkdirs() }.absolutePath

actual suspend fun saveImageBytesToGallery(bytes: ByteArray, fileName: String): Result<Unit> = runCatching {
    val dir = File(File(System.getProperty("user.home"), "Pictures"), "PoiShelf").apply { mkdirs() }
    File(dir, fileName).writeBytes(bytes)
}
