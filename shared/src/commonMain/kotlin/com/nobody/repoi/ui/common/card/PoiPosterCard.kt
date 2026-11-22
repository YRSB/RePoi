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

        cover()

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

        selectionOverlay?.invoke(this)
    }
}
