package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.data.remote.model.ZoneTabsSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZoneSwitcherGroupsTest {

    private fun zone(nodeId: Long, name: String = "专区$nodeId") =
        ZoneItem(nodeId = nodeId, nameCN = name)

    private fun category(nodeId: Long, name: String = "专区$nodeId") =
        FeedCategory(tabKey = "z$nodeId", name = name, nodeId = nodeId, zoneIds = nodeId.toString())

    private fun List<ZoneSwitcherGroup>.quick(): ZoneSwitcherGroup = first { it.title == "快捷" }

    private fun List<ZoneSwitcherGroup>.myZones(): ZoneSwitcherGroup? = firstOrNull { it.title == "我的专区" }

    private fun myZoneKeys(groups: List<ZoneSwitcherGroup>): List<String> =
        groups.myZones()?.items?.map { it.tabKey } ?: emptyList()

    @Test
    fun quickGroup_alwaysContainsAllAndFollow_whenCategoriesEmpty() {
        val groups = buildZoneSwitcherGroups(emptyList(), ZoneTabsSettings())

        assertEquals(listOf("all", "follow"), groups.quick().items.map { it.tabKey })
        assertEquals(listOf("全部", "关注"), groups.quick().items.map { it.label })
        assertNull(groups.myZones())
    }

    @Test
    fun quickGroup_keepsAllEvenWhenShowAllDisabled() {

        val settings = ZoneTabsSettings(showAll = false)
        val categories = buildFeedCategories(listOf(zone(100)), settings)
        assertTrue(categories.none { it.tabKey == "all" })

        val groups = buildZoneSwitcherGroups(categories, settings)

        assertEquals(listOf("all", "follow"), groups.quick().items.map { it.tabKey })
        assertEquals(listOf("z100"), myZoneKeys(groups))
    }

    @Test
    fun emptyFollow_stillOfferedInQuickGroupWithEmptyZoneIds() {
        val categories = buildFeedCategories(emptyList(), ZoneTabsSettings())
        val follow = categories.first { it.tabKey == "follow" }
        assertEquals("-1", follow.zoneIds)

        val groups = buildZoneSwitcherGroups(categories, ZoneTabsSettings())

        assertEquals(listOf("all", "follow"), groups.quick().items.map { it.tabKey })
        assertNull(groups.myZones())
    }

    @Test
    fun categories_defaultSortKeepsFocusOrder() {
        val categories = buildFeedCategories(
            focusZones = listOf(zone(300), zone(100), zone(200)),
            settings = ZoneTabsSettings(sort = "default")
        )

        assertEquals(listOf("all", "follow", "z300", "z100", "z200"), categories.map { it.tabKey })
        assertEquals("300,100,200", categories.first { it.tabKey == "follow" }.zoneIds)
    }

    @Test
    fun categories_recentSortFollowsRecentZoneIdsAndAppendsUnmapped() {
        val categories = buildFeedCategories(
            focusZones = listOf(zone(100), zone(200), zone(300)),
            settings = ZoneTabsSettings(sort = "recent", recentZoneIds = listOf(300L, 100L))
        )

        assertEquals(listOf("all", "follow", "z300", "z100", "z200"), categories.map { it.tabKey })
    }

    @Test
    fun categories_customSortFollowsCustomOrderAndAppendsUnmapped() {
        val categories = buildFeedCategories(
            focusZones = listOf(zone(100), zone(200), zone(300)),
            settings = ZoneTabsSettings(sort = "custom", customOrder = listOf(200L, 100L))
        )

        assertEquals(listOf("all", "follow", "z200", "z100", "z300"), categories.map { it.tabKey })
    }

    @Test
    fun categories_filtersHiddenZoneIds() {
        val categories = buildFeedCategories(
            focusZones = listOf(zone(100), zone(200), zone(300)),
            settings = ZoneTabsSettings(hiddenZoneIds = listOf(200L))
        )

        assertEquals(listOf("all", "follow", "z100", "z300"), categories.map { it.tabKey })

        assertEquals("100,300", categories.first { it.tabKey == "follow" }.zoneIds)
    }

    @Test
    fun myZones_recentSortFollowsRecentZoneIds() {
        val categories = listOf(
            FeedCategory(tabKey = "all", name = "全部"),
            FeedCategory(tabKey = "follow", name = "关注"),
            category(100),
            category(200),
            category(300)
        )
        val groups = buildZoneSwitcherGroups(
            categories = categories,
            settings = ZoneTabsSettings(sort = "recent", recentZoneIds = listOf(300L, 100L))
        )

        assertEquals(listOf("z300", "z100", "z200"), myZoneKeys(groups))
        assertEquals(listOf("专区300", "专区100", "专区200"), groups.myZones()?.items?.map { it.label })
    }

    @Test
    fun myZones_customSortFollowsCustomOrder() {
        val categories = listOf(
            FeedCategory(tabKey = "all", name = "全部"),
            FeedCategory(tabKey = "follow", name = "关注"),
            category(100),
            category(200),
            category(300)
        )
        val groups = buildZoneSwitcherGroups(
            categories = categories,
            settings = ZoneTabsSettings(sort = "custom", customOrder = listOf(200L, 100L))
        )

        assertEquals(listOf("z200", "z100", "z300"), myZoneKeys(groups))
    }

    @Test
    fun myZones_filtersHiddenZoneIds() {
        val categories = listOf(
            FeedCategory(tabKey = "all", name = "全部"),
            FeedCategory(tabKey = "follow", name = "关注"),
            category(100),
            category(200),
            category(300)
        )
        val groups = buildZoneSwitcherGroups(
            categories = categories,
            settings = ZoneTabsSettings(hiddenZoneIds = listOf(200L))
        )

        assertEquals(listOf("z100", "z300"), myZoneKeys(groups))
    }

    @Test
    fun resolveZoneSwitcherCategory_allFallsBackWhenShowAllDisabled() {
        val categories = buildFeedCategories(emptyList(), ZoneTabsSettings(showAll = false))
        assertTrue(categories.none { it.tabKey == "all" })

        val all = resolveZoneSwitcherCategory(categories, "all")

        assertEquals("all", all?.tabKey)
        assertEquals("全部", all?.name)
        assertEquals(0L, all?.nodeId)
        assertEquals(0, all?.id)
    }

    @Test
    fun resolveZoneSwitcherCategory_unknownTabKeyReturnsNull() {
        assertNull(resolveZoneSwitcherCategory(emptyList(), "z404"))
    }
}
