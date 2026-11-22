package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.remote.model.CommentItem

interface CommentAwareUiState {
    val hotComments: List<CommentItem>
    val latestComments: List<CommentItem>
    val pendingPraiseIds: Set<Long>
    val commentActionMessage: String?

    fun copyWithComments(
        hotComments: List<CommentItem> = this.hotComments,
        latestComments: List<CommentItem> = this.latestComments,
        pendingPraiseIds: Set<Long> = this.pendingPraiseIds,
        commentActionMessage: String? = this.commentActionMessage
    ): CommentAwareUiState
}

data class CommentFeedState(
    val totalCommentCount: Int = 0,
    val hotComments: List<CommentItem> = emptyList(),
    val latestComments: List<CommentItem> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = false,
    val commentsError: String? = null
)
