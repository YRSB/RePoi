package com.nobody.repoi.platform

import android.content.Context
import coil3.PlatformContext
import org.koin.mp.KoinPlatform.getKoin

actual fun platformContext(): PlatformContext = getKoin().get<Context>()

actual fun imageCacheDir(): String {
    val context: Context = getKoin().get()
    return context.cacheDir.resolve("image_cache").apply { mkdirs() }.absolutePath
}
