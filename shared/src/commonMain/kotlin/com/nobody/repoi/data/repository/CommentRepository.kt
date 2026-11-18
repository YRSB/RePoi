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

/**
 * 评论互动数据仓储：子评论详情分页、主评论点赞切换、子评论回复发表。
 *
 * 6 个详情页的评论内联组件与评论详情页共用本仓储；跨页面的主楼更新经
 * [commentUpdates] 广播，各详情 ViewModel 按 id 合并到自己的热/最新两路列表。
 * 令牌失效自愈由客户端网络管道统一处理，本仓储只负责领域映射与广播。
 */
class CommentRepository(
    private val api: HpoiApi,
) {

    private val _commentUpdates = MutableSharedFlow<CommentItem>(extraBufferCapacity = 16)
    val commentUpdates: SharedFlow<CommentItem> = _commentUpdates.asSharedFlow()

    private val _deletedComments = MutableSharedFlow<DeletedComment>(extraBufferCapacity = 16)
    val deletedComments: SharedFlow<DeletedComment> = _deletedComments.asSharedFlow()

    /** 发布一条主楼更新（点赞/回复成功后调用，上游列表页自动合并）。 */
    suspend fun emitCommentUpdate(item: CommentItem) {
        _commentUpdates.emit(item)
    }

    /** 发布一条删除广播（删除成功后调用，上游列表页按 id 移除）。 */
    suspend fun emitDeleted(deleted: DeletedComment) {
        _deletedComments.emit(deleted)
    }

    /**
     * 获取单条主评论的子评论分页（api/comment/reply/list）。
     *
     * @param commentId 主评论 ID（普通条目评论）或讨论楼层节点 ID（isDiscuss = true）
     * @param page 分页页码，从 1 开始
     * @param pageSize 每页条数，默认 20
     * @param isDiscuss 是否为讨论帖楼层：true 时直接调用 api/discuss/reply/get
     * @param parentSnapshot 讨论贴场景下外部持有的主楼快照（discuss/reply/get 不回传主楼体）
     */
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
        val error = repliesResult.exceptionOrNull() ?: ApiException(-9005, "找不到该评论")

        // 评论未命中是领域正常态：映射为空回复页，不用字符串匹配
        if (error is CommentNotFoundException) {
            return@runCatching CommentThreadPage(comment = parentSnapshot)
        }

        // 个别讨论节点被上游当做普通评论传入或报删除时，自动回落 discuss/reply/get
        val canFallback = error is ApiException &&
            (error.code == -900 || error.code == -9005 || error.code == 404 ||
                error.message?.contains("删除") == true)
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

    /**
     * 切换主评论点赞状态（api/comment/praise/del 或 api/comment/praise/add）。
     *
     * @param commentId 评论 ID
     * @param relateNodeId 评论关联的条目节点 ID（必传）
     * @param praised 当前是否已赞：true 调 del，false 调 add。
     */
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

    /**
     * 发表主楼评论（api/comment/add）。
     * 原版成功后直接刷新列表，不解析返回体，此处同样只关心成功与否。
     */
    suspend fun addComment(
        nodeId: Long,
        content: String,
        emoticon: String? = null
    ): Result<Unit> = runCatching {
        api.addComment(nodeId = nodeId, content = content, emoticon = emoticon)
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
                // 缺失 reply 时仅接受唯一内容匹配，避免误归因他人评论
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
