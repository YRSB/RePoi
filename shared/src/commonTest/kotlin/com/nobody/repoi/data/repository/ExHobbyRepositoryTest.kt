package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ExHobby 图包仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ExHobbyRepositoryTest`（逐用例；Mockito `mock(ExHobbyApiService)` →
 * MockEngine 背后的真实 [ExHobbyApi]；`verify(times(n))` → 本地请求计数；
 * JUnit4 → kotlin.test）。
 */
class ExHobbyRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private inner class Engine {
        var calls = 0
        var next: () -> String = { "[]" }
        var failure: Throwable? = null
        val engine = MockEngine {
            calls++
            failure?.let { throw it }
            respond(
                content = next(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        fun api(): ExHobbyApi = ExHobbyApi(HttpClient(engine), json)
    }

    @Test
    fun getPictures_whenSuccess_returnsListAndCachesInMemory() = runTest {
        val engine = Engine()
        engine.next = { """[{"path":"2024/01/pic1.jpg"},{"path":"2024/01/pic2.jpg"}]""" }
        val repository = ExHobbyRepository(engine.api())

        val result1 = repository.getPictures(100L, 1)
        assertTrue(result1.isSuccess)
        assertEquals(2, result1.getOrThrow().size)
        assertEquals("2024/01/pic1.jpg", result1.getOrThrow()[0].path)

        // 第二次请求应命中内存缓存，不再重复调用网络
        val result2 = repository.getPictures(100L, 1)
        assertTrue(result2.isSuccess)
        assertEquals(2, result2.getOrThrow().size)

        assertEquals(1, engine.calls)
    }

    @Test
    fun getPictures_whenEmptyList_returnsSuccessEmptyList() = runTest {
        val engine = Engine()
        engine.next = { "[]" }
        val repository = ExHobbyRepository(engine.api())

        val result = repository.getPictures(100L, 2)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun getPictures_whenApiThrows_returnsFailureWithApiException() = runTest {
        val engine = Engine()
        engine.failure = RuntimeException("Network timeout")
        val repository = ExHobbyRepository(engine.api())

        val result = repository.getPictures(100L, 1)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ApiException)
        assertEquals(-1, (exception as ApiException).code)
        assertTrue(exception.message?.contains("Network timeout") == true)
    }

    @Test
    fun getPictures_whenCancelled_rethrowsCancellationException() = runTest {
        val engine = Engine()
        engine.failure = CancellationException("Job cancelled")
        val repository = ExHobbyRepository(engine.api())

        val result = repository.getPictures(100L, 1)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
    }

    @Test
    fun clearCache_clearsTargetItemCache() = runTest {
        val engine = Engine()
        engine.next = { """[{"path":"2024/01/pic1.jpg"}]""" }
        val repository = ExHobbyRepository(engine.api())

        repository.getPictures(100L, 1)
        assertEquals(1, engine.calls)

        // 清除指定 item 缓存
        repository.clearCache(100L)

        // 再次获取应重新调用网络
        repository.getPictures(100L, 1)
        assertEquals(2, engine.calls)

        // 清除所有缓存
        repository.clearCache(null)
        repository.getPictures(100L, 1)
        assertEquals(3, engine.calls)
    }
}
