package com.nobody.poishelf.ui.common.comment

import com.nobody.poishelf.data.remote.model.CommentItem

/**
 * 带有评论区互动能力的详情页 UI 状态规范接口。
 *
 * 供 6 个详情 ViewModel (Album/Hobby/Person/Series/Vendor/Works) 实现，
 * 配合 [CommentInteractionDelegate] 消除大量重复的点赞、回复及跨页主楼广播同步逻辑。
 */
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
