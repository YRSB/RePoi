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
