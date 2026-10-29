package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 首页动态仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HomeRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test + `runTest`）。
 */
class HomeRepositoryTest {

    private fun setup(): Pair<MockApi, HomeRepository> {
        val mock = MockApi()
        return mock to HomeRepository(mock.api())
    }

    @Test
    fun getHomeFeed_successReturnsList() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"actionComment":"hello"}]}}""")
        val result = repository.getHomeFeed()
        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals(1L, items[0].id)
        assertEquals("hello", items[0].actionComment)

        val recorded = mock.takeRequest()
        assertTrue(recorded.encodedPath!!.startsWith("/api/user-action/list-v3"))
        assertTrue(recorded.queryParameter("categoryId") == "0")
        assertTrue(recorded.path!!.contains("subType=recommend"))
        assertTrue(recorded.path!!.contains("lastTime=0"))
        assertTrue(recorded.path!!.contains("pageSize=20"))
    }

    @Test
    fun getHomeFeed_failureThrowsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val result = repository.getHomeFeed()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(500, error.code)
    }
}
