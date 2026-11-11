package com.nobody.repoi.ui.common.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 分体图文网格卡基元 (PoiCaptionGridCard)
 *
 * 承载上图下文（如厂商/系列 1:1 正方形网格卡、相册瀑布流等）呈现的展示基元。
 *
 * @param onClick 单击回调
 * @param modifier 外部修饰符
 * @param onLongClick 长按回调
 * @param shape 卡片外形，默认 MaterialTheme.shapes.medium
 * @param colors 卡片颜色配置，默认 surfaceContainerLow
 * @param elevation 卡片标高配置，默认 1.dp
 * @param overlay 覆盖层内容（如多选态遮罩、右上角操作图标等）
 * @param cover 上方封面内容
 * @param caption 下方文本标题/内容
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PoiCaptionGridCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: CardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ),
    elevation: CardElevation = CardDefaults.elevatedCardElevation(
        defaultElevation = 1.dp
    ),
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    cover: @Composable () -> Unit,
    caption: @Composable () -> Unit
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            } else {
                Modifier
            }
        )

    val cardContent = @Composable {
        if (overlay != null) {
            Box {
                Column(modifier = Modifier.fillMaxWidth()) {
                    cover()
                    caption()
                }
                overlay()
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                cover()
                caption()
            }
        }
    }

    if (onLongClick != null) {
        ElevatedCard(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = { cardContent() }
        )
    } else {
        ElevatedCard(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = { cardContent() }
        )
    }
}
