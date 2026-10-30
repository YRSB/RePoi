package com.nobody.repoi.ui.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.common.comment.CommentFooter
import com.nobody.repoi.ui.common.comment.CommentItemCard
import com.nobody.repoi.ui.common.comment.CommentReplyDialog
import com.nobody.repoi.ui.common.comment.CommentReplyRow
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * 评论详情页：头部主楼 + 子评论分页流 + 底部回复输入框（对齐原版 CommentReplyActivity）。
 */
@Composable
fun CommentDetailScreen(
    navParam: CommentDetailNavParam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommentDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val appMessages = LocalAppMessages.current
    val listState = rememberLazyListState()
    var commentUserId by remember { mutableStateOf(viewModel.currentCommentUserId) }

    LaunchedEffect(Unit) { commentUserId = viewModel.resolveCommentUserId() }

    LaunchedEffect(navParam) {
        viewModel.loadDetail(navParam)
    }

    // 一次性提示 Toast
    val actionMessage = uiState.actionMessage
    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            appMessages.show(actionMessage)
            viewModel.consumeActionMessage()
        }
    }

    // 触底自动翻页
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 2
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.isLoadingMore) {
        if (shouldLoadMore && !uiState.isLoadingMore && uiState.hasMore &&
            uiState.phase == CommentDetailPhase.Success
        ) {
            viewModel.loadNextPage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部抽屉把手与标题栏（对齐“评论抽屉”规范）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = ExpressiveTokens.ElevationLevel1
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val replyCount = uiState.comment?.reply ?: uiState.replies.size
                    Text(
                        text = if (replyCount > 0) "共 $replyCount 条回复" else "评论回复",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }

        when (uiState.phase) {
            CommentDetailPhase.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "正在加载评论详情...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            CommentDetailPhase.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "获取评论详情失败",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.loadDetail(navParam) }) {
                            Text("点击重试")
                        }
                    }
                }
            }

            CommentDetailPhase.Success -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    val comment = uiState.comment
                    if (comment != null) {
                        item(key = "detail_header_${comment.id}") {
                            CommentItemCard(
                                comment = comment,
                                isHot = uiState.isHot,
                                isDark = isDark,
                                showReplyPreview = false,
                                interaction = com.nobody.repoi.ui.common.comment.CommentSectionInteraction(
                                    pendingPraiseIds = if (uiState.isPraisePending) setOf(comment.id) else emptySet(),
                                    currentUserId = commentUserId,
                                    onPraiseClick = { viewModel.togglePraise() },
                                    onReplySubmit = { _, target, content, stickers ->
                                        viewModel.submitReply(
                                            content = content,
                                            replyTarget = target,
                                            stickers = stickers
                                        )
                                    },
                                    onDeleteComment = { val ok = viewModel.deleteHeaderComment(); if (ok) onBack(); ok },
                                    onLoadStickerPacks = { viewModel.loadStickerPacks() },
                                    onViewAllReplies = null
                                )
                            )
                        }
                    }

                    if (uiState.replies.isNotEmpty()) {
                        item(key = "detail_replies_title") {
                            // 原版标题无计数
                            Text(
                                text = "全部回复",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                        items(
                            items = uiState.replies,
                            key = { "detail_reply_${it.id}" }
                        ) { reply ->
                            CommentReplyRow(
                                reply = reply,
                                isDark = isDark,
                                onReplyClick = { viewModel.onReplyTargetChanged(it) },
                                currentUserId = commentUserId,
                                onDeleteReply = { viewModel.deleteDetailReply(it) }
                            )
                        }
                    }

                    item(key = "detail_footer") {
                        CommentFooter(
                            isLoadingMore = uiState.isLoadingMore,
                            hasMore = uiState.hasMore,
                            hasAny = uiState.replies.isNotEmpty(),
                            isDark = isDark,
                            onRetry = { viewModel.loadNextPage() }
                        )
                    }
                }
            }
        }
    }

    // 点击子评论行“回复”按钮时弹出的输入抽屉（对齐设计图规范）：目标唯一来源为 ViewModel，
    // 成功时 ViewModel 清空草稿与目标并关框，失败时两者完整保留。
    val currentComment = uiState.comment
    val targetToReply = uiState.replyTarget
    if (targetToReply != null && currentComment != null) {
        CommentReplyDialog(
            comment = currentComment,
            target = targetToReply,
            onDismiss = { viewModel.clearReplyTarget() },
            onSubmit = { content, stickers ->
                val ok = viewModel.submitReply(
                    content = content,
                    replyTarget = targetToReply,
                    stickers = stickers,
                    clearInputOnSuccess = true
                )
                if (ok) {
                    viewModel.clearReplyTarget()
                }
                ok
            },
            onLoadStickerPacks = { viewModel.loadStickerPacks() }
        )
    }
}
