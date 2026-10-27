package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.util.CdnUrlUtils
import kotlinx.serialization.json.Json

class ZoneRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: ZoneRepository
    private lateinit var preferences: ViewPreferencesManager
    private lateinit var stores: TestDataStores

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        preferences = ViewPreferencesManager(stores.createDataStore("view_prefs"))
        repository = ZoneRepository(
        apiService = api,
            viewPreferencesManager = preferences
            )
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
    }

    @Test
    fun getZoneHomeTabs_success_updatesFlows() = runTest {
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
        // 对齐生产真实逆向格式：内层嵌套 zone 实体
                mock.respondWhen(Regex.fromLiteral("api/zone/home-tabs/visited"), """{"success":true,"code":200,"msg":"ok"}""")
        mock.respondWhen(Regex.fromLiteral("api/zone/info"), """
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
                            """.trimIndent())


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
                mock.respondWhen(Regex(".*"), "")


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
        // 1. 模拟关注成功
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"focus":true,"count":10}}""")
        val followResult = repository.toggleFocus(100L, "手办", "figure.jpg")
        assertTrue(followResult.isSuccess)
        assertTrue(followResult.getOrThrow().focus)
        assertEquals(1, repository.focusZonesFlow.value.size)
        assertEquals(100L, repository.focusZonesFlow.value.first().nodeId)

        // 2. 模拟取消关注成功
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"focus":false,"count":9}}""")
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

        val request = mock.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.recordedPath!!.contains("zoneNodeId=100"))
        assertTrue(request.recordedPath!!.contains("order=upd"))
    }

    @Test
    fun getZoneItems_success_parsesItems() = runTest {
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

        val request = mock.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.recordedPath!!.contains("nodeId=100"))
        assertTrue(request.recordedPath!!.contains("itemType=company"))
    }
}
