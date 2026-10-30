package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 关注动态仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `FollowRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test）。
 */
class FollowRepositoryTest {

    private fun setup(): Pair<MockApi, FollowRepository> {
        val mock = MockApi()
        return mock to FollowRepository(mock.api(), mock.json)
    }

    @Test
    fun getFollowFeed_itemFirstPageComputesCursor() = runTest {
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
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
        // 引用去重体还原后应与首项关联同一封面
        assertEquals("2026/01/cover.jpg", items[1].relateItem?.cover)
        assertEquals(100L, items[1].relateItem?.id)
    }

    @Test
    fun getFollowFeed_failureThrowsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录过期"))
        val result = repository.getFollowFeed()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(4001, error.code)
    }

    @Test
    fun parseFollowCursor_supportsBothFormats() {
        assertEquals(1725880000000L, parseFollowCursor("1725880000000"))
        assertTrue(parseFollowCursor("2024-09-09 12:00:00") > 0L)
        assertEquals(0L, parseFollowCursor(null))
        assertEquals(0L, parseFollowCursor("not-a-date"))
    }
}
