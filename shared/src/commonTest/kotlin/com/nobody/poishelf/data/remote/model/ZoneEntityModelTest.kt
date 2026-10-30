package com.nobody.poishelf.data.remote.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 针对 api/zone/items 真实下发报文的反序列化单元测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ZoneEntityModelTest`（逐用例，JUnit4 → kotlin.test）。
 */
class ZoneEntityModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun testDeserializeZoneCompanyItems() {
        val rawJson = """
        {
          "@id": "1",
          "total": 427,
          "hasMore": true,
          "list": [
            {
              "firstChar": "A",
              "name_zh": ["Alter"],
              "itemType": "company",
              "nameCN": "Alter",
              "cover": "2025/01/f719fa83bebb4cbfb9590fc845b992f2.jpg",
              "@id": "1",
              "id": 2518,
              "itemId": 26,
              "hits": 121202,
              "name": "アルター"
            },
            {
              "firstChar": "N",
              "nameCN": "Native",
              "cover": "2019/03/e81583a2f577449abc9117a7ec43d9cb.png",
              "id": 2531,
              "itemId": 41,
              "hits": 111440,
              "name": "ネイティブ"
            }
          ]
        }
        """.trimIndent()

        val payload = json.decodeFromString<ZoneItemsPayload>(rawJson)

        assertEquals(427, payload.total)
        assertTrue(payload.hasMore)
        assertEquals(2, payload.list.size)

        val item1 = payload.list[0]
        assertEquals(2518L, item1.id)
        assertEquals(26L, item1.itemId)
        assertEquals(26L, item1.targetId)
        assertEquals("Alter", item1.displayName)
        assertEquals("2025/01/f719fa83bebb4cbfb9590fc845b992f2.jpg", item1.cover)
        assertEquals("浏览 12.1万", item1.displayHitsText)

        val item2 = payload.list[1]
        assertEquals(41L, item2.targetId)
        assertEquals("Native", item2.displayName)
        assertEquals("浏览 11.1万", item2.displayHitsText)
    }

    @Test
    fun testDisplayNameAndHitsFormatting() {
        val itemWithNameOnly = ZoneEntityItem(
            id = 100L,
            itemId = 0L,
            name = "原生名称",
            nameCN = "",
            hits = 8500L
        )
        assertEquals("原生名称", itemWithNameOnly.displayName)
        assertEquals(100L, itemWithNameOnly.targetId)
        assertEquals("浏览 8500", itemWithNameOnly.displayHitsText)

        val itemWithZeroHits = ZoneEntityItem(
            id = 200L,
            name = null,
            nameCN = null,
            hits = 0L
        )
        assertEquals("未命名", itemWithZeroHits.displayName)
        assertEquals("浏览 0", itemWithZeroHits.displayHitsText)

        val itemWithExactTenThousands = ZoneEntityItem(
            id = 300L,
            hits = 20000L
        )
        assertEquals("浏览 2万", itemWithExactTenThousands.displayHitsText)
    }

    @Test
    fun testEmptyPayloadHandling() {
        val emptyJson = """{"total": 0, "hasMore": false, "list": []}"""
        val payload = json.decodeFromString<ZoneItemsPayload>(emptyJson)
        assertEquals(0, payload.total)
        assertFalse(payload.hasMore)
        assertTrue(payload.list.isEmpty())
    }
}
