package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.api.HpoiApi
import com.nobody.poishelf.data.remote.model.AlbumData
import com.nobody.poishelf.data.remote.model.ApiException
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.RelatedHobbyItem
import com.nobody.poishelf.data.remote.model.SeriesData
import com.nobody.poishelf.data.remote.model.SeriesForumPayload
import com.nobody.poishelf.data.remote.model.SeriesGoodsItem
import com.nobody.poishelf.data.remote.model.SeriesLinkItem
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 系列详情多端点聚合数据仓储。
 */
class SeriesDetailRepository(
    private val api: HpoiApi,
    private val collectRepository: CollectRepository
) {

    /**
     * 获取系列主体详情 (api/item/get-v2?itemId=...&itemType=series)。
     */
    suspend fun getSeriesDetail(itemId: Long): Result<SeriesData> = runCatching {
        val response = api.getSeriesDetail(itemId = itemId, itemType = "series")
        if (response.isSuccess) {
            response.data?.itemData ?: throw ApiException(response.code, "未找到该系列数据")
        } else {
            throw ApiException(response.code, response.msg ?: "获取系列详情失败")
        }
    }

    /**
     * 获取系列关联周边手办 (api/item/relate/hobby)。
     */
    suspend fun getSeriesHobbys(
        nodeId: Long,
        pageSize: Int = 9
    ): Result<List<RelatedHobbyItem>> = runCatching {
        val response = api.getSeriesHobbys(
            id = nodeId,
            itemType = "series",
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.hobbys ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取子系列列表 (api/series/link/series?isChild=1)。
     */
    suspend fun getSeriesSubSeries(
        seriesId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): Result<List<SeriesLinkItem>> = runCatching {
        val response = api.getSeriesLinkSeries(
            seriesId = seriesId,
            isChild = 1,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取相关系列列表 (api/series/link/series?isChild=2)。
     */
    suspend fun getSeriesRelateSeries(
        seriesId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): Result<List<SeriesLinkItem>> = runCatching {
        val response = api.getSeriesLinkSeries(
            seriesId = seriesId,
            isChild = 2,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取系列关联相册列表 (api/series/album)。
     */
    suspend fun getSeriesAlbums(
        seriesId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): Result<List<AlbumData>> = runCatching {
        val response = api.getSeriesAlbums(
            seriesId = seriesId,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.album ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取系列在售商品推荐列表 (api/goods/tb/more-v2)。
     */
    suspend fun getSeriesGoods(
        nodeId: Long,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<SeriesGoodsItem>> = runCatching {
        val response = api.getSeriesGoods(
            nodeId = nodeId,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取系列论坛与讨论帖 (api/series/forum)。
     */
    suspend fun getSeriesForum(seriesId: Long): Result<SeriesForumPayload?> = runCatching {
        val response = api.getSeriesForum(seriesId = seriesId)
        if (response.isSuccess) {
            response.data
        } else {
            null
        }
    }

    /**
     * 查询系列收藏状态 (api/collect/state)。
     * 未收藏返回 false，失败时透出由调用方兜底。
     */
    suspend fun getCollectState(nodeId: Long): Result<Boolean> =
        collectRepository.getCollectState(nodeId).map { it?.isCollected == true }

    /**
     * 查询系列屏蔽状态 (api/mask/state-v2)。
     * state == 1 表示已屏蔽，若未登录或未屏蔽安全返回 false。
     */
    suspend fun getMaskState(nodeId: Long): Result<Boolean> = runCatching {
        val response = api.getMaskState(nodeId)
        if (response.isSuccess) {
            val jsonObject = response.data as? JsonObject
            val state = (jsonObject?.get("state") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            state == 1
        } else {
            false
        }
    }

    /**
     * 获取评论列表 (api/comment/get)。
     */
    suspend fun getComments(
        nodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<CommentItem>> = runCatching {
        val response = api.getComments(
            nodeId = nodeId,
            type = type,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else if (response.isCommentNotFound) {
            emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取评论失败")
        }
    }
}
