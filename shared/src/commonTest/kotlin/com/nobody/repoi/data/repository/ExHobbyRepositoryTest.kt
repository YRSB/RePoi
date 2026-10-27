package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ExHobby 仓储测试（Mockito → MockEngine + 真 `ExHobbyApi`）。
 *
 * 老测试用 mock(ExHobbyApiService) 验证内存缓存语义；KMP 侧改用 MockEngine 队列，
 * `verify(times(n))` 翻译为 `mock.requestCount` 断言。
 */
class ExHobbyRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: ExHobbyApi
    private lateinit var json: Json
    private lateinit var repository: ExHobbyRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = ExHobbyApi(client, json)
        repository = ExHobbyRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    @Test
    fun getPictures_whenSuccess_returnsListAndCachesInMemory() = runTest {
        mock.enqueue("""[{"path":"2024/01/pic1.jpg"},{"path":"2024/01/pic2.jpg"}]""")

        val result1 = repository.getPictures(100L, 1)
        assertTrue(result1.isSuccess)
        assertEquals(2, result1.getOrThrow().size)
        assertEquals("2024/01/pic1.jpg", result1.getOrThrow()[0].path)

        // 第二次请求应命中内存缓存，不再重复调用网络
        val result2 = repository.getPictures(100L, 1)
        assertTrue(result2.isSuccess)
        assertEquals(2, result2.getOrThrow().size)

        assertEquals(1, mock.requestCount)
    }

    @Test
    fun getPictures_whenEmptyList_returnsSuccessEmptyList() = runTest {
        mock.enqueue("""[]""")

        val result = repository.getPictures(100L, 2)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun getPictures_whenApiThrows_returnsFailureWithApiException() = runTest {
        mock.enqueue("Internal Server Error", HttpStatusCode.InternalServerError)

        val result = repository.getPictures(100L, 1)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ApiException)
        assertEquals(-1, (exception as ApiException).code)
    }

    @Test
    fun getPictures_whenCancelled_rethrowsCancellationException() = runTest {
        // MockEngine handler 直接抛 CancellationException，等价老 mock 的 thenThrow 语义。
        val cancelEngine = MockEngine { throw CancellationException("Job cancelled") }
        val cancelClient = HttpClient(cancelEngine) { expectSuccess = true }
        try {
            val cancelRepo = ExHobbyRepository(ExHobbyApi(cancelClient, json))
            val result = cancelRepo.getPictures(100L, 1)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is CancellationException)
        } finally {
            cancelClient.close()
        }
    }

    @Test
    fun clearCache_clearsTargetItemCache() = runTest {
        mock.enqueue("""[{"path":"2024/01/pic1.jpg"}]""")
        mock.enqueue("""[{"path":"2024/01/pic1.jpg"}]""")
        mock.enqueue("""[{"path":"2024/01/pic1.jpg"}]""")

        repository.getPictures(100L, 1)
        assertEquals(1, mock.requestCount)

        // 清除指定 item 缓存
        repository.clearCache(100L)

        // 再次获取应重新调用网络
        repository.getPictures(100L, 1)
        assertEquals(2, mock.requestCount)

        // 清除所有缓存
        repository.clearCache(null)
        repository.getPictures(100L, 1)
        assertEquals(3, mock.requestCount)
    }
}
