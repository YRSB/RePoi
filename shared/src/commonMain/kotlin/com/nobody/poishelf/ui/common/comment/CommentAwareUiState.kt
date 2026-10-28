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

/**
 * 评论区只读状态快照模型（原 `AdaptiveDetailCommentScaffold.kt:48`，M2 上到本包：
 * 纯数据 + [CommentItem] 引用，无 Compose 依赖；M3/M4 评论区 UI 共用）。
 *
 * 聚合评论列表、加载标记与分页状态，精简调用点 10+ 个零散参数。
 */
data class CommentFeedState(
    val totalCommentCount: Int = 0,
    val hotComments: List<CommentItem> = emptyList(),
    val latestComments: List<CommentItem> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = false,
    val commentsError: String? = null
)
