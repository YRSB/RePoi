package com.nobody.repoi.data.remote.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 动态筛选共享定义（[ActionSubType]）单元测试（纯逻辑，无 MockApi）。
 *
 * 覆盖 #153 验收：9 项顺序与 2.43 文案、旧键 `all` 兼容映射、非法/空值回落、
 * 请求键值有效且唯一。
 */
class ActionSubTypeTest {

    @Test
    fun ordered_matches2_43OrderAndLabels() {
        assertEquals(
            listOf(
                "recommend" to "推荐",
                "comment" to "评论",
                "allInfo" to "全部情报",
                "confirm" to "制作",
                "official_pic" to "更图",
                "preorder" to "开订",
                "delay" to "延期",
                "release" to "出荷",
                "reorder" to "再版"
            ),
            ActionSubType.ordered.map { it.apiValue to it.label }
        )
    }

    @Test
    fun ordered_hasNineDistinctApiValues() {
        val apiValues = ActionSubType.ordered.map { it.apiValue }
        assertEquals(9, apiValues.size)
        // 键值一一对应、无重复
        assertEquals(apiValues.size, apiValues.toSet().size)
        assertEquals(ActionSubType.entries.toSet(), ActionSubType.ordered.toSet())
    }

    @Test
    fun fromApi_acceptsEveryApiValueAndRejectsLegacyAll() {
        ActionSubType.entries.forEach { subType ->
            assertEquals(subType, ActionSubType.fromApi(subType.apiValue))
        }
        // 旧键 all 不是合法协议键值，不能被当作可直接发送的值
        assertNull(ActionSubType.fromApi("all"))
        assertNull(ActionSubType.fromApi("unknown"))
        assertNull(ActionSubType.fromApi(null))
        assertNull(ActionSubType.fromApi(""))
    }

    @Test
    fun fromPersisted_mapsLegacyAllToAllInfo() {
        assertEquals(ActionSubType.AllInfo, ActionSubType.fromPersisted("all"))
        assertEquals("allInfo", ActionSubType.normalizeKey("all"))
        // 新键幂等：allInfo 不会被二次改写
        assertEquals(ActionSubType.AllInfo, ActionSubType.fromPersisted("allInfo"))
        assertEquals("allInfo", ActionSubType.normalizeKey("allInfo"))
    }

    @Test
    fun fromPersisted_fallsBackToDefaultOnInvalidOrBlank() {
        assertEquals(ActionSubType.Recommend, ActionSubType.DEFAULT)
        assertEquals(ActionSubType.DEFAULT, ActionSubType.fromPersisted(null))
        assertEquals(ActionSubType.DEFAULT, ActionSubType.fromPersisted(""))
        assertEquals(ActionSubType.DEFAULT, ActionSubType.fromPersisted("zzz_bogus"))
        assertEquals("recommend", ActionSubType.normalizeKey(null))
        assertEquals("recommend", ActionSubType.normalizeKey(""))
        assertEquals("recommend", ActionSubType.normalizeKey("zzz_bogus"))
    }

    @Test
    fun everyEntryRoundTripsThroughPersistedKey() {
        ActionSubType.entries.forEach { subType ->
            assertEquals(subType, ActionSubType.fromPersisted(subType.apiValue))
            assertEquals(subType.apiValue, ActionSubType.normalizeKey(subType.apiValue))
        }
    }
}
