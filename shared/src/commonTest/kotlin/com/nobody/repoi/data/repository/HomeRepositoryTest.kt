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

class HomeRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: HomeRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = HomeRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    @Test
    fun getHomeFeed_successReturnsList() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"actionComment":"hello"}]}}""")
        val result = repository.getHomeFeed()
        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals(1L, items[0].id)
        assertEquals("hello", items[0].actionComment)

        val path = mock.takeRequest().recordedPath.orEmpty()
        assertTrue(path.startsWith("/api/user-action/list-v3"))
        assertTrue(path.contains("categoryId=0"))
        assertTrue(path.contains("subType=recommend"))
        assertTrue(path.contains("lastTime=0"))
        assertTrue(path.contains("pageSize=20"))
    }

    @Test
    fun getHomeFeed_failureThrowsApiException() = runTest {
        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val result = repository.getHomeFeed()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(500, error.code)
    }
}
