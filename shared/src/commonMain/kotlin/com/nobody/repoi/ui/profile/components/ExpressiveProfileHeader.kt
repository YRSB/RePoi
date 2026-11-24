package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    isDark: Boolean,
    hasUnreadNotice: Boolean,
    onToggleTheme: () -> Unit,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
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
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.52f),
                                Color.Black.copy(alpha = 0.38f),
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        IconButton(onClick = onMailClick, modifier = Modifier.size(38.dp)) {
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
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        IconButton(onClick = onToggleTheme, modifier = Modifier.size(38.dp)) {
                            Icon(
                                painter = painterResource(if (isDark) PoiIcons.Filled.WbSunny else PoiIcons.Filled.NightsStay),
                                contentDescription = "切换深浅色主题",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.28f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                    ) {
                        IconButton(onClick = onSettingsClick, modifier = Modifier.size(38.dp)) {
                            Icon(
                                painter = painterResource(PoiIcons.Filled.Settings),
                                contentDescription = "设置",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(Color.White, Color.White.copy(alpha = 0.45f))
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .background(Color.White.copy(alpha = 0.15f))
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
                            modifier = Modifier.size(38.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                if (profile != null) onNavigateToUserInfo(profile.id) else onLoginClick()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile?.displayName ?: "点击登录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            painter = painterResource(PoiIcons.Filled.ChevronRight),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isLoggedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Lv.${profile.level}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "H",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    )
                                    Text(
                                        text = if (userMp > 0) userMp.toString() else "0",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Icon(
                                painter = painterResource(PoiIcons.Filled.QrCode),
                                contentDescription = "个人二维码",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    } else {
                        Text(
                            text = "登录后同步心愿与手账",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
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
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.32f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SocialStatColumn(
                        count = fellow?.friendCount ?: 0,
                        label = "关注",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (profile != null) onNavigateToFriendList(profile.id, 0) else onLoginClick()
                        }
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight(0.6f)
                            .width(1.dp),
                        color = Color.White.copy(alpha = 0.25f)
                    )

                    SocialStatColumn(
                        count = fellow?.fanCount ?: 0,
                        label = "粉丝",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (profile != null) onNavigateToFriendList(profile.id, 1) else onLoginClick()
                        }
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight(0.6f)
                            .width(1.dp),
                        color = Color.White.copy(alpha = 0.25f)
                    )

                    SocialStatColumn(
                        count = fellow?.praiseCount ?: 0,
                        label = "获赞",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (profile != null) onNavigateToFriendList(profile.id, 3) else onLoginClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialStatColumn(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
