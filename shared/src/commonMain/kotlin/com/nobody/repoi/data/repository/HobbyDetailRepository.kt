package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.model.HobbyAlbumItem
import com.nobody.repoi.data.remote.model.HobbyDetailData
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.HobbyTimelineItem
import com.nobody.repoi.data.remote.model.InfoListEntry
import com.nobody.repoi.data.remote.model.PicItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 手办条目详情多端点聚合数据仓储。
 */
class HobbyDetailRepository(
    private val api: HpoiApi
) {

    /**
     * 获取手办基础详情数据 (api/item/get-v2)。
     */
    suspend fun getHobbyDetail(itemId: Long, itemType: String): Result<HobbyDetailData> = runCatching {
        api.getHobbyDetail(itemId = itemId, itemType = itemType).itemData
            ?: throw ApiException(-1, "未找到手办详情数据")
    }

    /**
     * 获取手办结构化属性参数列表 (api/item/infoList)。
     */
    suspend fun getInfoList(nodeId: Long, itemType: String): Result<List<InfoListEntry>> = runCatching {
        api.getInfoList(nodeId = nodeId, itemType = itemType).list.orEmpty()
    }

    /**
     * 获取手办发售情报时间轴 (api/user-action/item-info/list)。
     */
    suspend fun getTimeline(nodeId: Long, page: Int = 1, pageSize: Int = 20): Result<List<HobbyTimelineItem>> = runCatching {
        api.getTimeline(nodeId = nodeId, page = page, pageSize = pageSize).list.orEmpty()
    }

    /**
     * 获取官方宣发图片/定妆照 (api/pic/list/relate-v2, type=official)。
     */
    suspend fun getOfficialPics(itemId: Long, itemType: String): Result<List<PicItem>> = runCatching {
        val list = api.getPicList(
            itemId = itemId,
            itemType = itemType,
            picSubType = "official",
            page = 1,
            pageSize = 30
        ).list.orEmpty()
        // 过滤并优先保留官方定妆照
        val officialOnly = list.filter { it.subType == null || it.subType == "official" }
        if (officialOnly.isNotEmpty()) officialOnly else list
    }

    /**
     * 获取玩家摄影相册列表 (api/hobby/album)。
     */
    suspend fun getUserAlbums(
        nodeId: Long,
        itemType: String,
        page: Int = 1,
        pageSize: Int = 9,
        order: String = "hits"
    ): Result<List<HobbyAlbumItem>> = runCatching {
        api.getHobbyAlbum(
            nodeId = nodeId,
            itemType = itemType,
            page = page,
            pageSize = pageSize,
            order = order
        ).list.orEmpty()
    }

    /**
     * 获取评论列表 (api/comment/get)。
     * @param type 为 null 时拉取最新评论，"hot_top" 拉取热门神评。
     */
    suspend fun getComments(
        nodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<CommentItem>> = runCatching {
        api.getComments(
            nodeId = nodeId,
            type = type,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }.recoverCatching { error ->
        if (error is CommentNotFoundException) emptyList() else throw error
    }

    /**
     * 获取相关推荐手办列表（聚合同款/再版分组 api/hobby/group 与全局热门推荐 api/hobby/push/list/V2）。
     *
     * @param itemNodeId 当前手办节点 ID（用于分组接口查询与排除自身）
     * @param currentItemId 当前手办条目业务 ID（用于排除自身）
     */
    suspend fun getRelatedHobbies(itemNodeId: Long, currentItemId: Long = 0L): Result<List<HobbyItemSummary>> = runCatching {
        coroutineScope {
            val groupDeferred = async {
                runCatching { api.getHobbyGroup(id = itemNodeId) }
            }
            val pushDeferred = async {
                runCatching { api.getHobbyPushList(page = 1, pageSize = 10) }
            }

            val groupResult = groupDeferred.await()
            val pushResult = pushDeferred.await()

            // 若两个接口均异常失败，抛出 ApiException 以维持标准契约
            if (groupResult.isFailure && pushResult.isFailure) {
                val groupEx = groupResult.exceptionOrNull()
                val pushEx = pushResult.exceptionOrNull()
                throw ApiException(
                    code = -1,
                    message = "获取相关手办失败: ${groupEx?.message ?: pushEx?.message ?: "未知网络错误"}"
                )
            }

            val groupItems = groupResult.getOrNull()?.groups.orEmpty().flatMap { group ->
                group.items?.map { item -> item.copy(typeName = group.typeName) }.orEmpty()
            }

            val pushItems = pushResult.getOrNull()?.hobbys.orEmpty()

            val excludedIds = buildSet {
                if (itemNodeId > 0L) add(itemNodeId)
                if (currentItemId > 0L) add(currentItemId)
            }
            val seenIds = HashSet(excludedIds)
            val result = mutableListOf<HobbyItemSummary>()

            for (item in groupItems + pushItems) {
                val actualId = item.actualItemId
                val isSelf = actualId in excludedIds || item.id in excludedIds ||
                    item.itemNodeId in excludedIds || item.itemId in excludedIds
                if (!isSelf && actualId > 0L && seenIds.add(actualId)) {
                    result.add(item)
                }
            }

            result.take(15)
        }
    }
}
