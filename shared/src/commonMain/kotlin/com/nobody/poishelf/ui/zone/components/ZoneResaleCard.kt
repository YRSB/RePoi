package com.nobody.poishelf.ui.zone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.poishelf.data.remote.model.ResaleSearchItem
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.rememberListImageRequest
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import com.nobody.poishelf.util.ImageAspectRatioCache

/**
 * 专区二手交易卡片（参考首页动态 ActionCard 交易样式重构）。
 *
 * 布局层次：
 * - 左侧：固定 96dp 宽度的自适应比例海报，纯净展示、圆角防抖动
 * - 右侧 Header：卖家头像、昵称、出物徽章、相对时间与柔和分割线
 * - 右侧 Body：交易描述正文、价格微胶囊（高亮强调）、成色胶囊与地区胶囊
 * - 右侧 Footer：关联手办条目名称沉底展示与私信/更多操作按钮
 */
@Composable
fun ZoneResaleCard(
    item: ResaleSearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onUserClick: ((Long) -> Unit)? = null,
    onContactClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null
) {
    val imageViewer = LocalImageViewer.current
    val coverUrl = remember(item) { item.displayCoverUrl }
    val thumbRequest = rememberListImageRequest(item.hobbyCover ?: item.displayCover, coverUrl ?: "", "resale")
    val userAvatarUrl = remember(item.user?.header) { CdnUrlUtils.getHeadUrl(item.user?.header) }
    var loadFailed by remember(coverUrl) { mutableStateOf(false) }

    // 自适应图片纵横比：优先从 LRU 缓存获取，官方钳制比例不小于 0.4f
    var imageRatio by remember(coverUrl) {
        mutableFloatStateOf(coverUrl?.let { ImageAspectRatioCache[it] } ?: 1.0f)
    }
    val effectiveRatio = imageRatio.coerceAtLeast(0.4f)

    val targetUserId = item.user?.userId ?: 0L
    val isUserClickable = onUserClick != null && targetUserId > 0L

    ElevatedCard(
        onClick = onClick,
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationDefault
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // ── 【左侧栏】：自适应海报（宽定死 96dp，纯净无遮挡）──
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(effectiveRatio)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrBlank() && !loadFailed) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = item.detail,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state ->
                            val size = state.painter.intrinsicSize
                            if (size.width > 0 && size.height > 0) {
                                val ratio = size.width / size.height
                                if (ratio in 0.2f..5.0f && kotlin.math.abs(ratio - imageRatio) > 0.01f) {
                                    imageRatio = ratio
                                    coverUrl?.let { ImageAspectRatioCache[it] = ratio }
                                }
                            }
                        },
                        onError = { loadFailed = true }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BrokenImage,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── 【右侧栏】：Header (卖家信息) + Body (正文与参数胶囊) + Footer (沉底手办信息与操作) ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 1.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 上半部内容区
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. 卖家用户信息行（头像 + 昵称 + 出物徽章 + 相对发布时间）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 左侧：头像 + 昵称
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .then(
                                    if (isUserClickable) {
                                        Modifier.clickable { onUserClick?.invoke(targetUserId) }
                                    } else Modifier
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (!userAvatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = userAvatarUrl,
                                        contentDescription = item.user?.nickname,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Text(
                                text = item.user?.nickname?.ifBlank { null } ?: "胶友",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 右侧：出物徽章 + 相对时间
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = ExpressiveTokens.PillShape,
                                color = if (item.isBuy) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (item.isBuy) "收购" else "出物",
                                    color = if (item.isBuy) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            val timeDisplay = item.addDateStr?.takeIf { it.isNotBlank() }
                                ?: item.addDate?.takeIf { it.isNotBlank() }
                            if (!timeDisplay.isNullOrBlank()) {
                                Text(
                                    text = timeDisplay,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 10.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // 柔和分割线
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 1.dp)
                    )

                    // 2. 描述正文（最多 3 行）
                    val detailText = item.cleanDetail.trim().ifBlank { null }
                    if (detailText != null) {
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 19.sp
                        )
                    }

                    // 3. 关键交易参数胶囊（价格 + 成色 + 地点）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 价格胶囊（高亮强调）
                        Surface(
                            shape = ExpressiveTokens.ChipShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Sell,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.5.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = item.displayPriceText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // 成色胶囊
                        val purityText = item.purityStr?.trim()?.ifBlank { null }
                        if (purityText != null) {
                            Surface(
                                shape = ExpressiveTokens.ChipShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.5.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = purityText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // 地区胶囊
                        val location = item.location?.trim()?.ifBlank { null }
                        if (location != null) {
                            Surface(
                                shape = ExpressiveTokens.ChipShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.5.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = location,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. 底部沉底栏（左侧关联手办条目名称 + 右侧私信/更多操作）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val firstHobby = item.item?.firstOrNull()
                    val figureName = firstHobby?.nameCN?.ifBlank { null } ?: firstHobby?.name
                    if (!figureName.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CollectionsBookmark,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = figureName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // 右侧查看实物图、私信与更多操作按钮
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!item.isBuy && item.hasUserPic && item.userPics.isNotEmpty()) {
                            IconButton(
                                onClick = { imageViewer.show(item.userPics, 0) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = "查看实物图",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (onContactClick != null) {
                            IconButton(
                                onClick = onContactClick,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = "私信",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (onMoreClick != null) {
                            IconButton(
                                onClick = onMoreClick,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreHoriz,
                                    contentDescription = "更多",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 二手卡片骨架加载占位（与真实卡片 1:1 动态对齐结构）。
 */
@Composable
fun ZoneResaleCardSkeleton(
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationDefault
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧海报骨架
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(130.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .shimmerBackground()
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 右侧详情骨架
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 1.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header 用户骨架行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(14.dp)
                                    .clip(ExpressiveTokens.ExtraSmallShape)
                                    .shimmerBackground()
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(14.dp)
                                .clip(ExpressiveTokens.PillShape)
                                .shimmerBackground()
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))

                    // Body 文本骨架
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(14.dp)
                            .clip(ExpressiveTokens.ExtraSmallShape)
                            .shimmerBackground()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(ExpressiveTokens.ExtraSmallShape)
                            .shimmerBackground()
                    )

                    // 胶囊骨架
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(22.dp)
                                .clip(ExpressiveTokens.ChipShape)
                                .shimmerBackground()
                        )
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(22.dp)
                                .clip(ExpressiveTokens.ChipShape)
                                .shimmerBackground()
                        )
                    }
                }

                // Footer 沉底骨架
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(12.dp)
                            .clip(ExpressiveTokens.ExtraSmallShape)
                            .shimmerBackground()
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .shimmerBackground()
                    )
                }
            }
        }
    }
}
