package com.nobody.repoi.ui.navigation

sealed interface RouteTarget {
    data class Internal(val key: AppNavKey) : RouteTarget

    data class External(val url: String) : RouteTarget

    data class Notice(val message: String) : RouteTarget
}
