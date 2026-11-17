package com.nobody.repoi.data.remote.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 高级筛选「生效」判定必须与 `ItemRepository.queryHobbyBoard` 的下发规则同义：
 * 弹层以「不限」哨兵回填时（`sex`/`r18` = `-1`、`scale` = `"0"`、空白 `specs`）不得点亮筛选 Chip。
 */
class ZoneHobbyFilterTest {

    @Test
    fun hasAdvancedFilters_treatsSentinelsAsUnset() {
        assertFalse(ZoneHobbyFilter().hasAdvancedFilters)
        assertFalse(ZoneHobbyFilter(sex = -1).hasAdvancedFilters)
        assertFalse(ZoneHobbyFilter(r18 = -1).hasAdvancedFilters)
        assertFalse(ZoneHobbyFilter(scale = "0").hasAdvancedFilters)
        assertFalse(ZoneHobbyFilter(specs = " ").hasAdvancedFilters)
        assertFalse(
            ZoneHobbyFilter(sex = -1, r18 = -1, scale = "0", specs = " ").hasAdvancedFilters
        )
    }

    @Test
    fun hasAdvancedFilters_detectsValuesThatAreSent() {
        // 0 是合法取值（与 -1 的「不限」区分开），必须点亮
        assertTrue(ZoneHobbyFilter(sex = 0).hasAdvancedFilters)
        assertTrue(ZoneHobbyFilter(r18 = 18).hasAdvancedFilters)
        assertTrue(ZoneHobbyFilter(scale = "7.0").hasAdvancedFilters)
        assertTrue(ZoneHobbyFilter(specs = "649223").hasAdvancedFilters)
    }
}
