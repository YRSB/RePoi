package com.nobody.poishelf.ui.collect.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.poishelf.data.remote.model.UserCollectCommonItem
import com.nobody.poishelf.ui.theme.ExpressiveElevatedCard
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils

// ── 作品收藏卡片（对标原版 UserCollectWorksAdapter + item_collect_works.xml）──

/** 原版作品封面竖版比例 90×130 */
internal const val WorksPosterWidth = 90

/** 原版作品封面竖版比例 90×130 的高度 */
internal const val WorksPosterHeight = 130

/**
 * 作品详情跳转 ID 的**安全解析**。
 *
 * 原版 `UserCollectWorksAdapter.java:125-140` 在「想看档且无 `i`/`c` 键」时会整体改读嵌套 `item` 对象，
 * 点击时统一取 `x0.K`（`itemId`）。因此：
 *
 * ⚠️ **嵌套 `item` 解包后绝不能回落到外层收藏记录 `id`** —— 否则会带着收藏记录 id 跳进
 * 一个完全无关的作品详情。本函数在无法确定真实 id 时返回 `0L`，由调用方禁用点击。
 */
internal fun worksDetailItemId(item: UserCollectCommonItem): Long {
    item.item?.actualItemId?.takeIf { it > 0L }?.let { return it }
    return item.itemId.takeIf { it > 0L } ?: 0L
}

/**
 * 发售时间前缀（对齐原版 `UserCollectWorksAdapter.java:83-98` 的 `e(categoryId)`）。
 *
 * - `10100` → 上映时间（影视）
 * - `10200` → 首发时间（游戏）
 * - `10300/10400/10500/10600` → 发售时间（漫画/动画/音乐等）
 * - 其余 → 发布时间
 */
internal fun worksReleaseDatePrefix(categoryId: Int): String = when (categoryId) {
    10100 -> "上映时间："
    10200 -> "首发时间："
    10300, 10400, 10500, 10600 -> "发售时间："
    else -> "发布时间："
}

/**
 * 状态/进度文案（对齐原版 `UserCollectWorksAdapter.java:100-108` 的 `f(state, current)`）。
 *
 * 1 想看 / 2 观看至N集 / 3 看过，其余空串。
 *
 * [state] 可空：非作品分类的 `state` 是字符串（care/want/...），解析后为 null。
 */
internal fun worksStateProgressText(state: Int?, current: Int): String = when (state) {
    1 -> "想看"
    2 -> "观看至${current}集"
    3 -> "看过"
    else -> ""
}

/**
 * 作品收藏卡片。
 *
 * 竖版 90×130 海报在左，右侧三行：作品名（`nameCN`）、发售/首映/上映时间行、状态/进度行。
 * 真实实体 id 不可确定时整卡禁用点击（见 [worksDetailItemId]）。
 */
@Composable
internal fun WorksCollectCard(
    item: UserCollectCommonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { CdnUrlUtils.getCoverSmallUrl(item.actualCover) }
    val detailId = remember(item.item, item.itemId) { worksDetailItemId(item) }
    val isClickable = detailId > 0L

    // 嵌套 item 优先承载分类与发售时间，原版两分支都从「实体对象」读取
    val entityCategoryId = item.item?.categoryId?.takeIf { it > 0 } ?: item.categoryId
    val releaseDateString = remember(item.releaseDateString) {
        item.releaseDateString?.trim()?.ifBlank { null }
    }
    val stateText = worksStateProgressText(item.state, item.current)

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
            // 竖版封面 90×130
            WorksPosterImage(
                coverUrl = coverUrl,
                contentDescription = item.displayName
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isClickable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )

                if (stateText.isNotBlank()) {
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!releaseDateString.isNullOrBlank()) {
                    Text(
                        text = worksReleaseDatePrefix(entityCategoryId) + releaseDateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 作品竖版封面（90×130），加载失败时回落破损图标。
 */
@Composable
private fun WorksPosterImage(
    coverUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var loadFailed by remember(coverUrl) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(WorksPosterWidth.dp)
            .height(WorksPosterHeight.dp)
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