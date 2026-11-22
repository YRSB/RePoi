package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedResaleBoardTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {

        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private class Env(
        val feedMock: MockApi,
        val zoneMock: MockApi,
        val resaleMock: MockApi,
        val preferences: ViewPreferencesManager,
        val zoneRepository: ZoneRepository,
        val dispatcher: CoroutineDispatcher,
    ) {
        fun viewModel(): HomeFeedViewModel = HomeFeedViewModel(
            HomeRepository(feedMock.api(dispatcher)),
            AlbumRepository(MockApi().api(dispatcher)),
            ResaleRepository(resaleMock.api(dispatcher)),
            ItemRepository(MockApi().api(dispatcher)),
            zoneRepository,
            preferences,
            FakeUserSession()
        )
    }

    private suspend fun env(savedTabKey: String = "all"): Env {
        val feedMock = MockApi()
        feedMock.route(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        val zoneMock = MockApi()
        zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[]}}"""
        )
        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        preferences.setFeedZoneTabKey(savedTabKey)
        preferences.setFeedSubType("recommend")
        val zoneRepository = ZoneRepository(zoneMock.api(testDispatcher), preferences)
        return Env(feedMock, zoneMock, MockApi(), preferences, zoneRepository, testDispatcher)
    }

    private fun resaleItemJson(id: Long, addDate: String?, type: Int = 1): String =
        """{"id":$id,"addDate":${if (addDate == null) "null" else "\"$addDate\""},"type":$type,"price":"100"}"""

    private fun resalePageJson(items: List<Pair<Long, String?>>, type: Int = 1): String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[${items.joinToString(",") { resaleItemJson(it.first, it.second, type) }}]}}"""

    private fun Env.stubResalePages(
        type: Int = 1,
        pages: Map<String?, List<Pair<Long, String?>>>,
    ) {
        resaleMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/resale/search") },
            body = { _, form, _ ->
                val requestedType = form["type"]?.toIntOrNull() ?: 1
                val cursor = form["lastTime"]
                val items = if (requestedType == type) pages[cursor].orEmpty() else emptyList()
                resalePageJson(items, requestedType)
            }
        )
    }

    private fun Env.stubZoneInfo(nodeId: Long, tabs: List<String>) {
        val tabsJson = tabs.joinToString(",") { type -> "\"$type\"" }
        zoneMock.routeWhen(
            predicate = { path, _, query -> path.contains("api/zone/info") && query["nodeId"] == nodeId.toString() },
            body = { _, _, _ ->
                """{"success":true,"code":200,"msg":"ok","data":{"zone":{"nodeId":$nodeId,"nameCN":"手办"},"focusState":false,"followCount":0,"tabs":[$tabsJson]}}"""
            }
        )
    }

    private fun List<ResaleSearchItem>.ids(): List<Long> = map { it.id }

    private fun HomeFeedViewModel.resaleContentItems(): List<Long> =
        (uiState.value.currentBoardContent as ZoneBoardContent.Resale).items.ids()

    @Test
    fun resaleBoard_isLazyLoadedOnlyAfterSwitch() = runTest(testDispatcher) {
        val env = env()
        env.stubResalePages(pages = mapOf(null to listOf(7L to "2026-10-24 12:00:00")))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertTrue(ZoneBoard.Resale in viewModel.uiState.value.boards)
        assertEquals(0, env.resaleMock.requestCount)

        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()

        assertEquals(1, env.resaleMock.requestCount)
        val request = env.resaleMock.lastRequest()
        assertEquals("/api/resale/search", request.encodedPath)
        val form = request.formEntries()
        assertEquals(ResaleBoardOptions.DEFAULT_TYPE.toString(), form["type"])
        assertEquals(ResaleBoardOptions.PAGE_SIZE.toString(), form["pageSize"])
        assertEquals("1", form["state"])

        assertFalse(form.containsKey("zones"))
        assertFalse(form.containsKey("homeScope"))

        val state = viewModel.uiState.value
        assertEquals(listOf(7L), state.resale.items.ids())
        assertTrue(state.resale.isLoaded)
        assertEquals(listOf(7L), viewModel.resaleContentItems())
        assertEquals(ResaleBoardOptions.DEFAULT_TYPE, state.currentBoardFilters.resaleType)
    }

    @Test
    fun resaleSingleZone_sendsNodeIdAsZones() = runTest(testDispatcher) {
        val env = env()
        env.stubZoneInfo(nodeId = 100L, tabs = listOf("action", "resale_item"))
        env.stubResalePages(pages = mapOf(null to listOf(21L to "2026-10-24 12:00:00")))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()
        assertTrue(ZoneBoard.Resale in viewModel.uiState.value.boards)

        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()

        val form = env.resaleMock.lastRequest().formEntries()
        assertEquals("100", form["zones"])
        assertFalse(form.containsKey("homeScope"))
        assertEquals(listOf(21L), viewModel.uiState.value.resale.items.ids())
    }

    @Test
    fun followScope_hidesResaleBoardAndNeverRequests() = runTest(testDispatcher) {
        val env = env()
        env.zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[{"nodeId":100,"nameCN":"手办"},{"nodeId":200,"nameCN":"模型"}]}}"""
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertTrue(ZoneBoard.Resale !in state.boards)
        assertEquals(ZoneBoard.Action, state.selectedBoard)
        assertEquals(0, env.resaleMock.requestCount)
    }

    @Test
    fun resaleTypeChange_resetsListAndReloadsFirstPage() = runTest(testDispatcher) {
        val env = env()
        env.resaleMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/resale/search") },
            body = { _, form, _ ->
                when (form["type"]) {
                    "2" -> resalePageJson(listOf(31L to "2026-10-24 12:00:00", 32L to "2026-10-24 11:00:00"), type = 2)
                    else -> resalePageJson(listOf(1L to "2026-10-24 12:00:00"), type = 1)
                }
            }
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()
        assertEquals(listOf(1L), viewModel.uiState.value.resale.items.ids())

        viewModel.selectResaleType(2)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.resale.type)
        assertEquals(listOf(31L, 32L), state.resale.items.ids())
        assertEquals(2, state.currentBoardFilters.resaleType)
        val form = env.resaleMock.lastRequest().formEntries()
        assertEquals("2", form["type"])

        val before = env.resaleMock.requestCount
        viewModel.selectResaleType(99)
        advanceUntilIdle()
        assertEquals(before, env.resaleMock.requestCount)
    }

    @Test
    fun resalePurityChange_filtersAndOmitsParamForAll() = runTest(testDispatcher) {
        val env = env()
        env.resaleMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/resale/search") },
            body = { _, form, _ ->
                val items = if (form["puritys"] == "11") {
                    listOf(41L to "2026-10-24 12:00:00")
                } else {
                    listOf(42L to "2026-10-24 12:00:00")
                }
                resalePageJson(items)
            }
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()

        viewModel.selectResalePurity("11")
        advanceUntilIdle()
        assertEquals("11", viewModel.uiState.value.currentBoardFilters.resalePurity)
        assertEquals("11", env.resaleMock.lastRequest().formEntries()["puritys"])

        viewModel.selectResalePurity(null)
        advanceUntilIdle()
        val form = env.resaleMock.lastRequest().formEntries()
        assertFalse(form.containsKey("puritys"), "全部成色应省略 puritys 参数：$form")
        assertEquals(null, viewModel.uiState.value.currentBoardFilters.resalePurity)
    }

    @Test
    fun resalePagination_appendsDistinctNextPageWithCursor() = runTest(testDispatcher) {
        val env = env()
        val firstPage = (1L..24L).map { it to "2026-10-24 12:00:00" }

        env.stubResalePages(
            pages = mapOf(
                null to firstPage,
                "2026-10-24 12:00:00" to listOf(24L to "2026-10-24 11:00:00", 25L to "2026-10-24 10:00:00")
            )
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()

        val page1 = viewModel.uiState.value
        assertEquals(24, page1.resale.items.size)
        assertTrue(page1.resale.hasMore)
        assertTrue(page1.currentBoardHasMore)

        viewModel.loadMoreSelectedBoard()
        advanceUntilIdle()

        val page2 = viewModel.uiState.value
        assertEquals(25, page2.resale.items.size)
        assertEquals(listOf(24L, 25L), page2.resale.items.ids().takeLast(2))
        assertFalse(page2.resale.hasMore)

        assertEquals(
            "2026-10-24 12:00:00",
            env.resaleMock.lastRequest().formEntries()["lastTime"]
        )
    }

    @Test
    fun resaleSwitchAwayAndBack_keepsStateWithoutReload() = runTest(testDispatcher) {
        val env = env()
        env.stubResalePages(pages = mapOf(null to listOf(51L to "2026-10-24 12:00:00")))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()
        viewModel.selectResalePurity("9")
        advanceUntilIdle()
        val requestCount = env.resaleMock.requestCount

        viewModel.selectBoard(ZoneBoard.Action)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneBoard.Resale, state.selectedBoard)

        assertEquals(requestCount, env.resaleMock.requestCount)
        assertEquals("9", state.resale.purity)
        assertTrue(state.resale.isLoaded)
    }

    @Test
    fun resaleRetryAfterFailure_reloadsFirstPage() = runTest(testDispatcher) {
        val env = env()

        env.resaleMock.enqueue(
            env.resaleMock.failure(500, "网络异常"),
            resalePageJson(listOf(61L to "2026-10-24 12:00:00"))
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertEquals("网络异常", failed.resale.errorMessage)
        assertEquals(0, failed.resale.items.size)

        viewModel.retrySelectedBoard()
        advanceUntilIdle()

        val recovered = viewModel.uiState.value
        assertEquals(null, recovered.resale.errorMessage)
        assertEquals(listOf(61L), recovered.resale.items.ids())
    }
}
