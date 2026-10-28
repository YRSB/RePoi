package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.local.TokenManager
import com.nobody.poishelf.data.remote.api.HpoiApi
import com.nobody.poishelf.data.remote.model.ApiException
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.CommentReplyItem
import com.nobody.poishelf.data.remote.model.CommentThreadPage
import com.nobody.poishelf.data.remote.model.CommentUser
import com.nobody.poishelf.platform.nowMillis
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

typealias CommentThreadPage = com.nobody.poishelf.data.remote.model.CommentThreadPage

data class DeletedComment(
    val commentId: Long,
    val replyId: Long? = null
)

/**
 * 评论互动数据仓储：子评论详情分页、主评论点赞切换、子评论回复发表。
 *
 * 6 个详情页的评论内联组件与评论详情页共用本仓储；跨页面的主楼更新经
 * [commentUpdates] 广播，各详情 ViewModel 按 id 合并到自己的热/最新两路列表。
 */
class CommentRepository(
    private val api: HpoiApi,
    private val tokenManager: TokenManager
) {

    private val _commentUpdates = MutableSharedFlow<CommentItem>(extraBufferCapacity = 16)
    val commentUpdates: SharedFlow<CommentItem> = _commentUpdates.asSharedFlow()

    private val _deletedComments = MutableSharedFlow<DeletedComment>(extraBufferCapacity = 16)
    val deletedComments: SharedFlow<DeletedComment> = _deletedComments.asSharedFlow()

    /** 发布一条主楼更新（点赞/回复成功后调用，上游列表页自动合并）。 */
    suspend fun emitCommentUpdate(item: CommentItem) {
        _commentUpdates.emit(item)
    }

    suspend fun emitDeleted(deleted: DeletedComment) {
        _deletedComments.emit(deleted)
    }

    /**
     * 获取单条主评论的子评论分页（常规条目评论 api/comment/getreply）。
     */
    suspend fun getReplies(
        commentId: Long,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<CommentThreadPage> = getReplies(
        commentId = commentId,
        page = page,
        pageSize = pageSize,
        isDiscuss = false,
        parentSnapshot = null
    )

    /**
     * 获取单条主评论的子评论分页（支持常规条目评论 api/comment/getreply 与讨论贴回帖 api/discuss/reply/get）。
     */
    suspend fun getReplies(
        commentId: Long,
        page: Int = 1,
        pageSize: Int = 10,
        isDiscuss: Boolean = false,
        parentSnapshot: CommentItem? = null
    ): Result<CommentThreadPage> = runCatching {
        if (isDiscuss) {
            val discussResp = api.getDiscussReplies(discussNodeId = commentId, page = page)
            if (discussResp.isSuccess) {
                return@runCatching discussResp.data?.toCommentThreadPage(parentSnapshot)
                    ?: CommentThreadPage(comment = parentSnapshot)
            } else {
                throw ApiException(discussResp.code, discussResp.msg ?: "获取讨论回复失败")
            }
        }
        val response = api.getCommentReplies(
            commentId = commentId,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            CommentThreadPage(
                comment = response.data?.comment,
                replies = response.data?.list ?: emptyList()
            )
        } else if (response.code == -900 || response.msg?.contains("删除") == true) {
            // 自愈兜底：如果常规评论接口报 -900（该评论已被删除），实际可能是讨论区回帖！自动尝试 discuss 接口拉取
            val discussResp = runCatching {
                api.getDiscussReplies(discussNodeId = commentId, page = page)
            }.getOrNull()
            if (discussResp != null && discussResp.isSuccess && discussResp.data != null) {
                discussResp.data.toCommentThreadPage(parentSnapshot)
            } else {
                throw ApiException(response.code, response.msg ?: "获取子评论失败")
            }
        } else {
            throw ApiException(response.code, response.msg ?: "获取子评论失败")
        }
    }

    /**
     * 切换主评论点赞状态（api/item/praise/add|del）。
     * @param praised 当前是否已赞：true 调 del，false 调 add。
     */
    suspend fun togglePraise(
        commentId: Long,
        relateNodeId: Long,
        praised: Boolean
    ): Result<Unit> = runCatching {
        if (tokenManager.currentToken.isBlank()) {
            throw ApiException(4001, "请先登录后再点赞")
        }
        val response = if (praised) {
            api.praiseCommentDel(commentId = commentId, relateNodeId = relateNodeId)
        } else {
            api.praiseCommentAdd(commentId = commentId, relateNodeId = relateNodeId)
        }
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "点赞操作失败")
        }
    }

    /**
     * 发表主楼评论（api/comment/add）。
     * 原版成功后直接刷新列表，不解析返回体，此处同样只关心成功与否。
     */
    suspend fun addComment(
        nodeId: Long,
        content: String,
        emoticon: String? = null
    ): Result<Unit> = runCatching {
        if (tokenManager.currentToken.isBlank()) {
            throw ApiException(4001, "请先登录后再发表评论")
        }
        val response = api.addComment(nodeId = nodeId, content = content, emoticon = emoticon)
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "评论发表失败")
        }
    }

    /**
     * 发表常规条目子评论回复（api/comment/add/reply）。
     */
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

    /**
     * 发表子评论回复（支持条目评论 api/comment/add/reply 与讨论贴回帖 api/discuss/reply/add）。
     * @return 新建的子评论对象。
     */
    suspend fun addReply(
        commentId: Long,
        content: String,
        toUserId: Long? = null,
        toReplyId: Long? = null,
        emoticon: String? = null,
        isDiscuss: Boolean = false,
        relateNodeId: Long = 0L
    ): Result<CommentReplyItem> = runCatching {
        if (tokenManager.currentToken.isBlank()) {
            throw ApiException(4001, "请先登录后再回复")
        }
        if (isDiscuss) {
            val response = api.addDiscussReply(
                itemNodeId = relateNodeId,
                discussNodeId = commentId,
                content = content,
                toUserNodeId = toUserId,
                toReplyNodeId = toReplyId,
                emoticon = emoticon
            )
            if (response.isSuccess) {
                val threadResult = getReplies(commentId = commentId, page = 1, isDiscuss = true)
                val candidates = threadResult.getOrNull()?.replies
                    ?.filter { it.contentCleanText.trim() == content.trim() } ?: emptyList()
                if (candidates.isNotEmpty()) {
                    candidates.last()
                } else {
                    CommentReplyItem(
                        id = nowMillis(),
                        content = content,
                        user = CommentUser(
                            userId = tokenManager.currentUserId,
                            nickname = tokenManager.currentUserName
                        )
                    )
                }
            } else {
                throw ApiException(response.code, response.msg ?: "回复发表失败")
            }
        } else {
            val response = api.addCommentReply(
                commentId = commentId,
                content = content,
                toUserId = toUserId,
                toReplyId = toReplyId,
                emoticon = emoticon
            )
            if (response.isSuccess) {
                val reply = response.data?.reply
                if (reply != null) {
                    reply
                } else {
                    // 缺失 reply 时仅接受唯一内容匹配，避免误归因他人评论
                    val threadResult = getReplies(commentId, page = 1)
                    val candidates = threadResult.getOrNull()?.replies
                        ?.filter { it.contentCleanText.trim() == content.trim() } ?: emptyList()
                    if (candidates.size == 1) {
                        candidates.first()
                    } else {
                        throw ApiException(response.code, "回复已发表，正在刷新列表，请稍后下拉重试")
                    }
                }
            } else {
                throw ApiException(response.code, response.msg ?: "回复发表失败")
            }
        }
    }

    suspend fun deleteComment(
        nodeId: Long,
        type: String
    ): Result<Unit> = runCatching {
        if (tokenManager.currentToken.isBlank()) {
            throw ApiException(4001, "请先登录后再删除评论")
        }
        val response = if (type == "discuss" || type == "discuss_reply") {
            api.deleteDiscuss(nodeId = nodeId, type = type)
        } else {
            api.deleteComment(nodeId = nodeId, type = type)
        }
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "删除评论失败")
        }
    }

}
