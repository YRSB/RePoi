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

class SeriesDetailRepository(
    private val api: HpoiApi,
    private val collectRepository: CollectRepository
) {

    suspend fun getSeriesDetail(itemId: Long): Result<SeriesData> = runCatching {
        api.getSeriesDetail(itemId = itemId, itemType = "series").itemData
            ?: throw ApiException(-1, "未找到该系列数据")
    }

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

    suspend fun getSeriesForum(seriesId: Long): Result<SeriesForumPayload?> = runCatching {
        api.getSeriesForum(seriesId = seriesId)
    }

    suspend fun getCollectState(nodeId: Long): Result<Boolean> =
        collectRepository.refreshCollectState(nodeId).map { it?.isCollected == true }

    suspend fun getMaskState(nodeId: Long): Result<Boolean> = runCatching {
        val jsonObject = api.getMaskState(nodeId) as? JsonObject
        val state = (jsonObject?.get("state") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        state == 1
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
}
