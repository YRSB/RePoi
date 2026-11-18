package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.PostDetailData

/**
 * 讨论贴与回帖数据仓储。
 * 提供帖子详情查询、回帖列表分页、发表主楼回帖、发表楼中楼回复及删除等能力。
 */
class PostRepository(
    private val api: HpoiApi
) {

    /**
     * 获取帖子详情 (api/posts/info-v2 与 api/posts/info 双向容错)。
     * @param postId 帖子业务 ID (itemId) 或全局节点 ID (nodeId)
     * @param nodeId 可选的帖子全局节点 ID (nodeId)
     */
    suspend fun getPostDetail(postId: Long, nodeId: Long? = null): Result<PostDetailData> = runCatching {
        if (postId <= 0L && (nodeId == null || nodeId <= 0L)) {
            throw ApiException(400, "帖子ID无效")
        }

        // 1. 判断入参特征：若明确传入了 nodeId 或 postId 属于大号 node id (>= 10,000,000)
        val isLikelyNodeId = postId >= 10_000_000L
        val effectiveItemId = if (!isLikelyNodeId && postId > 0L) postId else null
        val effectiveNodeId = nodeId ?: if (isLikelyNodeId) postId else null

        // 2. 优先策略：若有 itemId，直接调用 info-v2 (可携带关联手办 relateItem)
        if (effectiveItemId != null) {
            val postV2 = runCatching {
                api.getPostInfo(nodeId = effectiveNodeId, itemId = effectiveItemId)
            }.getOrNull()?.posts
            if (postV2 != null) return@runCatching postV2
        }

        // 3. 容错策略：若仅有 nodeId 或 info-v2 失败，调用 api/posts/info (v1) 反解真实 itemId
        val postV1 = api.getPostInfoV1(
            nodeId = effectiveNodeId,
            itemId = effectiveItemId ?: postId.takeIf { it > 0L }
        )?.posts ?: throw ApiException(-9005, "找不到该帖子，可能已被删除")

        val resolvedItemId = postV1.itemId.takeIf { it > 0L }
        val resolvedNodeId = postV1.id.takeIf { it > 0L } ?: effectiveNodeId

        // 4. 若成功反解出 itemId，尝试调用 info-v2 补齐 relateItem 关联手办数据
        if (resolvedItemId != null) {
            val enriched = runCatching {
                api.getPostInfo(nodeId = resolvedNodeId, itemId = resolvedItemId)
            }.getOrNull()?.posts
            if (enriched != null) return@runCatching enriched
        }

        postV1
    }

    /**
     * 获取讨论贴回帖列表 (api/discuss/get)。
     * @param postId 帖子节点 ID（对应 itemNodeId）
     * @param order 排序："" 为最新（倒序），"asc" 为最早（正序）
     * @param page 分页页码，从 1 开始
     * @param pageSize 每页条数
     */
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

    /**
     * 发表讨论贴主楼回帖 (api/discuss/add)。
     * @param postId 帖子节点 ID（对应 itemNodeId）
     * @param content 回帖纯文本内容
     * @param emoticon 表情包序列化参数（可选）
     */
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

    /**
     * 发表讨论贴楼中楼回复 (api/discuss/reply/add)。
     * @param postId 帖子节点 ID（对应 itemNodeId）
     * @param discussId 主楼回帖节点 ID（对应 discussNodeId）
     * @param content 回复内容
     * @param toUserId 被回复目标用户 ID（可选）
     * @param toReplyId 被回复目标楼中楼 ID（可选）
     * @param emoticon 表情包序列化参数（可选）
     */
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

    /**
     * 删除讨论贴回帖或楼中楼回复 (api/discuss/del)。
     * @param id 回帖或回复节点 ID
     * @param isSubReply 是否为楼中楼子回复
     */
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

    /**
     * 删除讨论贴自身 (api/posts/del)。
     * @param postId 帖子节点 ID
     */
    suspend fun deletePost(postId: Long): Result<Unit> = runCatching {
        if (postId <= 0L) throw ApiException(400, "帖子ID无效")
        api.deletePost(nodeId = postId)
    }
}
