package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.AmazonMoneyData
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
            """{"success":true,"code":200,"data":{"data":{"count":{"preorder":5,"buy":12,"care":17}}}}"""
        )

        val result = repository.getOrderStaticData(12345L)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(5, data.count?.preorder)
        assertEquals(12, data.count?.buy)
        assertEquals(17, data.count?.care)
    }

    @Test
    fun getOrderStatic_success() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"data":{"count":{"preorder":3,"buy":8,"care":11}}}}"""
        )

        val result = repository.getOrderStatic(12345L)
        assertTrue(result.isSuccess)
        val count = result.getOrThrow()
        assertEquals(3, count.preorder)
        assertEquals(8, count.buy)
        assertEquals(11, count.care)
    }

    @Test
    fun parseOrderFinance_computesAllCurrenciesAndFluctuation() {
        val repository = UserFinanceRepository(MockApi().api())
        val preorderJson = buildJsonArray {
            add(buildJsonObject {
                put("key", "category_100")
                put("val", buildJsonArray {
                    add(buildJsonObject {
                        put("currency", "yen")
                        put("money", 10000.0)
                    })
                    add(buildJsonObject {
                        put("currency", "dollar")
                        put("money", 100.0)
                    })
                })
            })
            add(buildJsonObject {
                put("key", "category_200")
                put("val", buildJsonArray {
                    add(buildJsonObject {
                        put("currency", "euro")
                        put("money", 50.0)
                    })
                    add(buildJsonObject {
                        put("currency", "hkd")
                        put("money", 200.0)
                    })
                    add(buildJsonObject {
                        put("currency", "rmb")
                        put("money", 1000.0)
                    })
                })
            })
        }
        val orderJson = buildJsonArray {
            add(buildJsonObject {
                put("key", "category_300")
                put("val", buildJsonArray {
                    add(buildJsonObject {
                        put("currency", "cny")
                        put("money", 888.0)
                    })
                })
            })
        }
        val orderData = OrderStaticData(
            count = OrderCount(buy = 1, preorder = 2, care = 3),
            allPreorderMoney = preorderJson,
            allOrderMoney = orderJson,
            amazonMoney = AmazonMoneyData(initialPrice = 1000, price = 1250)
        )

        val finance = repository.parseOrderFinance(orderData)

        // 待补款：手办 JPY 10000 * 0.06 = 600 + USD 100 * 6.4 = 640 => 1240
        // 动漫模型 EUR 50 * 6.9 = 345 + HKD 200 * 0.92 = 184 + RMB 1000 * 1.0 = 1000 => 1529
        assertEquals(2, finance.supplementedGroups.size)
        assertEquals("手办", finance.supplementedGroups[0].categoryName)
        assertEquals(1240.0, finance.supplementedGroups[0].totalRmb, 0.001)
        assertEquals("动漫模型", finance.supplementedGroups[1].categoryName)
        assertEquals(1529.0, finance.supplementedGroups[1].totalRmb, 0.001)
        assertEquals(2769.0, finance.supplementedTotalRmb, 0.001)

        // 已入总额：CNY 888 * 1.0 = 888
        assertEquals(1, finance.historyGroups.size)
        assertEquals("Doll娃娃", finance.historyGroups[0].categoryName)
        assertEquals(888.0, finance.historyTotalRmb, 0.001)

        // 亚马逊涨跌幅：(1250 - 1000) * 100 / 1000 = +25%
        assertEquals("+25%", finance.amazonFluctuation)
    }

    @Test
    fun parseOrderFinance_handlesEdgeCasesAndNullSafely() {
        val repository = UserFinanceRepository(MockApi().api())

        val emptyFinance = repository.parseOrderFinance(null)
        assertEquals(0.0, emptyFinance.supplementedTotalRmb, 0.001)
        assertEquals(0.0, emptyFinance.historyTotalRmb, 0.001)
        assertEquals("0%", emptyFinance.amazonFluctuation)
        assertTrue(emptyFinance.supplementedGroups.isEmpty())
        assertTrue(emptyFinance.historyGroups.isEmpty())

        assertEquals("-15%", AmazonMoneyData(initialPrice = 1000, price = 850).fluctuationText)
        assertEquals("0%", AmazonMoneyData(initialPrice = 1000, price = 1000).fluctuationText)
        assertEquals("0%", AmazonMoneyData(initialPrice = 0, price = 500).fluctuationText)
        assertEquals("0%", AmazonMoneyData(initialPrice = null, price = 500).fluctuationText)
    }

    @Test
    fun parseCategoryMoneyGroups_supportsStringifiedJsonAndAliases() {
        val repository = UserFinanceRepository(MockApi().api())
        val stringifiedJson = buildJsonArray {
            add(buildJsonObject {
                put("key", "category_figure")
                put("val", "[{\"currency\":\" JPY \",\"money\":\"1000\"},{\"currency\":\"港币\",\"money\":50}]")
            })
        }

        val groups = repository.parseCategoryMoneyGroups(stringifiedJson)

        assertEquals(1, groups.size)
        assertEquals("手办", groups[0].categoryName)
        assertEquals(2, groups[0].items.size)
        assertEquals("日元", groups[0].items[0].displayName)
        assertEquals("JP¥", groups[0].items[0].symbol)
        assertEquals("港元", groups[0].items[1].displayName)
        assertEquals("HK$", groups[0].items[1].symbol)
        // 1000 * 0.06 = 60 + 50 * 0.92 = 46 => 106
        assertEquals(106.0, groups[0].totalRmb, 0.001)
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
                        put("money", 10000.0) // 10000 JPY * 0.06 = 600 RMB
                    })
                })
            })
        }

        val groups = repository.parseCategoryMoneyGroups(jsonArray)
        assertEquals(1, groups.size)
        val figureGroup = groups.first()
        assertEquals("手办", figureGroup.categoryName)
        assertEquals(2, figureGroup.items.size)
        // 100 RMB + 10000 JPY (600 RMB) = 700 RMB
        assertEquals(700.0, figureGroup.totalRmb, 0.01)
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
