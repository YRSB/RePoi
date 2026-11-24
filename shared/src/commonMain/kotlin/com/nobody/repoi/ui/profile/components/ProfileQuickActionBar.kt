package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        if (isCompact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionButton(
                    icon = PoiIcons.Filled.Star,
                    label = "我的收藏",
                    tint = MaterialTheme.colorScheme.primary,
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

                QuickActionButton(
                    icon = PoiIcons.Filled.PhotoLibrary,
                    label = "我的发布",
                    tint = MaterialTheme.colorScheme.secondary,
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

                QuickActionButton(
                    icon = PoiIcons.Filled.ShoppingBag,
                    label = "我的二手",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isLoggedIn) {
                            onShowMessage("二手闲置市场功能即将上线")
                        } else {
                            onLoginClick()
                        }
                    }
                )

                QuickActionButton(
                    icon = PoiIcons.Filled.History,
                    label = "浏览历史",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isLoggedIn) onNavigateToHistory() else onLoginClick()
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionButton(
                        icon = PoiIcons.Filled.Star,
                        label = "我的收藏",
                        tint = MaterialTheme.colorScheme.primary,
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
                    QuickActionButton(
                        icon = PoiIcons.Filled.PhotoLibrary,
                        label = "我的发布",
                        tint = MaterialTheme.colorScheme.secondary,
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
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionButton(
                        icon = PoiIcons.Filled.ShoppingBag,
                        label = "我的二手",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isLoggedIn) {
                                onShowMessage("二手闲置市场功能即将上线")
                            } else {
                                onLoginClick()
                            }
                        }
                    )
                    QuickActionButton(
                        icon = PoiIcons.Filled.History,
                        label = "浏览历史",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isLoggedIn) onNavigateToHistory() else onLoginClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: DrawableResource,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = tint
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
