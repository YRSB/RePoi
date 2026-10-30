package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 讨论板版块仓储单测：关注列表解析、关注取关、排序保存、版块详情与帖子流查询（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ForumRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；Mockito → [FakeTokenManager]；
 * JUnit4 → kotlin.test）。
 * 移植说明：`updateFocusOrder` 的 `order=3%2C1%2C2` 为 OkHttp 线上百分号编码形态；
 * MockEngine 侧捕获的是解码后 form 值，断言改为 `formEntries()["order"] == "3,1,2"`，语义等价。
 */
class ForumRepositoryTest {

    private fun setup(token: String = "utoken-test"): Pair<MockApi, ForumRepository> {
        val mock = MockApi()
        return mock to ForumRepository(mock.api(), FakeTokenManager(token))
    }

    @Test
    fun getFocusForums_success_parsesPlatesAndStringCount() = runTest {
        val (mock, repository) = setup()
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
        assertTrue(recorded.encodedPath!!.startsWith("/api/forum/focus/list"))
        assertTrue(recorded.bodyContains("forumPage=1"))
    }

    @Test
    fun getUnfocusForums_error_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录失效"))

        val result = repository.getUnfocusForums()

        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun toggleFocus_follow_postsStateOne() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.toggleFocus(forumNodeId = 11L, follow = true)

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.encodedPath!!.startsWith("/api/forum/focus"))
        val body = recorded.formBodyText()
        assertTrue(body.contains("forumNodeId=11"))
        assertTrue(body.contains("state=1"))
    }

    @Test
    fun toggleFocus_withoutLogin_throws4001() = runTest {
        val (mock, repository) = setup(token = "")

        val result = repository.toggleFocus(forumNodeId = 11L, follow = true)

        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun updateFocusOrder_success_postsCommaSeparatedIds() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.updateFocusOrder(listOf(3L, 1L, 2L))

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.encodedPath!!.startsWith("/api/forum/focus/sort/upd"))
        // 线上编码形态 `order=3%2C1%2C2`；MockEngine 侧断言解码后值，语义等价
        assertEquals("3,1,2", recorded.formEntries()["order"])
    }

    @Test
    fun getForumDetail_success_parsesAdmin() = runTest {
        val (mock, repository) = setup()
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
        assertTrue(mock.takeRequest().encodedPath!!.startsWith("/api/forum/11"))
    }

    @Test
    fun getForumPosts_success_passesForumFilterAndOrder() = runTest {
        val (mock, repository) = setup()
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
        assertTrue(recorded.path!!.contains("api/posts/search"))
        assertTrue(recorded.path!!.contains("forumNodeId=0"))
        assertTrue(recorded.path!!.contains("order=hits"))
        assertTrue(recorded.path!!.contains("page=2"))
    }
}
