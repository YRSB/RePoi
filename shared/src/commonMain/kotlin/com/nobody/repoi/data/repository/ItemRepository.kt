package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.HOBBY_BOARD_PAGE_SIZE
import com.nobody.repoi.data.remote.model.HOBBY_R18_UNLIMITED
import com.nobody.repoi.data.remote.model.HOBBY_SCALE_UNLIMITED
import com.nobody.repoi.data.remote.model.HOBBY_SEX_UNLIMITED
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.ZoneHobbyFilter
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.nobody.repoi.platform.DateTimeFormats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class ItemHomePreviewData(
    val preOrderItems: List<HobbyItemSummary> = emptyList(),
    val releaseItems: List<HobbyItemSummary> = emptyList()
)

class ItemRepository(
    private val api: HpoiApi,
) {
    /**
     * 并行拉取条目探索主页在指定分类下的两个预览区块：
     * 1. 热门预订 (order=hits7Day, releaseStart=今+2月, hasShopItem=1, pageSize=9)
     * 2. 热门出荷 (order=hits7Day, releaseStart=今-3月, releaseEnd=今天, hasShopItem=1, pageSize=9)
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getItemHomePreview(categoryId: Int): Result<ItemHomePreviewData> = runCatching {
        coroutineScope {
            val todayStr = DateTimeFormats.todayYmd()
            val preOrderStartStr = shiftMonths(2)
            val shipStartStr = shiftMonths(-3)

            // 热门预订参数
            val preOrderParams = mutableMapOf(
                "order" to "hits7Day",
                "page" to "1",
                "pageSize" to "9",
                "category" to categoryId.toString(),
                "hasShopItem" to "1",
                "releaseStart" to preOrderStartStr
            )

            // 热门出荷参数
            val shipParams = mutableMapOf(
                "order" to "hits7Day",
                "page" to "1",
                "pageSize" to "9",
                "category" to categoryId.toString(),
                "hasShopItem" to "1",
                "releaseStart" to shipStartStr,
                "releaseEnd" to todayStr
            )

            val preOrderDeferred = async { api.queryHobbyList(preOrderParams) }
            val shipDeferred = async { api.queryHobbyList(shipParams) }

            val preOrderList = preOrderDeferred.await().list.orEmpty()
            val shipList = shipDeferred.await().list.orEmpty()

            ItemHomePreviewData(
                preOrderItems = preOrderList,
                releaseItems = shipList
            )
        }
    }

    /**
     * 独立列表多维检索查询
     */
    suspend fun queryHobbyList(params: Map<String, String>): Result<List<HobbyItemSummary>> = runCatching {
        api.queryHobbyList(params).list.orEmpty()
    }

    /**
     * 按查询作用域取产品板块列表（首页聚合 / 单专区共用，issue #159）。
     *
     * 参数拼装唯一入口：[buildHobbyBoardParams] 保证三处调用点（首页 / 专区详情 / 后续板块）逐字同参：
     * - 单专区：`zones=<nodeId>`，**绝不传 `homeScope`**（非空 `homeScope` 会覆盖 `zones` 导致变全站）；
     * - 全部聚合：省略 `zones`（等价全站，不必传 `homeScope=all`）；
     * - 关注聚合：产品板块不可用（`zones` 逗号串是交集语义、`homeScope=follow` 待登录态复验），
     *   调用方必须按 `homeBoardsFor(Follow)` 隐藏该板块并短路，不得走到这里。
     */
    suspend fun queryHobbyBoard(
        scope: ZoneQueryScope,
        page: Int = 1,
        pageSize: Int = HOBBY_BOARD_PAGE_SIZE,
        filters: ZoneHobbyFilter = ZoneHobbyFilter()
    ): Result<List<HobbyItemSummary>> = queryHobbyList(
        buildHobbyBoardParams(scope = scope, page = page, pageSize = pageSize, filters = filters)
    )

    /**
     * 计算热门预订的 releaseStart (今+2月)
     */
    fun calculatePreOrderStartDate(): String = shiftMonths(2)

    /**
     * 计算热门出荷的 releaseStart (今-3月) 与 releaseEnd (今天)
     */
    fun calculateShipDateRange(): Pair<String, String> = Pair(shiftMonths(-3), DateTimeFormats.todayYmd())

    private fun shiftMonths(months: Int): String {
        @OptIn(ExperimentalTime::class)
        val shifted = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(DatePeriod(months = months))
        return "${shifted.year}-${shifted.month.number.toString().padStart(2, '0')}-${
            shifted.day.toString().padStart(2, '0')
        }"
    }
}

/**
 * 产品板块请求参数拼装（`POST api/hobby/query-v2` 表单）。
 *
 * 与专区详情历史实现逐字一致（参数集合与插入顺序都不变），并在其上收敛作用域：
 * 仅写入 `scope.zonesParam()`，**不写 `homeScope`**（见 `docs/research/aggregate-api-verification.md`
 * §2.2/§4.3：产品接口只要 `homeScope` 非空就会忽略 `zones`）。
 */
internal fun buildHobbyBoardParams(
    scope: ZoneQueryScope,
    page: Int,
    pageSize: Int,
    filters: ZoneHobbyFilter
): MutableMap<String, String> {
    val params = mutableMapOf<String, String>()
    params["page"] = page.toString()
    params["pageSize"] = pageSize.toString()
    params["category"] = (filters.subCategoryId ?: filters.categoryId).toString()
    params["order"] = filters.order
    scope.zonesParam()?.let { params["zones"] = it }

    filters.releaseYear?.let { if (it > 0) params["releaseYear"] = it.toString() }
    filters.releaseMonth?.let { if (it > 0) params["releaseMonth"] = it.toString() }
    if (filters.keyword.isNotBlank()) params["keyword"] = filters.keyword.trim()
    filters.sex?.let { if (it != HOBBY_SEX_UNLIMITED) params["sex"] = it.toString() }
    filters.specs?.let { if (it.isNotBlank()) params["specs"] = it }
    filters.scale?.let { if (it.isNotBlank() && it != HOBBY_SCALE_UNLIMITED) params["scale"] = it }
    filters.r18?.let { if (it != HOBBY_R18_UNLIMITED) params["r18"] = it.toString() }
    return params
}
