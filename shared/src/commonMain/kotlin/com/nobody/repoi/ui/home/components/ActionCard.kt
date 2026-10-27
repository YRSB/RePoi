package com.nobody.repoi.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import com.nobody.repoi.util.ImageAspectRatioCache
import coil3.compose.LocalPlatformContext
import com.nobody.repoi.util.DateTimeFormats
import kotlin.time.Clock
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateItem
import com.nobody.repoi.data.remote.model.UserProfileSummary
import com.nobody.repoi.ui.common.LevelPill
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.theme.FormFactorPreviews
import com.nobody.repoi.ui.theme.RePoiTheme
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.FeedMediaResolver
import com.nobody.repoi.util.FeedContentResolver
import com.nobody.repoi.util.resolveMediaDisplayInfo

@Composable
fun ActionCard(
    actionItem: ActionItem,
    modifier: Modifier = Modifier,
    fallbackAvatarUrl: String? = null,
    onItemClick: ((ActionItem) -> Unit)? = null,
    onUserClick: ((Long) -> Unit)? = null,
    onImageClick: ((imageUrls: List<String>, initialIndex: Int) -> Unit)? = null
) {
    val relateItem = actionItem.relateItem
    val mediaInfo = remember(actionItem) { actionItem.resolveMediaDisplayInfo() }
    val shouldShowLeftCover = mediaInfo.shouldShowLeftCover
    val coverUrl = mediaInfo.coverUrl
    val displayExtraImages = mediaInfo.displayExtraImages
    val allRawUrls = mediaInfo.allRawUrls

    // 状态判定：当无用户信息或为官方出荷/预定等事件（actionType=401）时，切换为官方情报自适应模式
    val isOfficialNotice = actionItem.user == null || actionItem.actionType == 401
    val firstRelateData = actionItem.relateData?.firstOrNull()
    val price = firstRelateData?.price?.trim()?.ifBlank { null }
    val detail = firstRelateData?.detail?.trim()?.ifBlank { null }
    val tradePurity = firstRelateData?.purityStr?.trim()?.ifBlank { null }
    val isTrade = FeedContentResolver.isTradeAction(actionItem)
    val mainComment = remember(actionItem) { FeedContentResolver.resolveMainComment(actionItem) }

    val figureName = relateItem?.nameCN?.ifBlank { null } ?: relateItem?.name
    val isCommentSameAsFigure = isOfficialNotice && !mainComment.isNullOrBlank() && !figureName.isNullOrBlank() &&
        (mainComment.equals(figureName, ignoreCase = true) || mainComment.equals(relateItem?.name, ignoreCase = true) || mainComment.equals(relateItem?.nameCN, ignoreCase = true))

    val context = LocalPlatformContext.current
    val coverRequest = remember(context, coverUrl) {
        if (coverUrl.isNotBlank()) {
            ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(false)
                .build()
        } else null
    }
    val extraImageRequests = remember(context, displayExtraImages) {
        displayExtraImages.take(3).map { path ->
            ImageRequest.Builder(context)
                .data(CdnUrlUtils.getPicSmallUrl(path))
                .crossfade(false)
                .build()
        }
    }

    // 采用 Hpoi 官方设计：默认 1:1 方形占位防跳动 (c(1.0f))，优先从 LRU 缓存获取已解码比例
    var imageRatio by remember(coverUrl) {
        mutableFloatStateOf(ImageAspectRatioCache[coverUrl] ?: 1.0f)
    }
    // 官方钳制规则：下限 0.4f (f(0.4f)，高度最大至 2.5 倍宽)，超宽图无上限原比展示 (e() 未设)
    val effectiveRatio = imageRatio.coerceAtLeast(0.4f)

    // 🎨 M3 Expressive 卡片容器：采用 surfaceContainerLow 与标准水波纹交互
    ElevatedCard(
        onClick = { onItemClick?.invoke(actionItem) },
        enabled = onItemClick != null,
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
            // ── 【左侧栏】：Hpoi 官方同款自适应海报（宽定死 96dp，相册无图时不显示图）──
            if (shouldShowLeftCover) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(effectiveRatio)
                        .clip(ExpressiveTokens.SmallCardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = onImageClick != null && allRawUrls.isNotEmpty()) {
                            onImageClick?.invoke(allRawUrls, 0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (coverRequest != null) {
                        AsyncImage(
                            model = coverRequest,
                            contentDescription = "条目海报",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            onSuccess = { state ->
                                val size = state.painter.intrinsicSize
                                if (size.width > 0 && size.height > 0) {
                                    val ratio = size.width / size.height
                                    if (ratio in 0.2f..5.0f && kotlin.math.abs(ratio - imageRatio) > 0.01f) {
                                        imageRatio = ratio
                                        ImageAspectRatioCache[coverUrl] = ratio
                                    }
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无配图",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))
            }

            // ── 【右侧栏】：Header (用户信息/官方情报) + Body (二选一：文本/参数 或 附图) + Footer (沉底手办信息与互动数据) ──
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
                    if (isOfficialNotice) {
                        // 1. 官方情报专属头部 (📢 情报速递 + 状态徽章 + 相对时间)
                        OfficialNoticeHeader(actionItem = actionItem)
                    } else {
                        // 1. 玩家用户信息区 (头像 + 昵称 + 等级 + 签名 + 时间/动作)
                        UserFeedHeader(
                            actionItem = actionItem,
                            fallbackAvatarUrl = fallbackAvatarUrl,
                            onUserClick = onUserClick
                        )

                        // 柔和分割线
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }

                    // 2. Body 内容区（二选一互斥排版：有附图展示精致矩阵，高度严格受限防止撑爆卡片；否则展示正文与参数）
                    if (displayExtraImages.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 72.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val showImages = displayExtraImages.take(3)
                            val remainingCount = displayExtraImages.size - 3

                            showImages.forEachIndexed { index, path ->
                                val clickIndex = allRawUrls.indexOfFirst {
                                    it.substringAfterLast("/").substringBefore("?") == path.substringAfterLast("/").substringBefore("?")
                                }.coerceAtLeast(0)
                                val isLastWithMore = index == 2 && remainingCount > 0

                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .sizeIn(maxWidth = 72.dp, maxHeight = 72.dp)
                                        .aspectRatio(1f)
                                        .clip(ExpressiveTokens.SmallCardShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            onImageClick?.invoke(allRawUrls, clickIndex)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val request = extraImageRequests.getOrNull(index)
                                    if (request != null) {
                                        AsyncImage(
                                            model = request,
                                            contentDescription = "附图缩略图",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    if (isLastWithMore) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "+$remainingCount",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 官方逻辑：相册无封面（album 且 cover 空）时隐藏左图，纯文字卡片展示相册标题
                        val albumTitle = relateItem?.nameCN?.ifBlank { null } ?: relateItem?.name ?: ""
                        if (mediaInfo.isAlbumNoCover && mainComment.isNullOrBlank() && albumTitle.isNotBlank()) {
                            Text(
                                text = albumTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!mainComment.isNullOrBlank() && !isCommentSameAsFigure) {
                            Text(
                                text = mainComment,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 19.sp
                            )
                        }

                        // 官方情报价格与出荷日参数胶囊 (仅在官方情报模式存在时展示)
                        if (isOfficialNotice && (!price.isNullOrBlank() || !detail.isNullOrBlank())) {
                            NoticeParamChips(
                                price = price,
                                releaseDate = detail
                            )
                        }
                        if (isTrade && (!price.isNullOrBlank() || !tradePurity.isNullOrBlank())) {
                            NoticeParamChips(
                                price = price?.let { "¥$it" },
                                releaseDate = tradePurity,
                                priceIcon = Icons.Filled.Sell,
                                releaseDateIcon = Icons.Filled.Verified
                            )
                        }
                    }
                }

                // 3. 底部条目信息与互动数据行 (手办名 + 💬 讨论数 + ♡ 收藏数) ── 始终沉底对齐
                ActionCardBottomStats(
                    relateItem = relateItem,
                    mainComment = if (isCommentSameAsFigure) null else mainComment
                )
            }
        }
    }
}

/**
 * 官方情报卡片专属头部：展示「📢 情报速递」微晶标签、预定/出荷状态徽章与右侧发布时间。
 */
@Composable
private fun OfficialNoticeHeader(
    actionItem: ActionItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OfficialBulletBadge()
            ActionStatusBadge(actionItem = actionItem)
        }

        val relativeTime = remember(actionItem.actionDate) { formatRelativeTime(actionItem.actionDate) }
        Text(
            text = relativeTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 11.sp
        )
    }
}

/**
 * 「情报速递」微胶囊标识。
 */
@Composable
private fun OfficialBulletBadge() {
    Surface(
        shape = ExpressiveTokens.PillShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Campaign,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "情报速递",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 10.sp,
                lineHeight = 12.sp
            )
        }
    }
}

private data class StatusBadgeStyle(
    val label: String,
    val containerColor: Color,
    val contentColor: Color
)

/**
 * 官方预定/出荷/延期/再版专属动态状态徽章。
 */
@Composable
private fun ActionStatusBadge(actionItem: ActionItem) {
    val style = when (actionItem.actionType) {
        401 -> when (actionItem.actionData) {
            "confirm" -> StatusBadgeStyle(
                label = "制作决定",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            "official_pic" -> StatusBadgeStyle(
                label = "官图公布",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            "preorder" -> StatusBadgeStyle(
                label = "预订开始",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            "delay" -> StatusBadgeStyle(
                label = "发售延期",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
            "release" -> StatusBadgeStyle(
                label = "出荷发售",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
            "reorder" -> StatusBadgeStyle(
                label = "再版情报",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            else -> StatusBadgeStyle(
                label = "官方快讯",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        else -> StatusBadgeStyle(
            label = "官方动态",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    Surface(
        shape = ExpressiveTokens.PillShape,
        color = style.containerColor
    ) {
        Text(
            text = style.label,
            color = style.contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 官方情报参数胶囊：展示手办定价与出荷时间。
 */
@Composable
private fun NoticeParamChips(
    price: String?,
    releaseDate: String?,
    modifier: Modifier = Modifier,
    priceIcon: ImageVector = Icons.Filled.Sell,
    releaseDateIcon: ImageVector = Icons.Filled.CalendarToday
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!price.isNullOrBlank()) {
            ParamChip(
                icon = priceIcon,
                label = price,
                iconTint = MaterialTheme.colorScheme.primary
            )
        }

        if (!releaseDate.isNullOrBlank()) {
            ParamChip(
                icon = releaseDateIcon,
                label = releaseDate,
                iconTint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ParamChip(
    icon: ImageVector,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
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
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(11.5.dp),
                tint = iconTint
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 普通玩家动态头部：头像、昵称、等级徽章、个性签名与时间动作。
 */
@Composable
private fun UserFeedHeader(
    actionItem: ActionItem,
    modifier: Modifier = Modifier,
    fallbackAvatarUrl: String? = null,
    onUserClick: ((Long) -> Unit)? = null
) {
    val user = actionItem.user
    val targetUserId = user?.actualUserId ?: 0L
    val isUserClickable = onUserClick != null && targetUserId > 0L

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val userAvatar = user?.header?.ifBlank { null }
        val avatarUrl = fallbackAvatarUrl?.ifBlank { null } ?: CdnUrlUtils.getHeadSmallUrl(userAvatar)
        val context = LocalPlatformContext.current
        val avatarRequest = remember(context, avatarUrl) {
            if (avatarUrl.isNotBlank()) {
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(false)
                    .build()
            } else null
        }
        var avatarLoadFailed by remember(avatarUrl) { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (isUserClickable) {
                        Modifier.clickable { onUserClick?.invoke(targetUserId) }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            if (avatarRequest != null && !avatarLoadFailed) {
                AsyncImage(
                    model = avatarRequest,
                    contentDescription = "用户头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { avatarLoadFailed = true }
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 第一行：用户名与等级徽章（左），时间（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = user?.nickname?.ifBlank { null }
                            ?: user?.userName
                            ?: "手办玩家",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.5.sp,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(
                                if (isUserClickable) {
                                    Modifier.clickable { onUserClick?.invoke(targetUserId) }
                                } else Modifier
                            )
                    )

                    LevelPill(level = user?.level ?: 0)
                }

                Spacer(modifier = Modifier.width(6.dp))

                val relativeTime = remember(actionItem.actionDate) { formatRelativeTime(actionItem.actionDate) }
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.5.sp,
                    maxLines = 1
                )
            }

            // 第二行：个性签名（左），操作类型（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val signText = user?.sign?.ifBlank { null } ?: "还没有信仰_(:з」∠)_"
                Text(
                    text = signText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.5.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = getActionVerb(actionItem),
                    style = MaterialTheme.typography.labelSmall,
                    color = getActionVerbColor(actionItem),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 卡片底部通用行：手办名称沉底对齐与讨论数、收藏数。
 */
@Composable
private fun ActionCardBottomStats(
    relateItem: RelateItem?,
    mainComment: String?,
    modifier: Modifier = Modifier
) {
    val figureName = relateItem?.nameCN?.ifBlank { null } ?: relateItem?.name
    val hasFigureName = !figureName.isNullOrBlank() && figureName != mainComment
    val hasStats = (relateItem?.commentCount ?: 0) > 0 || (relateItem?.collect ?: 0) > 0

    if (!hasFigureName && !hasStats) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasFigureName) {
            Text(
                text = figureName!!,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧：讨论数 💬 与 收藏数 ♡
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "讨论数",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "${relateItem?.commentCount ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "收藏数",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "${relateItem?.collect ?: 0}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}

private fun getActionVerb(actionItem: ActionItem): String =
    FeedContentResolver.resolveActionVerb(actionItem)

@Composable
private fun getActionVerbColor(actionItem: ActionItem): Color {
    return when (actionItem.actionType) {
        103, 104 -> MaterialTheme.colorScheme.tertiary
        401 -> when (actionItem.actionData) {
            "delay" -> MaterialTheme.colorScheme.error
            "preorder" -> MaterialTheme.colorScheme.primary
            "confirm" -> MaterialTheme.colorScheme.tertiary
            "official_pic" -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        }
        else -> MaterialTheme.colorScheme.primary
    }
}

private fun formatRelativeTime(timeStr: String?): String {
    if (timeStr.isNullOrBlank()) return "刚刚"
    val timestamp = timeStr.trim().toLongOrNull()
        ?: DateTimeFormats.parseDateTimeToEpochMillis(timeStr.trim())
        ?: return timeStr
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3600_000L -> "${diff / 60_000L}分钟前"
        diff < 86400_000L -> "${diff / 3600_000L}小时前"
        diff < 86400_000L * 7 -> "${diff / 86400_000L}天前"
        else -> DateTimeFormats.epochMillisToMonthDay(timestamp)
    }
}


@FormFactorPreviews
@Composable
private fun ActionCardPreview() {
    RePoiTheme {
        ActionCard(
            actionItem = ActionItem(
                id = 1001L,
                actionComment = "实物做工超赞，涂装还原度满分！",
                user = UserProfileSummary(
                    nickname = "手办收藏家",
                    level = 5
                )
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
