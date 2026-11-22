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

    @OptIn(ExperimentalTime::class)
    suspend fun getItemHomePreview(categoryId: Int): Result<ItemHomePreviewData> = runCatching {
        coroutineScope {
            val todayStr = DateTimeFormats.todayYmd()
            val preOrderStartStr = shiftMonths(2)
            val shipStartStr = shiftMonths(-3)

            val preOrderParams = mutableMapOf(
                "order" to "hits7Day",
                "page" to "1",
                "pageSize" to "9",
                "category" to categoryId.toString(),
                "hasShopItem" to "1",
                "releaseStart" to preOrderStartStr
            )

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

    suspend fun queryHobbyList(params: Map<String, String>): Result<List<HobbyItemSummary>> = runCatching {
        api.queryHobbyList(params).list.orEmpty()
    }

    suspend fun queryHobbyBoard(
        scope: ZoneQueryScope,
        page: Int = 1,
        pageSize: Int = HOBBY_BOARD_PAGE_SIZE,
        filters: ZoneHobbyFilter = ZoneHobbyFilter()
    ): Result<List<HobbyItemSummary>> = queryHobbyList(
        buildHobbyBoardParams(scope = scope, page = page, pageSize = pageSize, filters = filters)
    )

    fun calculatePreOrderStartDate(): String = shiftMonths(2)

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
