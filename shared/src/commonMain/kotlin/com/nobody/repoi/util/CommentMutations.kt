package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem

object CommentMutations {

    const val REPLY_PREVIEW_LIMIT = 3

    fun previewReplies(item: CommentItem): List<CommentReplyItem> {
        return filterShielded(item.replys ?: emptyList()).take(REPLY_PREVIEW_LIMIT)
    }

    fun isShielded(item: CommentItem): Boolean = item.isShielded

    fun isShielded(reply: CommentReplyItem): Boolean = reply.isShielded

    fun filterShielded(replies: List<CommentReplyItem>): List<CommentReplyItem> {
        return replies.filterNot { isShielded(it) }
    }

    fun filterShieldedComments(comments: List<CommentItem>): List<CommentItem> {
        return comments.filterNot { isShielded(it) }.map { comment ->
            if (comment.replys != null) {
                comment.copy(replys = filterShielded(comment.replys))
            } else {
                comment
            }
        }
    }

    fun resolveReplyToUserId(target: CommentReplyItem?): Long? {
        val user = target?.user ?: return null
        return when {
            user.id != 0L -> user.id
            user.userId != 0L -> user.userId
            else -> null
        }
    }

    fun hasMoreReplies(item: CommentItem): Boolean {
        val validReplies = filterShielded(item.replys ?: emptyList())
        return item.reply > REPLY_PREVIEW_LIMIT || validReplies.size > REPLY_PREVIEW_LIMIT
    }

    fun applyPraise(comments: List<CommentItem>, commentId: Long): List<CommentItem> {
        return comments.map { item ->
            if (item.id != commentId) {
                item
            } else {
                val praised = !item.praiseState
                item.copy(
                    praiseState = praised,
                    praiseCount = (item.praiseCount + if (praised) 1 else -1).coerceAtLeast(0)
                )
            }
        }
    }

    fun applyReplyAppended(
        comments: List<CommentItem>,
        commentId: Long,
        reply: CommentReplyItem
    ): List<CommentItem> {
        return comments.map { item ->
            if (item.id != commentId) {
                item
            } else if ((item.replys ?: emptyList()).any { it.id == reply.id }) {
                item
            } else {
                item.copy(
                    replys = (item.replys ?: emptyList()) + reply,
                    reply = item.reply + 1
                )
            }
        }
    }

    fun applyCommentRemoved(comments: List<CommentItem>, commentId: Long): List<CommentItem> {
        return comments.filter { it.id != commentId }
    }

    fun applyReplyRemoved(
        comments: List<CommentItem>,
        commentId: Long,
        replyId: Long
    ): List<CommentItem> {
        return comments.map { item ->
            if (item.id != commentId) {
                item
            } else {
                val remaining = (item.replys ?: emptyList()).filter { it.id != replyId }
                if (remaining.size == (item.replys ?: emptyList()).size) {
                    item
                } else {
                    item.copy(
                        replys = remaining,
                        reply = (item.reply - 1).coerceAtLeast(0)
                    )
                }
            }
        }
    }

    fun applyExternalUpdate(comments: List<CommentItem>, updated: CommentItem): List<CommentItem> {
        if (comments.none { it.id == updated.id }) return comments
        return comments.map { existing ->
            if (existing.id != updated.id) {
                existing
            } else {
                val merged = ((existing.replys ?: emptyList()) + (updated.replys ?: emptyList()))
                    .distinctBy { it.id }
                existing.copy(
                    praiseState = updated.praiseState,
                    praiseCount = updated.praiseCount,
                    reply = updated.reply,
                    replys = merged
                )
            }
        }
    }
}
