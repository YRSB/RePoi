package com.nobody.repoi.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 首页三级选择恢复规则纯函数测试（#156）。
 *
 * 覆盖：二级键编码/解码往返与非法值、一级三分支回落（follow / 不可用专区 / 合法专区）、
 * 二级前缀不匹配与 type 不在可用板块列表、空板块列表退化。
 */
class HomeSelectionRestoreTest {

    // ── 编码 / 解码 ─────────────────────────────────────────────────────

    @Test
    fun encodeBoardKey_producesTabKeyAndType() {
        assertEquals("all|action", encodeBoardKey("all", "action"))
        assertEquals("follow|album", encodeBoardKey("follow", "album"))
        assertEquals("z33476094|hobby", encodeBoardKey("z33476094", "hobby"))
        // 首尾空白归一，避免脏值进入偏好
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
        // 缺少分隔符
        assertNull(decodeBoardKey("action"))
        assertNull(decodeBoardKey("all action"))
        // 分隔符两侧为空
        assertNull(decodeBoardKey("|action"))
        assertNull(decodeBoardKey("all|"))
        assertNull(decodeBoardKey(" | "))
        // 段数不为 2
        assertNull(decodeBoardKey("all|action|album"))
        // 仅一级的旧值（无二级板块）按非法处理
        assertNull(decodeBoardKey("z100"))
    }

    // ── 一级恢复 ───────────────────────────────────────────────────────

    private val availableTabKeys = listOf("all", "follow", "z100", "z200")

    @Test
    fun resolveInitialTabKey_followFallsBackToAll() {
        // 冷启动规则：上次为「关注」不恢复，回落「全部」
        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey("follow", availableTabKeys))
        // 即使「关注」在可用集合中也必须回落
        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey(ZONE_TAB_KEY_FOLLOW, listOf("follow")))
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
        // 专区已取关 / 被首页设置隐藏 → 回落「全部」
        assertEquals(ZONE_TAB_KEY_ALL, resolveInitialTabKey("z999", availableTabKeys))
    }

    @Test
    fun resolveInitialTabKey_allAlwaysRestoresAll() {
        // 「全部」可在设置隐藏后仍经切换器选中，恢复时保持 all 而非退化成首个分类
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

    // ── 二级恢复 ───────────────────────────────────────────────────────

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
        // 上次在别的专区选的板块不跨专区恢复
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
        // 前缀匹配但该板块当前不可用（未接入 / 服务端未下发）→ 该专区首板块
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
