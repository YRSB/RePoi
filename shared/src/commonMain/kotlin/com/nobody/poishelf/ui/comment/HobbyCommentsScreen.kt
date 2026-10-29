package com.nobody.poishelf.ui.comment

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.ui.common.comment.CommentFeedPane
import com.nobody.poishelf.ui.common.comment.CommentFilterMode
import com.nobody.poishelf.ui.common.comment.CommentSectionInteraction
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * 全屏评论列表页：基于 [CommentFeedPane] 实现，支持“默认/热门/最新”三种排序、下拉刷新与右下角 FAB 评论入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HobbyCommentsScreen(
    navParam: HobbyCommentsNavParam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    viewModel: HobbyCommentsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val appMessages = LocalAppMessages.current
    val listState = rememberLazyListState()

    var sortMode by rememberSaveable { mutableStateOf(CommentFilterMode.DEFAULT) }
    var userId by remember { mutableStateOf(viewModel.currentUserId) }

    LaunchedEffect(navParam) { viewModel.loadInitial(navParam) }
    LaunchedEffect(Unit) { userId = viewModel.resolveUserId() }

    val actionMessage = uiState.commentActionMessage
    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            appMessages.show(actionMessage)
            viewModel.consumeMessage()
        }
    }

    val interaction = remember(uiState.pendingPraiseIds, userId, navParam.nodeId) {
        CommentSectionInteraction(
            pendingPraiseIds = uiState.pendingPraiseIds,
            currentUserId = userId,
            onPraiseClick = { viewModel.togglePraise(it) },
            onReplySubmit = { comment, target, content, stickers ->
                viewModel.submitReply(comment, target, content, stickers)
            },
            onDeleteComment = { viewModel.deleteComment(it) },
            onDeleteReply = { comment, reply -> viewModel.deleteReply(comment, reply) },
            onAddComment = { content, stickers ->
                viewModel.submitTopLevel(content, stickers)
            },
            onLoadStickerPacks = { viewModel.loadStickerPacks() },
            onViewAllReplies = { comment ->
                onNavigateToCommentDetail?.invoke(
                    CommentDetailNavParam(
                        commentId = comment.id,
                        relateNodeId = navParam.nodeId,
                        snapshot = comment,
                        isHot = uiState.hotComments.any { it.id == comment.id }
                    )
                )
            }
        )
    }

    val totalCount = remember(uiState.totalCommentCount, uiState.hotComments, uiState.latestComments) {
        val loaded = uiState.hotComments.size + uiState.latestComments.size
        maxOf(uiState.totalCommentCount, loaded)
    }

    CommentFeedPane(
        filterMode = sortMode,
        onFilterModeChange = { sortMode = it },
        totalCommentCount = totalCount,
        hotComments = uiState.hotComments,
        latestComments = uiState.latestComments,
        isCommentsLoading = uiState.isCommentsLoading,
        isLoadingMoreComments = uiState.isLoadingMoreComments,
        hasMoreComments = uiState.hasMoreComments,
        isDark = isDark,
        modifier = modifier.fillMaxSize(),
        listState = listState,
        title = "评论",
        commentsError = uiState.commentsError,
        onBack = onBack,
        onRetryLoadMore = { viewModel.loadNextPage() },
        onRefreshComments = { viewModel.refresh() },
        onLoadMoreComments = { viewModel.loadNextPage() },
        interaction = interaction,
        bottomContentPadding = 88.dp
    )
}
