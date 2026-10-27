package com.nobody.repoi.ui.detail.album

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.ui.navigation.HobbyAlbumListNavParam
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HobbyAlbumListViewModel 的 KMP 移植版（真实仓储 + MockEngine）。
 *
 * 相册端点走 dynamicResponder 按 `(order, page)` 分流，其余测试用队列串行消费。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyAlbumListViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: HobbyDetailRepository

    private val navParam = HobbyAlbumListNavParam(
        nodeId = 59570L,
        itemType = "hobby",
        title = "伊芙的相关相册"
    )

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine(dispatchers.engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = HobbyDetailRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        dispatchers.tearDown()
    }

    private fun albumJson(id: Long, name: String): String =
        """{"id":$id,"name":"$name","cover":"covers/hobby_album_$id.jpg","praiseCount":15,"user":{"userId":${100L + id},"nickname":"摄影师$id"},"picCount":5}"""

    private fun albumsJson(ids: Iterable<Long>, nameOf: (Long) -> String): String {
        val items = ids.joinToString(",") { albumJson(it, nameOf(it)) }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""
    }

    private fun formField(req: HttpRequestData, key: String): String? =
        (req.body as? io.ktor.client.request.forms.FormDataContent)?.formData?.get(key)

    @Test
    fun loadInitial_success_populatesAlbumsAndUpdatesState() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(albumsJson(1L..20L) { "相册 $it" })

        val viewModel = HobbyAlbumListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInitialLoading)
        assertEquals(20, state.albums.size)
        assertEquals("伊芙的相关相册", state.title)
        assertEquals(HobbyAlbumSortOrder.HOT, state.selectedOrder)
        assertTrue(state.hasMore)
        assertNull(state.errorMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadInitial_failure_setsErrorMessage() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":false,"code":500,"msg":"网络异常","data":null}""")

        val viewModel = HobbyAlbumListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInitialLoading)
        assertTrue(state.albums.isEmpty())
        assertNotNull(state.errorMessage)
        assertEquals("网络异常", state.errorMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun selectOrder_switchesOrderAndReloadsList() = runTest(dispatchers.testDispatcher) {
        mock.dynamicResponder = { req ->
            val order = formField(req, "order") ?: "hits"
            val body = if (order == "addTime") {
                albumsJson(10L..15L) { "最新 $it" }
            } else {
                albumsJson(1L..5L) { "热门 $it" }
            }
            body to HttpStatusCode.OK
        }

        val viewModel = HobbyAlbumListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        assertEquals(HobbyAlbumSortOrder.HOT, viewModel.uiState.value.selectedOrder)
        assertEquals(5, viewModel.uiState.value.albums.size)

        // 切换为“最新”
        viewModel.selectOrder(HobbyAlbumSortOrder.NEW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(HobbyAlbumSortOrder.NEW, state.selectedOrder)
        assertFalse(state.isInitialLoading)
        assertEquals(6, state.albums.size)
        assertEquals("最新 10", state.albums.first().displayName)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun refresh_success_updatesAlbums() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(albumsJson(1L..5L) { "初始 $it" })
        mock.enqueue(albumsJson(10L..15L) { "刷新 $it" })

        val viewModel = HobbyAlbumListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertEquals(6, state.albums.size)
        assertEquals("刷新 10", state.albums.first().displayName)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadMore_success_appendsNewAlbumsWithoutDuplicates() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(albumsJson(1L..20L) { "相册 $it" })
        val page2 = "[${albumJson(20L, "相册 20 重复")}," +
            (21L..30L).joinToString(",") { albumJson(it, "相册 $it") } + "]"
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":$page2}}""")

        val viewModel = HobbyAlbumListViewModel(repository)
        viewModel.loadInitial(navParam)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingMore)
        assertEquals(30, state.albums.size)
        assertEquals(2, state.page)
        assertFalse(state.hasMore)
        viewModel.viewModelScope.cancel()
    }
}
