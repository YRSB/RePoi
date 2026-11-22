package com.nobody.repoi.util

import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageResult

class CorruptCacheRecoveryInterceptor(
    private val diskCache: DiskCache
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val result = chain.proceed()

        if (result is ErrorResult && request.diskCachePolicy.readEnabled && isCorruptOrDecodeError(result.throwable)) {
            val key = request.diskCacheKey ?: request.data.toString()
            if (key.isNotBlank()) {
                diskCache.remove(key)
                AppLogger.w("ImageLoader", "检测到解码异常(${result.throwable.message})，已抹除脏缓存并立即触发网络重试: $key")
                val retryRequest = request.newBuilder()
                    .diskCachePolicy(CachePolicy.WRITE_ONLY)
                    .memoryCachePolicy(CachePolicy.WRITE_ONLY)
                    .build()
                return chain.withRequest(retryRequest).proceed()
            }
        }

        return result
    }

    private fun isCorruptOrDecodeError(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            val msg = current.message?.lowercase().orEmpty()
            if (msg.contains("decod") ||
                msg.contains("unimplemented") ||
                msg.contains("input contained an error") ||
                msg.contains("corrupt") ||
                msg.contains("premature") ||
                msg.contains("truncat") ||
                msg.contains("eof")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
