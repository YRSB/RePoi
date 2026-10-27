package com.nobody.repoi.ui.exhobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.ImageAspectRatioCache

/**
 * ExHobby 扩展图库横滑预览组件（供手办详情页与相册详情页单流平铺挂载）。
 *
 * 采用 /pic/n/ 大图变体与大图画廊共享缓存，点击卡片即时呈现大图，利用 ImageAspectRatioCache 动态维持图片长宽比。
 *
 * Wave 6 自 exhobby 波前移真身（Coil3 版），供 detail 波内引用。
 */
@Composable
fun ExHobbyPreviewSection(
    pics: List<ExHobbyImageItem>,
    onPicClick: (Int) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "ExHobby"
) {
    if (pics.isEmpty()) return

    val hasMore = pics.size > 9
    val displayPics = if (hasMore) pics.take(9) else pics
    // 图片请求上下文屏级取一次下传，避免逐项查询重复订阅
    val context = LocalPlatformContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 横滑图集按图片类型复用条目，提升滚动复用命中率
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = displayPics,
                key = { index, item -> "exhobby_${item.path}_$index" },
                contentType = { _, _ -> "exhobby_pic" }
            ) { index, item ->
                val ratioKey = remember(item.path) { "exhobby:${item.path}" }
                val initialRatio = ImageAspectRatioCache[ratioKey] ?: 1.0f
                var aspectRatio by remember(ratioKey) { mutableFloatStateOf(initialRatio) }

                Card(
                    onClick = { onPicClick(index) },
                    modifier = Modifier.height(160.dp),
                    shape = ExpressiveTokens.MediumCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .height(160.dp)
                            .aspectRatio(aspectRatio)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(CdnUrlUtils.getExHobbyFullUrl(item.path))
                                .crossfade(true)
                                .build(),
                            contentDescription = "ExHobby 扩展图 $index",
                            contentScale = ContentScale.Crop,
                            onSuccess = { state ->
                                val size = state.painter.intrinsicSize
                                if (size.width > 0 && size.height > 0) {
                                    val ratio = (size.width / size.height).coerceIn(0.5f, 2.2f)
                                    aspectRatio = ratio
                                    ImageAspectRatioCache[ratioKey] = ratio
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (hasMore) {
                item(key = "exhobby_more_tile") {
                    ExHobbyMoreTile(
                        onMoreClick = onMoreClick,
                        modifier = Modifier.size(160.dp)
                    )
                }
            }
        }
    }
}

/**
 * ExHobby 扩展图库「查看更多」卡片（样式与官图 OfficialPicsMoreTile 保持一致）。
 */
@Composable
private fun ExHobbyMoreTile(
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onMoreClick,
        modifier = modifier,
        shape = ExpressiveTokens.MediumCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "查看更多",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
