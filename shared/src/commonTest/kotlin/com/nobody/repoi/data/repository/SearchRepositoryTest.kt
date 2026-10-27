package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.recordedBody
import com.nobody.repoi.test.recordedPath
import com.nobody.repoi.test.TestDataStores
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
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
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

class SearchRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var historyManager: SearchHistoryManager
    private lateinit var stores: TestDataStores
    private lateinit var repository: SearchRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        historyManager = SearchHistoryManager(stores.createDataStore("search_history"), json)
        apiService = api
        repository = SearchRepository(api, historyManager)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
    }

    private fun parseForm(body: String): Map<String, String> {
        return body.split("&")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].decodeURLPart() to
                    (parts.getOrElse(1) { "" }).decodeURLPart()
            }
    }

    private fun enqueueSearchSuccess(): Unit {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"result":[{"id":1,"nameCN":"初音未来"}],"query":{"totalCount":1,"pageCount":2,"category":10000}}}"""
            )
    }

    @Test
    fun search_sendsAllFiltersAndAggregateFlag() = runTest {
        enqueueSearchSuccess()

        val result = repository.search(
            category = 10000,
            itemType = "hobby",
            keyword = "  初音  ",
            page = 2,
            pageSize = 30,
            order = "praiseCount",
            company = 88L,
            releaseYear = "2025",
            attribute = 15,
            aggregate = false
        )

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().result?.size)
        assertEquals(2, result.getOrThrow().query?.pageCount)

        val request = mock.takeRequest()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/search", request.recordedPath)
        val form = parseForm(request.recordedBody())
        assertEquals("10000", form["category"])
        assertEquals("hobby", form["itemType"])
        assertEquals("初音", form["keyword"])
        assertEquals("2", form["page"])
        assertEquals("30", form["pageSize"])
        assertEquals("false", form["aggregate"])
        assertEquals("praiseCount", form["order"])
        assertEquals("88", form["company"])
        assertEquals("2025", form["releaseYear"])
        assertEquals("15", form["attribute"])
    }

    @Test
    fun search_omitsEmptyAndDefaultFilters() = runTest {
        enqueueSearchSuccess()

        repository.search(
            category = 10000,
            itemType = "hobby",
            keyword = "初音",
            order = "default",
            releaseYear = "全部",
            aggregate = true
        )

        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("true", form["aggregate"])
        assertFalse(form.containsKey("order"))
        assertFalse(form.containsKey("company"))
        assertFalse(form.containsKey("releaseYear"))
        assertFalse(form.containsKey("attribute"))
    }

    @Test
    fun search_failureThrowsApiException() = runTest {
        mock.enqueue(
                """{"success":false,"code":4001,"msg":"登录过期","data":null}"""
            )

        val result = repository.search(10000, "hobby", "初音")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(4001, error.code)
        assertEquals("登录过期", error.message)
    }

    @Test
    fun searchUsers_sendsTrimmedParamsAndMapsUsers() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"userList":[{"userId":9,"nickname":"胶友"}]}}"""
            )

        val result = repository.searchUsers(keyword = "  胶友  ", page = 3, pageSize = 10)

        assertTrue(result.isSuccess)
        assertEquals(9L, result.getOrThrow().userList?.first()?.userId)
        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("胶友", form["keyword"])
        assertEquals("3", form["page"])
        assertEquals("10", form["pageSize"])
    }

    @Test
    fun searchUsers_failureThrowsApiException() = runTest {
        mock.enqueue(
                """{"success":false,"code":500,"msg":"搜索用户失败","data":null}"""
            )

        val result = repository.searchUsers("胶友")

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getHotwords_mapsListAndDegradesToEmptyOnFailure() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"list":[{"word":"初音","count":3}]}}"""
            )
        val success = repository.getHotwords(10000)
        assertTrue(success.isSuccess)
        assertEquals("初音", success.getOrThrow().first().word)

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val degraded = repository.getHotwords(10000)
        assertTrue(degraded.isSuccess)
        assertTrue(degraded.getOrThrow().isEmpty())
    }

    @Test
    fun getAutoFill_blankKeywordReturnsEmptyWithoutRequest() = runTest {
        val result = repository.getAutoFill(10000, "   ")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun getAutoFill_mapsSuggestionsAndDegradesToEmptyOnFailure() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"keywords":["初音未来","初音手办"]}}"""
            )
        val success = repository.getAutoFill(10000, " 初音 ")
        assertTrue(success.isSuccess)
        assertEquals(listOf("初音未来", "初音手办"), success.getOrThrow())
        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("10000", form["type"])
        assertEquals("初音", form["keywords"])

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val degraded = repository.getAutoFill(10000, "初音")
        assertTrue(degraded.isSuccess)
        assertTrue(degraded.getOrThrow().isEmpty())
    }

    @Test
    fun getElementAttributes_mapsListAndThrowsOnFailure() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"attributeList":[{"id":15,"name":"主角","categoryId":40100}]}}"""
            )
        val success = repository.getElementAttributes(40100)
        assertTrue(success.isSuccess)
        assertEquals("主角", success.getOrThrow().first().name)
        val request = mock.takeRequest()
        assertEquals(HttpMethod.Get, request.method)
        assertTrue(request.recordedPath?.contains("categoryId=40100") == true)

        mock.enqueue("""{"success":false,"code":500,"msg":"属性加载失败","data":null}""")
        val failure = repository.getElementAttributes(40100)
        assertTrue(failure.isFailure)
        assertEquals("属性加载失败", (failure.exceptionOrNull() as ApiException).message)
    }

    @Test
    fun historyOperations_delegateToManager() = runTest {
        repository.addSearchHistory("初音")
        repository.removeSearchHistory("初音")
        repository.clearSearchHistory()

        // 真对象：add 后可见，remove 后消失，clear 后全空
        historyManager.addKeyword("验证词")
        assertTrue(historyManager.historyFlow.first().contains("验证词"))
        historyManager.removeKeyword("验证词")
        assertTrue(!historyManager.historyFlow.first().contains("验证词"))
        historyManager.addKeyword("验证词2")
        historyManager.clearAll()
        assertTrue(historyManager.historyFlow.first().isEmpty())
    }

    @Test
    fun searchHistoryFlow_exposesManagerFlow() = runTest {
        historyManager.addKeyword("初音")
        historyManager.addKeyword("手办")
        val newRepository = SearchRepository(api, historyManager)

        assertEquals(listOf("手办", "初音"), newRepository.searchHistoryFlow.first())
    }
}
