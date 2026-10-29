package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.local.TestDataStore
import com.nobody.poishelf.data.local.ViewPreferencesManager
import com.nobody.poishelf.util.CdnUrlUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 专区仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ZoneRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi（含按路径分发 `route`，等价 Dispatcher）；
 * Mockito `mock(ViewPreferencesManager)` → 真实 ViewPreferencesManager
 *（[TestDataStore] 隔离目录；`getCached*Json()` 生产默认即 null，与桩语义一致）；
 * JUnit4 → kotlin.test）。
 */
class ZoneRepositoryTest {

    private fun setup(): Pair<MockApi, ZoneRepository> {
        val mock = MockApi()
        val preferences = ViewPreferencesManager(TestDataStore.viewPrefs())
        return mock to ZoneRepository(mock.api(), preferences)
    }

    @Test
    fun getZoneHomeTabs_success_updatesFlows() = runTest {
        val (mock, repository) = setup()
        // 对齐生产真实抓包/逆向格式：hiddenZoneIds 与 customOrder 为 Long 数组 []
        val mockBody = """
            {
                "success": true,
                "code": 200,
                "msg": "ok",
                "data": {
                    "settings": {
                        "showAll": true,
                        "sort": "recent",
                        "hiddenZoneIds": [],
                        "customOrder": [],
                        "recentZoneIds": []
                    },
                    "focusZones": [
                        { "nodeId": 100, "name": "figure", "nameCN": "手办", "icon": "zone/figure.png" }
                    ],
                    "groups": [
                        {
                            "name": "模玩产品",
                            "zones": [
                                { "nodeId": 200, "name": "model", "nameCN": "机娘", "icon": "model.png" }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()
        mock.enqueue(mockBody)

        val result = repository.getZoneHomeTabs(forceRefresh = true)
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(1, payload.focusZones?.size)
        assertEquals(100L, payload.focusZones?.first()?.nodeId)
        assertEquals("手办", payload.focusZones?.first()?.displayName)

        // 验证 Flow 是否同步更新
        assertEquals(1, repository.focusZonesFlow.value.size)
        assertEquals(100L, repository.focusZonesFlow.value.first().nodeId)
        assertEquals("recent", repository.settingsFlow.value.sort)
        assertTrue(repository.settingsFlow.value.hiddenZoneIds.isEmpty())
    }

    @Test
    fun getZoneInfo_success_returnsZoneInfoPayload() = runTest {
        val (mock, repository) = setup()
        // 对齐生产真实逆向格式：内层嵌套 zone 实体
        mock.route(
            "api/zone/home-tabs/visited",
            """{"success":true,"code":200,"msg":"ok"}"""
        )
        mock.route(
            "api/zone/info",
            """
            {
                "success": true,
                "code": 200,
                "msg": "ok",
                "data": {
                    "zone": {
                        "nodeId": 100,
                        "name": "figure",
                        "nameCN": "手办",
                        "desc": "这就是手办专区",
                        "icon": "zone/figure.png",
                        "cover": "zone/cover.jpg"
                    },
                    "focusState": true,
                    "followCount": 1728,
                    "tabs": ["action", "album", "posts", "hobby"]
                }
            }
            """.trimIndent()
        )

        val result = repository.getZoneInfo(100L)
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(100L, info.zone?.nodeId)
        assertEquals("手办", info.displayName)
        assertEquals("这就是手办专区", info.zone?.desc)
        assertTrue(info.focusState)
        assertEquals(1728L, info.followCount)
        assertEquals(4, info.tabs.size)
    }

    @Test
    fun getZoneInfo_productionObjectTabs_deserializesCorrectly() = runTest {
        val (mock, repository) = setup()
        mock.route("api/zone/home-tabs/visited", """{"success":true,"code":200,"msg":"ok"}""")
        mock.route(
            "api/zone/info",
            """
            {
                "success": true,
                "code": 200,
                "msg": "ok",
                "data": {
                    "zone": {
                        "nodeId": 39989602,
                        "name": "26年10月新番",
                        "nameCN": "26年10月新番",
                        "description": "2026年10月新番",
                        "icon": "zone/icon/2026/09/40daefb0c738482484352184c0267618.jpg",
                        "cover": "zone/cover/2026/09/5f589d366a0e4bf9ae3c76a480d6403a.jpg",
                        "webCover": "zone/web-cover/2026/09/6bc555853aee439681482bf42c6e2cf7.jpg"
                    },
                    "focusState": false,
                    "followCount": 35,
                    "tabs": [
                        { "count": 44, "type": "action", "title": "动态" },
                        { "count": 3, "type": "album", "title": "相册" },
                        { "count": 65, "type": "works", "title": "作品" }
                    ]
                }
            }
            """.trimIndent()
        )

        val result = repository.getZoneInfo(39989602L)
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(39989602L, info.zone?.nodeId)
        assertEquals("26年10月新番", info.displayName)
        assertEquals("2026年10月新番", info.zone?.displayDesc)
        assertEquals("zone/cover/2026/09/5f589d366a0e4bf9ae3c76a480d6403a.jpg", info.zone?.cover)
        assertEquals(3, info.tabs.size)
        assertEquals("action", info.tabs[0].type)
        assertEquals("动态", info.tabs[0].title)
        assertEquals(44L, info.tabs[0].count)
    }

    @Test
    fun toggleFocus_follow_and_unfollow_updatesFlow() = runTest {
        val (mock, repository) = setup()
        // 1. 模拟关注成功
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"focus":true,"count":10}}"""
        )
        val followResult = repository.toggleFocus(100L, "手办", "figure.jpg")
        assertTrue(followResult.isSuccess)
        assertTrue(followResult.getOrThrow().focus)
        assertEquals(1, repository.focusZonesFlow.value.size)
        assertEquals(100L, repository.focusZonesFlow.value.first().nodeId)

        // 2. 模拟取消关注成功
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"focus":false,"count":9}}"""
        )
        val unfollowResult = repository.toggleFocus(100L)
        assertTrue(unfollowResult.isSuccess)
        assertFalse(unfollowResult.getOrThrow().focus)
        assertTrue(repository.focusZonesFlow.value.isEmpty())
    }

    @Test
    fun formatZoneIconUrl_rules() {
        assertNull(CdnUrlUtils.getZoneIconUrl(null))
        assertNull(CdnUrlUtils.getZoneIconUrl(""))

        // 以 zone/ 开头
        val zoneUrl = CdnUrlUtils.getZoneIconUrl("zone/figure.png")
        assertEquals("https://rfx.hpoi.net/gk/zone/figure.png", zoneUrl)

        // 非 zone/ 开头
        val normalUrl = CdnUrlUtils.getZoneIconUrl("cover.png")
        assertEquals("https://rfx.hpoi.net/gk/cover/s/cover.png", normalUrl)

        // 完整 http url 原样返回
        val httpUrl = CdnUrlUtils.getZoneIconUrl("https://example.com/icon.png")
        assertEquals("https://example.com/icon.png", httpUrl)
    }

    @Test
    fun formatZoneCoverUrl_rules() {
        assertNull(CdnUrlUtils.getZoneCoverUrl(null))

        // 以 zone/ 开头
        val zoneCover = CdnUrlUtils.getZoneCoverUrl("zone/banner.jpg")
        assertEquals("https://rfx.hpoi.net/gk/zone/banner.jpg", zoneCover)

        // 非 zone/ 开头
        val normalCover = CdnUrlUtils.getZoneCoverUrl("banner.jpg")
        assertEquals("https://rfx.hpoi.net/gk/cover/n/banner.jpg", normalCover)
    }

    @Test
    fun getZonePosts_success_parsesPostList() = runTest {
        val (mock, repository) = setup()
        val mockJson = """
            {
              "success": true,
              "code": 200,
              "msg": "ok",
              "data": {
                "list": [
                  {
                    "id": 998877,
                    "title": "ALTER 高雄发售讨论",
                    "userName": "指挥官",
                    "discussCount": 18,
                    "topTime": 1700000000
                  }
                ]
              }
            }
        """.trimIndent()
        mock.enqueue(mockJson)

        val result = repository.getZonePosts(zoneNodeId = 100L, order = "upd", page = 1)
        assertTrue(result.isSuccess)
        val posts = result.getOrThrow()
        assertEquals(1, posts.size)
        assertEquals(998877L, posts[0].id)
        assertEquals("ALTER 高雄发售讨论", posts[0].title)
        assertEquals("指挥官", posts[0].userName)
        assertEquals(18, posts[0].discussCount)
        assertTrue(posts[0].isPinned)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("zoneNodeId=100"))
        assertTrue(recorded.path!!.contains("order=upd"))
    }

    @Test
    fun getZoneItems_success_parsesItems() = runTest {
        val (mock, repository) = setup()
        val mockJson = """
            {
              "success": true,
              "code": 200,
              "msg": "ok",
              "data": {
                "total": 427,
                "hasMore": true,
                "list": [
                  {
                    "id": 2518,
                    "itemId": 26,
                    "name": "アルター",
                    "nameCN": "Alter",
                    "cover": "2025/01/f719fa83bebb4cbfb9590fc845b992f2.jpg",
                    "hits": 121202
                  }
                ]
              }
            }
        """.trimIndent()
        mock.enqueue(mockJson)

        val result = repository.getZoneItems(nodeId = 100L, itemType = "company", page = 1)
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(427, payload.total)
        assertTrue(payload.hasMore)
        assertEquals(1, payload.list.size)
        assertEquals(26L, payload.list[0].targetId)
        assertEquals("Alter", payload.list[0].displayName)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.contains("nodeId=100"))
        assertTrue(recorded.path!!.contains("itemType=company"))
    }
}
