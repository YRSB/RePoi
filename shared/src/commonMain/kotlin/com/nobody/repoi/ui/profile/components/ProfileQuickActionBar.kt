package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.user.creation.UserCreationCategory
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private data class QuickActionEntry(
    val icon: DrawableResource,
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileQuickActionBar(
    profileId: Long,
    isLoggedIn: Boolean,
    isCompact: Boolean = true,
    onNavigateToCollect: (UserCollectNavParam) -> Unit,
    onNavigateToCreation: (UserCreationNavParam) -> Unit,
    onNavigateToHistory: () -> Unit,
    onShowMessage: (String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val entries = listOf(
        QuickActionEntry(
            icon = PoiIcons.Filled.Star,
            label = "我的收藏",
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            onClick = {
                if (isLoggedIn) {
                    onNavigateToCollect(
                        UserCollectNavParam(
                            userId = profileId,
                            initialCategory = UserCollectCategory.HOBBY.key
                        )
                    )
                } else {
                    onLoginClick()
                }
            }
        ),
        QuickActionEntry(
            icon = PoiIcons.Filled.PhotoLibrary,
            label = "我的发布",
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
            onClick = {
                if (isLoggedIn) {
                    onNavigateToCreation(
                        UserCreationNavParam(
                            userId = profileId,
                            initialCategory = UserCreationCategory.ALBUM.key
                        )
                    )
                } else {
                    onLoginClick()
                }
            }
        ),
        QuickActionEntry(
            icon = PoiIcons.Filled.ShoppingBag,
            label = "我的二手",
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
            onClick = {
                if (isLoggedIn) onShowMessage("二手闲置市场功能即将上线") else onLoginClick()
            }
        ),
        QuickActionEntry(
            icon = PoiIcons.Filled.History,
            label = "浏览历史",
            containerColor = colorScheme.surfaceContainerHighest,
            contentColor = colorScheme.onSurfaceVariant,
            onClick = {
                if (isLoggedIn) onNavigateToHistory() else onLoginClick()
            }
        )
    )

    val gap = ListItemDefaults.SegmentedGap
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        entries.chunked(2).forEach { rowEntries ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                rowEntries.forEach { entry ->
                    QuickActionSegmentedCell(
                        entry = entry,
                        isCompact = isCompact,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowEntries.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickActionSegmentedCell(
    entry: QuickActionEntry,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeSize = if (isCompact) 38.dp else 34.dp
    val iconSize = if (isCompact) 20.dp else 18.dp
    val horizontalPadding = if (isCompact) 14.dp else 10.dp

    SegmentedListItem(
        onClick = entry.onClick,
        modifier = modifier,
        shapes = ListItemDefaults.shapes(),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 10.dp),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(entry.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(entry.icon),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = entry.contentColor
                )
            }
        },
        content = {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
