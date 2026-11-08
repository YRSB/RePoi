package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderStaticData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserFinanceRepositoryTest {

    private fun setup(): Pair<MockApi, UserFinanceRepository> {
        val mock = MockApi()
        return mock to UserFinanceRepository(mock.api())
    }

    @Test
    fun getOrderStaticData_success() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"count":{"preorderCount":5,"buyCount":12,"totalCount":17}}}"""
        )

        val result = repository.getOrderStaticData(12345L)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(5, data.count?.preorderCount)
        assertEquals(12, data.count?.buyCount)
        assertEquals(17, data.count?.totalCount)
    }

    @Test
    fun getOrderStatic_success() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"count":{"preorderCount":3,"buyCount":8,"totalCount":11}}}"""
        )

        val result = repository.getOrderStatic(12345L)
        assertTrue(result.isSuccess)
        val count = result.getOrThrow()
        assertEquals(3, count.preorderCount)
        assertEquals(8, count.buyCount)
        assertEquals(11, count.totalCount)
    }

    @Test
    fun parseOrderFinance_nullPayload_returnsDefaults() {
        val repository = UserFinanceRepository(MockApi().api())
        val summary = repository.parseOrderFinance(null)
        assertEquals(0.0, summary.supplementedTotalRmb)
        assertEquals(0.0, summary.historyTotalRmb)
        assertEquals("0%", summary.amazonFluctuation)
        assertTrue(summary.supplementedGroups.isEmpty())
        assertTrue(summary.historyGroups.isEmpty())
    }

    @Test
    fun parseCategoryMoneyGroups_calculatesExchangeRatesProperly() {
        val repository = UserFinanceRepository(MockApi().api())
        val jsonArray = buildJsonArray {
            add(buildJsonObject {
                put("key", "category_figure")
                put("val", buildJsonArray {
                    add(buildJsonObject {
                        put("currency", "rmb")
                        put("money", 100.0)
                    })
                    add(buildJsonObject {
                        put("currency", "jpy")
                        put("money", 10000.0) // 10000 * 0.05 = 500
                    })
                })
            })
        }

        val groups = repository.parseCategoryMoneyGroups(jsonArray)
        assertEquals(1, groups.size)
        val figureGroup = groups.first()
        assertEquals("手办", figureGroup.categoryName)
        assertEquals(2, figureGroup.items.size)
        // 100 RMB + 10000 JPY (500 RMB) = 600 RMB
        assertEquals(600.0, figureGroup.totalRmb, 0.01)
    }

    @Test
    fun mapCategoryName_convertsKnownKeys() {
        val repository = UserFinanceRepository(MockApi().api())
        assertEquals("手办", repository.mapCategoryName("category_100"))
        assertEquals("手办", repository.mapCategoryName("figure"))
        assertEquals("动漫模型", repository.mapCategoryName("category_200"))
        assertEquals("Doll娃娃", repository.mapCategoryName("doll"))
        assertEquals("毛绒布偶", repository.mapCategoryName("category_400"))
        assertEquals("真实模型", repository.mapCategoryName("周边"))
        assertEquals("自定义", repository.mapCategoryName("自定义"))
    }
}
