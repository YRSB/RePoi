package com.nobody.repoi.ui.common.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.ui.home.components.shimmerBackground

/**
 * 列表条目卡基元 (PoiListCard)
 *
 * 承载单列图文条目呈现的水平横向展示基元。
 *
 * 封装核心能力：
 * 1. M3E 标准卡片容器标高与 MediumShape 圆角；
 * 2. 10dp 标准内边距与横向 10dp 图文间距拓扑；
 * 3. 左侧自决规格的缩略图槽位 [thumbnail]（配套 [PoiThumbnail]）；
 * 4. 右侧自适应信息列，内置标准标题排版或开放自定义槽位；
 * 5. 原生支持 [onLongClick] 与 [enabled] 状态。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PoiListCard(
    onClick: () -> Unit,
    thumbnail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    title: @Composable () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .then(
            if (onLongClick != null && enabled) {
                Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            } else {
                Modifier
            }
        )

    if (onLongClick != null && enabled) {
        ElevatedCard(
            modifier = cardModifier,
            shape = shape
        ) {
            ListCardLayout(
                thumbnail = thumbnail,
                title = title,
                content = content,
                verticalAlignment = verticalAlignment
            )
        }
    } else {
        ElevatedCard(
            onClick = onClick,
            enabled = enabled,
            modifier = cardModifier,
            shape = shape
        ) {
            ListCardLayout(
                thumbnail = thumbnail,
                title = title,
                content = content,
                verticalAlignment = verticalAlignment
            )
        }
    }
}

/**
 * 列表条目卡纯文本标题重载（提供高杠杆开箱即用排版）
 */
@Composable
fun PoiListCard(
    title: String,
    onClick: () -> Unit,
    thumbnail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    titleMaxLines: Int = 1,
    shape: Shape = MaterialTheme.shapes.medium,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    PoiListCard(
        onClick = onClick,
        thumbnail = thumbnail,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        shape = shape,
        verticalAlignment = verticalAlignment,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        },
        content = content
    )
}

@Composable
private fun ListCardLayout(
    thumbnail: @Composable () -> Unit,
    title: @Composable () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)?,
    verticalAlignment: Alignment.Vertical
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = verticalAlignment
    ) {
        // 左侧封面缩略图槽位
        thumbnail()

        // 右侧信息列
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            title()
            content?.invoke(this)
        }
    }
}

/**
 * 缩略图辅助构件 (PoiThumbnail)
 */
object PoiThumbnail {
    /** 标准缩略图默认边长 68.dp */
    val StandardSquareSize: Dp = 68.dp

    /** 作品竖版海报默认宽度 90.dp */
    val StandardPosterWidth: Dp = 90.dp

    /** 作品竖版海报默认高度 130.dp */
    val StandardPosterHeight: Dp = 130.dp

    /**
     * 正方形缩略图容器（默认 68dp，适用于手办、专区、通用实体等）
     */
    @Composable
    fun Square(
        modifier: Modifier = Modifier,
        size: Dp = StandardSquareSize,
        shape: Shape = MaterialTheme.shapes.small,
        content: @Composable BoxScope.() -> Unit
    ) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }

    /**
     * 竖版海报缩略图容器（默认 90x130dp，适用于作品等）
     */
    @Composable
    fun Poster(
        modifier: Modifier = Modifier,
        width: Dp = StandardPosterWidth,
        height: Dp = StandardPosterHeight,
        shape: Shape = MaterialTheme.shapes.small,
        content: @Composable BoxScope.() -> Unit
    ) {
        Box(
            modifier = modifier
                .size(width = width, height = height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * 列表条目卡骨架占位基元 (PoiListCardSkeleton)
 */
@Composable
fun PoiListCardSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    thumbnailSize: Dp = PoiThumbnail.StandardSquareSize,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = verticalAlignment
        ) {
            // 左侧缩略图骨架占位
            Box(
                modifier = Modifier
                    .size(thumbnailSize)
                    .clip(MaterialTheme.shapes.small)
                    .shimmerBackground()
            )

            // 右侧信息骨架占位
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (content != null) {
                    content()
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .shimmerBackground()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(14.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .shimmerBackground()
                    )
                }
            }
        }
    }
}
