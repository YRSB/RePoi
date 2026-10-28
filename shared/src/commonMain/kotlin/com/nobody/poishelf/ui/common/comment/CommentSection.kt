package com.nobody.poishelf.ui.common.comment

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.nobody.poishelf.ui.comment.CommentDetailNavParam
import com.nobody.poishelf.ui.comment.CommentDetailScreen
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
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
import com.nobody.poishelf.ui.home.components.ShimmerHost
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import coil3.compose.AsyncImage
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.CommentReplyItem
import com.nobody.poishelf.data.remote.model.CommentUser
import com.nobody.poishelf.data.remote.model.StickerPackData
import com.nobody.poishelf.util.canAddSticker
import com.nobody.poishelf.ui.common.LevelPill
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import com.nobody.poishelf.util.CommentMutations
import com.nobody.poishelf.util.CommentFeedEntry
import com.nobody.poishelf.util.buildCommentFeedEntries
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.util.FaceTextItem
import com.nobody.poishelf.util.loadFaceTexts

/**
 * 评论区内容过滤与展示模式。
 */
enum class CommentFilterMode(val label: String) {
    /** 默认模式：展示前 5 条热门评论 + 分隔线 + 最新时序评论流 */
    DEFAULT("默认"),

    /** 热门模式：只展示所有热门神评 */
    HOT("热门"),

    /** 最新评论模式：只展示按时间倒序的最新评论流 */
    LATEST("最新")
}

/**
 * 共享评论区组件：LazyListScope 扩展函数体系。
 * 支持在任何 LazyColumn 中直接接入评论流，具备独立的模式筛选、楼中楼折叠与分页能力。
 */
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
            CommentFeedEntry.Header -> item(key = "comments_header") {
                CommentSectionHeader(
                    title = title,
                    totalCommentCount = totalCommentCount,
                    currentMode = filterMode,
                    onModeChange = onFilterModeChange,
                    isDark = isDark
                )
            }
            CommentFeedEntry.Skeleton -> item(key = "comments_skeleton") {
                ShimmerHost { CommentsLoadingSkeleton(isDark = isDark) }
            }
            is CommentFeedEntry.HotComment -> item(key = entry.key) {
                CommentItemCard(
                    comment = entry.comment,
                    isHot = false,
                    isDark = isDark,
                    interaction = interaction,
                    showDivider = entry.showDivider
                )
            }
            CommentFeedEntry.LatestDivider -> item(key = "comments_divider_default") {
                LatestDividerRow(isDark = isDark)
            }
            is CommentFeedEntry.LatestComment -> item(key = entry.key) {
                CommentItemCard(comment = entry.comment, isHot = false, isDark = isDark, interaction = interaction)
            }
            is CommentFeedEntry.Empty -> item(key = entry.key) {
                EmptyCommentsCard(text = entry.text, isDark = isDark)
            }
            is CommentFeedEntry.PagedFooter -> item(key = entry.key) {
                CommentFooter(
                    isLoadingMore = entry.isLoadingMore,
                    hasMore = entry.hasMore,
                    hasAny = entry.hasAny,
                    isDark = isDark,
                    errorMessage = entry.errorMessage,
                    onRetry = onRetryLoadMore
                )
            }
            is CommentFeedEntry.HotFooter -> item(key = "hot_only_footer") {
                HotFooterText(hotCount = entry.hotCount)
            }
        }
    }
}

/**
 * 评论区头部：动态联动标题 + 右侧点击轮询排序（默认/热门/最新）。
 */
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

    // 动态联动标题
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
        // 左侧联动标题与总数（若有返回键则在最前呈现）
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                .clip(ExpressiveTokens.PillShape)
                .clickable { onModeChange(nextMode) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
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



/** 主楼发表草稿键：与任意主楼回复草稿互不干扰。 */
private const val TOP_LEVEL_COMMENT_DRAFT_ID = -1L

/**
 * 发表主楼评论弹窗（复用回复弹窗的输入、防双击与草稿逻辑，被回复人为空时标题即“发表评论”）。
 */
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

/**
 * 评论区单条主评论卡片（含子评论预览与回复点赞操作）。
 *
 * @param comment 评论数据实体。
 * @param isHot 是否为热门神评（高亮背景色与徽标）。
 * @param isDark 是否处于深色主题。
 * @param interaction 评论区交互参数聚合物。
 * @param showReplyPreview 是否展示楼中楼子评论预览（详情页头部复用时关闭，避免与下方全量回复重复）。
 */
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
    val canDelete = interaction.onDeleteComment != null &&
        isOwnUser(comment.user, interaction.currentUserId)

    fun openReplyDialog(target: CommentReplyItem?) {
        if (interaction.onReplySubmit == null) return
        replyTarget = target
        showReplyDialog = true
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

    // 极简流式评论卡片（直接对齐参考应用视觉规范）
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
            // 左侧头像 (36dp 圆形)
            AsyncImage(
                model = CdnUrlUtils.getHeadUrl(comment.user?.header),
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
                // 1. 昵称 + 等级徽章 + 热门徽标
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

                // 2. 发布时间（位于昵称下方，小字号浅灰色，直接对照设计图 0）
                val timeStr = comment.addDateString ?: comment.addTime ?: ""
                if (timeStr.isNotBlank()) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 3. 主评论正文
                Text(
                    text = comment.contentCleanText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                // 表情附图数量较少，用 Row 直铺避免懒加载开销
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
                                    model = CdnUrlUtils.getStickerUrl(stickerUrl),
                                    contentDescription = null,
                                    modifier = Modifier.height(50.dp)
                                )
                            }
                        }
                    }
                }

                // 4. 操作行：左侧【↩ 回复】，右侧【点赞】（直接对照设计图 0）
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
                                shape = ExpressiveTokens.ChipShape,
                                color = Color.Transparent,
                                modifier = Modifier.clickable { openReplyDialog(null) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
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
                                shape = ExpressiveTokens.ChipShape,
                                color = Color.Transparent,
                                modifier = Modifier.clickable { showDeleteConfirm = true }
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

                    // 点赞按钮
                    CommentPraiseButton(
                        comment = comment,
                        interaction = interaction
                    )
                }

                // 5. 【楼中楼子评论嵌套气泡框】(直接对照设计图 0)
                val replies = (comment.replys ?: emptyList()).filter { it.contentCleanText != "(已屏蔽)" }
                if (showReplyPreview && replies.isNotEmpty()) {
                    val canJumpToDetail = interaction.onViewAllReplies != null
                    val hasMoreReplies = CommentMutations.hasMoreReplies(comment)
                    val displayReplies = replies.take(CommentMutations.REPLY_PREVIEW_LIMIT)

                    Surface(
                        shape = ExpressiveTokens.SmallCardShape,
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
                                    text = replyRelationText(reply = reply),
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

        // 底部分割线（展示回复预览的主列表项显示，与正文对齐）
        if (showReplyPreview && showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 10.dp, start = 46.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * 评论点赞按钮（主楼右下角唯一入口，位于子评论区上方）。
 */
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
                Modifier.clickable { interaction.onPraiseClick?.invoke(comment.id) }
            } else {
                Modifier
            }
        )
    ) {
        Icon(
            imageVector = if (comment.praiseState) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
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

/**
 * 子评论关系富文本：“昵称A 回复 昵称B：正文”（无被回复人时为“昵称A：正文”）。
 */
@Composable
fun replyRelationText(
    reply: CommentReplyItem
): androidx.compose.ui.text.AnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    val authorName = reply.user?.nickname?.ifBlank { null } ?: "胶友"
    val toName = reply.toUser?.nickname?.ifBlank { null }
    val isSelfReply = isSelfReplyTarget(reply)
    val showReply = !toName.isNullOrBlank() && !isSelfReply
    return buildAnnotatedString {
        withStyle(SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.Bold)) {
            append(authorName)
        }
        if (showReply) {
            append(" ")
            withStyle(SpanStyle(color = colorScheme.outline)) {
                append("回复")
            }
            append(" ")
            withStyle(SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.Bold)) {
                append(toName ?: "")
            }
        }
        append("：")
        append(reply.contentCleanText)
    }
}

/**
 * 评论详情页子评论整行：头像 + 昵称等级行 + 正文行 + 时间行 + 分割线（对齐原版）。
 */
@Composable
fun CommentReplyRow(
    reply: CommentReplyItem,
    isDark: Boolean,
    onReplyClick: ((CommentReplyItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    currentUserId: Long = 0L,
    onDeleteReply: (suspend (CommentReplyItem) -> Boolean)? = null
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
                model = CdnUrlUtils.getHeadUrl(reply.user?.header),
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
                // 1. 昵称 + 等级徽章
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

                // 2. 发布时间
                val timeStr = reply.addDateString ?: reply.addTime ?: ""
                if (timeStr.isNotBlank()) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 3. 正文（带回复 @xxx 前缀）
                Text(
                    text = replyDetailContentText(reply = reply),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                // 子楼表情数量较少，用 Row 直铺避免懒加载开销
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
                                    model = CdnUrlUtils.getStickerUrl(stickerUrl),
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
                                shape = ExpressiveTokens.ChipShape,
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
                                shape = ExpressiveTokens.ChipShape,
                                color = Color.Transparent,
                                modifier = Modifier.clickable { onReplyClick(reply) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
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

/**
 * 详情页子评论正文：“回复 昵称：正文”（无被回复人时仅正文）。
 */
@Composable
fun replyDetailContentText(
    reply: CommentReplyItem
): androidx.compose.ui.text.AnnotatedString {
    val colorScheme = MaterialTheme.colorScheme
    val toName = reply.toUser?.nickname?.ifBlank { null }
    val showReply = !toName.isNullOrBlank() && !isSelfReplyTarget(reply)
    return buildAnnotatedString {
        if (showReply) {
            withStyle(SpanStyle(color = colorScheme.outline)) {
                append("回复")
            }
            append(" ")
            withStyle(SpanStyle(color = colorScheme.primary, fontWeight = FontWeight.Bold)) {
                append(toName ?: "")
            }
            append("：")
        }
        append(reply.contentCleanText)
    }
}

/**
 * 是否为自言自语式回复（作者与被回复人为同一人时不展示“回复”关系）。
 */
private fun isSelfReplyTarget(reply: CommentReplyItem): Boolean {
    val authorName = reply.user?.nickname?.ifBlank { null } ?: "胶友"
    val toName = reply.toUser?.nickname?.ifBlank { null }
    // 同名不同人需区分：优先按用户 ID 判定，仅双方均无可用 ID 时回退昵称比较
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
        shape = ExpressiveTokens.DialogShape,
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

/**
 * 回复发表弹窗：标题按被回复人显示“回复 XXX”，直接回复主楼时显示“发表评论”。
 *
 * 保证输入草稿安全：仅当提交成功时清空草稿并关闭弹窗；失败时保持弹窗打开并保留草稿。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var showEmojiPanel by rememberSaveable(comment.id, target?.id) { mutableStateOf(false) }
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
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusRequester) {
        // 等待首帧渲染就绪后立即请求焦点并呼起键盘，使输入框就位与软键盘升起同步
        androidx.compose.runtime.withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = {
            if (!isSubmitting) {
                keyboardController?.hide()
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // KMP：Dialog 窗口导航栏沉浸由各 shell 按平台惯例处理，此处仅保留输入法内边距逻辑。
        val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
        val navBarBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)
        val bottomContentPadding = remember(imeBottom, navBarBottom) {
            if (imeBottom > 0) 6.dp else with(LocalDensity.current) { (navBarBottom.toDp() + 6.dp) }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 半透明遮罩层：点击即可收起键盘并关闭输入弹窗
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isSubmitting) {
                            keyboardController?.hide()
                            onDismiss()
                        }
                    }
            )

            // 底部输入面板卡片（紧贴软键盘上沿或直接延伸至屏幕物理底端实现导航栏沉浸）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 消费卡片内部空白区域点击，防止误触发外层遮罩关闭
                    },
                shape = ExpressiveTokens.BottomSheetShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = ExpressiveTokens.ElevationLevel1
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = bottomContentPadding)
                ) {
                    // 顶部小把手指示条（保持 M3 Expressive 抽屉一致视觉）
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp, bottom = 4.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
            // 回复目标提示（直接对照参考图）
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

            // 无边框大输入区（直接对照设计图 1 & 2）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 160.dp)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                    enabled = !isSubmitting,
                    decorationBox = { innerTextField ->
                        if (inputText.text.isEmpty()) {
                            Text(
                                text = "输入回复内容",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
            // 已选表情数量较少，用 Row 直铺避免懒加载开销
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
                                    model = CdnUrlUtils.getStickerUrl(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    imageVector = Icons.Filled.Close,
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
            AnimatedVisibility(
                visible = showEmojiPanel && tabTitles.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                            columns = GridCells.Fixed(4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(faceTexts, key = { it.id }) { face ->
                                TextButton(onClick = { insertFaceText(face.content) }) {
                                    Text(
                                        text = face.content,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    } else {
                        val packItems = stickerPacks.getOrNull(packIndex)?.items.orEmpty()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(packItems.size, key = { it }) { gridIndex ->
                                val sticker = packItems[gridIndex]
                                AsyncImage(
                                    model = CdnUrlUtils.getStickerUrl(sticker.imageUrl),
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

            // 底部工具条与发送胶囊（直接对照设计图 1 & 2）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧工具图标：表情
                if (faceTexts.isNotEmpty() || stickerPacks.isNotEmpty() || (onLoadStickerPacks != null && !stickersLoaded)) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentSatisfied,
                        contentDescription = "表情",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                showEmojiPanel = !showEmojiPanel
                                if (showEmojiPanel) keyboardController?.hide() else focusRequester.requestFocus()
                            }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 右侧药丸形发送按钮（直接对照设计图）
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
                                showEmojiPanel = false
                                keyboardController?.hide()
                                onDismiss()
                            }
                        }
                    },
                    shape = ExpressiveTokens.PillShape,
                    color = if (inputText.text.isNotBlank() || selectedStickers.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    enabled = !isSubmitting,
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "发送",
                                color = if (inputText.text.isNotBlank() || selectedStickers.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
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

/**
 * 评论区空状态展示卡。
 */
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

/**
 * 评论区底部加载中与触底提示。
 */
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
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

/**
 * 评论区骨架加载占位。
 */
@Composable
private fun CommentsLoadingSkeleton(
    isDark: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(2) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveTokens.MediumCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).shimmerBackground())
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.width(80.dp).height(14.dp).clip(ExpressiveTokens.ExtraSmallShape).shimmerBackground())
                            Box(modifier = Modifier.width(50.dp).height(10.dp).clip(ExpressiveTokens.ExtraSmallShape).shimmerBackground())
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth(0.9f).height(16.dp).clip(ExpressiveTokens.ExtraSmallShape).shimmerBackground())
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(ExpressiveTokens.ExtraSmallShape).shimmerBackground())
                }
            }
        }
    }
}

/**
 * 瀑布流版共享评论区：与 [commentSectionItems] 同逻辑，所有条目独占整行。
 * 仅供 LazyVerticalStaggeredGrid 宿主（如相册详情图集流）使用，其他详情页沿用原函数。
 */
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
            CommentFeedEntry.Header -> item(key = "comments_header", span = StaggeredGridItemSpan.FullLine) {
                CommentSectionHeader(
                    title = title,
                    totalCommentCount = totalCommentCount,
                    currentMode = filterMode,
                    onModeChange = onFilterModeChange,
                    isDark = isDark
                )
            }
            CommentFeedEntry.Skeleton -> item(key = "comments_skeleton", span = StaggeredGridItemSpan.FullLine) {
                ShimmerHost { CommentsLoadingSkeleton(isDark = isDark) }
            }
            is CommentFeedEntry.HotComment -> item(key = entry.key, span = StaggeredGridItemSpan.FullLine) {
                CommentItemCard(
                    comment = entry.comment,
                    isHot = false,
                    isDark = isDark,
                    interaction = interaction,
                    showDivider = entry.showDivider
                )
            }
            CommentFeedEntry.LatestDivider -> item(key = "comments_divider_default", span = StaggeredGridItemSpan.FullLine) {
                LatestDividerRow(isDark = isDark)
            }
            is CommentFeedEntry.LatestComment -> item(key = entry.key, span = StaggeredGridItemSpan.FullLine) {
                CommentItemCard(comment = entry.comment, isHot = false, isDark = isDark, interaction = interaction)
            }
            is CommentFeedEntry.Empty -> item(key = entry.key, span = StaggeredGridItemSpan.FullLine) {
                EmptyCommentsCard(text = entry.text, isDark = isDark)
            }
            is CommentFeedEntry.PagedFooter -> item(key = entry.key, span = StaggeredGridItemSpan.FullLine) {
                CommentFooter(
                    isLoadingMore = entry.isLoadingMore,
                    hasMore = entry.hasMore,
                    hasAny = entry.hasAny,
                    isDark = isDark,
                    errorMessage = entry.errorMessage,
                    onRetry = onRetryLoadMore
                )
            }
            is CommentFeedEntry.HotFooter -> item(key = "hot_only_footer", span = StaggeredGridItemSpan.FullLine) {
                HotFooterText(hotCount = entry.hotCount)
            }
        }
    }
}

/**
 * 通用评论流面板容器：
 * 统一大屏（副栏）与小屏（全屏页）的评论呈现。
 * 包含：
 * 1. 顶部固定标题栏 CommentSectionHeader（支持可选 onBack 返回键，statusBarsPadding + 54dp 高度）；
 * 2. 评论流内置 PullToRefreshBox 下拉刷新及触底自动翻页加载（shouldLoadMore）；
 * 3. 右下角 FAB 触发发表主楼评论弹窗；
 * 4. 可选的楼中楼覆盖层插槽（threadOverlay），支持副栏内嵌滑入或外部路由。
 */
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
    val coroutineScope = rememberCoroutineScope()

    // 切换排序模式时自动回顶
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

    // 触底自动翻页检测：最新与默认模式翻页，热门模式不翻页
    // 派生状态按需订阅列表布局信息，键仅用 remember 避免传入状态导致重建
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
        // 1. 底层：主评论流列表
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部常驻副标题栏（高度 54dp + statusBarsPadding，对齐左侧 TopBar）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = ExpressiveTokens.ElevationLevel1
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

            // 独立滚动的评论流（包裹下拉刷新）
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
                        interaction = interaction
                    )
                }
            }
        }

        // 2. 右下角 FAB（发表主楼评论）
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
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "我要说两句",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 3. 楼中楼浮层覆盖插槽
        threadOverlay?.invoke()

        // 4. 发表主楼评论弹窗
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
    }
}

/**
 * 宽屏双栏模式副屏专用固定评论区组件：
 * 基于 [CommentFeedPane] 实现，内部集成楼中楼详情滑动覆盖副栏的浮层交互。
 */
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
    // 当前正在查看的楼中楼详情主评论（非空时副栏滑入楼中楼界面）
    var activeThreadComment by remember { mutableStateOf<CommentItem?>(null) }

    // 拦截返回按键：如果副栏正在展示楼中楼，则先收起楼中楼
    BackHandler(enabled = activeThreadComment != null) {
        activeThreadComment = null
    }

    // 包装 interaction：副栏内点击卡片或“查看回复详情”，直接在副栏内部滑入楼中楼覆盖层
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
                            onBack = { activeThreadComment = null }
                        )
                    }
                }
            }
        }
    )
}
