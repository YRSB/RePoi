package com.nobody.repoi.ui.search

import app.cash.turbine.test
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

    private fun TestScope.createEnv(): SearchTestEnv =
        createSearchTestEnv(testDispatcher, json)

    /** 提交搜索并切到相册，使周边/相册各有一份缓存。 */
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
    fun onTabVisible_loadsTargetTabWithoutChangingSelection() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.submitAndLoadHobby()
        advanceUntilIdle()
        env.mock.takeAllRequests()

        // 页面滑入即预取：只加载目标分类，不改变当前选中分类
        viewModel.onTabVisible(SearchTab.ALBUM)
        advanceUntilIdle()

        viewModel.uiState.value.selectedTab shouldBe SearchTab.HOBBY
        val album = viewModel.tabState(SearchTab.ALBUM)
        album.isLoaded shouldBe true
        album.items.size shouldBe 2
        env.mock.takeAllRequests().searchCategories() shouldBe listOf("70000")

        // 已加载分类再次可见：零请求
        viewModel.onTabVisible(SearchTab.ALBUM)
        advanceUntilIdle()
        env.mock.takeAllRequests().searchCategories().size shouldBe 0
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

        viewModel.uiState.test {
            skipItems(1)
            viewModel.onLoadMore(SearchTab.ALBUM)
            val loading = awaitItem()
            loading.tabStates.getValue(SearchTab.ALBUM).isLoadingMore shouldBe true
            loading.tabStates.getValue(SearchTab.HOBBY) shouldBe hobbyBefore
        }

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

        viewModel.uiState.test {
            skipItems(1)
            viewModel.onRefresh(SearchTab.HOBBY)
            val refreshing = awaitItem()
            refreshing.tabStates.getValue(SearchTab.HOBBY).isRefreshing shouldBe true
            refreshing.tabStates.getValue(SearchTab.HOBBY).items.size shouldBe 2
            refreshing.tabStates.getValue(SearchTab.ALBUM) shouldBe albumBefore
        }

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
