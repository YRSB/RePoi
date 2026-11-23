package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.platform.PoiShelfBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.CommentDetailScreen
import kotlinx.coroutines.launch
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.focus.focusTarget
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import coil3.compose.AsyncImage
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.stripReplyPrefix
import com.nobody.repoi.util.HtmlRichTextParser
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentUser
import com.nobody.repoi.data.remote.model.StickerPackData
import com.nobody.repoi.util.canAddSticker
import com.nobody.repoi.ui.common.LevelPill
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.util.CommentMutations
import com.nobody.repoi.util.CommentFeedEntry
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.util.buildCommentFeedEntries
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.util.FaceTextItem
import com.nobody.repoi.util.loadFaceTexts
import com.nobody.repoi.ui.theme.bottomSheet
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

fun LazyListScope.commentSectionItems(
    filterMode: CommentFilterMode,
    onFilterModeChange: (CommentFilterMode) -> Unit,
    totalCommentCount: Int,
    hotComments: List<CommentItem>,
    latestComments: List<CommentItem>,
    isCommentsLoading: Boolean,
    isLoadingMoreComments: Boolean,
    hasMoreComments: Boolean,
    isDark: Boolean,
    title: String = "评论",
    commentsError: String? = null,
    onRetryLoadMore: (() -> Unit)? = null,
    showHeader: Boolean = true,
    interaction: CommentSectionInteraction = CommentSectionInteraction()
) {
    val entries = buildCommentFeedEntries(
        filterMode = filterMode,
        hotComments = hotComments,
        latestComments = latestComments,
        isLoading = isCommentsLoading,
        isLoadingMore = isLoadingMoreComments,
        hasMore = hasMoreComments,
        commentsError = commentsError,
        showHeader = showHeader
    )
    entries.forEach { entry ->
        when (entry) {
            CommentFeedEntry.Header -> item(key = "comments_header", contentType = "comments_header") {
                CommentSectionHeader(
                    title = title,
                    totalCommentCount = totalCommentCount,
                    currentMode = filterMode,
                    onModeChange = onFilterModeChange,
                    isDark = isDark
                )
            }
            CommentFeedEntry.Loading -> item(key = "comments_loading", contentType = "comments_loading") {
                PoiLoadingView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
            is CommentFeedEntry.HotComment -> item(key = entry.key, contentType = "comment_card") {
                CommentItemCard(
                    comment = entry.comment,
                    isHot = false,
                    isDark = isDark,
                    interaction = interaction,
                    showDivider = entry.showDivider
                )
            }
            CommentFeedEntry.LatestDivider -> item(key = "comments_divider_default", contentType = "comments_divider") {
                LatestDividerRow(isDark = isDark)
            }
            is CommentFeedEntry.LatestComment -> item(key = entry.key, contentType = "comment_card") {
                CommentItemCard(comment = entry.comment, isHot = false, isDark = isDark, interaction = interaction)
            }
            is CommentFeedEntry.Empty -> item(key = entry.key, contentType = "comments_empty") {
                EmptyCommentsCard(text = entry.text, isDark = isDark)
            }
            is CommentFeedEntry.PagedFooter -> item(key = entry.key, contentType = "comments_footer") {
                CommentFooter(
                    isLoadingMore = entry.isLoadingMore,
                    hasMore = entry.hasMore,
                    hasAny = entry.hasAny,
                    isDark = isDark,
                    errorMessage = entry.errorMessage,
                    onRetry = onRetryLoadMore
                )
            }
            is CommentFeedEntry.HotFooter -> item(key = "hot_only_footer", contentType = "comments_hot_footer") {
                HotFooterText(hotCount = entry.hotCount)
            }
        }
    }
}

@Composable
fun CommentSectionHeader(
    totalCommentCount: Int,
    currentMode: CommentFilterMode,
    onModeChange: (CommentFilterMode) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    title: String = "评论",
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    onBack: (() -> Unit)? = null
) {
    val nextMode = when (currentMode) {
        CommentFilterMode.DEFAULT -> CommentFilterMode.HOT
        CommentFilterMode.HOT -> CommentFilterMode.LATEST
        CommentFilterMode.LATEST -> CommentFilterMode.DEFAULT
    }

    val dynamicTitle = when (currentMode) {
        CommentFilterMode.HOT -> "最热评论"
        CommentFilterMode.LATEST -> "最新评论"
        CommentFilterMode.DEFAULT -> if (title == "全部评论") "评论" else title
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBack != null) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = dynamicTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (totalCommentCount > 0) {
                Text(
                    text = "($totalCommentCount)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onModeChange(nextMode) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(PoiIcons.AutoMirrored.Filled.Sort),
                contentDescription = "排序方式",
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = currentMode.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val TOP_LEVEL_COMMENT_DRAFT_ID = -1L

@Composable
fun TopLevelCommentDialog(
    onDismiss: () -> Unit,
    onSubmit: suspend (String, List<String>) -> Boolean,
    modifier: Modifier = Modifier,
    onLoadStickerPacks: (suspend () -> List<StickerPackData>)? = null
) {
    CommentReplyDialog(
        comment = remember { CommentItem(id = TOP_LEVEL_COMMENT_DRAFT_ID) },
        target = null,
        onDismiss = onDismiss,
        onSubmit = onSubmit,
        modifier = modifier,
        onLoadStickerPacks = onLoadStickerPacks
    )
}

@Composable
fun CommentItemCard(
    comment: CommentItem,
    isHot: Boolean = false,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    interaction: CommentSectionInteraction = CommentSectionInteraction(),
    showReplyPreview: Boolean = true,
    showDivider: Boolean = true
) {
    var showReplyDialog by remember(comment.id) { mutableStateOf(false) }
    var replyTarget by remember(comment.id) { mutableStateOf<CommentReplyItem?>(null) }
    var showDeleteConfirm by remember(comment.id) { mutableStateOf(false) }
    var isDeleting by remember(comment.id) { mutableStateOf(false) }
    val deleteScope = rememberCoroutineScope()
    val canDelete = (interaction.onDeleteComment != null || interaction.onRequestDeleteComment != null) &&
        isOwnUser(comment.user, interaction.currentUserId)

    fun openReplyDialog(target: CommentReplyItem?) {
        if (interaction.onRequestReply != null) {
            interaction.onRequestReply.invoke(comment, target)
            return
        }
        if (interaction.onReplySubmit == null) return
        replyTarget = target
        showReplyDialog = true
    }

    fun openDeleteDialog() {
        if (interaction.onRequestDeleteComment != null) {
            interaction.onRequestDeleteComment.invoke(comment)
            return
        }
        showDeleteConfirm = true
    }

    if (showReplyDialog && interaction.onReplySubmit != null) {
        CommentReplyDialog(
            comment = comment,
            target = replyTarget,
            onDismiss = { showReplyDialog = false },
            onSubmit = { content, stickers ->
                val ok = interaction.onReplySubmit.invoke(comment, replyTarget, content, stickers)
                if (ok) {
                    showReplyDialog = false
                }
                ok
            },
            onLoadStickerPacks = interaction.onLoadStickerPacks
        )
    }

    if (showDeleteConfirm && interaction.onDeleteComment != null) {
        CommentDeleteConfirmDialog(
            title = "删除评论",
            text = "删除后无法恢复，子评论将一并删除，确定删除吗？",
            isDeleting = isDeleting,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                deleteScope.launch {
                    isDeleting = true
                    val ok = interaction.onDeleteComment.invoke(comment)
                    isDeleting = false
                    if (ok) showDeleteConfirm = false
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = interaction.onViewAllReplies != null,
                onClick = { interaction.onViewAllReplies?.invoke(comment) }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {

            AsyncImage(
                model = MediaPipeline.Default.resolveUrl(comment.user?.header, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD),
                contentDescription = comment.user?.nickname,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = comment.user?.nickname ?: "胶友",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LevelPill(level = comment.user?.level ?: 0)
                }

                val timeStr = comment.addDateString ?: comment.addTime ?: ""
                if (timeStr.isNotBlank()) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                val linkColor = MaterialTheme.colorScheme.primary
                val parsedCommentContent = remember(comment.content, interaction.onUrlClick, linkColor) {
                    HtmlRichTextParser.parse(
                        html = comment.content,
                        linkColor = linkColor,
                        onLinkClick = interaction.onUrlClick
                    )
                }
                Text(
                    text = if (parsedCommentContent.text.isNotBlank()) parsedCommentContent else AnnotatedString(comment.contentCleanText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (comment.emoticonUrls.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        comment.emoticonUrls.forEachIndexed { stickerIndex, stickerUrl ->
                            key(stickerIndex) {
                                AsyncImage(
                                    model = MediaPipeline.Default.resolveStickerUrl(stickerUrl),
                                    contentDescription = null,
                                    modifier = Modifier.height(50.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (interaction.onReplySubmit != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Color.Transparent,
                                modifier = Modifier.clickable { openReplyDialog(null) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(PoiIcons.AutoMirrored.Filled.Reply),
                                        contentDescription = "回复",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "回复",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (canDelete) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Color.Transparent,
                                modifier = Modifier.clickable { openDeleteDialog() }
                            ) {
                                Text(
                                    text = "删除",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (interaction.onReplySubmit == null && !canDelete) {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }

                    CommentPraiseButton(
                        comment = comment,
                        interaction = interaction
                    )
                }

                val replies = CommentMutations.filterShielded(comment.replys ?: emptyList())
                if (showReplyPreview && replies.isNotEmpty()) {
                    val canJumpToDetail = interaction.onViewAllReplies != null
                    val hasMoreReplies = CommentMutations.hasMoreReplies(comment)
                    val displayReplies = replies.take(CommentMutations.REPLY_PREVIEW_LIMIT)

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clickable(
                                enabled = canJumpToDetail,
                                onClick = { interaction.onViewAllReplies?.invoke(comment) }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            displayReplies.forEach { reply ->
                                Text(
                                    text = replyRelationText(
                                        reply = reply,
                                        onUrlClick = interaction.onUrlClick
                                    ),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (canJumpToDetail && hasMoreReplies) {
                                val replyCount = maxOf(comment.reply, replies.size)
                                Text(
                                    text = "共${replyCount}条回复",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showReplyPreview && showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp, start = 46.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun CommentPraiseButton(
    comment: CommentItem,
    interaction: CommentSectionInteraction,
    modifier: Modifier = Modifier
) {
    val isPraisePending = comment.id in interaction.pendingPraiseIds
    val praiseClickable = interaction.onPraiseClick != null && !isPraisePending
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.then(
            if (praiseClickable) {
                Modifier.clickable { interaction.onPraiseClick.invoke(comment.id) }
            } else {
                Modifier
            }
        )
    ) {
        Icon(
            painter = painterResource(if (comment.praiseState) PoiIcons.Filled.ThumbUp else PoiIcons.Outlined.ThumbUp),
            contentDescription = if (comment.praiseState) "已点赞" else "点赞",
            tint = if (comment.praiseState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        if (comment.praiseCount > 0 || comment.praiseState) {
            Text(
                text = "${comment.praiseCount}",
                fontSize = 11.sp,
                color = if (comment.praiseState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun isSelfReplyTarget(reply: CommentReplyItem): Boolean {
    val authorName = reply.user?.nickname?.ifBlank { null } ?: "胶友"
    val toName = reply.toUser?.nickname?.ifBlank { null }

    val fromIds = listOfNotNull(reply.user?.id, reply.user?.userId).filter { it != 0L }.toSet()
    val toIds = listOfNotNull(reply.toUser?.id, reply.toUser?.userId).filter { it != 0L }.toSet()
    return if (fromIds.isNotEmpty() && toIds.isNotEmpty()) {
        fromIds.intersect(toIds).isNotEmpty()
    } else if (fromIds.isEmpty() && toIds.isEmpty()) {
        toName == authorName
    } else {
        false
    }
}

fun buildReplyRelationText(
    reply: CommentReplyItem,
    primaryColor: Color,
    outlineColor: Color,
    onUrlClick: ((String) -> Unit)? = null
): AnnotatedString {
    val authorName = reply.user?.nickname?.ifBlank { null } ?: "胶友"
    val toName = reply.toUser?.nickname?.ifBlank { null }
    val isSelfReply = isSelfReplyTarget(reply)
    val showReply = !toName.isNullOrBlank() && !isSelfReply
    val contentRaw = reply.content?.ifBlank { null } ?: reply.htmlContent
    val strippedRaw = stripReplyPrefix(contentRaw, toName)
    val parsedContent = HtmlRichTextParser.parse(
        html = strippedRaw,
        linkColor = primaryColor,
        onLinkClick = onUrlClick
    )
    return buildAnnotatedString {
        withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
            append(authorName)
        }
        if (showReply) {
            append(" ")
            withStyle(SpanStyle(color = outlineColor)) {
                append("回复")
            }
            append(" ")
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                append(toName)
            }
        }
        append("：")
        if (parsedContent.text.isNotBlank()) {
            append(parsedContent)
        } else {
            append(reply.contentCleanText)
        }
    }
}

@Composable
fun replyRelationText(
    reply: CommentReplyItem,
    onUrlClick: ((String) -> Unit)? = null
): AnnotatedString = buildReplyRelationText(
    reply = reply,
    primaryColor = MaterialTheme.colorScheme.primary,
    outlineColor = MaterialTheme.colorScheme.outline,
    onUrlClick = onUrlClick
)

@Composable
fun CommentReplyRow(
    reply: CommentReplyItem,
    isDark: Boolean,
    onReplyClick: ((CommentReplyItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    currentUserId: Long = 0L,
    onDeleteReply: (suspend (CommentReplyItem) -> Boolean)? = null,
    onUrlClick: ((String) -> Unit)? = null
) {
    var showDeleteConfirm by remember(reply.id) { mutableStateOf(false) }
    var isDeleting by remember(reply.id) { mutableStateOf(false) }
    val deleteScope = rememberCoroutineScope()
    val canDelete = onDeleteReply != null && isOwnUser(reply.user, currentUserId)
    if (showDeleteConfirm && onDeleteReply != null) {
        CommentDeleteConfirmDialog(
            title = "删除回复",
            text = "删除后无法恢复，确定删除这条回复吗？",
            isDeleting = isDeleting,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                deleteScope.launch {
                    isDeleting = true
                    val ok = onDeleteReply.invoke(reply)
                    isDeleting = false
                    if (ok) showDeleteConfirm = false
                }
            }
        )
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = MediaPipeline.Default.resolveUrl(reply.user?.header, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD),
                contentDescription = reply.user?.nickname,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = reply.user?.nickname?.ifBlank { null } ?: "胶友",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LevelPill(level = reply.user?.level ?: 0)
                }

                val timeStr = reply.addDateString ?: reply.addTime ?: ""
                if (timeStr.isNotBlank()) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Text(
                    text = replyDetailContentText(
                        reply = reply,
                        onUrlClick = onUrlClick
                    ),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                if (reply.emoticonUrls.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        reply.emoticonUrls.forEachIndexed { stickerIndex, stickerUrl ->
                            key(stickerIndex) {
                                AsyncImage(
                                    model = MediaPipeline.Default.resolveStickerUrl(stickerUrl),
                                    contentDescription = null,
                                    modifier = Modifier.height(40.dp)
                                )
                            }
                        }
                    }
                }

                if (onReplyClick != null || canDelete) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canDelete) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Color.Transparent,
                                modifier = Modifier.clickable { showDeleteConfirm = true }
                            ) {
                                Text(
                                    text = "删除",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (onReplyClick != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = Color.Transparent,
                                modifier = Modifier.clickable { onReplyClick(reply) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(PoiIcons.AutoMirrored.Filled.Reply),
                                        contentDescription = "回复",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "回复",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 58.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

fun buildReplyDetailContentText(
    reply: CommentReplyItem,
    primaryColor: Color,
    outlineColor: Color,
    onUrlClick: ((String) -> Unit)? = null
): AnnotatedString {
    val toName = reply.toUser?.nickname?.ifBlank { null }
    val showReply = !toName.isNullOrBlank() && !isSelfReplyTarget(reply)
    val contentRaw = reply.content?.ifBlank { null } ?: reply.htmlContent
    val strippedRaw = stripReplyPrefix(contentRaw, toName)
    val parsedContent = HtmlRichTextParser.parse(
        html = strippedRaw,
        linkColor = primaryColor,
        onLinkClick = onUrlClick
    )
    return buildAnnotatedString {
        if (showReply) {
            withStyle(SpanStyle(color = outlineColor)) {
                append("回复")
            }
            append(" ")
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)) {
                append(toName)
            }
            append("：")
        }
        if (parsedContent.text.isNotBlank()) {
            append(parsedContent)
        } else {
            append(reply.contentCleanText)
        }
    }
}

@Composable
fun replyDetailContentText(
    reply: CommentReplyItem,
    onUrlClick: ((String) -> Unit)? = null
): AnnotatedString = buildReplyDetailContentText(
    reply = reply,
    primaryColor = MaterialTheme.colorScheme.primary,
    outlineColor = MaterialTheme.colorScheme.outline,
    onUrlClick = onUrlClick
)

private fun isOwnUser(user: CommentUser?, currentUserId: Long): Boolean {
    if (currentUserId <= 0L || user == null) return false
    return user.id == currentUserId || user.userId == currentUserId
}

@Composable
private fun CommentDeleteConfirmDialog(
    title: String,
    text: String,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                Text(text = "删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(text = "取消")
            }
        }
    )
}

enum class CommentInputSelector {
    NONE,
    EMOJI
}

@Composable
fun CommentReplyDialog(
    comment: CommentItem,
    target: CommentReplyItem?,
    onDismiss: () -> Unit,
    onSubmit: suspend (String, List<String>) -> Boolean,
    modifier: Modifier = Modifier,
    onLoadStickerPacks: (suspend () -> List<StickerPackData>)? = null
) {
    val messages = LocalAppMessages.current
    val coroutineScope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }
    var inputText by rememberSaveable(comment.id, target?.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var currentInputSelector by rememberSaveable(comment.id, target?.id) {
        mutableStateOf(CommentInputSelector.NONE)
    }
    var textFieldFocusState by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val selectorFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var faceTexts by remember(comment.id, target?.id) { mutableStateOf(listOf<FaceTextItem>()) }
    LaunchedEffect(comment.id, target?.id) {
        faceTexts = loadFaceTexts()
    }
    var selectedStickers by rememberSaveable(comment.id, target?.id) { mutableStateOf(arrayListOf<String>()) }
    var stickerPacks by remember(comment.id, target?.id) { mutableStateOf(listOf<StickerPackData>()) }
    var stickersLoaded by remember(comment.id, target?.id) { mutableStateOf(false) }
    var emojiTabIndex by rememberSaveable(comment.id, target?.id) { mutableStateOf(0) }
    LaunchedEffect(comment.id, target?.id) {
        if (onLoadStickerPacks != null) {
            stickerPacks = onLoadStickerPacks()
                .map { pack -> pack.copy(items = pack.items?.filter { it.imageUrl.isNotBlank() }) }
                .filter { !it.items.isNullOrEmpty() }
        }
        stickersLoaded = true
    }

    fun insertFaceText(face: String) {
        val current = inputText.text
        val rawStart = inputText.selection.start.coerceIn(0, current.length)
        val rawEnd = inputText.selection.end.coerceIn(0, current.length)
        val from = minOf(rawStart, rawEnd)
        val to = maxOf(rawStart, rawEnd)
        val merged = current.substring(0, from) + face + current.substring(to)
        inputText = inputText.copy(text = merged, selection = TextRange((from + face.length).coerceAtMost(merged.length)))
    }

    val targetName = target?.user?.nickname?.ifBlank { null }
        ?: if (comment.id != TOP_LEVEL_COMMENT_DRAFT_ID) comment.user?.nickname?.ifBlank { null } else null

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    if (currentInputSelector == CommentInputSelector.EMOJI) {
        SideEffect {
            selectorFocusRequester.requestFocus()
        }
    }

    val dismissAll = {
        if (!isSubmitting) {
            focusManager.clearFocus()
            keyboardController?.hide()
            onDismiss()
        }
    }

    PoiShelfBackHandler(enabled = true) {
        if (currentInputSelector != CommentInputSelector.NONE) {
            currentInputSelector = CommentInputSelector.NONE
        } else {
            dismissAll()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    dismissAll()
                }
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
            ) {

                if (targetName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "回复",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "@$targetName",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val inputTextStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp, max = 120.dp)
                        .verticalScroll(rememberScrollState())
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (currentInputSelector != CommentInputSelector.NONE) {
                                currentInputSelector = CommentInputSelector.NONE
                            }
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                    contentAlignment = Alignment.TopStart
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = inputTextStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                textFieldFocusState = state.isFocused
                                if (state.isFocused && currentInputSelector != CommentInputSelector.NONE) {
                                    currentInputSelector = CommentInputSelector.NONE
                                }
                            },
                        enabled = !isSubmitting,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.TopStart
                            ) {
                                if (inputText.text.isEmpty()) {
                                    Text(
                                        text = "输入回复内容",
                                        style = inputTextStyle.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (selectedStickers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedStickers.forEachIndexed { index, path ->
                            key(index) {
                                Box(modifier = Modifier.size(48.dp)) {
                                    AsyncImage(
                                        model = MediaPipeline.Default.resolveStickerUrl(path),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Icon(
                                        painter = painterResource(PoiIcons.Filled.Close),
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(16.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                                            .clickable { selectedStickers = ArrayList(selectedStickers - path) }
                                    )
                                }
                            }
                        }
                    }
                }

                val showFaceTab = faceTexts.isNotEmpty()
                val tabTitles = buildList {
                    if (showFaceTab) add("颜文字")
                    stickerPacks.forEach { pack -> add(pack.name.ifBlank { "表情包" }) }
                }
                val currentTab = emojiTabIndex.coerceIn(0, (tabTitles.size - 1).coerceAtLeast(0))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    if (faceTexts.isNotEmpty() || stickerPacks.isNotEmpty() || (onLoadStickerPacks != null && !stickersLoaded)) {
                        IconButton(
                            onClick = {
                                if (currentInputSelector == CommentInputSelector.EMOJI) {
                                    currentInputSelector = CommentInputSelector.NONE
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                } else {
                                    currentInputSelector = CommentInputSelector.EMOJI
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(PoiIcons.Outlined.SentimentSatisfied),
                                contentDescription = "表情",
                                tint = if (currentInputSelector == CommentInputSelector.EMOJI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (currentInputSelector == CommentInputSelector.EMOJI) {
                                currentInputSelector = CommentInputSelector.NONE
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            } else if (textFieldFocusState) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            } else {
                                currentInputSelector = CommentInputSelector.NONE
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Outlined.Keyboard),
                            contentDescription = "键盘",
                            tint = if (currentInputSelector == CommentInputSelector.NONE && textFieldFocusState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    val canSubmit = (inputText.text.isNotBlank() || selectedStickers.isNotEmpty()) && !isSubmitting
                    Surface(
                        onClick = {
                            if (!canSubmit) {
                                if (inputText.text.isBlank() && selectedStickers.isEmpty()) {
                                    messages.show("请输入评论内容")
                                }
                                return@Surface
                            }
                            isSubmitting = true
                            coroutineScope.launch {
                                val ok = onSubmit(inputText.text.trim(), selectedStickers.toList())
                                isSubmitting = false
                                if (ok) {
                                    inputText = TextFieldValue("")
                                    selectedStickers = arrayListOf()
                                    currentInputSelector = CommentInputSelector.NONE
                                    dismissAll()
                                }
                            }
                        },
                        shape = CircleShape,
                        color = if (canSubmit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        enabled = canSubmit && !isSubmitting,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSubmitting) {
                                LoadingIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = "发送",
                                    color = if (canSubmit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = currentInputSelector == CommentInputSelector.EMOJI && tabTitles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .focusRequester(selectorFocusRequester)
                            .focusTarget()
                    ) {
                        if (tabTitles.size > 1) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = currentTab,
                                edgePadding = 0.dp
                            ) {
                                tabTitles.forEachIndexed { index, title ->
                                    Tab(
                                        selected = index == currentTab,
                                        onClick = { emojiTabIndex = index },
                                        text = { Text(text = title, fontSize = 13.sp) }
                                    )
                                }
                            }
                        }
                        val packIndex = if (showFaceTab) currentTab - 1 else currentTab
                        if (showFaceTab && currentTab == 0) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(
                                    faceTexts,
                                    key = { it.id },
                                    contentType = { "face_text" }
                                ) { face ->
                                    val fontSize = when {
                                        face.content.length >= 12 -> 11.5.sp
                                        face.content.length >= 9 -> 12.5.sp
                                        else -> 13.5.sp
                                    }
                                    Surface(
                                        onClick = { insertFaceText(face.content) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = face.content,
                                                fontSize = fontSize,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                softWrap = false,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val packItems = stickerPacks.getOrNull(packIndex)?.items.orEmpty()
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(
                                    packItems,
                                    key = { sticker -> "${packIndex}_${sticker.imageUrl}" },
                                    contentType = { "sticker_item" }
                                ) { sticker ->
                                    AsyncImage(
                                        model = MediaPipeline.Default.resolveStickerUrl(sticker.imageUrl),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clickable {
                                                if (canAddSticker(selectedStickers.size)) {
                                                    selectedStickers = ArrayList(selectedStickers + sticker.imageUrl)
                                                } else {
                                                    messages.show("最多只能选择4个表情包")
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyCommentsCard(
    text: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CommentFooter(
    isLoadingMore: Boolean,
    hasMore: Boolean,
    hasAny: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoadingMore -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "正在拉取更多讨论...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            errorMessage != null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (onRetry != null) {
                        Text(
                            text = "点击重试",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onRetry() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            !hasMore && hasAny -> {
                Text(
                    text = "—— 已加载全部讨论 (´･ω･`) ——",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
@Composable
private fun LatestDividerRow(isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
            .weight(1f)
            .height(1.dp)
            .background(
                if (isDark) Color.White.copy(alpha = 0.12f)
                else Color.Black.copy(alpha = 0.08f)
            )
        )
        Text(
            text = " 全部最新评论 ",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Box(
            modifier = Modifier
            .weight(1f)
            .height(1.dp)
            .background(
                if (isDark) Color.White.copy(alpha = 0.12f)
                else Color.Black.copy(alpha = 0.08f)
            )
        )
    }
}
@Composable
private fun HotFooterText(hotCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "—— 已展示全部热门神评 (${hotCount}条) ——",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

fun LazyStaggeredGridScope.commentSectionStaggeredItems(
    filterMode: CommentFilterMode,
    onFilterModeChange: (CommentFilterMode) -> Unit,
    totalCommentCount: Int,
    hotComments: List<CommentItem>,
    latestComments: List<CommentItem>,
    isCommentsLoading: Boolean,
    isLoadingMoreComments: Boolean,
    hasMoreComments: Boolean,
    isDark: Boolean,
    title: String = "评论",
    commentsError: String? = null,
    onRetryLoadMore: (() -> Unit)? = null,
    interaction: CommentSectionInteraction = CommentSectionInteraction()
) {
    val entries = buildCommentFeedEntries(
        filterMode = filterMode,
        hotComments = hotComments,
        latestComments = latestComments,
        isLoading = isCommentsLoading,
        isLoadingMore = isLoadingMoreComments,
        hasMore = hasMoreComments,
        commentsError = commentsError
    )
    entries.forEach { entry ->
        when (entry) {
            CommentFeedEntry.Header -> item(key = "comments_header", span = StaggeredGridItemSpan.FullLine, contentType = "comments_header") {
                CommentSectionHeader(
                    title = title,
                    totalCommentCount = totalCommentCount,
                    currentMode = filterMode,
                    onModeChange = onFilterModeChange,
                    isDark = isDark
                )
            }
            CommentFeedEntry.Loading -> item(key = "comments_loading", span = StaggeredGridItemSpan.FullLine, contentType = "comments_loading") {
                PoiLoadingView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                )
            }
            is CommentFeedEntry.HotComment -> item(key = entry.key, span = StaggeredGridItemSpan.FullLine, contentType = "comment_card") {
                CommentItemCard(
                    comment = entry.comment,
                    isHot = false,
                    isDark = isDark,
                    interaction = interaction,
                    showDivider = entry.showDivider
                )
            }
            CommentFeedEntry.LatestDivider -> item(key = "comments_divider_default", span = StaggeredGridItemSpan.FullLine, contentType = "comments_divider") {
                LatestDividerRow(isDark = isDark)
            }
            is CommentFeedEntry.LatestComment -> item(key = entry.key, span = StaggeredGridItemSpan.FullLine, contentType = "comment_card") {
                CommentItemCard(comment = entry.comment, isHot = false, isDark = isDark, interaction = interaction)
            }
            is CommentFeedEntry.Empty -> item(key = entry.key, span = StaggeredGridItemSpan.FullLine, contentType = "comments_empty") {
                EmptyCommentsCard(text = entry.text, isDark = isDark)
            }
            is CommentFeedEntry.PagedFooter -> item(key = entry.key, span = StaggeredGridItemSpan.FullLine, contentType = "comments_footer") {
                CommentFooter(
                    isLoadingMore = entry.isLoadingMore,
                    hasMore = entry.hasMore,
                    hasAny = entry.hasAny,
                    isDark = isDark,
                    errorMessage = entry.errorMessage,
                    onRetry = onRetryLoadMore
                )
            }
            is CommentFeedEntry.HotFooter -> item(key = "hot_only_footer", span = StaggeredGridItemSpan.FullLine, contentType = "comments_hot_footer") {
                HotFooterText(hotCount = entry.hotCount)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentFeedPane(
    filterMode: CommentFilterMode,
    onFilterModeChange: (CommentFilterMode) -> Unit,
    totalCommentCount: Int,
    hotComments: List<CommentItem>,
    latestComments: List<CommentItem>,
    isCommentsLoading: Boolean,
    isLoadingMoreComments: Boolean,
    hasMoreComments: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    title: String = "评论",
    commentsError: String? = null,
    onBack: (() -> Unit)? = null,
    onRetryLoadMore: (() -> Unit)? = null,
    onRefreshComments: (() -> Unit)? = null,
    onLoadMoreComments: (() -> Unit)? = null,
    interaction: CommentSectionInteraction = CommentSectionInteraction(),
    bottomContentPadding: androidx.compose.ui.unit.Dp = 80.dp,
    isFabVisible: Boolean = true,
    threadOverlay: @Composable (() -> Unit)? = null
) {
    var showTopLevelCommentDialog by remember { mutableStateOf(false) }
    var replyDialogTarget by remember { mutableStateOf<Pair<CommentItem, CommentReplyItem?>?>(null) }
    var deleteCommentTarget by remember { mutableStateOf<CommentItem?>(null) }
    var isDeletingComment by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val effectiveInteraction = remember(interaction) {
        interaction.copy(
            onRequestReply = if (interaction.onReplySubmit != null) {
                { comment: CommentItem, target: CommentReplyItem? ->
                    replyDialogTarget = comment to target
                }
            } else null,
            onRequestDeleteComment = if (interaction.onDeleteComment != null) {
                { comment: CommentItem ->
                    deleteCommentTarget = comment
                }
            } else null
        )
    }

    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(filterMode) {
        if (isFirstComposition) {
            isFirstComposition = false
        } else {
            listState.scrollToItem(0)
        }
    }

    val handleModeChange: (CommentFilterMode) -> Unit = { newMode ->
        onFilterModeChange(newMode)
        coroutineScope.launch {
            listState.scrollToItem(0)
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            if (filterMode == CommentFilterMode.HOT) {
                false
            } else {
                val layoutInfo = listState.layoutInfo
                val total = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 4 && lastVisible >= total - 2
            }
        }
    }

    LaunchedEffect(shouldLoadMore, isLoadingMoreComments, hasMoreComments) {
        if (shouldLoadMore && !isLoadingMoreComments && hasMoreComments) {
            onLoadMoreComments?.invoke()
        }
    }

    Box(
        modifier = modifier
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CommentSectionHeader(
                        title = title,
                        totalCommentCount = totalCommentCount,
                        currentMode = filterMode,
                        onModeChange = handleModeChange,
                        isDark = isDark,
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = isCommentsLoading && (hotComments.isNotEmpty() || latestComments.isNotEmpty()),
                onRefresh = { onRefreshComments?.invoke() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding)
                ) {
                    commentSectionItems(
                        filterMode = filterMode,
                        onFilterModeChange = handleModeChange,
                        totalCommentCount = totalCommentCount,
                        hotComments = hotComments,
                        latestComments = latestComments,
                        isCommentsLoading = isCommentsLoading,
                        isLoadingMoreComments = isLoadingMoreComments,
                        hasMoreComments = hasMoreComments,
                        isDark = isDark,
                        title = title,
                        commentsError = commentsError,
                        onRetryLoadMore = onRetryLoadMore ?: onLoadMoreComments,
                        showHeader = false,
                        interaction = effectiveInteraction
                    )
                }
            }
        }

        if (interaction.onAddComment != null && isFabVisible) {
            FloatingActionButton(
                onClick = { showTopLevelCommentDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Filled.Edit),
                    contentDescription = "我要说两句",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        threadOverlay?.invoke()

        if (showTopLevelCommentDialog && interaction.onAddComment != null) {
            TopLevelCommentDialog(
                onDismiss = { showTopLevelCommentDialog = false },
                onSubmit = { content, stickers ->
                    val ok = interaction.onAddComment.invoke(content, stickers)
                    if (ok) {
                        showTopLevelCommentDialog = false
                    }
                    ok
                },
                onLoadStickerPacks = interaction.onLoadStickerPacks
            )
        }

        replyDialogTarget?.let { (comment, target) ->
            if (interaction.onReplySubmit != null) {
                CommentReplyDialog(
                    comment = comment,
                    target = target,
                    onDismiss = { replyDialogTarget = null },
                    onSubmit = { content, stickers ->
                        val ok = interaction.onReplySubmit.invoke(comment, target, content, stickers)
                        if (ok) {
                            replyDialogTarget = null
                        }
                        ok
                    },
                    onLoadStickerPacks = interaction.onLoadStickerPacks
                )
            }
        }

        deleteCommentTarget?.let { comment ->
            if (interaction.onDeleteComment != null) {
                CommentDeleteConfirmDialog(
                    title = "删除评论",
                    text = "删除后无法恢复，子评论将一并删除，确定删除吗？",
                    isDeleting = isDeletingComment,
                    onDismiss = { deleteCommentTarget = null },
                    onConfirm = {
                        coroutineScope.launch {
                            isDeletingComment = true
                            try {
                                val ok = interaction.onDeleteComment.invoke(comment)

                                if (ok && deleteCommentTarget?.id == comment.id) {
                                    deleteCommentTarget = null
                                }
                            } finally {
                                isDeletingComment = false
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SupportingCommentPane(
    filterMode: CommentFilterMode,
    onFilterModeChange: (CommentFilterMode) -> Unit,
    totalCommentCount: Int,
    hotComments: List<CommentItem>,
    latestComments: List<CommentItem>,
    isCommentsLoading: Boolean,
    isLoadingMoreComments: Boolean,
    hasMoreComments: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    title: String = "评论",
    commentsError: String? = null,
    onRetryLoadMore: (() -> Unit)? = null,
    onRefreshComments: (() -> Unit)? = null,
    onLoadMoreComments: (() -> Unit)? = null,
    interaction: CommentSectionInteraction = CommentSectionInteraction(),
    relateNodeId: Long = 0L,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 80.dp
) {

    var activeThreadComment by remember { mutableStateOf<CommentItem?>(null) }

    PoiShelfBackHandler(enabled = activeThreadComment != null) {
        activeThreadComment = null
    }

    val paneInteraction = remember(interaction) {
        interaction.copy(
            onViewAllReplies = { comment ->
                activeThreadComment = comment
            }
        )
    }

    CommentFeedPane(
        filterMode = filterMode,
        onFilterModeChange = onFilterModeChange,
        totalCommentCount = totalCommentCount,
        hotComments = hotComments,
        latestComments = latestComments,
        isCommentsLoading = isCommentsLoading,
        isLoadingMoreComments = isLoadingMoreComments,
        hasMoreComments = hasMoreComments,
        isDark = isDark,
        modifier = modifier,
        listState = listState,
        title = title,
        commentsError = commentsError,
        onBack = null,
        onRetryLoadMore = onRetryLoadMore,
        onRefreshComments = onRefreshComments,
        onLoadMoreComments = onLoadMoreComments ?: onRetryLoadMore,
        interaction = paneInteraction,
        bottomContentPadding = bottomContentPadding,
        isFabVisible = activeThreadComment == null,
        threadOverlay = {
            AnimatedVisibility(
                visible = activeThreadComment != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                val threadComment = activeThreadComment
                if (threadComment != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        CommentDetailScreen(
                            navParam = CommentDetailNavParam(
                                commentId = threadComment.id,
                                relateNodeId = relateNodeId,
                                snapshot = threadComment,
                                isHot = hotComments.any { it.id == threadComment.id },
                                isDiscuss = title == "回帖" || title.contains("讨论")
                            ),
                            onBack = { activeThreadComment = null },
                            onUrlClick = interaction.onUrlClick
                        )
                    }
                }
            }
        }
    )
}
