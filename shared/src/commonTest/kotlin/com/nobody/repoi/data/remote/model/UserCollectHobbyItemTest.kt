package com.nobody.repoi.data.remote.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserCollectHobbyItemTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun actualOrderMoney_prefersCollectionOrderMoney_andIgnoresOfficialMoney() {
        val item = UserCollectHobbyItem(
            money = 15000.0,
            currency = "yen",
            price = "15000日元",
            collection = UserCollectHobbyCollectionData(
                orderMoney = 680.0,
                orderMoneyCurrency = "rmb"
            )
        )
        assertEquals(680.0, item.actualOrderMoney)
        assertEquals("rmb", item.actualCurrency)
        assertEquals("¥680", item.formattedOrderMoneyText)
        assertEquals("15000日元", item.displayPriceText)
    }

    @Test
    fun actualOrderMoney_whenCollectionIsNull_returnsNull() {
        val item = UserCollectHobbyItem(
            money = 15000.0,
            currency = "yen",
            price = "15000日元",
            collection = null
        )
        assertNull(item.actualOrderMoney)
        assertNull(item.actualCurrency)
        assertNull(item.formattedOrderMoneyText)
        assertEquals("15000日元", item.displayPriceText)
    }

    @Test
    fun actualOrderMoney_whenCollectionOrderMoneyNonPositive_formattedTextReturnsNull() {
        val zeroItem = UserCollectHobbyItem(
            money = 15000.0,
            collection = UserCollectHobbyCollectionData(orderMoney = 0.0, orderMoneyCurrency = "rmb")
        )
        assertEquals(0.0, zeroItem.actualOrderMoney)
        assertNull(zeroItem.formattedOrderMoneyText)

        val negativeItem = UserCollectHobbyItem(
            money = 15000.0,
            collection = UserCollectHobbyCollectionData(orderMoney = -100.0, orderMoneyCurrency = "rmb")
        )
        assertEquals(-100.0, negativeItem.actualOrderMoney)
        assertNull(negativeItem.formattedOrderMoneyText)
    }

    @Test
    fun currencyType_parsesAllCurrenciesAndCalculatesFormattedText() {
        val usdItem = UserCollectHobbyItem(
            collection = UserCollectHobbyCollectionData(orderMoney = 120.0, orderMoneyCurrency = "dollar")
        )
        assertEquals("$120", usdItem.formattedOrderMoneyText)

        val eurItem = UserCollectHobbyItem(
            collection = UserCollectHobbyCollectionData(orderMoney = 85.0, orderMoneyCurrency = "euro")
        )
        assertEquals("€85", eurItem.formattedOrderMoneyText)

        val hkdItem = UserCollectHobbyItem(
            collection = UserCollectHobbyCollectionData(orderMoney = 250.0, orderMoneyCurrency = "hkd")
        )
        assertEquals("HK$250", hkdItem.formattedOrderMoneyText)

        val cnyItem = UserCollectHobbyItem(
            collection = UserCollectHobbyCollectionData(orderMoney = 399.0, orderMoneyCurrency = "rmb")
        )
        assertEquals("¥399", cnyItem.formattedOrderMoneyText)

        val jpyItem = UserCollectHobbyItem(
            collection = UserCollectHobbyCollectionData(orderMoney = 18000.0, orderMoneyCurrency = "yen")
        )
        assertEquals("JP¥18000", jpyItem.formattedOrderMoneyText)
    }

    @Test
    fun userCollectHobbyItem_parsesNestedCollectionAndComputesProperties() {
        val jsonStr = """{
            "id": 501,
            "itemId": 0,
            "itemNodeId": 601,
            "name": "Fallback Name",
            "nameCN": "优先中文名",
            "iconUrl": "icons/pic.jpg",
            "rating": 19.2,
            "ratingCount": 2,
            "collection": {
                "state": "preorder",
                "orderTime": "2026-05-20",
                "orderMoney": 16800.0,
                "orderMoneyCurrency": "yen"
            }
        }"""
        val item = json.decodeFromString<UserCollectHobbyItem>(jsonStr)
        assertEquals(501L, item.actualItemId)
        assertEquals("优先中文名", item.displayName)
        assertEquals("icons/pic.jpg", item.actualCover)
        assertEquals("preorder", item.actualCollectState)
        assertEquals("2026-05-20", item.actualOrderTime)
        assertEquals(16800.0, item.actualOrderMoney ?: 0.0, 0.001)
        assertEquals("yen", item.actualCurrency)
        assertEquals(9.6, item.averageRating, 0.001)
    }
}
