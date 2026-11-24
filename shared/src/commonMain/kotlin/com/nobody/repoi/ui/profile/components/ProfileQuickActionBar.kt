package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.user.creation.UserCreationCategory
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveBentoTile(
                icon = PoiIcons.Filled.Star,
                title = "我的收藏",
                subtitle = "手办周边",
                tint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.weight(1f),
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
            )

            ExpressiveBentoTile(
                icon = PoiIcons.Filled.PhotoLibrary,
                title = "我的发布",
                subtitle = "图集摄影",
                tint = MaterialTheme.colorScheme.secondary,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.weight(1f),
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
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpressiveBentoTile(
                icon = PoiIcons.Filled.ShoppingBag,
                title = "我的二手",
                subtitle = "闲置集市",
                tint = MaterialTheme.colorScheme.tertiary,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.weight(1f),
                onClick = {
                    if (isLoggedIn) {
                        onShowMessage("二手闲置市场功能即将上线")
                    } else {
                        onLoginClick()
                    }
                }
            )

            ExpressiveBentoTile(
                icon = PoiIcons.Filled.History,
                title = "浏览历史",
                subtitle = "足迹回溯",
                tint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (isLoggedIn) onNavigateToHistory() else onLoginClick()
                }
            )
        }
    }
}

@Composable
private fun ExpressiveBentoTile(
    icon: DrawableResource,
    title: String,
    subtitle: String,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = title,
                    modifier = Modifier.size(20.dp),
                    tint = tint
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
