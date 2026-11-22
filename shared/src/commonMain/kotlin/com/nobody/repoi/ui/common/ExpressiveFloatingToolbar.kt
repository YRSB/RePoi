package com.nobody.repoi.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun ExpressiveFloatingToolbar(
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    content: @Composable RowScope.() -> Unit
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        scrollBehavior = scrollBehavior,
        shape = CircleShape,
        expandedShadowElevation = 6.dp,
        collapsedShadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun ExpressiveToolbarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: DrawableResource? = null,
    contentDescription: String? = null,
    isProminent: Boolean = false,
    containerColor: Color = if (isProminent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    contentColor: Color = if (isProminent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    iconTint: Color = if (isProminent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
) {
    val hasText = !text.isNullOrBlank()

    if (hasText) {
        val label: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
        if (isProminent) {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                label()
            }
        } else {
            TextButton(
                onClick = onClick,
                modifier = modifier,
                shape = CircleShape,
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                label()
            }
        }
    } else {
        val iconContent: @Composable () -> Unit = {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (isProminent) {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            ) {
                iconContent()
            }
        } else {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor)
            ) {
                iconContent()
            }
        }
    }
}
