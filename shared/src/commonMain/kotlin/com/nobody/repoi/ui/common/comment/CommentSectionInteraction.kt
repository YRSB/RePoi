package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.StickerPackData

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
