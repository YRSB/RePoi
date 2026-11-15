package com.nobody.repoi.ui.home

import androidx.compose.runtime.Immutable
import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.data.remote.model.ZoneTabsSettings

/** 一级「全部」聚合的固定 tabKey（与专区 nodeId 无关）。 */
const val ZONE_TAB_KEY_ALL: String = "all"

/** 一级「关注」聚合的固定 tabKey。 */
const val ZONE_TAB_KEY_FOLLOW: String = "follow"

/**
 * 顶栏专区切换器的单个纯文本条目。
 *
 * 只承载 tabKey 与展示名：选中态、点击回调由 UI 负责，不引入图标/专区 id 等冗余字段。
 */
@Immutable
data class ZoneSwitcherItem(
    val tabKey: String,
    val label: String
)

/**
 * 顶栏专区切换器的一个分组（如「快捷」「我的专区」）。
 *
 * @param title 分组标题；为 null 表示不渲染标题（保留扩展位）。
 */
@Immutable
data class ZoneSwitcherGroup(
    val title: String?,
    val items: List<ZoneSwitcherItem>
)

/**
 * 依据「已关注专区 + 首页设置」构造首页一级分类列表（纯函数，便于单测）。
 *
 * 顺序约定与旧版首页专区 TabRow 完全一致：
 * 1. `showAll = true` 时首项为「全部」；
 * 2. 次项恒为「关注」（关注列表为空时 `zoneIds = "-1"`，供 ViewModel 短路不发请求）；
 * 3. 其后为过滤隐藏项、按 `sort`（default/recent/custom）排序后的已关注专区。
 */
internal fun buildFeedCategories(
    focusZones: List<ZoneItem>,
    settings: ZoneTabsSettings
): List<FeedCategory> {
    val list = mutableListOf<FeedCategory>()
    if (settings.showAll) {
        list.add(FeedCategory(tabKey = ZONE_TAB_KEY_ALL, name = "全部"))
    }

    // 过滤掉用户设置隐藏的专区，再按设置顺序重排。
    val hiddenSet = settings.hiddenZoneIds.toSet()
    val visibleFocusZones = focusZones.filter { it.nodeId > 0L && it.nodeId !in hiddenSet }
    val sortedFocusZones = visibleFocusZones.sortedByZoneSettings(settings) { it.nodeId }

    val allFocusZoneIds = sortedFocusZones.map { it.nodeId }.joinToString(",")
    // 关注分类：若关注列表为空，传 "-1" 作为未关注标记。
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

/**
 * 由首页一级分类派生顶栏切换器分组（纯函数）。
 *
 * 与 [buildFeedCategories] 共用同一份分类数据，保证切换器与内容区选中态永远一致：
 * - 「快捷」恒含「全部」「关注」；即使 `settings.showAll = false` 也保留「全部」入口
 *   （设置只影响旧分类列表展示，不影响切换器可达性）；
 * - 「我的专区」由 `nodeId > 0` 的分类组成，隐藏项剔除、顺序遵循 `settings.sort`；
 *   没有已关注专区时整组省略，避免出现只有标题的空分组。
 */
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

/**
 * 切换器首帧默认分组：数据尚未送达时也保证「全部」「关注」可选。
 */
internal fun defaultZoneSwitcherGroups(): List<ZoneSwitcherGroup> =
    buildZoneSwitcherGroups(categories = emptyList(), settings = ZoneTabsSettings())

/**
 * 把切换器点击的 tabKey 解析为当前分类列表中的 [FeedCategory]（纯函数）。
 *
 * 「全部」「关注」在分类列表缺失时也要能选中（典型场景：`showAll = false` 隐藏了「全部」，
 * 但切换器仍提供入口），因此这两项给出固定兜底分类；其余未知 tabKey 返回 null 表示忽略。
 */
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

/** 依据设置中的 default/recent/custom 规则排序；未命中排序索引的条目排在末尾并保持原相对顺序。 */
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
