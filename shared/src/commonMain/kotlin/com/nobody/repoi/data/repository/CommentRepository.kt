package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentReplyPayload
import com.nobody.repoi.data.remote.model.CommentThreadPage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

fun CommentReplyPayload.toCommentThreadPage(): CommentThreadPage =
    CommentThreadPage(comment = comment, replies = list ?: emptyList())

data class DeletedComment(
    val commentId: Long,
    val replyId: Long? = null
)

class CommentRepository(
    private val api: HpoiApi,
) {

    private val _commentUpdates = MutableSharedFlow<CommentItem>(extraBufferCapacity = 16)
    val commentUpdates: SharedFlow<CommentItem> = _commentUpdates.asSharedFlow()

    private val _deletedComments = MutableSharedFlow<DeletedComment>(extraBufferCapacity = 16)
    val deletedComments: SharedFlow<DeletedComment> = _deletedComments.asSharedFlow()

    suspend fun emitCommentUpdate(item: CommentItem) {
        _commentUpdates.emit(item)
    }

    suspend fun emitDeleted(deleted: DeletedComment) {
        _deletedComments.emit(deleted)
    }

    suspend fun getReplies(
        commentId: Long,
        page: Int,
        pageSize: Int = 20,
        isDiscuss: Boolean = false,
        parentSnapshot: CommentItem? = null
    ): Result<CommentThreadPage> = runCatching {
        if (isDiscuss) {
            val response = api.getDiscussReplies(discussNodeId = commentId, page = page)
            return@runCatching response?.toCommentThreadPage(parentSnapshot)
                ?: CommentThreadPage(comment = parentSnapshot)
        }

        val repliesResult = runCatching {
            api.getCommentReplies(
                commentId = commentId,
                page = page,
                pageSize = pageSize
            )?.toCommentThreadPage() ?: CommentThreadPage()
        }
        repliesResult.getOrNull()?.let { return@runCatching it }
        val error = repliesResult.exceptionOrNull()
            ?: return@runCatching CommentThreadPage(comment = parentSnapshot)

        if (error is CommentNotFoundException) {
            return@runCatching CommentThreadPage(comment = parentSnapshot)
        }

        val canFallback = error is ApiException &&
            (error.code == -900 || error.code == -9005 || error.code == 404)
        if (canFallback) {
            val fallback = runCatching {
                api.getDiscussReplies(discussNodeId = commentId, page = page)
            }.getOrNull()
            if (fallback != null) {
                return@runCatching fallback.toCommentThreadPage(parentSnapshot)
            }
        }
        throw error
    }

    suspend fun togglePraise(
        commentId: Long,
        relateNodeId: Long,
        praised: Boolean
    ): Result<Unit> = runCatching {
        if (praised) {
            api.praiseCommentDel(commentId = commentId, relateNodeId = relateNodeId)
        } else {
            api.praiseCommentAdd(commentId = commentId, relateNodeId = relateNodeId)
        }
    }

    suspend fun addComment(
        nodeId: Long,
        content: String,
        emoticon: String? = null
    ): Result<Unit> = runCatching {
        api.addComment(nodeId = nodeId, content = content, emoticon = emoticon)
    }

    suspend fun addReply(
        commentId: Long,
        content: String,
        toUserId: Long? = null,
        toReplyId: Long? = null,
        emoticon: String? = null
    ): Result<CommentReplyItem> = addReply(
        commentId = commentId,
        content = content,
        toUserId = toUserId,
        toReplyId = toReplyId,
        emoticon = emoticon,
        isDiscuss = false,
        relateNodeId = 0L
    )

    suspend fun addReply(
        commentId: Long,
        content: String,
        toUserId: Long? = null,
        toReplyId: Long? = null,
        emoticon: String? = null,
        isDiscuss: Boolean = false,
        relateNodeId: Long = 0L
    ): Result<CommentReplyItem> = runCatching {
        if (isDiscuss) {
            api.addDiscussReply(
                itemNodeId = relateNodeId,
                discussNodeId = commentId,
                content = content,
                toUserNodeId = toUserId,
                toReplyNodeId = toReplyId,
                emoticon = emoticon
            )
            val threadResult = getReplies(commentId = commentId, page = 1, isDiscuss = true)
            val candidates = threadResult.getOrNull()?.replies
                ?.filter { it.contentCleanText.trim() == content.trim() } ?: emptyList()
            if (candidates.isNotEmpty()) {
                candidates.last()
            } else {
                throw ApiException(-1, "回复已发表，正在刷新列表，请稍后下拉重试")
            }
        } else {
            val reply = api.addCommentReply(
                commentId = commentId,
                content = content,
                toUserId = toUserId,
                toReplyId = toReplyId,
                emoticon = emoticon
            )?.reply
            if (reply != null) {
                reply
            } else {

                val threadResult = getReplies(commentId, page = 1)
                val candidates = threadResult.getOrNull()?.replies
                    ?.filter { it.contentCleanText.trim() == content.trim() } ?: emptyList()
                if (candidates.size == 1) {
                    candidates.first()
                } else {
                    throw ApiException(-1, "回复已发表，正在刷新列表，请稍后下拉重试")
                }
            }
        }
    }

    suspend fun deleteComment(
        nodeId: Long,
        type: String
    ): Result<Unit> = runCatching {
        if (type == "discuss" || type == "discuss_reply") {
            api.deleteDiscuss(nodeId = nodeId, type = type)
        } else {
            api.deleteComment(nodeId = nodeId, type = type)
        }
    }
}
