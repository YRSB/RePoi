package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * 条目首页仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ItemRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；按体分发 Dispatcher → [MockApi.routeWhen]；
 * `java.time.LocalDate` → kotlinx-datetime；JUnit4 → kotlin.test）。
 */
class ItemRepositoryTest {

    private fun setup(): Pair<MockApi, ItemRepository> {
        val mock = MockApi()
        return mock to ItemRepository(mock.api())
    }

    private fun hobbyListJson(name: String): String {
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"nameCN":"$name"}]}}"""
    }

    @Test
    fun getItemHomePreview_sendsTwoDatedQueriesAndMapsBothSections() = runTest {
        val (mock, repository) = setup()
        mock.routeWhen(
            predicate = { _, _ -> true },
            body = { _, form ->
                val name = if (form.containsKey("releaseEnd")) "出荷手办" else "预订手办"
                hobbyListJson(name)
            }
        )

        val result = repository.getItemHomePreview(10000)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("预订手办", data.preOrderItems.single().nameCN)
        assertEquals("出荷手办", data.releaseItems.single().nameCN)

        assertEquals(2, mock.requestCount)
        val first = mock.takeRequest().formEntries()
        val second = mock.takeRequest().formEntries()
        val preOrderForm = listOf(first, second).first { !it.containsKey("releaseEnd") }
        val shipForm = listOf(first, second).first { it.containsKey("releaseEnd") }

        listOf(preOrderForm, shipForm).forEach { form ->
            assertEquals("hits7Day", form["order"])
            assertEquals("1", form["page"])
            assertEquals("9", form["pageSize"])
            assertEquals("10000", form["category"])
            assertEquals("1", form["hasShopItem"])
            assertTrue(form["releaseStart"]!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        }
        assertEquals(today(), shipForm["releaseEnd"])
        assertTrue(LocalDate.parse(preOrderForm["releaseStart"]!!) > LocalDate.parse(today()))
        assertTrue(LocalDate.parse(shipForm["releaseStart"]!!) < LocalDate.parse(today()))
    }

    @Test
    fun getItemHomePreview_partialFailureKeepsSuccessfulSection() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(hobbyListJson("预订手办"))
        mock.enqueue(mock.failure(500, "error"))

        val result = repository.getItemHomePreview(10000)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().preOrderItems.size + result.getOrThrow().releaseItems.size)
    }

    @Test
    fun queryHobbyList_sendsParamsAndMapsItems() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(hobbyListJson("筛选手办"))

        val result = repository.queryHobbyList(
            mapOf("category" to "100", "keyword" to "初音", "page" to "2")
        )

        assertTrue(result.isSuccess)
        assertEquals("筛选手办", result.getOrThrow().single().nameCN)
        val form = mock.takeRequest().formEntries()
        assertEquals("100", form["category"])
        assertEquals("初音", form["keyword"])
        assertEquals("2", form["page"])
    }

    @Test
    fun queryHobbyList_failureThrowsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(400, "参数错误"))

        val result = repository.queryHobbyList(mapOf("category" to "100"))

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(400, error.code)
        assertEquals("参数错误", error.message)
    }

    @Test
    fun dateHelpers_matchCalendarContract() {
        val (_, repository) = setup()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        assertEquals((today + kotlinx.datetime.DatePeriod(months = 2)).toString(), repository.calculatePreOrderStartDate())
        val shipRange = repository.calculateShipDateRange()
        assertEquals((today + kotlinx.datetime.DatePeriod(months = -3)).toString(), shipRange.first)
        assertEquals(today.toString(), shipRange.second)
    }

    private fun today(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
}
