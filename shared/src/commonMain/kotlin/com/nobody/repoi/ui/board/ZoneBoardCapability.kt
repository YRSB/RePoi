package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.remote.model.ZoneTabItem

object ZoneBoardCapability {

    val ALL_BOARDS: List<ZoneBoard> = ZoneBoard.HOME_ORDER

    val FOLLOW_UNAVAILABLE_BOARDS: Set<ZoneBoard> =
        setOf(ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale)

    val FOLLOW_BOARDS: List<ZoneBoard> =
        ALL_BOARDS.filterNot { it in FOLLOW_UNAVAILABLE_BOARDS }

    val SINGLE_FALLBACK_BOARDS: List<ZoneBoard> = listOf(ZoneBoard.Action)

    fun homeBoardsFor(
        scope: ZoneQueryScope,
        serverTabs: List<ZoneTabItem>? = null
    ): List<ZoneBoard> = when (scope) {
        ZoneQueryScope.All -> ALL_BOARDS
        is ZoneQueryScope.Follow -> FOLLOW_BOARDS
        is ZoneQueryScope.Single -> serverTabs?.let { ZoneBoard.tabsFor(it) } ?: SINGLE_FALLBACK_BOARDS
    }

    fun isAvailableOnHome(scope: ZoneQueryScope, board: ZoneBoard): Boolean = when (scope) {
        ZoneQueryScope.All -> board in ALL_BOARDS
        is ZoneQueryScope.Follow -> board in FOLLOW_BOARDS
        is ZoneQueryScope.Single -> board in ZoneBoard.CLIENT_WHITELIST
    }
}
