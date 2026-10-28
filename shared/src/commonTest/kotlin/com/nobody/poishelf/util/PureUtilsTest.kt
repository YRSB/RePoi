package com.nobody.poishelf.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 纯逻辑单测：价格格式化 / 日期时间 / 列表分页。
 */
class PureUtilsTest {

    @Test
    fun formatFixedRoundsHalfUp() {
        assertEquals("4.4", PriceFormatter.formatFixed(4.4, 1))
        assertEquals("4.35", PriceFormatter.formatFixed(4.35, 2))
        assertEquals("100.00", PriceFormatter.formatFixed(100.0, 2))
        assertEquals("0.00", PriceFormatter.formatFixed(0.0, 2))
        assertEquals("-1.50", PriceFormatter.formatFixed(-1.5, 2))
        assertEquals("9", PriceFormatter.formatFixed(9.0, 0))
    }

    @Test
    fun formatCurrencyMapsKnownCodes() {
        assertEquals("日元", PriceFormatter.formatCurrency("jpy"))
        assertEquals("人民币", PriceFormatter.formatCurrency("cny"))
        assertEquals("美元", PriceFormatter.formatCurrency("USD"))
        assertEquals("港元", PriceFormatter.formatCurrency("hkd"))
        assertEquals("xxx", PriceFormatter.formatCurrency("xxx"))
    }

    @Test
    fun formatMoneyZeroIsPending() {
        assertEquals("价格待定", PriceFormatter.format(0.0, "cny"))
        assertTrue(PriceFormatter.format(199.0, "cny").startsWith("199"))
    }

    @Test
    fun dateTimeParseServerPatterns() {
        val ymd = com.nobody.poishelf.platform.DateTimeFormats.parseServerDateTime("2024-01-15")
        assertTrue((ymd ?: 0L) > 0L)
        val ymdHms = com.nobody.poishelf.platform.DateTimeFormats.parseServerDateTime("2024-01-15 08:30:00")
        assertTrue((ymdHms ?: 0L) > 0L)
        assertEquals(null, com.nobody.poishelf.platform.DateTimeFormats.parseServerDateTime("not-a-date"))
        assertEquals(null, com.nobody.poishelf.platform.DateTimeFormats.parseServerDateTime(""))
    }

    @Test
    fun dateTimeYmdFromMillisFormatsDate() {
        val out = com.nobody.poishelf.platform.DateTimeFormats.ymdFromMillis(0L)
        assertTrue(out.matches(Regex("""\d{4}-\d{2}-\d{2}""")), out)
    }

    @Test
    fun listPagingAppendDedupsByKey() {
        val merged = ListPaging.appendPage(
            listOf(1L, 2L),
            listOf(2L, 3L),
        ) { it }
        assertEquals(listOf(1L, 2L, 3L), merged)
        assertTrue(ListPaging.hasMoreByFullPage(20, 20))
        assertEquals(false, ListPaging.hasMoreByFullPage(5, 20))
    }
}
