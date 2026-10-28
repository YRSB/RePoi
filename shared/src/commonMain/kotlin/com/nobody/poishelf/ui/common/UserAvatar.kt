package com.nobody.poishelf.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nobody.poishelf.util.CdnUrlUtils

/**
 * 统一的用户圆形头像组件。
 * 具备 URL 容错及网络加载失败（404 等）自动降级展示默认人像矢量图的能力。
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    contentDescription: String? = null
) {
    val cleanUrl = remember(avatarUrl) {
        val trimmed = avatarUrl?.trim()
        if (trimmed.isNullOrBlank()) {
            ""
        } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            CdnUrlUtils.getHeadSmallUrl(trimmed)
        }
    }
    var loadFailed by remember(cleanUrl) { mutableStateOf(false) }
    val context = LocalPlatformContext.current
    val avatarRequest = remember(context, cleanUrl) {
        if (cleanUrl.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(cleanUrl)
                .crossfade(false)
                .build()
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (avatarRequest != null && !loadFailed) {
            AsyncImage(
                model = avatarRequest,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { loadFailed = true }
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.65f),
                tint = iconColor
            )
        }
    }
}
