package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ZoneTabItem

enum class ZoneBoard(val type: String, val label: String, val emptyText: String) {
    Action("action", "动态", "暂无相关动态"),
    Album("album", "分享", "暂无相关分享"),
    Posts("posts", "讨论", "暂无相关讨论"),
    Hobby("hobby", "产品", "暂无相关产品"),
    Resale("resale_item", "二手", "暂无相关二手条目"),
    Company("company", "厂商", "暂无相关条目"),
    Series("series", "系列", "暂无相关条目"),
    Works("works", "作品", "暂无相关条目"),
    Character("charactar", "角色", "暂无相关条目"),
    Person("person", "人物", "暂无相关条目");

    val isEntity: Boolean
        get() = this in ENTITY

    fun tabTitle(serverTitle: String?): String = when (this) {
        Hobby -> label
        else -> serverTitle?.takeIf { it.isNotBlank() } ?: label
    }

    companion object {

        val ENTITY: Set<ZoneBoard> = setOf(Company, Series, Works, Character, Person)

        val HOME_ORDER: List<ZoneBoard> =
            listOf(Action, Album, Posts, Hobby, Resale, Company, Series, Works, Character, Person)

        val CLIENT_WHITELIST: Set<ZoneBoard> = HOME_ORDER.toSet()

        fun fromType(type: String): ZoneBoard? = when (type) {
            "share" -> Album
            else -> entries.firstOrNull { it.type == type }
        }

        fun tabsFor(
            serverTabs: List<ZoneTabItem>,
            available: Set<ZoneBoard> = CLIENT_WHITELIST
        ): List<ZoneBoard> {
            if (serverTabs.isEmpty()) return emptyList()
            return serverTabs
                .mapNotNull { fromType(it.type) }
                .distinct()
                .filter { it in available }
        }
    }
}
