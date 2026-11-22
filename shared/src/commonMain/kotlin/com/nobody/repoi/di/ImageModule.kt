package com.nobody.repoi.di

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import coil3.util.Logger as CoilLogger
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.media.DefaultMediaPipeline
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.platform.cacheDirPath
import com.nobody.repoi.util.AppLogger
import com.nobody.repoi.util.CorruptCacheRecoveryInterceptor
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.dsl.module

val imageModule = module {

    single<MediaPipeline> { DefaultMediaPipeline }

    single<DiskCache> {
        val viewPrefs = get<ViewPreferencesManager>()
        val maxBytes = runCatching {
            runBlocking(Dispatchers.IO) {
                viewPrefs.imageCacheMaxBytesFlow.first()
            }
        }.getOrDefault(viewPrefs.imageCacheMaxBytes)
        DiskCache.Builder()
            .directory(cacheDirPath().toPath().resolve("image_cache"))
            .maxSizeBytes(maxBytes)
            .build()
    }

    single<ImageLoader> {
        val diskCacheInstance = get<DiskCache>()
        ImageLoader.Builder(platformCoilContext()).components {
            add(CorruptCacheRecoveryInterceptor(diskCacheInstance))
            add(SvgDecoder.Factory())
            add(KtorNetworkFetcherFactory(httpClient = get<HttpClient>(named("coil"))))
        }.logger(object : CoilLogger {
            override var minLevel: CoilLogger.Level = CoilLogger.Level.Info

            override fun log(tag: String, level: CoilLogger.Level, message: String?, throwable: Throwable?) {
                val msg = message ?: ""
                if (throwable != null) {
                    AppLogger.e(tag, msg, throwable)
                    return
                }
                when (level) {
                    CoilLogger.Level.Verbose, CoilLogger.Level.Debug -> AppLogger.d(tag, msg)
                    CoilLogger.Level.Info -> AppLogger.i(tag, msg)
                    CoilLogger.Level.Warn -> AppLogger.w(tag, msg)
                    CoilLogger.Level.Error -> AppLogger.e(tag, msg, throwable)
                }
            }
        }).apply {
            poiShelfPlatformComponents()
        }.diskCache(diskCacheInstance)
        .build()
    }
}

expect fun platformCoilContext(): PlatformContext

expect fun ImageLoader.Builder.poiShelfPlatformComponents()
