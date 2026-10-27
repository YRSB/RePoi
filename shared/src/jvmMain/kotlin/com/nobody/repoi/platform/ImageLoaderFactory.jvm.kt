package com.nobody.repoi.platform

import coil3.PlatformContext
import java.io.File

actual fun platformContext(): PlatformContext = PlatformContext.INSTANCE

actual fun imageCacheDir(): String {
    val dir = File(System.getProperty("user.home"), ".repoi/image_cache")
    dir.mkdirs()
    return dir.absolutePath
}
