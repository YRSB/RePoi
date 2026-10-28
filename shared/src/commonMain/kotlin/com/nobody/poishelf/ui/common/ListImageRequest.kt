package com.nobody.poishelf.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * 列表图片请求（KMP commonMain 移植版）。
 *
 * 与原生版（`ui/common/ListImageRequest.kt`）差异：
 * - Coil2 `ImageRequest.Builder(context)` → Coil3 `ImageRequest.Builder(LocalPlatformContext)`；
 *   `crossfade(false)`/`memoryCacheKey` 语义保留（Coil3 `coil3.request.crossfade` 扩展导入后同名调用）。
 */
@Composable
fun rememberListImageRequest(
    cover: String?,
    url: String,
    bucket: String
): ImageRequest {
    val context = LocalPlatformContext.current
    val cacheKey = remember(cover, url, bucket) { listImageKey(cover, url, bucket) }
    return remember(context, url, cacheKey) {
        ImageRequest.Builder(context).data(url).crossfade(false).memoryCacheKey(cacheKey).build()
    }
}

fun listImageKey(
    cover: String?,
    url: String,
    bucket: String
): String {
    val base = cover?.substringAfterLast("/")?.takeIf { it.isNotBlank() } ?: url
    return "$base:$bucket"
}
