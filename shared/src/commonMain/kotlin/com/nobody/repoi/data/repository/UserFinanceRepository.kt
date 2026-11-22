package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.CategoryMoneyGroup
import com.nobody.repoi.data.remote.model.CurrencyItem
import com.nobody.repoi.data.remote.model.CurrencyType
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.OrderStaticData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

class UserFinanceRepository(
    private val api: HpoiApi,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
) {

    suspend fun getOrderStaticData(userId: Long): Result<OrderStaticData> = runCatching {
        api.getOrderStatic(userId = userId)
    }

    suspend fun getOrderStatic(userId: Long): Result<OrderCount> = runCatching {
        getOrderStaticData(userId).map { it.count ?: OrderCount() }.getOrThrow()
    }

    internal fun parseOrderFinance(orderPayload: OrderStaticData?): OrderFinanceSummary {
        if (orderPayload == null) return OrderFinanceSummary()
        val supplementedGroups = parseCategoryMoneyGroups(orderPayload.allPreorderMoney)
        val historyGroups = parseCategoryMoneyGroups(orderPayload.allOrderMoney)
        val supplementedTotalRmb = supplementedGroups.sumOf { it.totalRmb }
        val historyTotalRmb = historyGroups.sumOf { it.totalRmb }
        val amazonFluctuation = orderPayload.amazonMoney?.fluctuationText ?: "0%"

        return OrderFinanceSummary(
            supplementedTotalRmb = supplementedTotalRmb,
            historyTotalRmb = historyTotalRmb,
            amazonFluctuation = amazonFluctuation,
            supplementedGroups = supplementedGroups,
            historyGroups = historyGroups
        )
    }

    internal fun parseCategoryMoneyGroups(jsonElement: JsonElement?): List<CategoryMoneyGroup> {
        if (jsonElement == null) return emptyList()
        val array = when (jsonElement) {
            is JsonArray -> jsonElement
            is JsonPrimitive -> runCatching {
                json.parseToJsonElement(jsonElement.content) as? JsonArray
            }.getOrNull()
            else -> null
        } ?: return emptyList()

        val groups = mutableListOf<CategoryMoneyGroup>()
        for (element in array) {
            if (element !is JsonObject) continue
            val key = (element["key"] as? JsonPrimitive)?.content ?: ""
            val categoryName = mapCategoryName(key)
            val valElement = element["val"]
            val valArray = when (valElement) {
                is JsonArray -> valElement
                is JsonPrimitive -> runCatching {
                    json.parseToJsonElement(valElement.content) as? JsonArray
                }.getOrNull()
                else -> null
            }
            val items = mutableListOf<CurrencyItem>()
            if (valArray != null) {
                for (valElem in valArray) {
                    if (valElem !is JsonObject) continue
                    val currency = (valElem["currency"] as? JsonPrimitive)?.content?.trim() ?: "rmb"
                    val money = (valElem["money"] as? JsonPrimitive)?.doubleOrNull
                        ?: (valElem["money"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                        ?: 0.0
                    items.add(CurrencyItem(currency = currency, money = money))
                }
            } else if (element.containsKey("currency") && element.containsKey("money")) {
                val currency = (element["currency"] as? JsonPrimitive)?.content?.trim() ?: "rmb"
                val money = (element["money"] as? JsonPrimitive)?.doubleOrNull
                    ?: (element["money"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                    ?: 0.0
                items.add(CurrencyItem(currency = currency, money = money))
            }
            val groupTotalRmb = items.sumOf { it.money * getExchangeRate(it.currency) }
            groups.add(
                CategoryMoneyGroup(
                    categoryKey = key,
                    categoryName = categoryName,
                    items = items,
                    totalRmb = groupTotalRmb
                )
            )
        }
        return groups
    }

    fun mapCategoryName(key: String): String {
        return when (key.lowercase().trim()) {
            "category_100", "100", "category_figure", "figure", "手办" -> "手办"
            "category_200", "200", "category_model", "model", "动漫模型", "模型" -> "动漫模型"
            "category_300", "300", "category_doll", "doll", "doll娃娃", "娃娃" -> "Doll娃娃"
            "category_400", "400", "category_moppet", "moppet", "毛绒布偶", "布偶", "毛绒" -> "毛绒布偶"
            "category_500", "500", "category_real", "real", "真实模型", "周边" -> "真实模型"
            else -> key.ifBlank { "其他" }
        }
    }

    fun getExchangeRate(currency: String): Double {
        return CurrencyType.fromString(currency).exchangeRateToRmb
    }
}
