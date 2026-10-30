package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.local.SearchHistoryManager
import com.nobody.poishelf.data.local.TestDataStore
import com.nobody.poishelf.data.remote.model.ApiException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 搜索仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `SearchRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；Mockito `mock(SearchHistoryManager)` →
 * 真实 SearchHistoryManager（[TestDataStore] 隔离目录；`verify` 调用计数断言 →
 * 真实状态流转断言，委托语义等价）；JUnit4 → kotlin.test）。
 */
class SearchRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun setup(): Triple<MockApi, SearchHistoryManager, SearchRepository> {
        val mock = MockApi()
        val historyManager = SearchHistoryManager(TestDataStore.searchHistoryPrefs(), json)
        return Triple(mock, historyManager, SearchRepository(mock.api(), historyManager))
    }

    private fun enqueueSearchSuccess(mock: MockApi): Unit {
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"result":[{"id":1,"nameCN":"初音未来"}],"query":{"totalCount":1,"pageCount":2,"category":10000}}}"""
        )
    }

    @Test
    fun search_sendsAllFiltersAndAggregateFlag() = runTest {
        val (mock, _, repository) = setup()
        enqueueSearchSuccess(mock)

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

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/search", recorded.path)
        val form = recorded.formEntries()
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
        val (mock, _, repository) = setup()
        enqueueSearchSuccess(mock)

        repository.search(
            category = 10000,
            itemType = "hobby",
            keyword = "初音",
            order = "default",
            releaseYear = "全部",
            aggregate = true
        )

        val form = mock.takeRequest().formEntries()
        assertEquals("true", form["aggregate"])
        assertFalse(form.containsKey("order"))
        assertFalse(form.containsKey("company"))
        assertFalse(form.containsKey("releaseYear"))
        assertFalse(form.containsKey("attribute"))
    }

    @Test
    fun search_failureThrowsApiException() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录过期"))

        val result = repository.search(10000, "hobby", "初音")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(4001, error.code)
        assertEquals("登录过期", error.message)
    }

    @Test
    fun searchUsers_sendsTrimmedParamsAndMapsUsers() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"userList":[{"userId":9,"nickname":"胶友"}]}}"""
        )

        val result = repository.searchUsers(keyword = "  胶友  ", page = 3, pageSize = 10)

        assertTrue(result.isSuccess)
        assertEquals(9L, result.getOrThrow().userList?.first()?.userId)
        val form = mock.takeRequest().formEntries()
        assertEquals("胶友", form["keyword"])
        assertEquals("3", form["page"])
        assertEquals("10", form["pageSize"])
    }

    @Test
    fun searchUsers_failureThrowsApiException() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.failure(500, "搜索用户失败"))

        val result = repository.searchUsers("胶友")

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getHotwords_mapsListAndDegradesToEmptyOnFailure() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"word":"初音","count":3}]}}"""
        )
        val success = repository.getHotwords(10000)
        assertTrue(success.isSuccess)
        assertEquals("初音", success.getOrThrow().first().word)

        mock.enqueue(mock.failure(500, "error"))
        val degraded = repository.getHotwords(10000)
        assertTrue(degraded.isSuccess)
        assertTrue(degraded.getOrThrow().isEmpty())
    }

    @Test
    fun getAutoFill_blankKeywordReturnsEmptyWithoutRequest() = runTest {
        val (mock, _, repository) = setup()
        val result = repository.getAutoFill(10000, "   ")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun getAutoFill_mapsSuggestionsAndDegradesToEmptyOnFailure() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"keywords":["初音未来","初音手办"]}}"""
        )
        val success = repository.getAutoFill(10000, " 初音 ")
        assertTrue(success.isSuccess)
        assertEquals(listOf("初音未来", "初音手办"), success.getOrThrow())
        val form = mock.takeRequest().formEntries()
        assertEquals("10000", form["type"])
        assertEquals("初音", form["keywords"])

        mock.enqueue(mock.failure(500, "error"))
        val degraded = repository.getAutoFill(10000, "初音")
        assertTrue(degraded.isSuccess)
        assertTrue(degraded.getOrThrow().isEmpty())
    }

    @Test
    fun getElementAttributes_mapsListAndThrowsOnFailure() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"attributeList":[{"id":15,"name":"主角","categoryId":40100}]}}"""
        )
        val success = repository.getElementAttributes(40100)
        assertTrue(success.isSuccess)
        assertEquals("主角", success.getOrThrow().first().name)
        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path?.contains("categoryId=40100") == true)

        mock.enqueue(mock.failure(500, "属性加载失败"))
        val failure = repository.getElementAttributes(40100)
        assertTrue(failure.isFailure)
        assertEquals("属性加载失败", (failure.exceptionOrNull() as ApiException).message)
    }

    @Test
    fun historyOperations_delegateToManager() = runTest {
        val (_, historyManager, repository) = setup()
        repository.addSearchHistory("初音")
        assertEquals(listOf("初音"), historyManager.historyFlow.first())

        repository.removeSearchHistory("初音")
        assertTrue(historyManager.historyFlow.first().isEmpty())

        repository.addSearchHistory("手办")
        repository.clearSearchHistory()
        assertTrue(historyManager.historyFlow.first().isEmpty())
    }

    @Test
    fun searchHistoryFlow_exposesManagerFlow() = runTest {
        val (_, historyManager, repository) = setup()
        historyManager.addKeyword("初音")
        historyManager.addKeyword("手办")

        assertEquals(listOf("手办", "初音"), repository.searchHistoryFlow.first())
    }
}
