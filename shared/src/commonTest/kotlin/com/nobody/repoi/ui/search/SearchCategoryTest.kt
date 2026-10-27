package com.nobody.repoi.ui.search

import com.nobody.repoi.data.remote.model.AttributeListPayload
import com.nobody.repoi.data.remote.model.CommonResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchCategoryTest {

    @Test
    fun resolveSearchCategory_hobbyUsesLocalTreeSelection() {
        assertEquals(10000, resolveSearchCategory(SearchTab.HOBBY, hobbyCategory = 10000))
        assertEquals(100, resolveSearchCategory(SearchTab.HOBBY, hobbyCategory = 100))
        assertEquals(900, resolveSearchCategory(SearchTab.HOBBY, hobbyCategory = 900))
    }

    @Test
    fun resolveSearchCategory_albumUsesSubCategory() {
        assertEquals(
            70000,
            resolveSearchCategory(SearchTab.ALBUM, albumCategory = AlbumSubCategory.ALL)
        )
        assertEquals(
            60001,
            resolveSearchCategory(SearchTab.ALBUM, albumCategory = AlbumSubCategory.FIGURE)
        )
    }

    @Test
    fun resolveSearchCategory_elementsUsesSubCategory() {
        assertEquals(
            50000,
            resolveSearchCategory(SearchTab.WORKS_ELEMENT, elementsCategory = ElementsSubCategory.ALL)
        )
        assertEquals(
            40100,
            resolveSearchCategory(SearchTab.WORKS_ELEMENT, elementsCategory = ElementsSubCategory.CHARACTER)
        )
        assertEquals(
            40500,
            resolveSearchCategory(SearchTab.WORKS_ELEMENT, elementsCategory = ElementsSubCategory.ORGANIZATION)
        )
    }

    @Test
    fun resolveSearchCategory_otherTabsUseOwnCategory() {
        assertEquals(40000, resolveSearchCategory(SearchTab.COMPANY))
        assertEquals(20000, resolveSearchCategory(SearchTab.WORKS))
        assertEquals(30000, resolveSearchCategory(SearchTab.PERSON))
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Test
    fun searchTab_allEntriesHaveValidTitlesAndIds() {
        assertEquals(8, SearchTab.entries.size)
        assertEquals(
            listOf(10000, 70000, 40000, 50000, 20000, 80000, 30000, 60000),
            SearchTab.entries.map { it.categoryId }
        )
        val expectedTabs = listOf(
            SearchTab.HOBBY to "周边",
            SearchTab.ALBUM to "相册",
            SearchTab.COMPANY to "厂商",
            SearchTab.WORKS_ELEMENT to "角色/要素",
            SearchTab.WORKS to "作品",
            SearchTab.SERIES to "系列",
            SearchTab.PERSON to "人物",
            SearchTab.USER to "用户"
        )
        expectedTabs.forEach { (tab, expectedTitle) ->
            assertEquals(expectedTitle, tab.title)
        }
        SearchTab.entries.forEach { tab ->
            assertEquals(tab, SearchTab.fromCategoryId(tab.categoryId))
        }
    }

    @Test
    fun attributeListPayload_parsesOfficialResponse() {
        val body = """{"attributeList":[{"id":15,"name":"主角","categoryId":40100},{"id":17,"name":"假面骑士","categoryId":40100}]}"""
        val parsed = json.decodeFromString(
            CommonResponse.serializer(AttributeListPayload.serializer()),
            """{"success":true,"code":200,"data":$body}"""
        )
        val attrs = parsed.data?.attributeList.orEmpty()
        assertEquals(2, attrs.size)
        assertEquals(15, attrs[0].id)
        assertEquals("主角", attrs[0].name)
        assertEquals(40100, attrs[0].categoryId)
    }

    @Test
    fun attributeListPayload_emptyForTopLevelCategory() {
        val parsed = json.decodeFromString(
            CommonResponse.serializer(AttributeListPayload.serializer()),
            """{"success":true,"code":200,"data":{"attributeList":[]}}"""
        )
        assertEquals(0, parsed.data?.attributeList.orEmpty().size)
    }

    @Test
    fun hobbySortOption_hasAllOptionsIncludingScoreAndHits() {
        assertEquals(6, HobbySortOption.entries.size)
        val expected = listOf(
            HobbySortOption.DEFAULT to ("" to "综合排序"),
            HobbySortOption.ADD_TIME to ("addTime" to "最新添加"),
            HobbySortOption.RELEASE_DATE to ("releaseDate" to "最晚发售"),
            HobbySortOption.RATING to ("rating" to "评分最高"),
            HobbySortOption.SCORE to ("_score" to "相关度"),
            HobbySortOption.HITS to ("hits" to "最热门")
        )
        expected.forEach { (option, pair) ->
            assertEquals(pair.first, option.key)
            assertEquals(pair.second, option.label)
        }
    }

    @Test
    fun isTabFilterable_returnsTrueOnlyForSupportedTabs() {
        assertTrue(isTabFilterable(SearchTab.HOBBY))
        assertTrue(isTabFilterable(SearchTab.ALBUM))
        assertTrue(isTabFilterable(SearchTab.WORKS_ELEMENT))

        assertFalse(isTabFilterable(SearchTab.COMPANY))
        assertFalse(isTabFilterable(SearchTab.WORKS))
        assertFalse(isTabFilterable(SearchTab.SERIES))
        assertFalse(isTabFilterable(SearchTab.PERSON))
        assertFalse(isTabFilterable(SearchTab.USER))
    }

    @Test
    fun isFilterActive_correctlyDetectsActiveFiltersPerTab() {
        assertFalse(isFilterActive(SearchTab.HOBBY))
        assertTrue(isFilterActive(SearchTab.HOBBY, order = "rating"))
        assertTrue(isFilterActive(SearchTab.HOBBY, hobbyCategory = 100))
        assertTrue(isFilterActive(SearchTab.HOBBY, hasCompany = true))
        assertTrue(isFilterActive(SearchTab.HOBBY, hasYear = true))
        assertFalse(isFilterActive(SearchTab.ALBUM))
        assertTrue(isFilterActive(SearchTab.ALBUM, order = "addTime"))
        assertTrue(isFilterActive(SearchTab.ALBUM, albumCategory = AlbumSubCategory.FIGURE))
        assertFalse(isFilterActive(SearchTab.WORKS_ELEMENT))
        assertTrue(isFilterActive(SearchTab.WORKS_ELEMENT, elementsCategory = ElementsSubCategory.CHARACTER))
        assertTrue(isFilterActive(SearchTab.WORKS_ELEMENT, attribute = 15))
        assertFalse(isFilterActive(SearchTab.USER))
    }

    @Test
    fun resolveSearchCategory_hobbyInvalidFallsBackToAll() {
        assertEquals(10000, resolveSearchCategory(SearchTab.HOBBY, hobbyCategory = 0))
        assertEquals(10000, resolveSearchCategory(SearchTab.HOBBY, hobbyCategory = -5))
    }

    @Test
    fun searchTab_fromUnknownCategoryFallsBackToHobby() {
        assertEquals(SearchTab.HOBBY, SearchTab.fromCategoryId(99999))
    }

    @Test
    fun searchTab_categoryIdsUniqueAndItemTypesNonEmpty() {
        val ids = SearchTab.entries.map { it.categoryId }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf("hobby", "album", "company", "works_element", "works", "series", "person", "user"),
            SearchTab.entries.map { it.itemType }
        )
    }
}
