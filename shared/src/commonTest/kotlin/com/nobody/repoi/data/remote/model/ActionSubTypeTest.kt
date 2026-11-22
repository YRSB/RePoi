package com.nobody.repoi.data.remote.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        assertEquals(apiValues.size, apiValues.toSet().size)
        assertEquals(ActionSubType.entries.toSet(), ActionSubType.ordered.toSet())
    }

    @Test
    fun fromApi_acceptsEveryApiValueAndRejectsLegacyAll() {
        ActionSubType.entries.forEach { subType ->
            assertEquals(subType, ActionSubType.fromApi(subType.apiValue))
        }

        assertNull(ActionSubType.fromApi("all"))
        assertNull(ActionSubType.fromApi("unknown"))
        assertNull(ActionSubType.fromApi(null))
        assertNull(ActionSubType.fromApi(""))
    }

    @Test
    fun fromPersisted_mapsLegacyAllToAllInfo() {
        assertEquals(ActionSubType.AllInfo, ActionSubType.fromPersisted("all"))
        assertEquals("allInfo", ActionSubType.normalizeKey("all"))

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
