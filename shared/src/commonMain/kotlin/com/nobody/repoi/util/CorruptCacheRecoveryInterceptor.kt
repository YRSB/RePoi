package com.nobody.repoi.util

import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageResult

/**
 * 损坏磁盘缓存自动恢复拦截器。
 *
 * 当本地磁盘缓存因网络中断、反爬拦截或系统崩溃写入了截断/损坏的数据时，
 * Android 原生 ImageDecoder 会抛出解码异常（如 "unimplemented Input contained an error"）。
 * 本拦截器捕获该异常后，自动从 [DiskCache] 抹除脏缓存条目，并立即以 [CachePolicy.WRITE_ONLY]
 * 重新向远端发起网络请求，实现对用户完全透明的自愈恢复。
 */
class CorruptCacheRecoveryInterceptor(
    private val diskCache: DiskCache
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val result = chain.proceed()

        // 仅在首次请求失败且启用了读缓存时才尝试自愈重试，单次自愈避免死循环
        if (result is ErrorResult && request.diskCachePolicy.readEnabled) {
            val throwable = result.throwable
            val msg = throwable.message?.lowercase() ?: ""

            // 识别解码器崩溃、格式错误、输入截断或解析异常
            val isCorruptOrDecodeError = msg.contains("decoder") ||
                msg.contains("unimplemented") ||
                msg.contains("input contained an error") ||
                msg.contains("corrupt") ||
                msg.contains("eof") ||
                msg.contains("premature") ||
                throwable is IllegalArgumentException ||
                throwable is IllegalStateException

            if (isCorruptOrDecodeError) {
                val key = request.diskCacheKey ?: request.data.toString()
                if (key.isNotBlank()) {
                    diskCache.remove(key)
                    AppLogger.w("ImageLoader", "检测到解码异常($msg)，已抹除脏缓存并立即触发网络重试: $key")
                    // 构造强制跳过读缓存的新请求，重新走网络拉取并写入磁盘
                    val retryRequest = request.newBuilder()
                        .diskCachePolicy(CachePolicy.WRITE_ONLY)
                        .memoryCachePolicy(CachePolicy.WRITE_ONLY)
                        .build()
                    return chain.withRequest(retryRequest).proceed()
                }
            }
        }

        return result
    }
}
