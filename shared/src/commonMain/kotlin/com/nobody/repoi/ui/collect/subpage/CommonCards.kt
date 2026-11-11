package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.card.PoiCaptionGridCard
import com.nobody.repoi.ui.common.card.PoiListCard
import com.nobody.repoi.ui.common.card.PoiThumbnail
import com.nobody.repoi.util.CdnUrlUtils

// ── 通用 4 页（厂商/系列/角色/人物）卡片 ─────────────────────

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
 * 通用 4 页的**列表卡**（原版 `CommonItemListAdapter` + `item_common_list.xml`）。
 *
 * 布局：基于 PoiListCard 深基元，左 68dp 规范方形缩略图 + 右侧名称。
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

    PoiListCard(
        title = item.displayName,
        onClick = onClick,
        modifier = modifier,
        thumbnail = {
            PoiThumbnail.Square {
                PoiAsyncImage(
                    model = coverUrl,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

/**
 * 通用 4 页的**网格卡**（原版 `CommonItemGridAdapter` + `item_common_grid.xml`）。
 *
 * 布局：基于 PoiCaptionGridCard 深基元，上图下文（上方 1:1 正方形封面 + 下方名称）。
 * 网格态不渲染统计行 —— 原版网格适配器只绑定「封面 + 名称」两个控件。
 */
@Composable
internal fun CommonCollectGridCard(
    item: UserCollectCommonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { commonCollectCoverUrl(item, isListView = false) }

    PoiCaptionGridCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        cover = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                PoiAsyncImage(
                    model = coverUrl,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        caption = {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    )
}
