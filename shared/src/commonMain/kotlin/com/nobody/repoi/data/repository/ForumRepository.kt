package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.ForumDetailData
import com.nobody.repoi.data.remote.model.ForumItem
import com.nobody.repoi.data.remote.model.PostItem

/**
 * 讨论板版块数据仓储。
 * 提供已关注/未关注版块列表、关注取关、排序保存、版块详情及版块帖子流查询能力。
 */
class ForumRepository(
    private val api: HpoiApi,
) {

    /**
     * 获取已关注版块列表 (api/forum/focus/list)。
     * @param forumPage 分页页码，从 1 开始
     */
    suspend fun getFocusForums(forumPage: Int = 1): Result<List<ForumItem>> = runCatching {
        api.getFocusForumList(forumPage = forumPage)?.forumList ?: emptyList()
    }

    /**
     * 获取未关注版块列表 (api/forum/not/focus/list)。
     * @param forumPage 分页页码，从 1 开始
     */
    suspend fun getUnfocusForums(forumPage: Int = 1): Result<List<ForumItem>> = runCatching {
        api.getUnfocusForumList(forumPage = forumPage)?.forumList ?: emptyList()
    }

    /**
     * 关注或取关指定版块 (api/forum/focus)。
     * @param forumNodeId 版块节点 ID
     * @param follow true=关注，false=取关
     */
    suspend fun toggleFocus(forumNodeId: Long, follow: Boolean): Result<Unit> = runCatching {
        if (forumNodeId <= 0L) throw ApiException(400, "版块ID无效")
        api.toggleForumFocus(
            forumNodeId = forumNodeId,
            state = if (follow) 1 else 0
        )
    }

    /**
     * 保存已关注版块排序 (api/forum/focus/sort/upd)。
     * @param orderedIds 按展示顺序排列的版块 id 列表
     */
    suspend fun updateFocusOrder(orderedIds: List<Long>): Result<Unit> = runCatching {
        api.updateForumFocusOrder(order = orderedIds.joinToString(","))
    }

    /**
     * 获取版块详情 (api/forum/{id})，用于讨论板 forum Tab 顶部版块头卡。
     * @param forumId 版块节点 ID
     */
    suspend fun getForumDetail(forumId: Long): Result<ForumDetailData?> = runCatching {
        if (forumId <= 0L) throw ApiException(400, "版块ID无效")
        api.getForumDetail(forumId = forumId)?.forum
    }

    /**
     * 查询讨论板帖子流 (api/posts/search)。
     * @param forumNodeId 版块过滤：0=综合，-1=关注，大于 0=指定版块
     * @param order 排序：updTime(回复) / addTime(新帖) / hits(推荐)
     * @param keyword 关键词过滤（站内搜索）
     * @param itemType 关联条目类型过滤：""(帖子) / hobby / works / works_element / company / person
     */
    suspend fun getForumPosts(
        forumNodeId: Long,
        order: String = "updTime",
        keyword: String? = null,
        itemType: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<PostItem>> = runCatching {
        api.searchPosts(
            forumNodeId = forumNodeId,
            order = order,
            keyword = keyword?.ifBlank { null },
            itemType = itemType?.ifBlank { null },
            page = page,
            pageSize = pageSize
        ).list
    }
}
