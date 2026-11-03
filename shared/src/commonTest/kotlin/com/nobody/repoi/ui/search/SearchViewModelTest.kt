package com.nobody.repoi.ui.search

import app.cash.turbine.test
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.SearchRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 搜索中枢 ViewModel 状态机单元测试（Turbine + Kotest）。
 *
 * 验证关键词输入、Tab 切换、搜索历史增删清理与排序状态流转。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private suspend fun setup(): Pair<SearchViewModel, SearchHistoryManager> {
        val mockApi = MockApi()
        val historyManager = SearchHistoryManager(TestDataStore.searchHistoryPrefs(), json)
        historyManager.clearAll()
        val searchRepo = SearchRepository(mockApi.api(), historyManager)
        val categoryManager = CategoryManager(mockApi.api())
        val viewModel = SearchViewModel(searchRepo, categoryManager)
        return viewModel to historyManager
    }

    @Test
    fun onKeywordChange_updatesUiStateKeyword() = runTest(testDispatcher) {
        val (viewModel, _) = setup()

        viewModel.uiState.test {
            awaitItem().keyword shouldBe ""

            viewModel.onKeywordChange("初音未来")
            awaitItem().keyword shouldBe "初音未来"

            viewModel.onKeywordChange("雪初音")
            awaitItem().keyword shouldBe "雪初音"
        }
    }

    @Test
    fun onSelectTab_updatesSelectedTab() = runTest(testDispatcher) {
        val (viewModel, _) = setup()

        viewModel.uiState.test {
            awaitItem().selectedTab shouldBe SearchTab.HOBBY

            viewModel.onSelectTab(SearchTab.USER)
            awaitItem().selectedTab shouldBe SearchTab.USER

            viewModel.onSelectTab(SearchTab.ALBUM)
            awaitItem().selectedTab shouldBe SearchTab.ALBUM
        }
    }

    @Test
    fun onSearch_withBlankKeyword_doesNotSetSubmitted() = runTest(testDispatcher) {
        val (viewModel, _) = setup()

        viewModel.onKeywordChange("   ")
        viewModel.onSearch()
        advanceUntilIdle()

        viewModel.uiState.value.isSubmitted shouldBe false
        viewModel.uiState.value.isLoading shouldBe false
    }

    @Test
    fun historyManagement_addAndDeleteAndClear() = runTest(testDispatcher) {
        val (viewModel, historyManager) = setup()

        viewModel.uiState.test {
            awaitItem().searchHistory.shouldBeEmpty()

            historyManager.addKeyword("粘土人")
            awaitItem().searchHistory shouldContain "粘土人"

            viewModel.onClearHistory()
            awaitItem().searchHistory.shouldBeEmpty()
        }
    }

    @Test
    fun onSortChange_updatesOrderState() = runTest(testDispatcher) {
        val (viewModel, _) = setup()

        viewModel.uiState.test {
            awaitItem().order shouldBe ""

            viewModel.onSortChange("releaseDate")
            awaitItem().order shouldBe "releaseDate"
        }
    }
}
