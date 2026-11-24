package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.theme.pill
import org.jetbrains.compose.resources.painterResource

@Composable
fun ExpressiveProfileHeader(
    profile: UserProfile?,
    fellow: FellowCountData?,
    userMp: Int,
    isSignedInToday: Boolean,
    isCheckingIn: Boolean,
    hasUnreadNotice: Boolean,
    onCheckInClick: () -> Unit,
    onMailClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToFriendList: (userId: Long, initialType: Int) -> Unit,
    onNavigateToUserInfo: (userId: Long) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerUrl = remember(profile?.bnr) {
        profile?.bnr?.let {
            MediaPipeline.Default.resolveUrl(it, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD)
        }.orEmpty()
    }

    val avatarUrl = remember(profile?.header) {
        profile?.header?.let {
            MediaPipeline.Default.resolveUrl(it, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD)
        }.orEmpty()
    }

    val isLoggedIn = profile != null
    val bannerHeight = 172.dp
    val avatarSize = 74.dp
    val avatarOverlap = 36.dp

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
        ) {
            if (bannerUrl.isNotBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            1.0f to Color.Transparent
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = onMailClick,
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.35f),
                            contentColor = Color.White
                        )
                    ) {
                        BadgedBox(
                            badge = {
                                if (hasUnreadNotice) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error)
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(PoiIcons.Filled.Mail),
                                contentDescription = "消息中心",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.35f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.Settings),
                            contentDescription = "设置",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = bannerHeight - avatarOverlap)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .shadow(elevation = 4.dp, shape = CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable {
                            if (profile != null) onNavigateToUserInfo(profile.id) else onLoginClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(if (isLoggedIn) PoiIcons.Filled.AccountBox else PoiIcons.Filled.Person),
                            contentDescription = "默认头像",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isLoggedIn) {
                    CheckInSpringButton(
                        isSignedInToday = isSignedInToday,
                        isCheckingIn = isCheckingIn,
                        onClick = onCheckInClick
                    )
                } else {
                    Button(
                        onClick = onLoginClick,
                        shape = MaterialTheme.shapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "登录",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            if (profile != null) onNavigateToUserInfo(profile.id) else onLoginClick()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = profile?.displayName ?: "点击登录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        painter = painterResource(PoiIcons.Filled.ChevronRight),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isLoggedIn) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Lv.${profile.level}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = MaterialTheme.shapes.pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "H",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFB800)
                            )
                            Text(
                                text = if (userMp > 0) userMp.toString() else "0",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(PoiIcons.Filled.QrCode),
                        contentDescription = "个人二维码",
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { onNavigateToUserInfo(profile.id) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isLoggedIn) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "登录后同步心愿与手账",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialStatItem(
                    count = fellow?.friendCount ?: 0,
                    label = "关注",
                    onClick = {
                        if (profile != null) onNavigateToFriendList(profile.id, 0) else onLoginClick()
                    }
                )

                SocialStatItem(
                    count = fellow?.fanCount ?: 0,
                    label = "粉丝",
                    onClick = {
                        if (profile != null) onNavigateToFriendList(profile.id, 1) else onLoginClick()
                    }
                )

                SocialStatItem(
                    count = fellow?.praiseCount ?: 0,
                    label = "获赞",
                    onClick = {
                        if (profile != null) onNavigateToFriendList(profile.id, 3) else onLoginClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SocialStatItem(
    count: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
