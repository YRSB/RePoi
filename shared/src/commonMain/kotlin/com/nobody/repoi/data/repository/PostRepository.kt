package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.PostDetailData

class PostRepository(
    private val api: HpoiApi
) {

    suspend fun getPostDetail(postId: Long, nodeId: Long? = null): Result<PostDetailData> = runCatching {
        if (postId <= 0L && (nodeId == null || nodeId <= 0L)) {
            throw ApiException(400, "帖子ID无效")
        }

        val isLikelyNodeId = postId >= 10_000_000L
        val effectiveItemId = if (!isLikelyNodeId && postId > 0L) postId else null
        val effectiveNodeId = nodeId ?: if (isLikelyNodeId) postId else null

        if (effectiveItemId != null) {
            val postV2 = runCatching {
                api.getPostInfo(nodeId = effectiveNodeId, itemId = effectiveItemId)
            }.getOrNull()?.posts
            if (postV2 != null) return@runCatching postV2
        }

        val postV1 = api.getPostInfoV1(
            nodeId = effectiveNodeId,
            itemId = effectiveItemId ?: postId.takeIf { it > 0L }
        )?.posts ?: throw ApiException(-9005, "找不到该帖子，可能已被删除")

        val resolvedItemId = postV1.itemId.takeIf { it > 0L }
        val resolvedNodeId = postV1.id.takeIf { it > 0L } ?: effectiveNodeId

        if (resolvedItemId != null) {
            val enriched = runCatching {
                api.getPostInfo(nodeId = resolvedNodeId, itemId = resolvedItemId)
            }.getOrNull()?.posts
            if (enriched != null) return@runCatching enriched
        }

        postV1
    }

    suspend fun getDiscussList(
        postId: Long,
        order: String = "",
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<CommentItem>> = runCatching {
        if (postId <= 0L) throw ApiException(400, "帖子ID无效")
        val response = api.getDiscussList(
            itemNodeId = postId,
            order = order.ifBlank { null },
            page = page,
            pageSize = pageSize
        )
        val discussList = response?.discussList ?: emptyList()
        discussList.map { it.toCommentItem() }
    }

    suspend fun addDiscuss(
        postId: Long,
        content: String,
        emoticon: String? = null
    ): Result<Unit> = runCatching {
        if (postId <= 0L) throw ApiException(400, "帖子ID无效")
        api.addDiscuss(
            itemNodeId = postId,
            content = content,
            emoticon = emoticon
        )
    }

    suspend fun addDiscussReply(
        postId: Long,
        discussId: Long,
        content: String,
        toUserId: Long? = null,
        toReplyId: Long? = null,
        emoticon: String? = null
    ): Result<Unit> = runCatching {
        if (postId <= 0L || discussId <= 0L) throw ApiException(400, "参数无效")
        api.addDiscussReply(
            itemNodeId = postId,
            discussNodeId = discussId,
            content = content,
            toUserNodeId = toUserId,
            toReplyNodeId = toReplyId,
            emoticon = emoticon
        )
    }

    suspend fun deleteDiscuss(
        id: Long,
        isSubReply: Boolean
    ): Result<Unit> = runCatching {
        if (id <= 0L) throw ApiException(400, "参数无效")
        val type = if (isSubReply) "discuss_reply" else "discuss"
        api.deleteDiscuss(
            nodeId = id,
            type = type
        )
    }

    suspend fun deletePost(postId: Long): Result<Unit> = runCatching {
        if (postId <= 0L) throw ApiException(400, "帖子ID无效")
        api.deletePost(nodeId = postId)
    }
}
