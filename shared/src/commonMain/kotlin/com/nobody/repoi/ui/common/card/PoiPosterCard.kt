package com.nobody.repoi.ui.common.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 网格海报卡基元 (PoiPosterCard)
 *
 * 承载手办、周边及通用多列网格呈现的竖版海报基元。
 *
 * 封装核心能力：
 * 1. 黄金比例纵横比控制（手办默认 9:13，相册瀑布流等可配置为 null 由内容自适应撑开）；
 * 2. 底部抗眩光渐变遮罩（防文字看不清）与发售时间/信息浮层；
 * 3. 右上角状态/热度徽章标准锚点；
 * 4. 原生支持 [onLongClick] 与多选浮层槽位 [selectionOverlay]。
 *
 * @param onClick 单击回调
 * @param modifier 外部修饰符
 * @param onLongClick 长按回调（非空时启用 combinedClickable）
 * @param aspectRatio 卡片纵横比（默认 9:13 黄金海报；传 null 时由封面自适应高度）
 * @param shape 卡片外形，默认 MaterialTheme.shapes.small
 * @param badgeText 右上角徽章文本（非空时自动采用标准半透明深色微胶囊呈现）
 * @param footerText 底部信息文本（非空时自动叠加抗眩光渐变遮罩居中呈现）
 * @param badge 自定义右上角徽章槽位（若提供则覆盖 [badgeText]）
 * @param footer 自定义底部信息槽位（若提供则覆盖 [footerText]）
 * @param selectionOverlay 多选模式浮层槽位（如半透明遮罩与勾选图标）
 * @param cover 封面内容（通常传入铺满的 [com.nobody.repoi.ui.common.PoiAsyncImage]）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PoiPosterCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    aspectRatio: Float? = 9f / 13f,
    shape: Shape = MaterialTheme.shapes.small,
    badgeText: String? = null,
    footerText: String? = null,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    footer: (@Composable BoxScope.() -> Unit)? = null,
    selectionOverlay: (@Composable BoxScope.() -> Unit)? = null,
    cover: @Composable BoxScope.() -> Unit
) {
    val defaultBottomScrim = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .then(if (aspectRatio != null) Modifier.aspectRatio(aspectRatio) else Modifier)
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            } else {
                Modifier
            }
        )

    if (onLongClick != null) {
        ElevatedCard(
            modifier = cardModifier,
            shape = shape
        ) {
            PosterContent(
                cover = cover,
                badgeText = badgeText,
                badge = badge,
                footerText = footerText,
                footer = footer,
                defaultBottomScrim = defaultBottomScrim,
                selectionOverlay = selectionOverlay
            )
        }
    } else {
        ElevatedCard(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape
        ) {
            PosterContent(
                cover = cover,
                badgeText = badgeText,
                badge = badge,
                footerText = footerText,
                footer = footer,
                defaultBottomScrim = defaultBottomScrim,
                selectionOverlay = selectionOverlay
            )
        }
    }
}

@Composable
private fun PosterContent(
    cover: @Composable BoxScope.() -> Unit,
    badgeText: String?,
    badge: (@Composable BoxScope.() -> Unit)?,
    footerText: String?,
    footer: (@Composable BoxScope.() -> Unit)?,
    defaultBottomScrim: Brush,
    selectionOverlay: (@Composable BoxScope.() -> Unit)?
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 底层封面
        cover()

        // 2. 右上角徽章锚点
        if (badge != null) {
            badge()
        } else if (!badgeText.isNullOrBlank()) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }

        // 3. 底部信息浮层（带防眩光渐变）
        if (footer != null) {
            footer()
        } else if (!footerText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(defaultBottomScrim)
                    .padding(top = 16.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = footerText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.95f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 4. 多选模式浮层
        selectionOverlay?.invoke(this)
    }
}
