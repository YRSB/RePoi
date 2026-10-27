package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.HobbyDetailData
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * 价格与货币格式化单元测试，严格覆盖 Hpoi 2.39 价格与税费逻辑。
 */
class PriceFormatterTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun formatCurrency_mapsKnownCurrenciesCorrectly() {
        assertEquals("日元", PriceFormatter.formatCurrency("yen"))
        assertEquals("日元", PriceFormatter.formatCurrency("JPY"))
        assertEquals("人民币", PriceFormatter.formatCurrency("rmb"))
        assertEquals("人民币", PriceFormatter.formatCurrency("CNY"))
        assertEquals("美元", PriceFormatter.formatCurrency("dollar"))
        assertEquals("美元", PriceFormatter.formatCurrency("usd"))
        assertEquals("欧元", PriceFormatter.formatCurrency("euro"))
        assertEquals("欧元", PriceFormatter.formatCurrency("eur"))
        assertEquals("港元", PriceFormatter.formatCurrency("hkd"))
        assertEquals("GBP", PriceFormatter.formatCurrency("GBP"))
        assertEquals("", PriceFormatter.formatCurrency(null))
    }

    @Test
    fun format_yenWithTax_formatsCorrectly() {
        val formatted = PriceFormatter.format(
            money = 15000.0,
            currency = "yen",
            tax = 1
        )
        assertEquals("15000日元(含税）", formatted)
    }

    @Test
    fun format_yenWithoutTax_formatsCorrectly() {
        val formatted = PriceFormatter.format(
            money = 15000.0,
            currency = "yen",
            tax = 0
        )
        assertEquals("15000日元", formatted)
    }

    @Test
    fun format_rmbDecimal_formatsCorrectly() {
        val formatted = PriceFormatter.format(
            money = 699.5,
            currency = "rmb",
            tax = 0
        )
        assertEquals("699.5人民币", formatted)
    }

    @Test
    fun format_zeroMoney_returnsPriceTbd() {
        val formatted = PriceFormatter.format(
            money = 0.0,
            currency = "yen",
            tax = 0
        )
        assertEquals("价格待定", formatted)
    }

    @Test
    fun format_nearZeroMoney_returnsPriceTbd() {
        val formatted = PriceFormatter.format(
            money = 0.0000001,
            currency = "yen",
            tax = 0
        )
        assertEquals("价格待定", formatted)
    }

    @Test
    fun format_fallbackExplicitText_usedDirectly() {
        val formatted = PriceFormatter.format(
            money = null,
            currency = null,
            price = "20000円"
        )
        assertEquals("20000円", formatted)
    }

    @Test
    fun format_pureNumberPrice_appendsCurrencyAndTax() {
        val formatted = PriceFormatter.format(
            money = null,
            currency = "yen",
            tax = 1,
            price = "18000"
        )
        assertEquals("18000日元(含税）", formatted)
    }

    @Test
    fun format_allNull_returnsEmpty() {
        val formatted = PriceFormatter.format(
            money = null,
            currency = null,
            tax = null,
            price = null
        )
        assertEquals("", formatted)
    }

    @Test
    fun hobbyDetailData_deserializesPriceAndTaxProperly() {
        val rawJson = """
            {
                "id": 5483525,
                "itemId": 59570,
                "nameCN": "测试手办",
                "money": 16500.0,
                "currency": "yen",
                "tax": 1,
                "releaseDate": "2024-11-20"
            }
        """.trimIndent()

        val data = json.decodeFromString<HobbyDetailData>(rawJson)
        assertEquals(16500.0, data.money)
        assertEquals("yen", data.currency)
        assertEquals(1, data.tax)
        assertEquals("16500日元(含税）", data.displayPriceText)
        assertEquals("2024/11", data.displayReleaseDateText)
    }

    @Test
    fun hobbyDetailData_handlesTbdReleaseDateAndPrice() {
        val rawJson = """
            {
                "id": 5483525,
                "itemId": 59570,
                "nameCN": "未定手办",
                "money": 0.0,
                "currency": "yen",
                "releaseDate": null
            }
        """.trimIndent()

        val data = json.decodeFromString<HobbyDetailData>(rawJson)
        assertEquals("价格待定", data.displayPriceText)
        assertEquals("时间待定", data.displayReleaseDateText)
    }

    @Test
    fun hobbyItemSummary_usesPriceFormatter() {
        val item = HobbyItemSummary(
            money = 22000.0,
            currency = "yen",
            tax = 1
        )
        assertEquals("22000日元(含税）", item.displayPriceText)
    }
}
