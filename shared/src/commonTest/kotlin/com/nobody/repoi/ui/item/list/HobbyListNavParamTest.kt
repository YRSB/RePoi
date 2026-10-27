package com.nobody.repoi.ui.item.list

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HobbyListNavParamTest {

    @Test
    fun sexTagLink_appliesSexFilterOnDefaultBoard() {
        val param = HobbyListNavParam.fromHobbyLink("hobby/all?order=hits7Day&sex=0&category=1", "女")!!
        assertEquals("女", param.title)
        assertEquals("hits7Day", param.order)
        assertEquals(0, param.sex)
        assertEquals(1, param.categoryId)
        assertNull(param.scale)
        assertNull(param.releaseYear)
        assertNull(param.releaseMonth)
        assertNull(param.r18)
    }

    @Test
    fun scaleEntryLink_appliesScaleFilterOnDefaultBoard() {
        val param = HobbyListNavParam.fromHobbyLink("hobby/all?order=hits7Day&scale=6.0&category=1", "1/6")!!
        assertEquals("6.0", param.scale)
        assertEquals(1, param.categoryId)
        assertNull(param.sex)
    }

    @Test
    fun releaseEntryLink_appliesYearMonthFilter() {
        val param = HobbyListNavParam.fromHobbyLink(
            "hobby/all?order=hits7Day&category=1&releaseYear=2015&releaseMonth=11",
            "2015年11月"
        )!!
        assertEquals(2015, param.releaseYear)
        assertEquals(11, param.releaseMonth)
        assertEquals(1, param.categoryId)
    }

    @Test
    fun r18TagLink_appliesR18Filter() {
        val param = HobbyListNavParam.fromHobbyLink("hobby/all?order=hits7Day&r18=300&category=1", "观感安心")!!
        assertEquals(300, param.r18)
        assertNull(param.sex)
    }

    @Test
    fun plainCategoryTagLink_keepsCategoryOnly() {
        val param = HobbyListNavParam.fromHobbyLink("hobby/all?order=hits7Day&category=1", "比例人形")!!
        assertEquals(1, param.categoryId)
        assertNull(param.sex)
        assertNull(param.scale)
        assertNull(param.r18)
    }

    @Test
    fun sentinelValues_fallBackToUnlimited() {
        val param = HobbyListNavParam.fromHobbyLink(
            "hobby/all?order=hits7Day&sex=-1&scale=0&category=0&releaseYear=0&releaseMonth=0&r18=-1",
            "全部"
        )!!
        assertNull(param.sex)
        assertNull(param.scale)
        assertNull(param.releaseYear)
        assertNull(param.releaseMonth)
        assertNull(param.r18)
        assertEquals(10000, param.categoryId)
    }

    @Test
    fun specsTagLink_appliesSpecsFilter() {
        val param = HobbyListNavParam.fromHobbyLink("hobby/all?order=hits7Day&specs=649231&category=1", "可动")!!
        assertEquals("649231", param.specs)
        assertEquals(1, param.categoryId)
        assertNull(param.sex)
    }

    @Test
    fun specsTrailingComma_normalizesToSheetCode() {
        val param = HobbyListNavParam.fromHobbyLink("hobby/all?order=hits7Day&specs=649234,&category=1", "可拆卸")!!
        assertEquals("649234", param.specs)
        assertEquals("可拆卸", param.title)
    }

    @Test
    fun workersAndTagLink_passThrough() {
        val param = HobbyListNavParam.fromHobbyLink("hobby/all?order=hits7Day&category=1&workers=1,2&tag=联动", "联动")!!
        assertEquals("1,2", param.workers)
        assertEquals("联动", param.tag)
    }

    @Test
    fun multiSpecsLink_keepsAllCodes() {
        val param = HobbyListNavParam.fromHobbyLink("hobby/all?order=hits7Day&specs=649234,649231&category=1", "多选")!!
        assertEquals("649231,649234", param.specs)
    }

    @Test
    fun specsSetHelpers_roundTrip() {
        assertEquals(setOf("649231", "649234"), HobbyListNavParam.parseSpecsSet("649234,649231,"))
        assertEquals("649231,649234", HobbyListNavParam.joinSpecs(setOf("649234", "649231")))
        assertNull(HobbyListNavParam.joinSpecs(emptySet()))
        assertEquals(emptySet<String>(), HobbyListNavParam.parseSpecsSet(null))
    }

    @Test
    fun nonHobbyLink_returnsNull() {
        assertNull(HobbyListNavParam.fromHobbyLink("series/20", "某系列"))
        assertFalse(HobbyListNavParam.isHobbyListLink("company/33"))
        assertTrue(HobbyListNavParam.isHobbyListLink("hobby/all?order=hits7Day&sex=1&category=1"))
    }
}
