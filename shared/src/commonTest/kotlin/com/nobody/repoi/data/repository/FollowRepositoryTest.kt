package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
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
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.serialization.json.Json

class FollowRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: FollowRepository

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = FollowRepository(api, json)
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    @Test
    fun getFollowFeed_itemFirstPageComputesCursor() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"actionDate":"1725880000000"},{"id":2,"actionDate":"1725870000000"}]}}""")
        val result = repository.getFollowFeed(type = "item", actionType = 0)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(2, page.items.size)
        assertEquals(1725870000000L, page.lastId)
        assertTrue(page.hasMore)
        val request = mock.takeRequest()
        assertTrue(request.recordedPath!!.contains("type=item"))
        assertTrue(request.recordedPath!!.contains("isHome=0"))
    }

    @Test
    fun getFollowFeed_userFeedOmitsItemOnlyParams() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        val result = repository.getFollowFeed(type = "user", lastId = 123L)
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrThrow().hasMore)
        val request = mock.takeRequest()
        assertTrue(request.recordedPath!!.contains("type=user"))
        assertTrue(!request.recordedPath!!.contains("isHome="))
    }

    @Test
    fun getFollowFeed_resolvesRefDedupedRelateItems() = runTest {
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
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录过期","data":null}""")
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
