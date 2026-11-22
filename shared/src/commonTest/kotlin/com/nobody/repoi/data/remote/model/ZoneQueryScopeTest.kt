package com.nobody.repoi.data.remote.model

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
        // 记忆键归一：去重 + 升序（与请求参顺序无关，见 stateKey KDoc）
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
    fun stateKey_followNormalizesOrderAndDuplicates() {
        // 关注集合顺序由 ZoneTabsSettings.sort（recent/custom）决定：换序不得产生第二个记忆键，
        // 否则 custom 排序调整后滚动位置与筛选折叠比例丢失（旧键无人回收）。
        val canonical = ZoneQueryScope.Follow(listOf(1L, 2L, 3L)).stateKey()

        assertEquals("follow:1,2,3", canonical)
        assertEquals(canonical, ZoneQueryScope.Follow(listOf(3L, 1L, 2L)).stateKey())
        assertEquals(canonical, ZoneQueryScope.Follow(listOf(2L, 3L, 1L, 2L)).stateKey())
        // 请求参仍保持原序：归一只作用于记忆键
        assertEquals("3,1,2", ZoneQueryScope.Follow(listOf(3L, 1L, 2L)).zonesParam())
    }

    @Test
    fun zoneParams_areScopeSpecific() {
        assertNull(ZoneQueryScope.All.zonesParam())
        assertEquals("123", ZoneQueryScope.Single(123L).zonesParam())

        val follow = ZoneQueryScope.Follow(listOf(1L, 2L))
        assertEquals("1,2", follow.zonesParam())
        // 关注聚合没有单值 nodeId：讨论板块因此不接收关注作用域（关注态隐藏该板块）
        assertNull(follow.zoneNodeId())
    }
}
