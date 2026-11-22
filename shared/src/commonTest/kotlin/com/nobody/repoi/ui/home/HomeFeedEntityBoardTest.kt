package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.FakeUserSession
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedEntityBoardTest {

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
        val preferences: ViewPreferencesManager,
        val zoneRepository: ZoneRepository,
        val dispatcher: CoroutineDispatcher,
    ) {
        fun viewModel(): HomeFeedViewModel = HomeFeedViewModel(
            HomeRepository(feedMock.api(dispatcher)),
            AlbumRepository(MockApi().api(dispatcher)),
            ResaleRepository(MockApi().api(dispatcher)),
            ItemRepository(MockApi().api(dispatcher)),
            zoneRepository,
            preferences,
            FakeUserSession()
        )
    }

    private suspend fun env(
        focusZoneIds: List<Long> = emptyList(),
        savedTabKey: String = "all"
    ): Env {
        val feedMock = MockApi()
        feedMock.route(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        val zoneMock = MockApi()
        val focusJson = focusZoneIds.joinToString(",") { id -> """{"nodeId":$id,"nameCN":"专区$id"}""" }
        zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[$focusJson]}}"""
        )
        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        preferences.setFeedZoneTabKey(savedTabKey)
        preferences.setFeedSubType("recommend")
        val zoneRepository = ZoneRepository(zoneMock.api(testDispatcher), preferences)
        return Env(feedMock, zoneMock, preferences, zoneRepository, testDispatcher)
    }

    private fun entityPage(ids: List<Long>, hasMore: Boolean, itemType: String = "company"): String {
        val list = ids.joinToString(",") { id ->
            """{"id":${id + 10},"itemId":$id,"name":"$itemType-$id","nameCN":"$itemType-$id","cover":"c.jpg","hits":$id,"itemType":"$itemType"}"""
        }

        return """{"success":true,"code":200,"msg":"ok","data":{"total":10000,"hasMore":$hasMore,"list":[$list]}}"""
    }

    private fun Env.stubEntityPages(
        pages: Map<Int, Pair<List<Long>, Boolean>>,
        itemType: String = "company"
    ) {
        zoneMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/zone/items") },
            body = { _, _, query ->
                val page = query["page"]?.toIntOrNull() ?: 1
                val (ids, hasMore) = pages[page] ?: (emptyList<Long>() to false)
                entityPage(ids, hasMore, query["itemType"].orEmpty().ifBlank { itemType })
            }
        )
    }

    private fun Env.stubSingleEntityPage(ids: List<Long> = listOf(501L), hasMore: Boolean = false) {
        zoneMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/zone/items") },
            body = { _, _, query -> entityPage(ids, hasMore, query["itemType"].orEmpty()) }
        )
    }

    private fun Env.stubEntityPagesByZones(allIds: List<Long>, followIds: List<Long>) {
        zoneMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/zone/items") },
            body = { _, _, query ->
                val ids = if (query["zones"].isNullOrEmpty()) allIds else followIds
                entityPage(ids, hasMore = false, itemType = query["itemType"].orEmpty())
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

    private val expectedEntityOrder = listOf(
        ZoneBoard.Company,
        ZoneBoard.Series,
        ZoneBoard.Works,
        ZoneBoard.Character,
        ZoneBoard.Person
    )

    private fun entityOrder(): List<ZoneBoard> = ZoneBoard.HOME_ORDER.filter { it.isEntity }

    @Test
    fun entityBoards_areLazyAndPassItemTypeWithoutHomeScope() = runTest(testDispatcher) {
        val env = env()
        env.stubSingleEntityPage(ids = listOf(501L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertEquals(expectedEntityOrder, viewModel.uiState.value.boards.filter { it.isEntity })

        assertEquals(1, env.zoneMock.requestCount)

        entityOrder().forEachIndexed { index, board ->
            viewModel.selectBoard(board)
            advanceUntilIdle()

            val request = env.zoneMock.lastRequest()
            assertEquals("/api/zone/items", request.encodedPath)

            assertEquals(board.type, request.queryParameter("itemType"))

            assertNull(request.queryParameter("zones"))
            assertNull(request.queryParameter("homeScope"))
            assertEquals("1", request.queryParameter("page"))

            val state = viewModel.uiState.value
            assertEquals(board, state.selectedBoard)
            val content = state.currentBoardContent
            assertTrue(content is ZoneBoardContent.Entity, "实体板块内容应为 ZoneBoardContent.Entity")
            assertEquals(listOf(501L), (content as ZoneBoardContent.Entity).items.map { it.targetId })
            assertEquals(1, state.currentBoardItemCount)

            assertEquals(2 + index, env.zoneMock.requestCount)
        }
    }

    @Test
    fun entityFollowScope_sendsCommaSeparatedZonesWithoutHomeScope() = runTest(testDispatcher) {
        val env = env(focusZoneIds = listOf(100L, 200L))
        env.stubSingleEntityPage(ids = listOf(601L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Company)
        advanceUntilIdle()

        val request = env.zoneMock.lastRequest()
        assertEquals("/api/zone/items", request.encodedPath)
        assertEquals("company", request.queryParameter("itemType"))

        assertEquals("100,200", request.queryParameter("zones"))
        assertNull(request.queryParameter("homeScope"))

        viewModel.selectZoneTab(ZONE_TAB_KEY_ALL)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Company)
        advanceUntilIdle()
        val allRequest = env.zoneMock.lastRequest()
        assertEquals("/api/zone/items", allRequest.encodedPath)
        assertNull(allRequest.queryParameter("zones"))
        assertNull(allRequest.queryParameter("homeScope"))
    }

    @Test
    fun entityScopeSwitchInFlight_dropsStaleResponse() = runTest(testDispatcher) {

        val env = env(focusZoneIds = listOf(100L))
        env.stubEntityPagesByZones(allIds = listOf(701L), followIds = listOf(601L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Company)

        viewModel.selectZoneTab(ZONE_TAB_KEY_ALL)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Company)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.All, state.zoneScope)
        val content = state.currentBoardContent as ZoneBoardContent.Entity
        assertEquals(listOf(701L), content.items.map { it.targetId })
        assertEquals(listOf(701L), state.entityBoards.getValue(ZoneBoard.Company).items.map { it.targetId })
    }

    @Test
    fun entitySingleZone_sendsZonesNodeIdForEveryType() = runTest(testDispatcher) {
        val env = env()
        env.stubZoneInfo(100L, tabs = listOf("action", "company", "series", "works", "charactar", "person"))
        env.stubSingleEntityPage(ids = listOf(701L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()

        listOf(
            ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
            ZoneBoard.Character, ZoneBoard.Person
        ).forEach { board ->
            viewModel.selectBoard(board)
            advanceUntilIdle()
            val request = env.zoneMock.lastRequest()
            assertEquals("/api/zone/items", request.encodedPath)
            assertEquals("100", request.queryParameter("zones"))
            assertEquals(board.type, request.queryParameter("itemType"))
            assertNull(request.queryParameter("homeScope"))
        }
    }

    @Test
    fun entityPagination_appendsNextPageByHasMore() = runTest(testDispatcher) {
        val env = env()
        val firstPage = (1L..20L).toList()

        env.stubEntityPages(mapOf(1 to (firstPage to true), 2 to (listOf(21L, 22L, 23L) to false)))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Works)
        advanceUntilIdle()

        val page1 = viewModel.uiState.value
        assertEquals(20, page1.currentBoardItemCount)
        assertTrue(page1.currentBoardHasMore)
        assertFalse(page1.currentBoardIsLoadingMore)
        assertEquals("1", env.zoneMock.lastRequest().queryParameter("page"))
        assertEquals("works", env.zoneMock.lastRequest().queryParameter("itemType"))

        viewModel.loadNextPage()
        advanceUntilIdle()

        val page2 = viewModel.uiState.value
        assertEquals(23, page2.currentBoardItemCount)
        assertEquals((21L..23L).toList(), page2.currentBoardContent.let { content ->
            (content as ZoneBoardContent.Entity).items.map { it.targetId }.takeLast(3)
        })
        assertFalse(page2.currentBoardHasMore)
        assertEquals("2", env.zoneMock.lastRequest().queryParameter("page"))

        val before = env.zoneMock.requestCount
        viewModel.loadNextPage()
        advanceUntilIdle()
        assertEquals(before, env.zoneMock.requestCount)
    }

    @Test
    fun entityBoard_switchAwayAndBack_keepsLoadedState() = runTest(testDispatcher) {
        val env = env()
        env.stubSingleEntityPage(ids = listOf(801L, 802L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Series)
        advanceUntilIdle()
        val requestCount = env.zoneMock.requestCount
        val loadedItems = viewModel.uiState.value.entityBoards.getValue(ZoneBoard.Series).items

        viewModel.selectBoard(ZoneBoard.Action)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Series)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneBoard.Series, state.selectedBoard)

        assertEquals(requestCount, env.zoneMock.requestCount)
        assertEquals(loadedItems, state.entityBoards.getValue(ZoneBoard.Series).items)
        assertEquals(2, state.currentBoardItemCount)
    }

    @Test
    fun entityBoard_failure_showsErrorThenRetryLoads() = runTest(testDispatcher) {
        val env = env()
        env.zoneMock.route(
            "api/zone/items",
            """{"success":false,"code":500,"msg":"网络异常","data":null}"""
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Character)
        advanceUntilIdle()

        val failed = viewModel.uiState.value
        assertEquals("网络异常", failed.currentBoardErrorMessage)
        assertEquals(0, failed.currentBoardItemCount)
        assertFalse(failed.isCurrentBoardLoading)

        env.zoneMock.replaceRoute(
            "api/zone/items",
            """{"success":true,"code":200,"msg":"ok","data":{"total":1,"hasMore":false,"list":[{"id":21,"itemId":901,"nameCN":"角色901","cover":"c.jpg"}]}}"""
        )
        viewModel.retrySelectedBoard()
        advanceUntilIdle()

        val retried = viewModel.uiState.value
        assertNull(retried.currentBoardErrorMessage)
        assertEquals(listOf(901L), retried.currentBoardContent.let { content ->
            (content as ZoneBoardContent.Entity).items.map { it.targetId }
        })

        assertEquals("charactar", env.zoneMock.lastRequest().queryParameter("itemType"))
    }

    @Test
    fun emptyFollowEntityBoard_shortCircuitsWithoutRequest() = runTest(testDispatcher) {
        val env = env()
        env.stubSingleEntityPage()
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.zoneScope.isFollowEmpty)

        val before = env.zoneMock.requestCount
        viewModel.selectBoard(ZoneBoard.Person)
        advanceUntilIdle()

        val state = viewModel.uiState.value

        assertEquals(before, env.zoneMock.requestCount)
        assertEquals(0, state.currentBoardItemCount)
        assertTrue(state.entityBoards.getValue(ZoneBoard.Person).isEndReached)
        assertFalse(state.currentBoardHasMore)
    }
}
