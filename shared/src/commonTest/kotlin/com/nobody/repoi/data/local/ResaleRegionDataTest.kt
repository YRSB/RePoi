package com.nobody.repoi.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 二手地区静态数据纯逻辑测试（W4-W5 pure-logic；KMP commonTest）。
 *
 * `ResaleRegionData` 为纯内存静态区划表（无协程/网络/DataStore）；
 * ID 取自本文件第一手区划行。
 */
class ResaleRegionDataTest {

    @Test
    fun nullAndNationwide_returnZero() {
        assertEquals(0, ResaleRegionData.findProvinceId(null))
        assertEquals(0, ResaleRegionData.findProvinceId(0))
    }

    @Test
    fun provinceId_returnsItself() {
        assertEquals(10316361, ResaleRegionData.findProvinceId(10316361))
        assertEquals(10316350, ResaleRegionData.findProvinceId(10316350))
    }

    @Test
    fun cityId_returnsOwningProvince() {
        assertEquals(10316361, ResaleRegionData.findProvinceId(10316564))
        assertEquals(10316350, ResaleRegionData.findProvinceId(10316384))
    }

    @Test
    fun unknownId_returnsZero() {
        assertEquals(0, ResaleRegionData.findProvinceId(99999999))
    }

    @Test
    fun provincesTable_isNonEmptyAndUnique() {
        val provinces = ResaleRegionData.provinces
        assertTrue(provinces.isNotEmpty())
        assertEquals(provinces.size, provinces.map { it.id }.toSet().size)
        val hefei = provinces.first { it.id == 10316361 }
            .cities.first { it.id == 10316564 }
        assertEquals("合肥市", hefei.name)
    }
}
