package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class ItemHomePreviewData(
    val preOrderItems: List<HobbyItemSummary> = emptyList(),
    val releaseItems: List<HobbyItemSummary> = emptyList()
)

class ItemRepository constructor(
    private val apiService: HpoiApi
) {
    /**
     * 并行拉取条目探索主页在指定分类下的两个预览区块：
     * 1. 热门预订 (order=hits7Day, releaseStart=今+2月, hasShopItem=1, pageSize=9)
     * 2. 热门出荷 (order=hits7Day, releaseStart=今-3月, releaseEnd=今天, hasShopItem=1, pageSize=9)
     */
    suspend fun getItemHomePreview(categoryId: Int): Result<ItemHomePreviewData> = runCatching {
        coroutineScope {
            val todayStr = todayString()
            val preOrderStartStr = plusMonthsString(2)
            val shipStartStr = plusMonthsString(-3)

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

            val preOrderDeferred = async { apiService.queryHobbyList(preOrderParams) }
            val shipDeferred = async { apiService.queryHobbyList(shipParams) }

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
        val response = apiService.queryHobbyList(params)
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "查询列表失败")
        }
    }

    /**
     * 计算热门预订的 releaseStart (今+2月)
     */
    fun calculatePreOrderStartDate(): String = plusMonthsString(2)

    /**
     * 计算热门出荷的 releaseStart (今-3月) 与 releaseEnd (今天)
     */
    fun calculateShipDateRange(): Pair<String, String> = Pair(plusMonthsString(-3), todayString())
}

/**
 * KMP 日期 helper：系统时区今日 "yyyy-MM-dd"。
 */
@OptIn(ExperimentalTime::class)
private fun todayString(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let {
    "${it.year}-${it.monthNumber.toString().padStart(2, '0')}-${it.day.toString().padStart(2, '0')}"
}

/**
 * KMP 日期 helper：今日按月偏移 [months] 后的 "yyyy-MM-dd"。
 *
 * 与 `Calendar.add(MONTH, n)` 等价：月份回绕进位，日溢出时钳制到目标月末
 * （如 1-31 +1月 → 2-28/29，而非 3-02/03）。
 */
@OptIn(ExperimentalTime::class)
private fun plusMonthsString(months: Int): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    // kotlinx-datetime 的 Date.plus(MONTH) 即“溢出钳制”语义，与 Calendar 一致。
    val shifted = now.plus(months, DateTimeUnit.MONTH)
    return "${shifted.year}-${shifted.monthNumber.toString().padStart(2, '0')}-${shifted.day.toString().padStart(2, '0')}"
}
