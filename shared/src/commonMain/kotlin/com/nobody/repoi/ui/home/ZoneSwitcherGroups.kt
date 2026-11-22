package com.nobody.repoi.ui.home

import androidx.compose.runtime.Immutable
import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.data.remote.model.ZoneTabsSettings

const val ZONE_TAB_KEY_ALL: String = "all"

const val ZONE_TAB_KEY_FOLLOW: String = "follow"

@Immutable
data class ZoneSwitcherItem(
    val tabKey: String,
    val label: String
)

@Immutable
data class ZoneSwitcherGroup(
    val title: String?,
    val items: List<ZoneSwitcherItem>
)

internal fun buildFeedCategories(
    focusZones: List<ZoneItem>,
    settings: ZoneTabsSettings
): List<FeedCategory> {
    val list = mutableListOf<FeedCategory>()
    if (settings.showAll) {
        list.add(FeedCategory(tabKey = ZONE_TAB_KEY_ALL, name = "全部"))
    }

    val hiddenSet = settings.hiddenZoneIds.toSet()
    val visibleFocusZones = focusZones.filter { it.nodeId > 0L && it.nodeId !in hiddenSet }
    val sortedFocusZones = visibleFocusZones.sortedByZoneSettings(settings) { it.nodeId }

    val allFocusZoneIds = sortedFocusZones.map { it.nodeId }.joinToString(",")

    list.add(
        FeedCategory(
            tabKey = ZONE_TAB_KEY_FOLLOW,
            name = "关注",
            zoneIds = allFocusZoneIds.ifEmpty { "-1" }
        )
    )

    sortedFocusZones.forEach { zone ->
        list.add(
            FeedCategory(
                tabKey = "z${zone.nodeId}",
                name = zone.displayName,
                nodeId = zone.nodeId,
                zoneIds = zone.nodeId.toString()
            )
        )
    }
    return list
}

internal fun buildZoneSwitcherGroups(
    categories: List<FeedCategory>,
    settings: ZoneTabsSettings
): List<ZoneSwitcherGroup> {
    val allItem = categories.firstOrNull { it.tabKey == ZONE_TAB_KEY_ALL }?.toSwitcherItem()
        ?: ZoneSwitcherItem(tabKey = ZONE_TAB_KEY_ALL, label = "全部")
    val followItem = categories.firstOrNull { it.tabKey == ZONE_TAB_KEY_FOLLOW }?.toSwitcherItem()
        ?: ZoneSwitcherItem(tabKey = ZONE_TAB_KEY_FOLLOW, label = "关注")

    val hiddenSet = settings.hiddenZoneIds.toSet()
    val myZones = categories
        .filter { it.nodeId > 0L && it.nodeId !in hiddenSet }
        .sortedByZoneSettings(settings) { it.nodeId }
        .map { it.toSwitcherItem() }

    return buildList {
        add(ZoneSwitcherGroup(title = "快捷", items = listOf(allItem, followItem)))
        if (myZones.isNotEmpty()) {
            add(ZoneSwitcherGroup(title = "我的专区", items = myZones))
        }
    }
}

internal fun defaultZoneSwitcherGroups(): List<ZoneSwitcherGroup> =
    buildZoneSwitcherGroups(categories = emptyList(), settings = ZoneTabsSettings())

internal fun resolveZoneSwitcherCategory(
    categories: List<FeedCategory>,
    tabKey: String
): FeedCategory? {
    categories.firstOrNull { it.tabKey == tabKey }?.let { return it }
    return when (tabKey) {
        ZONE_TAB_KEY_ALL -> FeedCategory(tabKey = ZONE_TAB_KEY_ALL, name = "全部")
        ZONE_TAB_KEY_FOLLOW -> FeedCategory(
            tabKey = ZONE_TAB_KEY_FOLLOW,
            name = "关注",
            zoneIds = "-1"
        )
        else -> null
    }
}

private fun <T> List<T>.sortedByZoneSettings(
    settings: ZoneTabsSettings,
    nodeIdOf: (T) -> Long
): List<T> = when (settings.sort) {
    "custom" -> {
        val orderMap = settings.customOrder.mapIndexed { index, id -> id to index }.toMap()
        sortedBy { orderMap[nodeIdOf(it)] ?: Int.MAX_VALUE }
    }

    "recent" -> {
        val recentMap = settings.recentZoneIds.mapIndexed { index, id -> id to index }.toMap()
        sortedBy { recentMap[nodeIdOf(it)] ?: Int.MAX_VALUE }
    }

    else -> this
}

private fun FeedCategory.toSwitcherItem(): ZoneSwitcherItem =
    ZoneSwitcherItem(tabKey = tabKey, label = name)
