package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.remote.model.CommentItem

interface CommentPagedUiState : CommentAwareUiState {
    val isCommentsLoading: Boolean
    val commentsPage: Int
    val isLoadingMoreComments: Boolean
    val hasMoreComments: Boolean
    val commentsError: String?

    fun copyWithPaged(
        hotComments: List<CommentItem> = this.hotComments,
        latestComments: List<CommentItem> = this.latestComments,
        pendingPraiseIds: Set<Long> = this.pendingPraiseIds,
        commentActionMessage: String? = this.commentActionMessage,
        isCommentsLoading: Boolean = this.isCommentsLoading,
        commentsPage: Int = this.commentsPage,
        isLoadingMoreComments: Boolean = this.isLoadingMoreComments,
        hasMoreComments: Boolean = this.hasMoreComments,
        commentsError: String? = this.commentsError
    ): CommentPagedUiState
}
