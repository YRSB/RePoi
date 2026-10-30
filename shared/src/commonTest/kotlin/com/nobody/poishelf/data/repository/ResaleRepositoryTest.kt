package com.nobody.poishelf.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 二手仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ResaleRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test）。
 */
class ResaleRepositoryTest {

    private fun setup(): Pair<MockApi, ResaleRepository> {
        val mock = MockApi()
        return mock to ResaleRepository(mock.api())
    }

    @Test
    fun searchResaleSuccess() = runTest {
        val (mock, repository) = setup()
        val mockJson = """
        {
          "success": true,
          "code": 200,
          "msg": "操作成功",
          "data": {
            "list": [
              {
                "id": 1001,
                "price": "999",
                "purityStr": "全新未拆",
                "location": "北京市",
                "detail": "原封现货",
                "addDate": "2026-10-24 12:00:00"
              }
            ]
          }
        }
        """.trimIndent()

        mock.enqueue(mockJson)

        val result = repository.searchResale(
            zones = 33476094L,
            type = 1,
            keyword = "测试"
        )

        assertTrue(result.isSuccess)
        val payload = result.getOrNull()!!
        assertEquals(1, payload.list.size)
        assertEquals(1001L, payload.list[0].id)
        assertEquals("¥999", payload.list[0].displayPriceText)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/resale/search", recorded.path)
        val body = recorded.formBodyText()
        assertTrue(body.contains("zones=33476094"))
        assertTrue(body.contains("type=1"))
    }

    @Test
    fun searchResaleWithItemId() = runTest {
        val (mock, repository) = setup()
        val mockJson = """
        {
          "success": true,
          "code": 200,
          "msg": "操作成功",
          "data": {
            "list": []
          }
        }
        """.trimIndent()

        mock.enqueue(mockJson)

        val result = repository.searchResale(
            hobbyId = 125698L,
            type = 1
        )

        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/resale/search", recorded.path)
        val body = recorded.formBodyText()
        assertTrue(body.contains("itemId=125698"), "Request body must contain itemId parameter")
        assertFalse(body.contains("hobbyId="), "Request body must not contain invalid hobbyId parameter")
    }

    @Test
    fun getResaleDetailSuccess() = runTest {
        val (mock, repository) = setup()
        val mockJson = """
        {
          "success": true,
          "code": 200,
          "msg": "操作成功",
          "data": {
            "resale": {
              "id": 40855607,
              "price": "850",
              "purityStr": "无暇",
              "location": "菏泽市",
              "detail": "拆摆无暇，卖出回血",
              "addDate": "2026-10-24 13:47:46",
              "link": "https://m.tb.cn/xxx",
              "pic": "2023/10/p1.jpg,2023/10/p2.jpg",
              "type": 1,
              "linkItems": [
                {
                  "itemNodeId": 125698,
                  "name": "瓶儿",
                  "cover": "2026/06/c1.jpg"
                }
              ],
              "user": {
                "userId": 228637,
                "nickname": "leafwalker",
                "header": "2023/02/h1.gif",
                "level": 3
              }
            }
          }
        }
        """.trimIndent()

        mock.enqueue(mockJson)

        val result = repository.getResaleDetail(40855607L)

        assertTrue(result.isSuccess)
        val detail = result.getOrNull()!!
        assertEquals(40855607L, detail.id)
        assertEquals("¥850", detail.displayPriceText)
        assertEquals("无暇", detail.purityStr)
        assertEquals("菏泽市", detail.location)
        assertEquals("拆摆无暇，卖出回血", detail.detail)
        assertEquals("leafwalker", detail.user?.nickname)
        assertEquals(3, detail.user?.level)
        assertEquals(1, detail.linkItems.size)
        assertEquals("瓶儿", detail.firstHobby?.displayName)
        assertEquals(2, detail.userPics.size)
        assertTrue(detail.hasExternalLink)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/resale/data/get?nodeId=40855607", recorded.path)
    }
}
