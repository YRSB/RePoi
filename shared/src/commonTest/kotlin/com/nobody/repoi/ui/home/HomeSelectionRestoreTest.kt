package com.nobody.repoi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeSelectionRestoreTest {

    @Test
    fun encodeBoardKey_producesTabKeyAndType() {
        assertEquals("all|action", encodeBoardKey("all", "action"))
        assertEquals("follow|album", encodeBoardKey("follow", "album"))
        assertEquals("z33476094|hobby", encodeBoardKey("z33476094", "hobby"))

        assertEquals("all|action", encodeBoardKey(" all ", " action "))
    }

    @Test
    fun encodeBoardKey_blankPartFallsBackToEmpty() {
        assertEquals("", encodeBoardKey("", "action"))
        assertEquals("", encodeBoardKey("all", " "))
        assertEquals("", encodeBoardKey("", ""))
    }

    @Test
    fun decodeBoardKey_roundTripsEncodedValue() {
        listOf(
            "all" to "action",
            "follow" to "album",
            "z33476094" to "hobby",
        ).forEach { (tabKey, boardType) ->
            assertEquals(tabKey to boardType, decodeBoardKey(encodeBoardKey(tabKey, boardType)))
        }
    }

    @Test
    fun decodeBoardKey_rejectsIllegalValues() {
        assertNull(decodeBoardKey(null))
        assertNull(decodeBoardKey(""))
        assertNull(decodeBoardKey("   "))

        assertNull(decodeBoardKey("action"))
        assertNull(decodeBoardKey("all action"))

        assertNull(decodeBoardKey("|action"))
        assertNull(decodeBoardKey("all|"))
        assertNull(decodeBoardKey(" | "))

        assertNull(decodeBoardKey("all|action|album"))

        assertNull(decodeBoardKey("z100"))
    }

    private val availableTabKeys = listOf("all", "follow", "z100", "z200")

    @Test
    fun resolveInitialTabKey_followRestoresFollowWhenAvailable() {

        assertEquals(ZONE_TAB_KEY_FOLLOW, resolveInitialTabKey(ZONE_TAB_KEY_FOLLOW, availableTabKeys))

        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey(ZONE_TAB_KEY_FOLLOW, listOf("all", "z100")))
    }

    @Test
    fun resolveInitialTabKey_missingOrInvalidFallsBackToAll() {
        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey(null, availableTabKeys))
        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey("", availableTabKeys))
        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey("  ", availableTabKeys))
        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey("bogus", availableTabKeys))
    }

    @Test
    fun resolveInitialTabKey_restoresAvailableSingleZone() {
        assertEquals("z100", resolveInitialTabKey("z100", availableTabKeys))
        assertEquals("z200", resolveInitialTabKey("z200", availableTabKeys))
    }

    @Test
    fun resolveInitialTabKey_unavailableSingleZoneFallsBackToAll() {

        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey("z999", availableTabKeys))
    }

    @Test
    fun resolveInitialTabKey_allAlwaysRestoresAll() {

        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey(ZONE_TAB_KEY_ALL, availableTabKeys))
        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey(ZONE_TAB_KEY_ALL, listOf("follow", "z100")))
    }

    @Test
    fun zoneTabKeyNodeId_parsesOnlyPositiveSingleZoneKeys() {
        assertEquals(33476094L, zoneTabKeyNodeId("z33476094"))
        assertEquals(100L, zoneTabKeyNodeId(" z100 "))
        assertNull(zoneTabKeyNodeId(null))
        assertNull(zoneTabKeyNodeId("all"))
        assertNull(zoneTabKeyNodeId("follow"))
        assertNull(zoneTabKeyNodeId("z"))
        assertNull(zoneTabKeyNodeId("z-1"))
        assertNull(zoneTabKeyNodeId("z0"))
        assertNull(zoneTabKeyNodeId("zabc"))
    }

    @Test
    fun resolveInitialBoardType_restoresMatchingPrefixAndAvailableType() {
        assertEquals(
            "album",
            resolveInitialBoardType("z100|album", "z100", listOf("action", "album"))
        )
        assertEquals(
            "action",
            resolveInitialBoardType("all|action", "all", listOf("action"))
        )
    }

    @Test
    fun resolveInitialBoardType_prefixMismatchFallsBackToFirst() {

        assertEquals(
            "action",
            resolveInitialBoardType("z200|album", "z100", listOf("action", "album"))
        )
        assertEquals(
            "action",
            resolveInitialBoardType("all|album", "follow", listOf("action", "album"))
        )
    }

    @Test
    fun resolveInitialBoardType_typeNotInBoardsFallsBackToFirst() {

        assertEquals("action", resolveInitialBoardType("all|hobby", "all", listOf("action")))
        assertEquals(
            "action",
            resolveInitialBoardType("all|hobby", "all", listOf("action", "album"))
        )
    }

    @Test
    fun resolveInitialBoardType_illegalOrMissingSavedValueFallsBackToFirst() {
        val boards = listOf("action", "album")
        assertEquals("action", resolveInitialBoardType(null, "all", boards))
        assertEquals("action", resolveInitialBoardType("", "all", boards))
        assertEquals("action", resolveInitialBoardType("garbage", "all", boards))
    }

    @Test
    fun resolveInitialBoardType_emptyBoardsReturnsEmpty() {
        assertEquals("", resolveInitialBoardType("all|action", "all", emptyList()))
        assertEquals("", resolveInitialBoardType(null, "all", emptyList()))
    }
}
