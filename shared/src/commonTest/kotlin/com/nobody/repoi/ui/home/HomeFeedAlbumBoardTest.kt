package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.AlbumCategoryOption
import com.nobody.repoi.ui.board.ZoneBoard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
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

/**
 * 首页分享（相册）板块单测（issue #157 验收标准）。
 *
 * 覆盖：懒加载（首次切到才请求）、三作用域请求参数（`zones` 并集 / 单区 nodeId / 全站省略）、
 * 6 档排序与分类切换的重置重载、分页去重追加、切走再切回状态保留、空关注短路。
 *
 * 请求参数的事实依据：`docs/research/aggregate-api-verification.md` §3/§4.3/§5
 * （相册 `zones` 为并集，三种作用域均可用；`homeScope` 对相册无意义，不得发送）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedAlbumBoardTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        testDispatcher.cancelChildren()
        Dispatchers.resetMain()
    }

    private class Env(
        val feedMock: MockApi,
        val zoneMock: MockApi,
        val albumMock: MockApi,
        val preferences: ViewPreferencesManager,
        val zoneRepository: ZoneRepository,
        val dispatcher: CoroutineDispatcher,
    ) {
        val userSession = FakeUserSession()

        fun viewModel(): HomeFeedViewModel = HomeFeedViewModel(
            HomeRepository(feedMock.api(dispatcher)),
            AlbumRepository(albumMock.api(dispatcher)),
            zoneRepository,
            preferences,
            userSession
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

    private fun albumItem(id: Long): String =
        """{"id":$id,"itemId":${1000 + id},"nameCN":"相册$id","cover":"c.jpg"}"""

    private fun albumPageJson(ids: List<Long>): String =
        """{"success":true,"code":200,"msg":"ok","data":{"page":1,"list":[${ids.joinToString(",") { albumItem(it) }}]}}"""

    /** 按页码路由相册列表（页大小 24；整页才 `hasMore`）。 */
    private fun Env.stubAlbumPages(pages: Map<Int, List<Long>>) {
        albumMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/album/list") },
            body = { _, form, _ ->
                val page = form["page"]?.toIntOrNull() ?: 1
                albumPageJson(pages[page].orEmpty())
            }
        )
    }

    /** 单专区 `api/zone/info` 路由（首页据此装配二级板块）。 */
    private fun Env.stubZoneInfo(nodeId: Long, tabs: List<String>) {
        val tabsJson = tabs.joinToString(",") { type -> "\"$type\"" }
        zoneMock.routeWhen(
            predicate = { path, _, query -> path.contains("api/zone/info") && query["nodeId"] == nodeId.toString() },
            body = { _, _, _ ->
                """{"success":true,"code":200,"msg":"ok","data":{"zone":{"nodeId":$nodeId,"nameCN":"手办"},"focusState":false,"followCount":0,"tabs":[$tabsJson]}}"""
            }
        )
    }

    private fun Env.stubAlbumCategories(categories: List<AlbumCategoryOption>) {
        val json = categories.joinToString(",") { """{"categoryId":${it.id},"name":"${it.name}"}""" }
        albumMock.routeWhen(
            predicate = { path, _, query -> path.contains("api/zone/categories") && query["itemType"] == "album" },
            body = { _, _, _ ->
                """{"success":true,"code":200,"msg":"ok","data":{"visible":true,"categories":[$json]}}"""
            }
        )
    }

    private fun List<AlbumListItem>.ids(): List<Long> = map { it.targetAlbumId }

    @Test
    fun albumBoard_isLazyLoadedOnlyAfterSwitch() = runTest(testDispatcher) {
        val env = env()
        env.stubAlbumPages(mapOf(1 to listOf(7L, 8L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        // 聚合态二级 Tab 自动包含分享，但未切过去之前零请求
        assertEquals(ZoneBoard.HOME_ORDER.filter { it in ZoneBoard.HOME_IMPLEMENTED }, viewModel.uiState.value.boards)
        assertTrue(ZoneBoard.Album in viewModel.uiState.value.boards)
        assertEquals(0, env.albumMock.requestCount)

        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()

        assertEquals(1, env.albumMock.requestCount)
        val request = env.albumMock.lastRequest()
        assertEquals("/api/album/list", request.encodedPath)
        val form = request.formEntries()
        assertEquals(AlbumBoardOptions.DEFAULT_ORDER, form["order"])
        assertEquals("1", form["page"])
        assertEquals(AlbumBoardOptions.PAGE_SIZE.toString(), form["pageSize"])
        // 全部作用域：省略 zones / category / homeScope
        assertFalse(form.containsKey("zones"))
        assertFalse(form.containsKey("category"))
        assertFalse(form.containsKey("homeScope"))

        val state = viewModel.uiState.value
        assertEquals(listOf(7L, 8L), state.albumItems.ids())
        assertTrue(state.isAlbumLoaded)
        assertEquals(listOf(7L, 8L), state.currentBoardContent.let { content ->
            (content as com.nobody.repoi.ui.board.ZoneBoardContent.Album).items.ids()
        })
    }

    @Test
    fun albumFollowScope_sendsCommaSeparatedZonesUnion() = runTest(testDispatcher) {
        val env = env()
        env.zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[{"nodeId":100,"nameCN":"手办"},{"nodeId":200,"nameCN":"模型"}]}}"""
        )
        env.stubAlbumPages(mapOf(1 to listOf(11L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        // #156 冷启动把落盘的 follow 回落「全部」，因此运行期显式切到关注
        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()

        val form = env.albumMock.lastRequest().formEntries()
        assertEquals("100,200", form["zones"])
        assertFalse(form.containsKey("homeScope"))
        // 聚合态不提供分类候选（原版仅单专区展示分类入口）
        assertTrue(viewModel.uiState.value.albumCategoryOptions.isEmpty())
        assertTrue(viewModel.uiState.value.albumSelectedCategory == null)
    }

    @Test
    fun albumSingleZone_sendsNodeIdAndLoadsCategoryOptions() = runTest(testDispatcher) {
        val env = env()
        env.stubZoneInfo(nodeId = 100L, tabs = listOf("action", "album"))
        env.stubAlbumPages(mapOf(1 to listOf(21L)))
        env.stubAlbumCategories(
            listOf(
                AlbumCategoryOption(60100, "手办模型周边"),
                AlbumCategoryOption(60200, "GK/DIY")
            )
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()

        // 首屏相册列表：单专区 zones=nodeId
        val albumRequest = env.albumMock.takeRequest()
        assertEquals("/api/album/list", albumRequest.encodedPath)
        assertEquals("100", albumRequest.formEntries()["zones"])
        // 分类候选：单专区专用接口，itemType=album
        val categoriesRequest = env.albumMock.takeRequest()
        assertEquals("/api/zone/categories", categoriesRequest.encodedPath)
        assertEquals("100", categoriesRequest.queryParameter("nodeId"))
        assertEquals("album", categoriesRequest.queryParameter("itemType"))

        val state = viewModel.uiState.value
        assertEquals(
            listOf(AlbumCategoryOption(60100, "手办模型周边"), AlbumCategoryOption(60200, "GK/DIY")),
            state.albumCategoryOptions
        )

        // 切分类：清列表 + 回第一页 + 带 category 重新请求
        viewModel.selectAlbumCategory(60100)
        advanceUntilIdle()
        val filtered = env.albumMock.lastRequest()
        assertEquals("/api/album/list", filtered.encodedPath)
        val filteredForm = filtered.formEntries()
        assertEquals("60100", filteredForm["category"])
        assertEquals("1", filteredForm["page"])
        assertEquals(60100, viewModel.uiState.value.currentBoardFilters.albumCategory)

        // 切回全部分类：省略 category 参数
        viewModel.selectAlbumCategory(null)
        advanceUntilIdle()
        val allCategories = env.albumMock.lastRequest().formEntries()
        assertFalse(allCategories.containsKey("category"))
    }

    @Test
    fun albumOrderChange_resetsListAndReloadsFirstPage() = runTest(testDispatcher) {
        val env = env()
        env.albumMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/album/list") },
            body = { _, form, _ ->
                when (form["order"]) {
                    "picCount" -> albumPageJson(listOf(31L, 32L))
                    else -> albumPageJson(listOf(1L, 2L))
                }
            }
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.albumItems.ids())

        viewModel.selectAlbumOrder("picCount")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("picCount", state.albumSelectedOrder)
        assertEquals(listOf(31L, 32L), state.albumItems.ids())
        assertEquals(1, state.albumPage)
        val form = env.albumMock.lastRequest().formEntries()
        assertEquals("picCount", form["order"])
        assertEquals("1", form["page"])

        // 非法排序键被忽略，不发请求
        val before = env.albumMock.requestCount
        viewModel.selectAlbumOrder("bogus")
        advanceUntilIdle()
        assertEquals(before, env.albumMock.requestCount)
    }

    @Test
    fun albumPagination_appendsDistinctNextPage() = runTest(testDispatcher) {
        val env = env()
        val firstPage = (1L..24L).toList()
        // 第二页含重复项 24，验证宿主按 targetAlbumId 去重；非整页 → hasMore=false
        env.stubAlbumPages(mapOf(1 to firstPage, 2 to listOf(24L, 25L, 26L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()

        val page1 = viewModel.uiState.value
        assertEquals(24, page1.albumItems.size)
        assertTrue(page1.albumHasMore)
        assertTrue(page1.currentBoardHasMore)

        viewModel.loadNextPage()
        advanceUntilIdle()

        val page2 = viewModel.uiState.value
        assertEquals(26, page2.albumItems.size)
        assertEquals(listOf(24L, 25L, 26L), page2.albumItems.ids().takeLast(3))
        assertEquals(2, page2.albumPage)
        assertFalse(page2.albumHasMore)
        assertEquals("2", env.albumMock.lastRequest().formEntries()["page"])
    }

    @Test
    fun albumSwitchAwayAndBack_keepsStateWithoutReload() = runTest(testDispatcher) {
        val env = env()
        env.stubAlbumPages(mapOf(1 to listOf(41L, 42L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()
        viewModel.selectAlbumOrder("hits")
        advanceUntilIdle()
        val requestCount = env.albumMock.requestCount

        viewModel.selectBoard(ZoneBoard.Action)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneBoard.Album, state.selectedBoard)
        // 切走再切回：不重复请求，状态（items / 排序）保留
        assertEquals(requestCount, env.albumMock.requestCount)
        assertEquals("hits", state.albumSelectedOrder)
        assertTrue(state.isAlbumLoaded)
    }

    @Test
    fun emptyFollowAlbum_shortCircuitsWithoutRequest() = runTest(testDispatcher) {
        val env = env()
        val viewModel = env.viewModel()
        advanceUntilIdle()
        // #156 冷启动会回落 follow，这里模拟运行期显式切到「关注」且未关注任何专区
        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.zoneScope.isFollowEmpty)

        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, env.albumMock.requestCount)
        assertTrue(state.albumItems.isEmpty())
        assertFalse(state.albumHasMore)
        assertTrue(state.isAlbumLoaded)
    }
}
