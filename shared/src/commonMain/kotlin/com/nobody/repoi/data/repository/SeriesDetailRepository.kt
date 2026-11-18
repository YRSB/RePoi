package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.model.RelatedHobbyItem
import com.nobody.repoi.data.remote.model.SeriesData
import com.nobody.repoi.data.remote.model.SeriesForumPayload
import com.nobody.repoi.data.remote.model.SeriesGoodsItem
import com.nobody.repoi.data.remote.model.SeriesLinkItem
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
        api.getSeriesDetail(itemId = itemId, itemType = "series").itemData
            ?: throw ApiException(-1, "未找到该系列数据")
    }

    /**
     * 获取系列关联周边手办 (api/item/relate/hobby)。
     */
    suspend fun getSeriesHobbys(
        nodeId: Long,
        pageSize: Int = 9
    ): Result<List<RelatedHobbyItem>> = runCatching {
        api.getSeriesHobbys(
            id = nodeId,
            itemType = "series",
            pageSize = pageSize
        ).hobbys.orEmpty()
    }

    /**
     * 获取子系列列表 (api/series/link/series?isChild=1)。
     */
    suspend fun getSeriesSubSeries(
        seriesId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): Result<List<SeriesLinkItem>> = runCatching {
        api.getSeriesLinkSeries(
            seriesId = seriesId,
            isChild = 1,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }

    /**
     * 获取相关系列列表 (api/series/link/series?isChild=2)。
     */
    suspend fun getSeriesRelateSeries(
        seriesId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): Result<List<SeriesLinkItem>> = runCatching {
        api.getSeriesLinkSeries(
            seriesId = seriesId,
            isChild = 2,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }

    /**
     * 获取系列关联相册列表 (api/series/album)。
     */
    suspend fun getSeriesAlbums(
        seriesId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): Result<List<AlbumData>> = runCatching {
        api.getSeriesAlbums(
            seriesId = seriesId,
            page = page,
            pageSize = pageSize
        ).album.orEmpty()
    }

    /**
     * 获取系列在售商品推荐列表 (api/goods/tb/more-v2)。
     */
    suspend fun getSeriesGoods(
        nodeId: Long,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<SeriesGoodsItem>> = runCatching {
        api.getSeriesGoods(
            nodeId = nodeId,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }

    /**
     * 获取系列论坛与讨论帖 (api/series/forum)。
     */
    suspend fun getSeriesForum(seriesId: Long): Result<SeriesForumPayload?> = runCatching {
        api.getSeriesForum(seriesId = seriesId)
    }

    /**
     * 查询系列收藏状态 (api/collect/state)。
     * 未收藏返回 false，失败时透出由调用方兜底。
     */
    suspend fun getCollectState(nodeId: Long): Result<Boolean> =
        collectRepository.refreshCollectState(nodeId).map { it?.isCollected == true }

    /**
     * 查询系列屏蔽状态 (api/mask/state-v2)。
     * state == 1 表示已屏蔽，若未登录或未屏蔽安全返回 false。
     */
    suspend fun getMaskState(nodeId: Long): Result<Boolean> = runCatching {
        val jsonObject = api.getMaskState(nodeId) as? JsonObject
        val state = (jsonObject?.get("state") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        state == 1
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
        api.getComments(
            nodeId = nodeId,
            type = type,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }.recoverCatching { error ->
        if (error is CommentNotFoundException) emptyList() else throw error
    }
}
