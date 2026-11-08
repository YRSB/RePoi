package com.nobody.repoi.ui.exhobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.ui.common.PoiAsyncImage

/**
 * ExHobby 扩展图库横滑预览组件（供手办详情页与相册详情页单流平铺挂载）。
 *
 * 采用 `n/` 大图变体（ExHobby 仅 `s/` 与 `n/` 两档，无 `raw/`），纵横比由
 * [PoiAsyncImage] 的自适应比例统一维护，不再手写比例缓存。
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
                Card(
                    onClick = { onPicClick(index) },
                    modifier = Modifier.height(160.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    PoiAsyncImage(
                        path = item.path,
                        category = MediaCategory.EX_HOBBY,
                        purpose = ImagePurpose.CARD_STANDARD,
                        contentDescription = "ExHobby 扩展图 $index",
                        modifier = Modifier.height(160.dp),
                        adaptiveRatio = true,
                        fallbackRatio = 1.0f
                    )
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
        shape = MaterialTheme.shapes.medium,
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