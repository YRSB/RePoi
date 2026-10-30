package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.StickerPackData

/**
 * 评论区内容过滤与展示模式（原 `CommentSection.kt:143`，M2 上提到本包：
 * `CommentFeedEntries` 工具与 M3 评论区 UI 共用；UI 组件本体仍归 M3/M4）。
 */
enum class CommentFilterMode(val label: String) {
    /** 默认模式：展示前 5 条热门评论 + 分隔线 + 最新时序评论流 */
    DEFAULT("默认"),

    /** 热门模式：只展示所有热门神评 */
    HOT("热门"),

    /** 最新评论模式：只展示按时间倒序的最新评论流 */
    LATEST("最新")
}

data class CommentSectionInteraction(
    val pendingPraiseIds: Set<Long> = emptySet(),
    val currentUserId: Long = 0L,
    val onPraiseClick: ((Long) -> Unit)? = null,
    val onReplySubmit: (suspend (comment: CommentItem, target: CommentReplyItem?, content: String, stickers: List<String>) -> Boolean)? = null,
    val onDeleteComment: (suspend (comment: CommentItem) -> Boolean)? = null,
    val onDeleteReply: (suspend (comment: CommentItem, reply: CommentReplyItem) -> Boolean)? = null,
    val onViewAllReplies: ((CommentItem) -> Unit)? = null,
    val onAddComment: (suspend (content: String, stickers: List<String>) -> Boolean)? = null,
    val onLoadStickerPacks: (suspend () -> List<StickerPackData>)? = null
)
