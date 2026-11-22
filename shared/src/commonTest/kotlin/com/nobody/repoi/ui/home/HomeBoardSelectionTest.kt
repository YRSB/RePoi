package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneQueryScope
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeBoardSelectionTest {

    @Test
    fun toZoneQueryScope_all_returnsAll() {
        assertEquals(ZoneQueryScope.All, FeedCategory(tabKey = "all").toZoneQueryScope())
    }

    @Test
    fun toZoneQueryScope_follow_parsesPositiveIdsAndDropsInvalid() {
        val scope = FeedCategory(tabKey = "follow", zoneIds = "1, 2,abc,-1,0,3").toZoneQueryScope()
        assertEquals(ZoneQueryScope.Follow(listOf(1L, 2L, 3L)), scope)
    }

    @Test
    fun toZoneQueryScope_emptyFollow_keepsEmptyFollow() {

        val scope = FeedCategory(tabKey = "follow", zoneIds = "-1").toZoneQueryScope()
        assertEquals(ZoneQueryScope.Follow(emptyList()), scope)
    }

    @Test
    fun toZoneQueryScope_single_prefersNodeId() {
        val category = FeedCategory(tabKey = "z9", nodeId = 100L)
        assertEquals(ZoneQueryScope.Single(100L), category.toZoneQueryScope())
    }

    @Test
    fun toZoneQueryScope_single_fallsBackToTabKeyWhenNodeIdMissing() {
        val category = FeedCategory(tabKey = "z42", nodeId = 0L)
        assertEquals(ZoneQueryScope.Single(42L), category.toZoneQueryScope())
    }

    @Test
    fun toZoneQueryScope_unparsableKey_fallsBackToAll() {
        assertEquals(ZoneQueryScope.All, FeedCategory(tabKey = "vendor-7", nodeId = 0L).toZoneQueryScope())
    }
}
