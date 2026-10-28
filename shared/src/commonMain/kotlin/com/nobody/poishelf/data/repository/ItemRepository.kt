package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.api.HpoiApi
import com.nobody.poishelf.data.remote.model.ApiException
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.nobody.poishelf.platform.DateTimeFormats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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

            val preOrderRes = preOrderDeferred.await()
            val shipRes = shipDeferred.await()

            val preOrderList = if (preOrderRes.isSuccess) {
                preOrderRes.data?.list ?: emptyList()
            } else {
                emptyList()
            }

            val shipList = if (shipRes.isSuccess) {
                shipRes.data?.list ?: emptyList()
            } else {
                emptyList()
            }

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
        val response = api.queryHobbyList(params)
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "查询列表失败")
        }
    }

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
