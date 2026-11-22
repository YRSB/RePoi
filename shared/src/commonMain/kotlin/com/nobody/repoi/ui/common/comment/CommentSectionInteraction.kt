package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.StickerPackData

enum class CommentFilterMode(val label: String) {

    DEFAULT("默认"),

    HOT("热门"),

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
    val onLoadStickerPacks: (suspend () -> List<StickerPackData>)? = null,
    val onRequestReply: ((comment: CommentItem, target: CommentReplyItem?) -> Unit)? = null,
    val onRequestDeleteComment: ((comment: CommentItem) -> Unit)? = null,
    val onUrlClick: ((String) -> Unit)? = null
)
