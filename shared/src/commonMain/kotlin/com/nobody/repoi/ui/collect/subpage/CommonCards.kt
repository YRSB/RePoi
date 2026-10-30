package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.ui.theme.ExpressiveElevatedCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils

// ── 通用 4 页（厂商/系列/角色/人物）卡片 ─────────────────────

/**
 * 通用 4 页列表卡的缩略图边长。
 * 原版 `item_common_list.xml:5-15` 四页共用同一套 70dp 方形 + 7.5dp 圆角布局，
 * **人物/角色并无圆形头像**，故本项目四页统一方形。
 */
internal val CommonCollectThumbSize: Dp = 70.dp

/**
 * 系列页专属详情路由的判定：原版 `CommonItemListAdapter.java:51-52`、
 * `CommonItemGridAdapter.java:47-48` 均要求 `itemType == "series" && pageState == 1`
 * 才走 `SeriesDetailActivity`。
 */
internal fun UserCollectCommonItem.isSeriesDetailRoute(): Boolean =
    itemType?.trim()?.equals(SERIES_ITEM_TYPE, ignoreCase = true) == true && pageState == 1

/** 原版 `oe.a.f56941f` 的取值 */
internal const val SERIES_ITEM_TYPE = "series"

/**
 * 封面 CDN 变体：列表模式用 `cover/s/`（`getCoverSmallUrl`），网格模式用 `cover/sr/`（`getCoverSrUrl`）。
 * 原版四页的列表卡统一取 `pe.c.f58299i`（small）变体。
 */
internal fun commonCollectCoverUrl(item: UserCollectCommonItem, isListView: Boolean): String =
    if (isListView) {
        CdnUrlUtils.getCoverSmallUrl(item.actualCover)
    } else {
        CdnUrlUtils.getCoverSrUrl(item.actualCover)
    }

/**
 * 通用 4 页共用的方形缩略图（70dp + 小圆角），加载失败时回落破损图标。
 */
@Composable
internal fun CommonCollectThumbnail(
    coverUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = CommonCollectThumbSize
) {
    var loadFailed by remember(coverUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .clip(ExpressiveTokens.SmallCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl.isNotBlank() && !loadFailed) {
            AsyncImage(
                model = coverUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { loadFailed = true }
            )
        } else {
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 通用 4 页的**列表卡**（原版 `CommonItemListAdapter` + `item_common_list.xml`）。
 *
 * 布局：左 70dp 方形缩略图 + 右侧名称。
 * 原版四页卡片只展示封面与名称，**不渲染任何统计文字**（真机 2.39 实测确认）。
 */
@Composable
internal fun CommonCollectListCard(
    item: UserCollectCommonItem,
    category: UserCollectCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { commonCollectCoverUrl(item, isListView = true) }

    ExpressiveElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.MediumCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommonCollectThumbnail(
                coverUrl = coverUrl,
                contentDescription = item.displayName
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * 通用 4 页的**网格卡**（原版 `CommonItemGridAdapter` + `item_common_grid.xml`）。
 *
 * 网格态不渲染统计行 —— 原版网格适配器只绑定「封面 + 名称」两个控件。
 */
@Composable
internal fun CommonCollectGridCard(
    item: UserCollectCommonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { commonCollectCoverUrl(item, isListView = false) }
    var loadFailed by remember(coverUrl) { mutableStateOf(false) }

    ExpressiveElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.SmallCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl.isNotBlank() && !loadFailed) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { loadFailed = true }
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}