package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.ZoneHobbyFilter
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        mock.routeWhen(
            predicate = { path, form -> path.contains("api/hobby/query-v2") },
            body = { _, form ->
                if (form.containsKey("releaseEnd")) mock.failure(500, "error")
                else hobbyListJson("预订手办")
            }
        )

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

    // ==================== 产品板块按作用域取数（#159 验收） ====================

    @Test
    fun queryHobbyBoard_singleZone_sendsZonesWithoutHomeScope() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(hobbyListJson("单区产品"))

        val result = repository.queryHobbyBoard(
            scope = ZoneQueryScope.Single(33476094L),
            filters = ZoneHobbyFilter()
        )

        assertTrue(result.isSuccess)
        assertEquals("单区产品", result.getOrThrow().single().nameCN)
        val request = mock.takeRequest()
        assertEquals("/api/hobby/query-v2", request.encodedPath)
        val form = request.formEntries()
        // 单专区：zones = nodeId，绝不携带 homeScope（非空 homeScope 会覆盖 zones 变全站）
        assertEquals("33476094", form["zones"])
        assertFalse(form.containsKey("homeScope"))
        assertEquals("1", form["page"])
        assertEquals(HobbyBoardOptions.PAGE_SIZE.toString(), form["pageSize"])
        assertEquals(HobbyBoardOptions.DEFAULT_CATEGORY_ID.toString(), form["category"])
        assertEquals(HobbyBoardOptions.DEFAULT_ORDER, form["order"])
    }

    @Test
    fun queryHobbyBoard_allScope_omitsZoneParams() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(hobbyListJson("全站产品"))

        val result = repository.queryHobbyBoard(scope = ZoneQueryScope.All)

        assertTrue(result.isSuccess)
        val form = mock.takeRequest().formEntries()
        // 全部聚合：省略 zones（等价全站），也不发 homeScope
        assertFalse(form.containsKey("zones"))
        assertFalse(form.containsKey("homeScope"))
        assertEquals("1", form["page"])
    }

    @Test
    fun queryHobbyBoard_carriesAllBoardFilters() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(hobbyListJson("筛选产品"))

        val result = repository.queryHobbyBoard(
            scope = ZoneQueryScope.All,
            page = 3,
            pageSize = 24,
            filters = ZoneHobbyFilter(
                categoryId = 100,
                subCategoryId = 1,
                order = "rating",
                releaseYear = 2026,
                releaseMonth = 3,
                keyword = "  初音  ",
                sex = 0,
                specs = "649223,649224",
                scale = "7.0",
                r18 = 18
            )
        )

        assertTrue(result.isSuccess)
        val form = mock.takeRequest().formEntries()
        assertEquals("3", form["page"])
        assertEquals("24", form["pageSize"])
        // 分类优先取子分类
        assertEquals("1", form["category"])
        assertEquals("rating", form["order"])
        assertEquals("2026", form["releaseYear"])
        assertEquals("3", form["releaseMonth"])
        assertEquals("初音", form["keyword"])
        assertEquals("0", form["sex"])
        assertEquals("649223,649224", form["specs"])
        assertEquals("7.0", form["scale"])
        assertEquals("18", form["r18"])
    }

    @Test
    fun queryHobbyBoard_defaultSentinelsAreOmitted() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(hobbyListJson("默认筛选"))

        // 高级筛选的「不限」哨兵（sex/r18 = -1）与空关键字/空属性不得下发
        repository.queryHobbyBoard(
            scope = ZoneQueryScope.All,
            filters = ZoneHobbyFilter(sex = -1, r18 = -1, specs = " ", scale = "0", releaseYear = 0, releaseMonth = 0)
        )

        val form = mock.takeRequest().formEntries()
        assertFalse(form.containsKey("sex"))
        assertFalse(form.containsKey("r18"))
        assertFalse(form.containsKey("specs"))
        assertFalse(form.containsKey("scale"))
        assertFalse(form.containsKey("releaseYear"))
        assertFalse(form.containsKey("releaseMonth"))
        assertFalse(form.containsKey("keyword"))
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
