package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

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
