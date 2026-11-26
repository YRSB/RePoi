package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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

enum class ProfileQuickAction {
    COLLECT,
    CREATION,
    RESALE,
    HISTORY
}

private data class QuickActionEntry(
    val action: ProfileQuickAction,
    val icon: DrawableResource,
    val label: String,
    val subtitle: String,
    val containerColor: Color,
    val contentColor: Color,
    val statusBadge: String? = null,
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
    modifier: Modifier = Modifier,
    selectedAction: ProfileQuickAction? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val entries = listOf(
        QuickActionEntry(
            action = ProfileQuickAction.COLLECT,
            icon = PoiIcons.Filled.Star,
            label = "我的收藏",
            subtitle = "手办、周边与各类维基条目",
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
            action = ProfileQuickAction.CREATION,
            icon = PoiIcons.Filled.PhotoLibrary,
            label = "我的发布",
            subtitle = "相册、评测与个人创作",
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
            action = ProfileQuickAction.RESALE,
            icon = PoiIcons.Filled.ShoppingBag,
            label = "我的二手",
            subtitle = "闲置发布与交易管理",
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
            statusBadge = "即将上线",
            onClick = {
                if (isLoggedIn) onShowMessage("二手闲置市场功能即将上线") else onLoginClick()
            }
        ),
        QuickActionEntry(
            action = ProfileQuickAction.HISTORY,
            icon = PoiIcons.Filled.History,
            label = "浏览历史",
            subtitle = "近期查看过的条目记录",
            containerColor = colorScheme.surfaceContainerHighest,
            contentColor = colorScheme.onSurfaceVariant,
            onClick = {
                if (isLoggedIn) onNavigateToHistory() else onLoginClick()
            }
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        entries.forEach { entry ->
            QuickActionSegmentedRow(
                entry = entry,
                isCompact = isCompact,
                isSelected = entry.action == selectedAction
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickActionSegmentedRow(
    entry: QuickActionEntry,
    isCompact: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val badgeSize = if (isCompact) 40.dp else 36.dp
    val iconSize = if (isCompact) 20.dp else 18.dp
    val verticalPadding = if (isCompact) 14.dp else 12.dp
    val rowContainerColor = if (isSelected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLowest
    val titleColor = if (isSelected) colorScheme.onSecondaryContainer else colorScheme.onSurface
    val subtitleColor = if (isSelected) colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant
    val chevronColor = if (isSelected) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant

    SegmentedListItem(
        onClick = entry.onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { selected = isSelected },
        shapes = ListItemDefaults.shapes(),
        colors = ListItemDefaults.segmentedColors(
            containerColor = rowContainerColor
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = verticalPadding),
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
        supportingContent = {
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (entry.statusBadge != null) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = entry.statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Icon(
                    painter = painterResource(PoiIcons.ChevronRight),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = chevronColor
                )
            }
        },
        content = {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
