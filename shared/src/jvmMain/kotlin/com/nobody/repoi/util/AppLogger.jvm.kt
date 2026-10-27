package com.nobody.repoi.util

import java.io.File

actual fun platformLog(priority: Int, tag: String, message: String, throwable: Throwable?) {
    val level = when (priority) {
        2 -> "D"
        3 -> "I"
        4 -> "W"
        else -> "E"
    }
    System.err.println("$level/$tag: $message")
    throwable?.printStackTrace(System.err)
}

actual fun logCacheDir(): String {
    val dir = File(System.getProperty("user.home"), ".repoi/cache")
    dir.mkdirs()
    return dir.absolutePath
}

actual fun platformVersionName(): String = "1.0.0(1)"

actual fun platformDeviceInfo(): String {
    val name = System.getProperty("os.name") ?: "unknown"
    val version = System.getProperty("os.version") ?: "unknown"
    val arch = System.getProperty("os.arch") ?: "unknown"
    return "$name $version ($arch)"
}
