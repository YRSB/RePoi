package com.nobody.repoi.data.remote.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 针对 api/resale/search 真实下发报文的反序列化单元测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ResaleSearchModelTest`（逐用例，JUnit4 → kotlin.test）。
 */
class ResaleSearchModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun testDeserializeResaleSearchPayload() {
        val rawJson = """
        {
          "list": [
            {
              "itemType": "resale_item",
              "item": [
                {
                  "cover": "2026/06/741317f1737a4f4892f1363382e7f48e.jpg",
                  "itemId": 125698,
                  "itemType": "hobby",
                  "name": "ソフィア・F・シャーリング",
                  "nameCN": "索菲亚· F· 希琳",
                  "id": 37888435
                }
              ],
              "purity": 11,
              "addDateStr": "刚刚",
              "type": 1,
              "addDate": "2026-10-24 13:47:46",
              "zoneIds": [33476094],
              "price": "1950",
              "purityStr": "转单",
              "location": "上海市",
              "id": 40855607,
              "state": 1,
              "detail": "原价转一单蜜瓜家的",
              "user": {
                "nickname": "Fisher",
                "header": "2023/02/11e2871341fb4c92b81457324cae3c61.gif",
                "userId": 228637
              }
            },
            {
              "itemType": "resale_item",
              "purity": 9,
              "pic": "2021/05/390959069be4475cbc125fbb398799d2.png",
              "type": 1,
              "addDate": "2026-10-24 13:20:48",
              "price": "900",
              "purityStr": "仅拆",
              "location": "上海",
              "id": 9040617,
              "state": 1,
              "detail": "仅拆外盒",
              "user": {
                "nickname": "不高兴的悠悠球",
                "header": "2020/03/18ea7e12406944c59e453d0073af34e8.jpg",
                "userId": 120556
              }
            }
          ]
        }
        """.trimIndent()

        val payload = json.decodeFromString<ResaleSearchPayload>(rawJson)

        assertEquals(2, payload.list.size)

        // Item 1: 关联手办条目，且无 pic
        val item1 = payload.list[0]
        assertEquals(40855607L, item1.id)
        assertEquals("¥1950", item1.displayPriceText)
        assertEquals("转单", item1.purityStr)
        assertEquals(11, item1.purity)
        assertEquals("上海市", item1.location)
        assertEquals("原价转一单蜜瓜家的", item1.detail)
        assertEquals("2026-10-24 13:47:46", item1.addDate)
        assertEquals("2026/06/741317f1737a4f4892f1363382e7f48e.jpg", item1.displayCover)
        assertEquals("https://rfx.hpoi.net/gk/cover/n/2026/06/741317f1737a4f4892f1363382e7f48e.jpg", item1.displayCoverUrl)
        assertEquals(125698L, item1.targetItemId)
        assertEquals("Fisher", item1.user?.nickname)
        assertEquals(228637L, item1.user?.userId)

        // Item 2: 未关联手办，仅有自定义实物图 pic
        val item2 = payload.list[1]
        assertEquals(9040617L, item2.id)
        assertEquals("¥900", item2.displayPriceText)
        assertEquals("仅拆", item2.purityStr)
        assertEquals("2021/05/390959069be4475cbc125fbb398799d2.png", item2.displayCover)
        assertEquals("https://rfx.hpoi.net/gk/resale/n/2021/05/390959069be4475cbc125fbb398799d2.png", item2.displayCoverUrl)
        assertTrue(item2.hasUserPic)
        assertEquals(listOf("https://rfx.hpoi.net/gk/resale/n/2021/05/390959069be4475cbc125fbb398799d2.png"), item2.userPics)
        assertEquals(0L, item2.targetItemId)
    }

    @Test
    fun testCoverPriorityWhenBothCoverAndPicExist() {
        // 对齐 Hpoi 官方：同时存在手办封面与卖家实物图时，卡片封面必须优先展示手办封面
        val itemWithBoth = ResaleSearchItem(
            id = 123L,
            price = "820",
            pic = "2026/10/pic1.png,2026/10/pic2.png",
            item = listOf(
                ResaleHobbyData(
                    itemId = 999L,
                    nameCN = "碧蓝航线 光荣 凉夜香雪",
                    cover = "2026/06/hobby_cover.jpg"
                )
            )
        )
        // 卡片封面优先展示手办官图
        assertEquals("https://rfx.hpoi.net/gk/cover/n/2026/06/hobby_cover.jpg", itemWithBoth.displayCoverUrl)
        // 卖家附图支持多张逗号分隔并拼装为 resale/n/ 规范路径
        assertTrue(itemWithBoth.hasUserPic)
        assertEquals(2, itemWithBoth.userPics.size)
        assertEquals("https://rfx.hpoi.net/gk/resale/n/2026/10/pic1.png", itemWithBoth.userPics[0])
        assertEquals("https://rfx.hpoi.net/gk/resale/n/2026/10/pic2.png", itemWithBoth.userPics[1])
    }

    @Test
    fun testFallbackPriceAndCover() {
        val blankItem = ResaleSearchItem(
            id = 1L,
            price = "",
            pic = null,
            item = null
        )
        assertEquals("面议", blankItem.displayPriceText)
        assertNull(blankItem.displayCover)
        assertEquals(0L, blankItem.targetItemId)
    }
}
