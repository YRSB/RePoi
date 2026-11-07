package com.nobody.repoi.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache

/** Coil 上下文（jvm）：`PlatformContext.INSTANCE`。 */
actual fun platformCoilContext(): PlatformContext = PlatformContext.INSTANCE

/**
 * 内存缓存（Desktop JVM）：固定 192 MiB 封顶，并按堆上限的 1/4 再收敛，
 * 防止 4K 图集在小堆（如 `-Xmx512m`）下把内存缓存撑爆。
 */
actual fun createCoilMemoryCache(context: PlatformContext): MemoryCache {
    val hardCapBytes = 192L * 1024 * 1024
    val heapCapBytes = Runtime.getRuntime().maxMemory() / 4
    return MemoryCache.Builder()
        .maxSizeBytes(minOf(hardCapBytes, heapCapBytes))
        .build()
}

/** 平台图片组件（jvm）：no-op（SvgDecoder + Ktor 网络拉取 common 侧已装配）。 */
actual fun ImageLoader.Builder.poiShelfPlatformComponents() {
}
