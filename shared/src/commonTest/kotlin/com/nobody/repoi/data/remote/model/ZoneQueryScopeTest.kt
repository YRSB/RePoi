package com.nobody.repoi.data.remote.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZoneQueryScopeTest {

    @Test
    fun all_omitsEveryZoneFilter() {
        val scope = ZoneQueryScope.All

        assertNull(scope.zonesParam())
        assertNull(scope.zoneNodeId())
        assertEquals("all", scope.stateKey())
        assertFalse(scope.isFollowEmpty)
    }

    @Test
    fun single_derivesNodeIdForBothTracks() {
        val scope = ZoneQueryScope.Single(33476094L)

        assertEquals("33476094", scope.zonesParam())
        assertEquals(33476094L, scope.zoneNodeId())
        assertEquals("z33476094", scope.stateKey())
        assertFalse(scope.isFollowEmpty)
    }

    @Test
    fun follow_derivesCommaSeparatedUnion() {
        val scope = ZoneQueryScope.Follow(listOf(33476094L, 39989602L, 39228575L))

        assertEquals("33476094,39989602,39228575", scope.zonesParam())
        assertNull(scope.zoneNodeId())

        assertEquals("follow:33476094,39228575,39989602", scope.stateKey())
        assertFalse(scope.isFollowEmpty)
    }

    @Test
    fun follow_singleZoneStillProducesCommaParamWithoutTrailingComma() {
        val scope = ZoneQueryScope.Follow(listOf(42L))

        assertEquals("42", scope.zonesParam())
        assertEquals("follow:42", scope.stateKey())
    }

    @Test
    fun follow_emptySet_isMarkedEmptyAndNeverEmitsBlankZonesParam() {
        val scope = ZoneQueryScope.Follow(emptyList())

        assertTrue(scope.isFollowEmpty)

        assertNull(scope.zonesParam())
        assertEquals("follow:", scope.stateKey())
        assertNull(scope.zoneNodeId())
    }

    @Test
    fun stateKeys_areStableAndScopeSpecific() {
        val keys = listOf(
            ZoneQueryScope.All,
            ZoneQueryScope.Follow(listOf(1L, 2L)),
            ZoneQueryScope.Single(123L)
        ).map { it.stateKey() }

        assertEquals(listOf("all", "follow:1,2", "z123"), keys)

        assertEquals(ZoneQueryScope.Single(123L).stateKey(), ZoneQueryScope.Single(123L).stateKey())
        assertEquals(
            ZoneQueryScope.Follow(listOf(1L, 2L)).stateKey(),
            ZoneQueryScope.Follow(listOf(1L, 2L)).stateKey()
        )
    }

    @Test
    fun stateKey_followNormalizesOrderAndDuplicates() {

        val canonical = ZoneQueryScope.Follow(listOf(1L, 2L, 3L)).stateKey()

        assertEquals("follow:1,2,3", canonical)
        assertEquals(canonical, ZoneQueryScope.Follow(listOf(3L, 1L, 2L)).stateKey())
        assertEquals(canonical, ZoneQueryScope.Follow(listOf(2L, 3L, 1L, 2L)).stateKey())

        assertEquals("3,1,2", ZoneQueryScope.Follow(listOf(3L, 1L, 2L)).zonesParam())
    }

    @Test
    fun zoneParams_areScopeSpecific() {
        assertNull(ZoneQueryScope.All.zonesParam())
        assertEquals("123", ZoneQueryScope.Single(123L).zonesParam())

        val follow = ZoneQueryScope.Follow(listOf(1L, 2L))
        assertEquals("1,2", follow.zonesParam())

        assertNull(follow.zoneNodeId())
    }
}
