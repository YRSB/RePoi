package com.nobody.repoi.ui.search

import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.RecordedHttpRequest
import com.nobody.repoi.data.repository.SearchRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 搜索中枢多分类独立状态机测试。
 *
 * 覆盖：新搜索会话原子重置、按需懒加载、已加载分类缓存复用、
 * 单分类刷新/分页隔离、筛选排序只作用于归属分类。
 *
 * 路由按 `path == api/search` + form `category` 分发，避免依赖请求顺序。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchTabStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        testDispatcher.cancelChildren()
        Dispatchers.resetMain()
    }

    // ── 测试基建 ─────────────────────────────────────────────────

    private class TestEnv(val mock: MockApi, val viewModel: SearchViewModel)

    private fun MockApi.searchPageJson(category: String, page: Int): String {
        val ids = when (page) {
            1 -> listOf(1L, 2L)
            2 -> listOf(3L)
            else -> emptyList()
        }
        val items = ids.joinToString(",") { id ->
            """{"id":$id,"itemId":$id,"name":"$category-page$page-item$id"}"""
        }
        return ok(
            """{"result":[$items],"query":{"category":${category},"page":$page,"pageCount":3,"totalCount":3}}"""
        )
    }

    private fun MockApi.userPageJson(page: Int): String {
        val count = if (page == 1) 12 else 2
        val start = page * 100
        val users = (1..count).joinToString(",") { offset ->
            """{"userId":${start + offset},"nickname":"u${page}_$offset"}"""
        }
        return ok("""{"userList":[$users]}""")
    }

    private fun MockApi.stubSearchEndpoints() {
        routeWhen(
            predicate = { path, form -> path.endsWith("/api/search") && form["category"] != null },
            body = { _, form ->
                val category = form["category"].orEmpty()
                val page = form["page"]?.toIntOrNull() ?: 1
                searchPageJson(category, page)
            }
        )
        routeWhen(
            predicate = { path, _ -> path.endsWith("/api/search/user") },
            body = { _, form -> userPageJson(form["page"]?.toIntOrNull() ?: 1) }
        )
        routeWhen(
            predicate = { path, _ -> path.endsWith("/api/search/auto/fill") },
            body = { _, _ -> ok("""{"keywords":[]}""") }
        )
        routeWhen(
            predicate = { path, _ -> path.endsWith("/api/search/hotword") },
            body = { _, _ -> ok("""{"list":[]}""") }
        )
    }

    private fun TestScope.createEnv(): TestEnv {
        val mock = MockApi().apply { stubSearchEndpoints() }
        val historyManager = SearchHistoryManager(TestDataStore.inMemory(), json)
        val api = mock.api(testDispatcher)
        val viewModel = SearchViewModel(
            SearchRepository(api, historyManager),
            CategoryManager(api, backgroundScope)
        )
        return TestEnv(mock, viewModel)
    }

    /** 取走全部已记录请求（请求顺序无关，调用方按 path/form 过滤断言）。 */
    private fun MockApi.takeAllRequests(): List<RecordedHttpRequest> = buildList {
        while (requestCount > 0) {
            add(takeRequest())
        }
    }

    /** 主搜索（POST api/search）请求携带的分类 ID，按发出顺序。 */
    private fun List<RecordedHttpRequest>.searchCategories(): List<String> =
        filter { it.encodedPath?.endsWith("/api/search") == true }
            .mapNotNull { it.formEntries()["category"] }

    private fun SearchViewModel.tabState(tab: SearchTab): TabSearchState =
        uiState.value.tabStates.getValue(tab)

    private fun SearchViewModel.loadHobbyAndAlbum() {
        onKeywordChange("初音未来")
        onSearch()
        onSelectTab(SearchTab.ALBUM)
    }

    // ── 用例 ─────────────────────────────────────────────────────

    @Test
    fun newSearch_clearsEveryTabAndLoadsOnlyCurrentTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.loadHobbyAndAlbum()
        advanceUntilIdle()
        viewModel.tabState(SearchTab.ALBUM).isLoaded shouldBe true
        env.mock.takeAllRequests()

        // 新搜索只加载当前前台分类（此时为 ALBUM），其余分类（含已缓存的 HOBBY）全部失效
        viewModel.onSearch("雪初音")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.searchSessionId shouldBe 2
        state.selectedTab shouldBe SearchTab.ALBUM
        state.tabStates.size shouldBe SearchTab.entries.size

        SearchTab.entries.filter { it != SearchTab.ALBUM }.forEach { tab ->
            val tabState = state.tabStates.getValue(tab)
            tabState.isLoaded shouldBe false
            tabState.items.size shouldBe 0
            tabState.userItems.size shouldBe 0
            tabState.isLoading shouldBe false
        }
        state.tabStates.getValue(SearchTab.ALBUM).isLoaded shouldBe true
        state.tabStates.getValue(SearchTab.ALBUM).items.size shouldBe 2

        env.mock.takeAllRequests().searchCategories() shouldBe listOf("70000")
    }

    @Test
    fun onSelectTab_loadsOnlyTargetTabLazily() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.onKeywordChange("初音未来")
        viewModel.onSearch()
        advanceUntilIdle()
        env.mock.takeAllRequests()

        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()

        val album = viewModel.tabState(SearchTab.ALBUM)
        album.isLoaded shouldBe true
        album.items.size shouldBe 2
        album.page shouldBe 1
        viewModel.tabState(SearchTab.HOBBY).items.size shouldBe 2

        env.mock.takeAllRequests().searchCategories() shouldBe listOf("70000")
    }

    @Test
    fun switchingBackToLoadedTab_reusesCacheWithoutRequests() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.loadHobbyAndAlbum()
        advanceUntilIdle()
        env.mock.takeAllRequests()

        val hobbyBefore = viewModel.tabState(SearchTab.HOBBY)
        val albumBefore = viewModel.tabState(SearchTab.ALBUM)

        viewModel.onSelectTab(SearchTab.HOBBY)
        advanceUntilIdle()

        viewModel.uiState.value.selectedTab shouldBe SearchTab.HOBBY
        viewModel.tabState(SearchTab.HOBBY) shouldBe hobbyBefore
        viewModel.tabState(SearchTab.ALBUM) shouldBe albumBefore
        env.mock.takeAllRequests().searchCategories().size shouldBe 0
    }

    @Test
    fun onLoadMore_appendsOnlyToTargetTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.loadHobbyAndAlbum()
        advanceUntilIdle()
        env.mock.takeAllRequests()

        val hobbyBefore = viewModel.tabState(SearchTab.HOBBY)

        viewModel.onLoadMore(SearchTab.ALBUM)
        viewModel.tabState(SearchTab.ALBUM).isLoadingMore shouldBe true
        viewModel.tabState(SearchTab.HOBBY) shouldBe hobbyBefore

        advanceUntilIdle()

        val album = viewModel.tabState(SearchTab.ALBUM)
        album.page shouldBe 2
        album.items.size shouldBe 3
        album.isLoadingMore shouldBe false
        album.hasMore shouldBe true
        viewModel.tabState(SearchTab.HOBBY) shouldBe hobbyBefore

        env.mock.takeAllRequests().searchCategories() shouldBe listOf("70000")
    }

    @Test
    fun onRefresh_reloadsOnlyTargetTabAndKeepsOldList() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.loadHobbyAndAlbum()
        advanceUntilIdle()
        env.mock.takeAllRequests()

        val albumBefore = viewModel.tabState(SearchTab.ALBUM)

        viewModel.onRefresh(SearchTab.HOBBY)
        viewModel.tabState(SearchTab.HOBBY).isRefreshing shouldBe true
        viewModel.tabState(SearchTab.HOBBY).items.size shouldBe 2
        viewModel.tabState(SearchTab.ALBUM) shouldBe albumBefore

        advanceUntilIdle()

        val hobby = viewModel.tabState(SearchTab.HOBBY)
        hobby.isRefreshing shouldBe false
        hobby.isLoaded shouldBe true
        hobby.page shouldBe 1
        hobby.items.size shouldBe 2
        viewModel.tabState(SearchTab.ALBUM) shouldBe albumBefore

        env.mock.takeAllRequests().searchCategories() shouldBe listOf("10000")
    }

    @Test
    fun hobbyFilterChange_reloadsOnlyHobbyTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.loadHobbyAndAlbum()
        advanceUntilIdle()
        env.mock.takeAllRequests()

        val albumBefore = viewModel.tabState(SearchTab.ALBUM)

        viewModel.onHobbyCategoryChange(100)
        viewModel.tabState(SearchTab.ALBUM) shouldBe albumBefore

        advanceUntilIdle()

        val hobby = viewModel.tabState(SearchTab.HOBBY)
        hobby.isLoaded shouldBe true
        hobby.isLoading shouldBe false
        hobby.items.size shouldBe 2
        viewModel.tabState(SearchTab.ALBUM) shouldBe albumBefore

        env.mock.takeAllRequests().searchCategories() shouldBe listOf("100")
    }

    @Test
    fun sortChange_reloadsOnlyCurrentTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.loadHobbyAndAlbum()
        advanceUntilIdle()
        env.mock.takeAllRequests()

        val hobbyBefore = viewModel.tabState(SearchTab.HOBBY)

        viewModel.onSortChange("hits")
        advanceUntilIdle()

        viewModel.uiState.value.order shouldBe "hits"
        viewModel.tabState(SearchTab.HOBBY) shouldBe hobbyBefore
        viewModel.tabState(SearchTab.ALBUM).isLoaded shouldBe true

        env.mock.takeAllRequests().searchCategories() shouldBe listOf("70000")
    }

    @Test
    fun filterChange_withoutSubmittedSearch_doesNotRequest() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.onHobbyCategoryChange(100)
        viewModel.onAlbumCategoryChange(AlbumSubCategory.FIGURE)
        advanceUntilIdle()

        viewModel.tabState(SearchTab.HOBBY).isLoaded shouldBe false
        viewModel.tabState(SearchTab.ALBUM).isLoaded shouldBe false
        env.mock.takeAllRequests().searchCategories().size shouldBe 0
    }
}
