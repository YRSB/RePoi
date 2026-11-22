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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.ui.album.components.AlbumTextCover
import com.nobody.repoi.data.remote.model.RelateItem
import com.nobody.repoi.data.remote.model.UserProfileSummary
import com.nobody.repoi.ui.common.LevelPill
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.theme.FormFactorPreviews
import com.nobody.repoi.ui.theme.PoiShelfTheme
import com.nobody.repoi.util.FeedMediaResolver
import com.nobody.repoi.util.FeedContentResolver
import com.nobody.repoi.util.HtmlRichTextParser
import com.nobody.repoi.util.resolveMediaDisplayInfo
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.platform.nowMillis

internal const val ActionCardPosterTag = "action_card_poster"

private const val AlbumPosterFallbackRatio = 4f / 3f

@Composable
fun ActionCard(
    actionItem: ActionItem,
    modifier: Modifier = Modifier,
    fallbackAvatarUrl: String? = null,
    onActionClick: ((ActionItem) -> Unit)? = null,
    onImageClick: ((imageUrls: List<String>, initialIndex: Int) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null
) {
    val relateItem = actionItem.relateItem
    val mediaInfo = remember(actionItem) { actionItem.resolveMediaDisplayInfo() }
    val shouldShowLeftCover = mediaInfo.shouldShowLeftCover
    val coverUrl = mediaInfo.coverUrl
    val displayExtraImages = mediaInfo.displayExtraImages
    val allRawUrls = mediaInfo.allRawUrls

    val isOfficialNotice = actionItem.user == null || actionItem.actionType == 401
    val firstRelateData = actionItem.relateData?.firstOrNull()
    val price = firstRelateData?.price?.trim()?.ifBlank { null }
    val detail = firstRelateData?.detail?.trim()?.ifBlank { null }
    val tradePurity = firstRelateData?.purityStr?.trim()?.ifBlank { null }
    val isTrade = FeedContentResolver.isTradeAction(actionItem)
    val mainComment = remember(actionItem) { FeedContentResolver.resolveMainComment(actionItem) }

    val uriHandler = LocalUriHandler.current
    val effectiveUrlClick: (String) -> Unit = onUrlClick ?: { url -> uriHandler.openUri(url) }
    val linkColor = MaterialTheme.colorScheme.primary
    val parsedMainComment = remember(mainComment, linkColor, effectiveUrlClick) {
        if (mainComment.isNullOrBlank()) null
        else HtmlRichTextParser.parse(
            html = mainComment,
            linkColor = linkColor,
            onLinkClick = effectiveUrlClick
        )
    }

    val figureName = relateItem?.nameCN?.ifBlank { null } ?: relateItem?.name
    val parsedText = parsedMainComment?.text
    val isCommentSameAsFigure = isOfficialNotice && !parsedText.isNullOrBlank() && !figureName.isNullOrBlank() &&
        (parsedText.equals(figureName, ignoreCase = true) ||
            parsedText.equals(relateItem?.name, ignoreCase = true) ||
            parsedText.equals(relateItem?.nameCN, ignoreCase = true))

    val relateCategoryId = relateItem?.categoryId ?: 0
    val isAlbumAction = relateItem?.isAlbum == true ||
        relateCategoryId in 60000..79999 ||
        actionItem.actionType == 3 ||
        actionItem.actionType == 103 ||
        actionItem.actionType == 104

    val posterAspectRatio: Float? = when {
        isAlbumAction -> null
        actionItem.resolvePosterShape() == ActionPosterShape.SQUARE -> 1f
        else -> 9f / 13f
    }

    val extraImageUrls = remember(displayExtraImages) {
        displayExtraImages.map { MediaPipeline.Default.resolvePictureUrl(it, ImagePurpose.THUMBNAIL) }
    }

    ElevatedCard(
        onClick = { onActionClick?.invoke(actionItem) },
        enabled = onActionClick != null,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
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

            if (shouldShowLeftCover) {
                if (mediaInfo.isAlbumNoCover) {
                    val albumTitle = relateItem?.nameCN?.ifBlank { null } ?: relateItem?.name ?: ""

                    AlbumTextCover(
                        title = albumTitle,
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(9f / 13f),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                    )
                } else {

                    val posterModifier = Modifier
                        .width(96.dp)
                        .then(
                            if (posterAspectRatio != null) {
                                Modifier.aspectRatio(posterAspectRatio)
                            } else {
                                Modifier
                            }
                        )
                        .clip(MaterialTheme.shapes.small)
                        .testTag(ActionCardPosterTag)

                    PoiAsyncImage(
                        model = coverUrl,
                        contentDescription = "条目海报",
                        modifier = posterModifier,
                        contentScale = ContentScale.Fit,
                        alignment = if (isAlbumAction) Alignment.TopCenter else Alignment.Center,
                        adaptiveRatio = isAlbumAction,
                        fallbackRatio = if (isAlbumAction) AlbumPosterFallbackRatio else null,
                        enableFullscreenPreview = false,
                        onClick = if (onImageClick != null && allRawUrls.isNotEmpty()) {
                            { onImageClick(allRawUrls, 0) }
                        } else {
                            null
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 1.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isOfficialNotice) {

                        OfficialNoticeHeader(actionItem = actionItem)
                    } else {

                        UserFeedHeader(
                            actionItem = actionItem,
                            fallbackAvatarUrl = fallbackAvatarUrl,
                            onActionClick = onActionClick
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }

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
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            onImageClick?.invoke(allRawUrls, clickIndex)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val thumbUrl = extraImageUrls.getOrNull(index)
                                    if (thumbUrl != null) {
                                        PoiAsyncImage(
                                            model = thumbUrl,
                                            contentDescription = "附图缩略图",
                                            modifier = Modifier.fillMaxSize()
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
                        if (parsedMainComment != null && parsedMainComment.text.isNotBlank() && !isCommentSameAsFigure) {
                            Text(
                                text = parsedMainComment,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 19.sp
                            )
                        }

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
                                priceIcon = PoiIcons.Sell,
                                releaseDateIcon = PoiIcons.Verified
                            )
                        }
                    }
                }

                ActionCardBottomStats(
                    relateItem = relateItem,
                    mainComment = if (isCommentSameAsFigure) null else mainComment
                )
            }
        }
    }
}

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

@Composable
private fun OfficialBulletBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                painter = painterResource(PoiIcons.Campaign),
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
        shape = CircleShape,
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

@Composable
private fun NoticeParamChips(
    price: String?,
    releaseDate: String?,
    modifier: Modifier = Modifier,
    priceIcon: DrawableResource = PoiIcons.Sell,
    releaseDateIcon: DrawableResource = PoiIcons.CalendarToday
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
    icon: DrawableResource,
    label: String,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
        ) {
            Icon(
                painter = painterResource(icon),
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

@Composable
private fun UserFeedHeader(
    actionItem: ActionItem,
    modifier: Modifier = Modifier,
    fallbackAvatarUrl: String? = null,
    onActionClick: ((ActionItem) -> Unit)? = null
) {
    val user = actionItem.user
    val targetUserId = user?.actualUserId ?: 0L
    val isUserClickable = onActionClick != null && targetUserId > 0L

    val onAuthorClick: () -> Unit = { onActionClick?.invoke(actionItem.copy(relateItem = null)) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val userAvatar = user?.header?.ifBlank { null }

        val hasFallback = fallbackAvatarUrl?.isNotBlank() == true
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(
                    if (isUserClickable) {
                        Modifier.clickable(onClick = onAuthorClick)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(PoiIcons.Person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
            if (hasFallback) {
                PoiAsyncImage(
                    model = fallbackAvatarUrl,
                    contentDescription = "用户头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                PoiAsyncImage(
                    path = userAvatar,
                    category = MediaCategory.AVATAR,
                    purpose = ImagePurpose.THUMBNAIL,
                    contentDescription = "用户头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

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
                                    Modifier.clickable(onClick = onAuthorClick)
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
                text = figureName,
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Comment),
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
                    painter = painterResource(PoiIcons.Favorite),
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
    val timestamp = timeStr.trim().toLongOrNull() ?: DateTimeFormats.parseServerDateTime(timeStr.trim()) ?: return timeStr
    val now = nowMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3600_000L -> "${diff / 60_000L}分钟前"
        diff < 86400_000L -> "${diff / 3600_000L}小时前"
        diff < 86400_000L * 7 -> "${diff / 86400_000L}天前"
        else -> {
            DateTimeFormats.ymdFromMillis(timestamp).substring(5)
        }
    }
}

@FormFactorPreviews
@Composable
private fun ActionCardPreview() {
    PoiShelfTheme {
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
