package com.nobody.repoi.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.nobody.repoi.platform.cacheDirPath
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Coil3 图片模块：单例 `ImageLoader`，网络经命名 `"coil"` Ktor client
 *（Referer+UA 图片头），磁盘缓存 250MB（`cacheDir/image_cache`）。
 *
 * 平台差异收敛到 [platformCoilContext] + [ImageLoader.Builder.poiShelfPlatformComponents]
 *（PodAura `PodAuraImage` 同形；gif/video 解码器不在本模块依赖内，android 实际为空实现）。
 */
val imageModule = module {
    single<ImageLoader> {
        ImageLoader.Builder(platformCoilContext()).components {
            add(SvgDecoder.Factory())
            add(KtorNetworkFetcherFactory(httpClient = get<HttpClient>(named("coil"))))
        }.apply {
            poiShelfPlatformComponents()
        }.diskCache {
            DiskCache.Builder()
                .directory(cacheDirPath().toPath().resolve("image_cache"))
                .maxSizeBytes(250L * 1024L * 1024L)
                .build()
        }.build()
    }
}

/** Coil `PlatformContext`：android = 应用 Context，jvm = `PlatformContext.INSTANCE`。 */
expect fun platformCoilContext(): PlatformContext

/** 平台图片组件扩展点（android/gif、jvm/no-op；gif 解码器未引入本模块时为空）。 */
expect fun ImageLoader.Builder.poiShelfPlatformComponents()
