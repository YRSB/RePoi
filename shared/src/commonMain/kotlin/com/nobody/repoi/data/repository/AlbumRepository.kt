package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.AlbumOrder
import com.nobody.repoi.data.remote.model.AlbumZoneTab
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.ZoneCategoryItem

data class AlbumPage(
    val items: List<AlbumListItem> = emptyList(),
    val hasMore: Boolean = false
)

class AlbumRepository(
    private val api: HpoiApi
) {
    suspend fun getZoneTabs(): Result<List<AlbumZoneTab>> = runCatching {
        val response = api.getZoneHomeTabs()
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

    /**
     * 相册列表。
     *
     * @param zones 原始 `zones` 参数（`null` / 空白 = 不传，即全站）。注意本参数**只能**由
     *   持有 `ZoneQueryScope` 的调用方经 `scope.zonesParam()` 派生：`All` 与空 `Follow` 都返回
     *   `null`，而「关注为空必须短路」是上层纪律（服务端把空串当全站）。首页/专区详情的板块路径
     *   统一走 `AlbumBoardLoader`（内部派生），不要在各调用点散拼 zone 参数。
     */
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
        val response = api.getAlbumList(
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

    /**
     * 相册/分享板块的分类选项（单专区专用）。
     *
     * 请求 `GET api/zone/categories?nodeId=&itemType=album`（反编译证据见
     * `data/remote/model/ZoneCategoryModels.kt` KDoc）；`data.visible=false` 表示服务端要求不展示
     * 分类入口，此时返回空列表；其余情况返回已过滤脏数据（`categoryId > 0` 且 `name` 非空），
     * 「全部」由 UI 自行追加，不在此返回。
     */
    suspend fun getAlbumCategories(nodeId: Long): Result<List<ZoneCategoryItem>> = runCatching {
        val response = api.getZoneCategories(nodeId = nodeId, itemType = "album")
        if (!response.isSuccess) throw ApiException(response.code, response.msg ?: "获取相册分类失败")
        if (response.data?.visible == false) return@runCatching emptyList()
        response.data?.categories.orEmpty().filter { it.categoryId > 0 && !it.name.isNullOrBlank() }
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
        val response = api.getPraiseTop(type = type, page = page, pageSize = pageSize)
        if (response.isSuccess) {
            val rawList = response.data?.list ?: emptyList()
            val items = rawList.filter { it.targetAlbumId > 0L }
            AlbumPage(items = items, hasMore = rawList.size >= pageSize)
        } else {
            throw ApiException(response.code, response.msg ?: "获取点赞榜失败")
        }
    }
}
