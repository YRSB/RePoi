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
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.serialization.json.Json

/**
 * 讨论板版块仓储单测：关注列表解析、关注取关、排序保存、版块详情与帖子流查询。
 */
class ForumRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: ForumRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var stores: TestDataStores

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        tokenManager = TokenManager(stores.createDataStore("token"))
        tokenManager.currentToken = "utoken-test"
        repository = ForumRepository(api, tokenManager)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
    }

    @Test
    fun getFocusForums_success_parsesPlatesAndStringCount() = runTest {
        mock.enqueue(
                """
                {
                    "success": true,
                    "code": 200,
                    "msg": "ok",
                    "data": {
                        "forumList": [
                            {"id": 11, "name": "灌水吐槽", "detail": "不吐不快", "focusUserCount": "512", "plateState": 1, "levelLimit": 0, "addTime": "2025-01-01 00:00:00", "postsCount": 99},
                            {"id": 12, "name": "综合讨论", "detail": "", "focusUserCount": 459, "plateState": 0, "levelLimit": 3, "addTime": "", "postsCount": 10}
                        ]
                    }
                }
                """.trimIndent()
            )

        val plates = repository.getFocusForums(forumPage = 1).getOrThrow()

        assertEquals(2, plates.size)
        assertEquals("灌水吐槽", plates[0].name)
        assertEquals(512, plates[0].effectiveFocusCount)
        assertTrue(plates[0].isFollowed)
        assertFalse(plates[0].isNewPlate)
        assertEquals(459, plates[1].effectiveFocusCount)
        assertFalse(plates[1].isFollowed)
        val recorded = mock.takeRequest()
        assertTrue(recorded.recordedPath!!.startsWith("/api/forum/focus/list"))
        assertTrue(recorded.recordedBody().contains("forumPage=1"))
    }

    @Test
    fun getUnfocusForums_error_throwsApiException() = runTest {
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录失效","data":{}}""")

        val result = repository.getUnfocusForums()

        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun toggleFocus_follow_postsStateOne() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.toggleFocus(forumNodeId = 11L, follow = true)

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.recordedPath!!.startsWith("/api/forum/focus"))
        val body = recorded.recordedBody()
        assertTrue(body.contains("forumNodeId=11"))
        assertTrue(body.contains("state=1"))
    }

    @Test
    fun toggleFocus_withoutLogin_throws4001() = runTest {
        tokenManager.currentToken = ""

        val result = repository.toggleFocus(forumNodeId = 11L, follow = true)

        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun updateFocusOrder_success_postsCommaSeparatedIds() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.updateFocusOrder(listOf(3L, 1L, 2L))

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.recordedPath!!.startsWith("/api/forum/focus/sort/upd"))
        assertTrue(recorded.recordedBody().contains("order=3%2C1%2C2"))
    }

    @Test
    fun getForumDetail_success_parsesAdmin() = runTest {
        mock.enqueue(
                """
                {
                    "success": true,
                    "code": 200,
                    "msg": "ok",
                    "data": {
                        "forum": {
                            "id": 11,
                            "name": "灌水吐槽",
                            "postsCount": 120,
                            "adminList": [{"header": "/head/s/a.jpg", "nickname": "版主A"}]
                        }
                    }
                }
                """.trimIndent()
            )

        val forum = repository.getForumDetail(11L).getOrThrow()

        assertEquals("灌水吐槽", forum?.name)
        assertEquals(120, forum?.postsCount)
        assertEquals("版主A", forum?.adminList?.firstOrNull()?.nickname)
        assertTrue(mock.takeRequest().recordedPath!!.startsWith("/api/forum/11"))
    }

    @Test
    fun getForumPosts_success_passesForumFilterAndOrder() = runTest {
        mock.enqueue(
                """
                {
                    "success": true,
                    "code": 200,
                    "msg": "ok",
                    "data": {
                        "list": [
                            {"id": 40819834, "itemId": 5450, "title": "标题", "forumName": "灌水吐槽", "discussCount": 5, "topTime": 0}
                        ]
                    }
                }
                """.trimIndent()
            )

        val posts = repository.getForumPosts(forumNodeId = 0L, order = "hits", page = 2).getOrThrow()

        assertEquals(1, posts.size)
        assertEquals(5450L, posts[0].itemId)
        val recorded = mock.takeRequest()
        assertTrue(recorded.recordedPath!!.contains("api/posts/search"))
        assertTrue(recorded.recordedPath!!.contains("forumNodeId=0"))
        assertTrue(recorded.recordedPath!!.contains("order=hits"))
        assertTrue(recorded.recordedPath!!.contains("page=2"))
    }
}
