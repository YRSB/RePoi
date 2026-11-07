package com.nobody.repoi.platform

import android.content.ComponentCallbacks2
import coil3.ImageLoader
import com.nobody.repoi.util.ImageAspectRatioCache
import org.koin.core.context.GlobalContext

/**
 * Android 平台内存修剪工具：
 * 承接系统的 [ComponentCallbacks2.onTrimMemory] 与 [ComponentCallbacks2.onLowMemory] 回调，
 * 封装在 shared 模块内部以访问 Coil 内存缓存与宽高比缓存，避免 androidApp 壳工程直接依赖 Coil3。
 */
object PlatformMemoryTrimmer {
    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            clearImageCaches()
        }
    }

    fun onLowMemory() {
        clearImageCaches()
    }

    private fun clearImageCaches() {
        try {
            val imageLoader = GlobalContext.get().getOrNull<ImageLoader>()
            imageLoader?.memoryCache?.clear()
        } catch (_: Exception) {}
        ImageAspectRatioCache.clear()
    }
}
