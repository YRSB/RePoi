package com.nobody.repoi.platform

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath

expect fun platformContext(): PlatformContext

/** 各端 Coil 磁盘缓存目录。 */
expect fun imageCacheDir(): String

/**
 * 构建全局 Coil3 ImageLoader，对齐老项目 `PoiShelfApplication.newImageLoader`：
 * 内存缓存 25%、磁盘缓存 250MB、不遵循缓存头、无 crossfade；
 * 网络层复用图片 HttpClient（自带 Referer/UA 头）。
 */
fun createImageLoader(
    context: PlatformContext,
    imageHttpClient: HttpClient
): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(KtorNetworkFetcherFactory(httpClient = { imageHttpClient }))
    }
    .memoryCache {
        MemoryCache.Builder()
            .maxSizePercent(context, 0.25)
            .strongReferencesEnabled(true)
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(imageCacheDir().toPath())
            .maxSizeBytes(250L * 1024 * 1024)
            .build()
    }
    .respectCacheHeaders(false)
    .crossfade(false)
    .build()
