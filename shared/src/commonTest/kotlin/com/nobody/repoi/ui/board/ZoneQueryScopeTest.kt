package com.nobody.repoi.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 查询作用域三态的参数派生矩阵（issue #155 / #157–#162 共用约定）。
 *
 * 依据 `docs/research/aggregate-api-verification.md` §4.3：
 * - 全站聚合省略 zone 参数；
 * - 关注聚合使用 `zones` 逗号串（并集）；动态使用 `zoneIds`；讨论只认 `zoneNodeId` 单值；
 * - 空关注必须短路，`zonesParam()` 不得产出空串（服务端把空串当「不过滤 = 全站」）。
 */
class ZoneQueryScopeTest {

    @Test
    fun all_omitsEveryZoneFilter() {
        val scope = ZoneQueryScope.All

        assertNull(scope.zonesParam())
        assertNull(scope.zoneNodeId())
        assertEquals("all", scope.homeScope())
        assertEquals("all", scope.stateKey())
        assertFalse(scope.isFollowEmpty)
    }

    @Test
    fun single_derivesNodeIdForBothTracks() {
        val scope = ZoneQueryScope.Single(33476094L)

        assertEquals("33476094", scope.zonesParam())
        assertEquals(33476094L, scope.zoneNodeId())
        assertNull(scope.homeScope())
        assertEquals("z33476094", scope.stateKey())
        assertFalse(scope.isFollowEmpty)
    }

    @Test
    fun follow_derivesCommaSeparatedUnion() {
        val scope = ZoneQueryScope.Follow(listOf(33476094L, 39989602L, 39228575L))

        assertEquals("33476094,39989602,39228575", scope.zonesParam())
        assertNull(scope.zoneNodeId())
        assertEquals("follow", scope.homeScope())
        assertEquals("follow:33476094,39989602,39228575", scope.stateKey())
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
        // 空关注必须由上层短路：null 而不是空串（空串会被服务端当成全站）
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
        // 同一作用域多次派生结果一致，可直接用于滚动/筛选记忆键
        assertEquals(ZoneQueryScope.Single(123L).stateKey(), ZoneQueryScope.Single(123L).stateKey())
        assertEquals(
            ZoneQueryScope.Follow(listOf(1L, 2L)).stateKey(),
            ZoneQueryScope.Follow(listOf(1L, 2L)).stateKey()
        )
    }

    @Test
    fun zoneFilters_areExclusiveOnTheWire() {
        // 依据 §2.2：非空 homeScope 会被服务端用于覆盖 zones。
        // All 只带 homeScope、Single 只带 zones；Follow 的 zones 非空时，调用方必须**不下发** homeScope
        // （homeScope()="follow" 仅作语义标记，供登录态产品关注态复用）。
        assertNull(ZoneQueryScope.All.zonesParam())
        assertEquals("all", ZoneQueryScope.All.homeScope())

        assertNull(ZoneQueryScope.Single(123L).homeScope())
        assertEquals("123", ZoneQueryScope.Single(123L).zonesParam())

        val follow = ZoneQueryScope.Follow(listOf(1L, 2L))
        assertEquals("1,2", follow.zonesParam())
        assertEquals("follow", follow.homeScope())
    }
}
