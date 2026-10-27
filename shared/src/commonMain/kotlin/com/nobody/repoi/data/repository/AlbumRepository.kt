package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.AlbumOrder
import com.nobody.repoi.data.remote.model.AlbumZoneTab
import com.nobody.repoi.data.remote.model.ApiException

data class AlbumPage(
    val items: List<AlbumListItem> = emptyList(),
    val hasMore: Boolean = false
)

class AlbumRepository constructor(
    private val apiService: HpoiApi
) {
    suspend fun getZoneTabs(): Result<List<AlbumZoneTab>> = runCatching {
        val response = apiService.getZoneHomeTabs()
        if (!response.isSuccess) throw ApiException(response.code, response.msg ?: "获取相册分区失败")
        val data = response.data ?: return@runCatching listOf(AlbumZoneTab.ALL)
        val tabs = mutableListOf<AlbumZoneTab>()
        if (data.settings?.showAll != false) tabs.add(AlbumZoneTab.ALL)
        val followIds = data.focusZones
            ?.map { it.nodeId }
            ?.filter { it > 0L }
            ?.joinToString(",")
            .orEmpty()
        tabs.add(AlbumZoneTab(key = "follow", name = "关注", zoneIds = followIds))
        data.homeZones
            ?.filter { (it.nodeId) > 0L }
            ?.forEach { zone ->
                val name = zone.nameCN?.ifBlank { null } ?: zone.name ?: return@forEach
                tabs.add(AlbumZoneTab(key = "z${zone.nodeId}", name = name, zoneIds = zone.nodeId.toString()))
            }
        tabs
    }.recoverCatching { error ->
        if ((error as? ApiException)?.code == 4001) throw error
        listOf(AlbumZoneTab.ALL)
    }

    suspend fun getAlbums(
        page: Int = 1,
        pageSize: Int = 24,
        order: AlbumOrder = AlbumOrder.Recommend,
        zones: String? = null,
        category: Int? = null,
        releaseStart: String? = null,
        releaseEnd: String? = null,
        orderString: String? = null
    ): Result<AlbumPage> = runCatching {
        val finalOrder = orderString?.ifBlank { null } ?: order.apiValue
        val response = apiService.getAlbumList(
            page = page,
            pageSize = pageSize,
            order = finalOrder,
            zones = zones?.ifBlank { null },
            category = category,
            releaseStart = releaseStart,
            releaseEnd = releaseEnd
        )
        if (response.isSuccess) {
            val rawList = response.data?.list ?: emptyList()
            val items = rawList.filter { it.targetAlbumId > 0L }
            AlbumPage(items = items, hasMore = rawList.size >= pageSize)
        } else {
            throw ApiException(response.code, response.msg ?: "获取相册列表失败")
        }
    }

    suspend fun getRankingWindow(
        releaseStart: String,
        releaseEnd: String,
        pageSize: Int = 5
    ): Result<List<AlbumListItem>> = runCatching {
        getAlbums(
            page = 1,
            pageSize = pageSize,
            order = AlbumOrder.Praise,
            releaseStart = releaseStart,
            releaseEnd = releaseEnd
        ).getOrThrow().items
    }

    suspend fun getPraiseTop(
        type: String = "month",
        page: Int = 1,
        pageSize: Int = 10
    ): Result<AlbumPage> = runCatching {
        val response = apiService.getPraiseTop(type = type, page = page, pageSize = pageSize)
        if (response.isSuccess) {
            val rawList = response.data?.list ?: emptyList()
            val items = rawList.filter { it.targetAlbumId > 0L }
            AlbumPage(items = items, hasMore = rawList.size >= pageSize)
        } else {
            throw ApiException(response.code, response.msg ?: "获取点赞榜失败")
        }
    }
}
