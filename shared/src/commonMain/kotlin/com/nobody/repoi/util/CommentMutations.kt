package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem

/**
 * 评论点赞与回复的纯列表变换（乐观更新与回滚共用，供各详情 ViewModel 调用）。
 */
object CommentMutations {

    /** 预览上限：内联子评论最多展示 3 条，超出走评论详情页（对齐原版）。 */
    const val REPLY_PREVIEW_LIMIT = 3

    /** 内联预览子评论（过滤屏蔽项后取前 3 条，与渲染侧同一口径）。 */
    fun previewReplies(item: CommentItem): List<CommentReplyItem> {
        return filterShielded(item.replys ?: emptyList()).take(REPLY_PREVIEW_LIMIT)
    }

    /** 屏蔽项过滤（详情页与内联同一口径）。 */
    fun filterShielded(replies: List<CommentReplyItem>): List<CommentReplyItem> {
        return replies.filter { it.contentCleanText != "(已屏蔽)" }
    }

    /** 回复目标用户 ID：取首个非零值，双零时传空（与展示侧判定对齐）。 */
    fun resolveReplyToUserId(target: CommentReplyItem?): Long? {
        val user = target?.user ?: return null
        return when {
            user.id != 0L -> user.id
            user.userId != 0L -> user.userId
            else -> null
        }
    }

    /** 是否需要展示“共 X 条回复”（总数或实际列表超出预览窗口即展示）。 */
    fun hasMoreReplies(item: CommentItem): Boolean {
        return item.reply > REPLY_PREVIEW_LIMIT ||
            (item.replys ?: emptyList()).size > REPLY_PREVIEW_LIMIT
    }

    /** 点赞乐观应用：取反 praiseState 并 ±1。 */
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

    /** 回复发表成功：追加子评论并 reply + 1（按 id 去重，避免降级回捞重复）。 */
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

    /** 外部更新合并：点赞态/计数与总数以新值为准，子评论按 id 增量合并（避免空列表冲掉预览）。 */
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
