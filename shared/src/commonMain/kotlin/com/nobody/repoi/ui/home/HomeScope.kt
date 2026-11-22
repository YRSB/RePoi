package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneQueryScope

internal fun FeedCategory.toZoneQueryScope(): ZoneQueryScope = when (tabKey) {
    ZONE_TAB_KEY_ALL -> ZoneQueryScope.All
    ZONE_TAB_KEY_FOLLOW -> ZoneQueryScope.Follow(
        zoneIds.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L }
    )

    else -> {
        val zoneNodeId = nodeId.takeIf { it > 0L }
            ?: tabKey.removePrefix("z").toLongOrNull()?.takeIf { it > 0L }
        if (zoneNodeId != null) ZoneQueryScope.Single(zoneNodeId) else ZoneQueryScope.All
    }
}

val HomeFeedUiState.zoneScope: ZoneQueryScope
    get() = selectedCategory.toZoneQueryScope()
