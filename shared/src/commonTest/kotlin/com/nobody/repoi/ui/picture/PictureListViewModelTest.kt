package com.nobody.repoi.ui.picture

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.PictureListRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.test.recordedBody
import com.nobody.repoi.ui.navigation.PictureListNavParam
import io.ktor.client.HttpClient
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PictureListViewModel 的 KMP 移植版（真实仓储 + MockEngine）。
 *
 * 原测试用 StandardTestDispatcher + setMain 冻结协程以断言静默/重入语义；
 * KMP 侧 loader 的 stage 切换是同步的（`_state.update` 在 launch 之前），
 * 因此 `advanceUntilIdle()` 后直接断言即可，`verify(times(1))` 翻译为请求历史计数。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PictureListViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: PictureListRepository

    private val navParam = PictureListNavParam(
        itemId = 59570L,
        itemType = "hobby",
        subType = "official",
        title = "官图全集"
    )

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine(dispatchers.engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = PictureListRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        dispatchers.tearDown()
    }

    private fun picJson(id: Long): String =
        """{"id":$id,"path":"items/pic_$id.jpg","pictureInfo":{"id":$id,"path":"items/pic_$id.jpg","width":1200,"height":1600}}"""

    private fun picsJson(ids: Iterable<Long>): String {
        val items = ids.joinToString(",") { picJson(it) }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""
    }

    private fun pageOf(body: String): String =
        body.split("&").firstOrNull { it.startsWith("page=") }
            ?.substringAfter("=")?.decodeURLPart() ?: ""

    @Test
    fun loadInitial_success_populatesItemsAndUpdatesState() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(picsJson(1L..20L))

        val viewModel = PictureListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.errorMessage)
        assertEquals(20, state.items.size)
        assertTrue(state.hasMore)
        assertEquals(1, state.currentPage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadInitial_failure_setsErrorMessage() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":false,"code":500,"msg":"网络异常","data":null}""")

        val viewModel = PictureListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertEquals("网络异常", state.errorMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadMore_success_appendsItemsAndDeduplicates() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(picsJson(1L..20L))
        mock.enqueue(picsJson(20L..25L)) // 包含重复 ID 20

        val viewModel = PictureListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.items.size)

        viewModel.loadMore(navParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAppending)
        assertNull(state.loadMoreError)
        // 20 + (21..25) 共 5 个新项，去重后为 25 项
        assertEquals(25, state.items.size)
        // page2 返回 6 项 (< 20)，说明已到达末尾
        assertFalse(state.hasMore)
        assertEquals(2, state.currentPage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadMoreSilently_success_doesNotTriggerAppendingState() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(picsJson(1L..20L))
        mock.enqueue(picsJson(21L..30L))

        val viewModel = PictureListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        viewModel.loadMoreSilently(navParam)
        // 静默翻页不触发可见追加指示器（stage 切换同步，立即断言）
        assertFalse(viewModel.uiState.value.isAppending)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(30, state.items.size)
        assertFalse(state.isAppending)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun refresh_success_reloadsPage1() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(picsJson(1L..20L))
        mock.enqueue(picsJson(listOf(99L)))

        val viewModel = PictureListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.items.size)

        viewModel.refresh(navParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(1, state.items.size)
        assertEquals(99L, state.items.first().id)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadMore_whileActive_ignoresReentrantCalls() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(picsJson(1L..20L))
        mock.enqueue(picsJson(21L..30L))

        val viewModel = PictureListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        // 第一次调用同步进入 Appending 阶段，第二次必须被 stage 守卫拦截
        viewModel.loadMoreSilently(navParam)
        viewModel.loadMoreSilently(navParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(30, state.items.size)
        assertEquals(2, state.currentPage)
        // page=2 的请求只发出一次
        val page2Count = mock.engine.requestHistory.count { pageOf(it.recordedBody()) == "2" }
        assertEquals(1, page2Count)
        viewModel.viewModelScope.cancel()
    }
}
