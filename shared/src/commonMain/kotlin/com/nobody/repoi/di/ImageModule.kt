package com.nobody.repoi.di

import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import coil3.util.Logger as CoilLogger
import com.nobody.repoi.platform.cacheDirPath
import com.nobody.repoi.util.AppLogger
import com.nobody.repoi.util.CorruptCacheRecoveryInterceptor
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Coil3 图片模块：单例 `ImageLoader`，网络经命名 `"coil"` Ktor client
 *（Referer+UA 图片头），磁盘缓存 250MB（`cacheDir/image_cache`），集成 `AppLogger` 记录图片加载日志，
 * 并通过 [CorruptCacheRecoveryInterceptor] 在解码/加载失败时自动剔除本地损坏磁盘缓存并立即自动重试。
 *
 * 平台差异收敛到 [platformCoilContext] + [ImageLoader.Builder.poiShelfPlatformComponents]
 *（PodAura `PodAuraImage` 同形；gif/video 解码器不在本模块依赖内，android 实际为空实现）。
 */
val imageModule = module {
    single<ImageLoader> {
        val diskCacheInstance = DiskCache.Builder()
            .directory(cacheDirPath().toPath().resolve("image_cache"))
            .maxSizeBytes(250L * 1024L * 1024L)
            .build()

        ImageLoader.Builder(platformCoilContext()).components {
            add(CorruptCacheRecoveryInterceptor(diskCacheInstance))
            add(SvgDecoder.Factory())
            add(KtorNetworkFetcherFactory(httpClient = get<HttpClient>(named("coil"))))
        }.logger(object : CoilLogger {
            override var minLevel: CoilLogger.Level = CoilLogger.Level.Debug

            override fun log(tag: String, priority: CoilLogger.Level, message: String?, throwable: Throwable?) {
                val msg = message ?: ""
                when (priority) {
                    CoilLogger.Level.Verbose, CoilLogger.Level.Debug -> AppLogger.d(tag, msg)
                    CoilLogger.Level.Info -> AppLogger.i(tag, msg)
                    CoilLogger.Level.Warn -> AppLogger.w(tag, msg)
                    CoilLogger.Level.Error -> AppLogger.e(tag, msg, throwable)
                }
            }
        }).eventListener(object : EventListener() {
            override fun onError(request: ImageRequest, result: ErrorResult) {
                // 当图片加载或解码抛出异常时，自动剔除本地可能损坏、截断或写入了非图片数据的脏磁盘缓存
                val key = request.diskCacheKey ?: request.data.toString()
                if (key.isNotBlank()) {
                    val removed = diskCacheInstance.remove(key)
                    if (removed) {
                        AppLogger.w("ImageLoader", "图片加载/解码失败，已自动剔除本地损坏缓存: $key")
                    }
                }
            }
        }).apply {
            poiShelfPlatformComponents()
        }.diskCache(diskCacheInstance)
        .build()
    }
}

/** Coil `PlatformContext`：android = 应用 Context，jvm = `PlatformContext.INSTANCE`。 */
expect fun platformCoilContext(): PlatformContext

/** 平台图片组件扩展点（android/gif、jvm/no-op；gif 解码器未引入本模块时为空）。 */
expect fun ImageLoader.Builder.poiShelfPlatformComponents()
