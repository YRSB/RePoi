package com.nobody.repoi.ui.item.list

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FilterLabelsTest {

    @Test
    fun emptyFilters_returnsBaseTitle() {
        assertEquals("周边列表", FilterLabels.composeTitle("周边列表", null, null, null, null, null))
        assertEquals("热门预订", FilterLabels.composeTitle("热门预订", -1, "", "0", -1, " "))
    }

    @Test
    fun singleFilters_useCodebookNames() {
        assertEquals("周边列表(妹子)", FilterLabels.composeTitle("周边列表", 0, null, null, null, null))
        assertEquals("周边列表(1/6)", FilterLabels.composeTitle("周边列表", null, null, "6.0", null, null))
        assertEquals("周边列表(观感谨慎+)", FilterLabels.composeTitle("周边列表", null, null, null, 320, null))
        assertEquals("周边列表(联动)", FilterLabels.composeTitle("周边列表", null, null, null, null, "联动"))
    }

    @Test
    fun fullFilters_followOfficialOrder() {
        assertEquals(
            "周边列表(观感谨慎+ 可动 可脱 妹子 1/6 联动)",
            FilterLabels.composeTitle("周边列表", 0, "649234,649231", "6.0", 320, "联动")
        )
    }

    @Test
    fun scaleName_trimsDecimalNoise() {
        assertEquals("1/6", FilterLabels.scaleName("6.0"))
        assertEquals("1/2.5", FilterLabels.scaleName("2.5"))
        assertEquals("1/300", FilterLabels.scaleName("300.0"))
        assertNull(FilterLabels.scaleName("0"))
        assertNull(FilterLabels.scaleName(null))
    }

    @Test
    fun sentinelCodes_resolveToNull() {
        assertNull(FilterLabels.sexName(-1))
        assertNull(FilterLabels.r18Name(-1))
        assertNull(FilterLabels.r18Name(399))
    }
}
