package com.nobody.repoi.data.remote.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        assertTrue(ZoneHobbyFilter(sex = 0).hasAdvancedFilters)
        assertTrue(ZoneHobbyFilter(r18 = 18).hasAdvancedFilters)
        assertTrue(ZoneHobbyFilter(scale = "7.0").hasAdvancedFilters)
        assertTrue(ZoneHobbyFilter(specs = "649223").hasAdvancedFilters)
    }
}
