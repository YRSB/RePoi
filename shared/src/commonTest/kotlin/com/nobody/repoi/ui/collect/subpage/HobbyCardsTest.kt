package com.nobody.repoi.ui.collect.subpage

import com.nobody.repoi.data.remote.model.UserCollectHobbyCollectionData
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import kotlin.test.Test
import kotlin.test.assertEquals

class HobbyCardsTest {

    @Test
    fun hobbyCardField_buyPriceAndCount_usesCollectionOrderMoney_andRendersCorrectly() {
        val item = UserCollectHobbyItem(
            money = 15000.0,
            currency = "yen",
            price = "15000日元",
            collection = UserCollectHobbyCollectionData(
                orderMoney = 680.0,
                orderMoneyCurrency = "rmb",
                buyNum = 2
            )
        )
        assertEquals("¥680", HobbyCardField.BUY_PRICE.value(item))
        assertEquals("2件¥680", HobbyCardField.BUY_PRICE_AND_COUNT.value(item))
        assertEquals("15000日元", HobbyCardField.PRICE.value(item))
    }

    @Test
    fun hobbyCardField_buyPriceAndCount_singleItem_rendersWithoutCountPrefix() {
        val item = UserCollectHobbyItem(
            money = 15000.0,
            currency = "yen",
            price = "15000日元",
            collection = UserCollectHobbyCollectionData(
                orderMoney = 680.0,
                orderMoneyCurrency = "rmb",
                buyNum = 1
            )
        )
        assertEquals("¥680", HobbyCardField.BUY_PRICE.value(item))
        assertEquals("¥680", HobbyCardField.BUY_PRICE_AND_COUNT.value(item))
    }

    @Test
    fun hobbyCardField_buyPrice_whenCollectionIsNull_rendersPlaceholder() {
        val item = UserCollectHobbyItem(
            money = 15000.0,
            currency = "yen",
            price = "15000日元",
            collection = null
        )
        assertEquals("-", HobbyCardField.BUY_PRICE.value(item))
        assertEquals("-", HobbyCardField.BUY_PRICE_AND_COUNT.value(item))
        assertEquals("15000日元", HobbyCardField.PRICE.value(item))
    }

    @Test
    fun hobbyCardField_buyPrice_whenOrderMoneyNonPositive_rendersPlaceholder() {
        val zeroItem = UserCollectHobbyItem(
            money = 15000.0,
            currency = "yen",
            price = "15000日元",
            collection = UserCollectHobbyCollectionData(orderMoney = 0.0, orderMoneyCurrency = "rmb")
        )
        assertEquals("-", HobbyCardField.BUY_PRICE.value(zeroItem))
        assertEquals("-", HobbyCardField.BUY_PRICE_AND_COUNT.value(zeroItem))
    }
}
