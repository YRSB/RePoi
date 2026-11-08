package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 关注动态与社交好友仓储测试（KMP commonTest 移植）。
 */
class FollowRepositoryTest {

    private fun setup(token: String = "utoken-test"): Triple<MockApi, FakeUserSession, FollowRepository> {
        val mock = MockApi()
        val session = FakeUserSession(token)
        return Triple(mock, session, FollowRepository(mock.api(), mock.json, session))
    }

    @Test
    fun getFollowFeed_itemFirstPageComputesCursor() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"actionDate":"1725880000000"},{"id":2,"actionDate":"1725870000000"}]}}""")
        val result = repository.getFollowFeed(type = "item", actionType = 0)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(2, page.items.size)
        assertEquals(1725870000000L, page.lastId)
        assertTrue(page.hasMore)
        val recorded = mock.takeRequest()
        assertTrue(recorded.path!!.contains("type=item"))
        assertTrue(recorded.path!!.contains("isHome=0"))
    }

    @Test
    fun getFollowFeed_userFeedOmitsItemOnlyParams() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        val result = repository.getFollowFeed(type = "user", lastId = 123L)
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow().hasMore)
        val recorded = mock.takeRequest()
        assertTrue(recorded.path!!.contains("type=user"))
        assertTrue(!recorded.path!!.contains("isHome="))
    }

    @Test
    fun getFollowFeed_resolvesRefDedupedRelateItems() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[
            |{"id":1,"actionDate":"1725880000000","relateItem":{"@id":"h1","id":100,"itemType":"hobby","nameCN":"测试条目","cover":"2026/01/cover.jpg"}},
            |{"id":2,"actionDate":"1725870000000","relateItem":{"@ref":"h1"}}
            |]}}""".trimMargin()
        )
        val result = repository.getFollowFeed(type = "item", actionType = 0)
        assertTrue(result.isSuccess)
        val items = result.getOrThrow().items
        assertEquals(2, items.size)
        assertEquals("测试条目", items[0].relateItem?.nameCN)
        assertEquals("2026/01/cover.jpg", items[1].relateItem?.cover)
        assertEquals(100L, items[1].relateItem?.id)
    }

    @Test
    fun getFollowFeed_failureThrowsApiException() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录过期"))
        val result = repository.getFollowFeed()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(4001, error.code)
    }

    @Test
    fun getFellowCount_success_parsesData() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"data":{"friendCount":42,"fansCount":108}}}"""
        )

        val result = repository.getFellowCount(999L)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(42, data.friendCount)
        assertEquals(108, data.fansCount)
    }

    @Test
    fun getFellowType_success_mapsFriendState() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"data":{"friendState":0}}""")

        val result = repository.getFellowType(888L)
        assertTrue(result.isSuccess)
        assertEquals(FriendState.FOLLOWING, result.getOrThrow())
    }

    @Test
    fun updateFriend_success_emitsFriendChangeEvent() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")

        val eventDeferred = async { repository.friendChangeEvent.first() }
        val result = repository.updateFriend(userId = 555L, state = FriendState.FOLLOWING)
        assertTrue(result.isSuccess)

        val event = eventDeferred.await()
        assertEquals(555L, event.userId)
        assertTrue(event.isFollow)
        assertEquals(FriendState.FOLLOWING, event.state)
    }

    @Test
    fun updateFriend_tokenExpired_selfHealsSessionAndThrows() = runTest {
        val (mock, session, repository) = setup("token-to-expire")
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录已失效"}""")

        val result = repository.updateFriend(userId = 555L, isFollow = true)
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(SessionState.Unauthenticated, session.state.value)
    }

    @Test
    fun getUserFellowList_success_parsesItems() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"list":[{"id":1,"userId":666,"userName":"TestUser"}]}}"""
        )

        val result = repository.getUserFellowList(userId = 999L, type = 0, page = 1)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals(666L, list.first().userId)
    }

    @Test
    fun parseFollowCursor_supportsBothFormats() {
        assertEquals(1725880000000L, parseFollowCursor("1725880000000"))
        assertTrue(parseFollowCursor("2024-09-09 12:00:00") > 0L)
        assertEquals(0L, parseFollowCursor(null))
        assertEquals(0L, parseFollowCursor("not-a-date"))
    }
}
