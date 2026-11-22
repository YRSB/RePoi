package com.nobody.repoi.ui.common.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(5.dp, verticalAlignment),
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

    val cardContent: @Composable () -> Unit = {
        ListCardLayout(
            thumbnail = thumbnail,
            title = title,
            content = content,
            verticalAlignment = verticalAlignment,
            verticalArrangement = verticalArrangement
        )
    }

    if (onLongClick != null && enabled) {
        ElevatedCard(
            modifier = cardModifier,
            shape = shape
        ) {
            cardContent()
        }
    } else {
        ElevatedCard(
            onClick = onClick,
            enabled = enabled,
            modifier = cardModifier,
            shape = shape
        ) {
            cardContent()
        }
    }
}

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
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(5.dp, verticalAlignment),
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
        verticalArrangement = verticalArrangement,
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
    verticalAlignment: Alignment.Vertical,
    verticalArrangement: Arrangement.Vertical
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = verticalAlignment
    ) {

        thumbnail()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = verticalArrangement
        ) {
            title()
            content?.invoke(this)
        }
    }
}

object PoiThumbnail {

    val StandardSquareSize: Dp = 68.dp

    val StandardPosterWidth: Dp = 90.dp

    val StandardPosterHeight: Dp = 130.dp

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
