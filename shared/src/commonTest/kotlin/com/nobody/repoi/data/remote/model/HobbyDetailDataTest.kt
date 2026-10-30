package com.nobody.repoi.data.remote.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 手办详情 DTO 的节点 ID 解析规则测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HobbyDetailDataTest`（逐用例，JUnit4 → kotlin.test）。
 */
class HobbyDetailDataTest {

    @Test
    fun actualNodeId_fallsBackToIdWhenNodeIdMissing() {
        val detailOnlyId = HobbyDetailData(
            id = 5483525L,
            nodeId = 0L,
            itemId = 59570L
        )
        assertEquals(5483525L, detailOnlyId.actualNodeId)
    }

    @Test
    fun actualNodeId_prefersNodeIdWhenPresent() {
        val detailWithNodeId = HobbyDetailData(
            id = 123L,
            nodeId = 99999L,
            itemId = 59570L
        )
        assertEquals(99999L, detailWithNodeId.actualNodeId)
    }
}
