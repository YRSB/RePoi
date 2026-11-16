package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneQueryScope
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 一级分类 → 查询作用域的纯函数契约（issue #156 恢复规则 / #162 聚合参数）。
 *
 * 板块可见性矩阵的断言在 `ui/board/ZoneBoardCapabilityTest`；本类只验证 `ui.home` 剩下的
 * 真实逻辑：tabKey/zoneIds 解析与非法值回落，防止调用点散拼 zone 参数。
 */
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
    fun toZoneQueryScope_emptyFollow_keepsEmptyAndIsShortCircuitedUpstream() {
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
