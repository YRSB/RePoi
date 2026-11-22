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

class HobbyDetailRepository(
    private val api: HpoiApi
) {

    suspend fun getHobbyDetail(itemId: Long, itemType: String): Result<HobbyDetailData> = runCatching {
        api.getHobbyDetail(itemId = itemId, itemType = itemType).itemData
            ?: throw ApiException(-1, "未找到手办详情数据")
    }

    suspend fun getInfoList(nodeId: Long, itemType: String): Result<List<InfoListEntry>> = runCatching {
        api.getInfoList(nodeId = nodeId, itemType = itemType).list.orEmpty()
    }

    suspend fun getTimeline(nodeId: Long, page: Int = 1, pageSize: Int = 20): Result<List<HobbyTimelineItem>> = runCatching {
        api.getTimeline(nodeId = nodeId, page = page, pageSize = pageSize).list.orEmpty()
    }

    suspend fun getOfficialPics(itemId: Long, itemType: String): Result<List<PicItem>> = runCatching {
        val list = api.getPicList(
            itemId = itemId,
            itemType = itemType,
            picSubType = "official",
            page = 1,
            pageSize = 30
        ).list.orEmpty()

        val officialOnly = list.filter { it.subType == null || it.subType == "official" }
        if (officialOnly.isNotEmpty()) officialOnly else list
    }

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
