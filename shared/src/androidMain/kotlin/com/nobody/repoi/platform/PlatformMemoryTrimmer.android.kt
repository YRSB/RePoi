package com.nobody.repoi.platform

import android.content.ComponentCallbacks2
import com.nobody.repoi.di.CoilImageLoaderHolder
import com.nobody.repoi.util.ImageAspectRatioCache

/**
 * Android 平台内存修剪工具：
 * 承接系统的 [ComponentCallbacks2.onTrimMemory] 与 [ComponentCallbacks2.onLowMemory] 回调，
 * 封装在 shared 模块内部以访问 Coil 内存缓存与宽高比缓存，避免 androidApp 壳工程直接依赖 Coil3。
 *
 * 策略：
 * - `onTrimMemory(>= TRIM_MEMORY_RUNNING_CRITICAL)`：清理 Coil 内存缓存。`TRIM_MEMORY_UI_HIDDEN`(20)
 *   及其后所有更重的等级（BACKGROUND/MODERATE/COMPLETE）都落在该阈值之上，无需重复判断；
 *   `RUNNING_MODERATE`(5)/`RUNNING_LOW`(10) 刻意不处理，避免前台中轻度压力反复清空导致图片重解码。
 * - `onLowMemory()`：系统级低内存，额外丢弃 [ImageAspectRatioCache]。
 *   宽高比缓存仅 500 个 Float（<32 KiB），日常 trim 清理只会让瀑布流高度回跳，故只在此处丢弃。
 *
 * 只从 [CoilImageLoaderHolder] 读取已构建的 `ImageLoader`，绝不走 Koin 解析，避免修剪回调反向触发懒加载。
 */
object PlatformMemoryTrimmer {
    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            clearImageLoaderMemoryCache()
        }
    }

    fun onLowMemory() {
        clearImageLoaderMemoryCache()
        ImageAspectRatioCache.clear()
    }

    private fun clearImageLoaderMemoryCache() {
        CoilImageLoaderHolder.imageLoader?.memoryCache?.clear()
    }
}
