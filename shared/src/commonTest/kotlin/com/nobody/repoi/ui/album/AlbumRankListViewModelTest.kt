package com.nobody.repoi.ui.album

import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AlbumRankListViewModel 的 KMP 移植版（真实仓储 + MockEngine，替代 Mockito）。
 *
 * 点赞榜走 GET `api/item/praise/top`（query `type`/`page`），窗口榜走 POST
 * `api/album/list`（表单 `releaseStart`/`releaseEnd`）；按页分流用
 * [MockRepoEngine.dynamicResponder]，顺序确定的用队列。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumRankListViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: AlbumRepository

    private val windowParam = AlbumRankListNavParam(
        title = "本周人气相册",
        releaseStart = "2026-09-07 00:00:00",
        releaseEnd = "2026-09-13 23:59:59"
    )
    private val praiseParam = AlbumRankListNavParam(title = "本月点赞榜", praiseType = "month")

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine(dispatchers.engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = AlbumRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        dispatchers.tearDown()
    }

    private fun albumJson(id: Long): String =
        """{"id":$id,"itemId":${1000L + id},"nameCN":"相册$id"}"""

    private fun albumListJson(ids: List<Long>): String {
        val items = ids.joinToString(",") { albumJson(it) }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""
    }

    private fun failJson(code: Int, msg: String): String =
        """{"success":false,"code":$code,"msg":"$msg","data":null}"""

    /** 点赞榜按 query page 分流。 */
    private fun stubPraisePages(vararg pages: String) {
        mock.dynamicResponder = { req: HttpRequestData ->
            if (!req.url.encodedPath.contains("api/item/praise/top")) {
                null
            } else {
                val page = req.url.parameters["page"]?.toIntOrNull() ?: 1
                (pages.getOrElse(page - 1) { pages.last() }) to HttpStatusCode.OK
            }
        }
    }

    @Test
    fun windowInitial_loadsPraiseWindow() = runTest(dispatchers.testDispatcher) {
        mock.respondWhen(Regex.fromLiteral("api/album/list"), albumListJson(listOf(1L, 2L)))

        val viewModel = AlbumRankListViewModel(repository)
        viewModel.loadInitial(windowParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.items.size)
        assertEquals("本周人气相册", state.title)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun praiseInitial_loadsPraiseTop() = runTest(dispatchers.testDispatcher) {
        mock.respondWhen(Regex.fromLiteral("api/item/praise/top"), albumListJson(listOf(3L)))

        val viewModel = AlbumRankListViewModel(repository)
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()

        assertEquals(3L, viewModel.uiState.value.items.single().targetAlbumId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadMore_appendsSecondPage() = runTest(dispatchers.testDispatcher) {
        stubPraisePages(albumListJson((1L..10L).toList()), albumListJson(listOf(11L)))

        val viewModel = AlbumRankListViewModel(repository)
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        viewModel.loadMore(praiseParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(11, state.items.size)
        assertEquals(2, state.page)
        assertFalse(state.hasMore)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadMoreOverlap_ranksFollowServerPositions() = runTest(dispatchers.testDispatcher) {
        stubPraisePages(albumListJson((1L..10L).toList()), albumListJson(listOf(10L, 11L)))

        val viewModel = AlbumRankListViewModel(repository)
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        viewModel.loadMore(praiseParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(10, state.ranks[10L])
        assertEquals(12, state.ranks[11L])
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun appendFailure_routesToLoadMoreError() = runTest(dispatchers.testDispatcher) {
        stubPraisePages(albumListJson(listOf(1L)), failJson(500, "追加失败"))

        val viewModel = AlbumRankListViewModel(repository)
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        viewModel.loadMore(praiseParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertEquals("追加失败", state.loadMoreError)
        assertEquals(1, state.items.size)
        assertFalse(state.isAppending)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun failure_showsErrorAndRetryRecovers() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(failJson(500, "榜单失败"))
        mock.enqueue(albumListJson(listOf(4L)))

        val viewModel = AlbumRankListViewModel(repository)
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.errorMessage != null)
        viewModel.retry(praiseParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorMessage)
        assertEquals(4L, state.items.single().targetAlbumId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun shiftPeriod_previousWeekReloadsWindow() = runTest(dispatchers.testDispatcher) {
        // loadInitial 打当前周窗口，shiftPeriod(-1) 打上周窗口。队列顺序确定。
        mock.enqueue(albumListJson(listOf(1L)))
        mock.enqueue(albumListJson(listOf(2L)))
        val param = windowParam.copy(period = RankPeriod.WEEK)

        val viewModel = AlbumRankListViewModel(repository)
        viewModel.loadInitial(param)
        advanceUntilIdle()
        viewModel.shiftPeriod(param, -1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(-1, state.diff)
        assertEquals("上周", state.periodLabel)
        assertEquals(2L, state.items.single().targetAlbumId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun shiftPeriod_cannotGoFuture() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(albumListJson(listOf(1L)))
        val param = windowParam.copy(period = RankPeriod.WEEK)

        val viewModel = AlbumRankListViewModel(repository)
        viewModel.loadInitial(param)
        advanceUntilIdle()
        viewModel.shiftPeriod(param, 1)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.diff)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun periodWindow_monthYearBounds() {
        val date = LocalDate(2026, 9, 11)
        assertEquals(
            Pair("2026-09-01 00:00:00", "2026-09-30 23:59:59"),
            periodWindow(RankPeriod.MONTH, 0, date)
        )
        assertEquals(
            Pair("2026-08-01 00:00:00", "2026-08-31 23:59:59"),
            periodWindow(RankPeriod.MONTH, -1, date)
        )
        assertEquals(
            Pair("2026-01-01 00:00:00", "2026-12-31 23:59:59"),
            periodWindow(RankPeriod.YEAR, 0, date)
        )
        assertEquals("本月", periodLabel(RankPeriod.MONTH, 0))
        assertEquals("上月", periodLabel(RankPeriod.MONTH, -1))
        assertEquals("本年", periodLabel(RankPeriod.YEAR, 0))
    }

    @Test
    fun userLevelOf_matchesOfficialFormula() {
        assertEquals(0, userLevelOf(0, 0, 0))
        assertEquals(8, userLevelOf(9000, 7000, 7000))
        assertEquals(displayLevelOf(5, 9000, 7000, 7000), 5)
        assertEquals(displayLevelOf(0, 9000, 7000, 7000), 8)
    }

    @Test
    fun levelTier_bands() {
        assertEquals(0, levelTier(0))
        assertEquals(1, levelTier(5))
        assertEquals(2, levelTier(9))
    }
}
