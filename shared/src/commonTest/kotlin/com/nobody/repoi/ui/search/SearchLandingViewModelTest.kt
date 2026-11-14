package com.nobody.repoi.ui.search

import app.cash.turbine.test
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.SearchRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 搜索落地页独立 ViewModel 状态机单元测试（Turbine + Kotest）。
 *
 * 验证搜索历史读写与删除、热词异步拉取与刷新、输入草稿变迁与 300ms 防抖联想建议过滤。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchLandingViewModelTest {

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

    private suspend fun setup(
        hotwordsJson: String = """{"list":[{"word":"初音未来","count":10},{"word":"粘土人","count":5}]}""",
        autofillJson: String = """{"keywords":["初音未来","初音手办","初音韶华"]}"""
    ): Triple<SearchLandingViewModel, SearchHistoryManager, MockApi> {
        val mockApi = MockApi()
        mockApi.routeWhen(
            predicate = { path, _ -> path.endsWith("/api/search/hotword") },
            body = { _, _ -> mockApi.ok(hotwordsJson) }
        )
        mockApi.routeWhen(
            predicate = { path, _ -> path.endsWith("/api/search/auto/fill") },
            body = { _, _ -> mockApi.ok(autofillJson) }
        )
        val historyManager = SearchHistoryManager(TestDataStore.inMemory(), json)
        historyManager.clearAll()
        val searchRepo = SearchRepository(mockApi.api(testDispatcher), historyManager)
        val viewModel = SearchLandingViewModel(historyManager, searchRepo)
        return Triple(viewModel, historyManager, mockApi)
    }

    @Test
    fun initialState_loadsSearchHistoryAndHotwords() = runTest(testDispatcher) {
        val (viewModel, _, _) = setup()
        advanceUntilIdle()

        viewModel.uiState.value.keyword shouldBe ""
        viewModel.uiState.value.searchHistory shouldBe emptyList()
        viewModel.uiState.value.isLoadingHotwords shouldBe false
        viewModel.uiState.value.hotwords.map { it.word } shouldContainExactly listOf("初音未来", "粘土人")
        viewModel.uiState.value.suggestions shouldBe emptyList()
    }

    @Test
    fun searchHistory_lifecycle_addRemoveAndClear() = runTest(testDispatcher) {
        val (viewModel, _, _) = setup()
        advanceUntilIdle()

        viewModel.uiState.test {
            // 初始状态
            awaitItem().searchHistory shouldBe emptyList()

            // 1. 添加 "手办"
            viewModel.onSearch("手办")
            advanceUntilIdle()
            awaitItem().searchHistory shouldContainExactly listOf("手办")

            // 2. 添加 "黏土人"（排在最前）
            viewModel.onSearch("黏土人")
            advanceUntilIdle()
            awaitItem().searchHistory shouldContainExactly listOf("黏土人", "手办")

            // 3. 删除单个 "手办"
            viewModel.onRemoveHistory("手办")
            advanceUntilIdle()
            awaitItem().searchHistory shouldContainExactly listOf("黏土人")

            // 4. 清空全部
            viewModel.onClearHistory()
            advanceUntilIdle()
            awaitItem().searchHistory shouldBe emptyList()
        }
    }

    @Test
    fun onSearch_withBlankKeyword_doesNotPersist() = runTest(testDispatcher) {
        val (viewModel, historyManager, _) = setup()
        advanceUntilIdle()

        viewModel.onSearch("   ")
        advanceUntilIdle()

        historyManager.getHistory() shouldBe emptyList()
        viewModel.uiState.value.searchHistory shouldBe emptyList()
    }

    @Test
    fun onKeywordChange_updatesKeywordAndDebouncesSuggestions() = runTest(testDispatcher) {
        val (viewModel, _, _) = setup()
        advanceUntilIdle()

        viewModel.onKeywordChange("初音")
        viewModel.uiState.value.keyword shouldBe "初音"
        viewModel.uiState.value.suggestions shouldBe emptyList()

        // 200ms 尚未满足 300ms 防抖门槛
        advanceTimeBy(200L)
        viewModel.uiState.value.suggestions shouldBe emptyList()

        // 累计达 350ms，触发联想拉取
        advanceTimeBy(150L)
        advanceUntilIdle()
        viewModel.uiState.value.suggestions shouldContainExactly listOf("初音未来", "初音手办", "初音韶华")
    }

    @Test
    fun onKeywordChange_rapidInputCancelsPreviousDebounce() = runTest(testDispatcher) {
        val (viewModel, _, _) = setup()
        advanceUntilIdle()

        viewModel.onKeywordChange("初")
        advanceTimeBy(100L)

        // 100ms 时快速输入新词，重置防抖计时
        viewModel.onKeywordChange("初音")
        viewModel.uiState.value.keyword shouldBe "初音"

        advanceTimeBy(200L)
        // 距第二次输入仅 200ms，联想不应过早派发
        viewModel.uiState.value.suggestions shouldBe emptyList()

        advanceTimeBy(150L)
        advanceUntilIdle()
        viewModel.uiState.value.suggestions shouldContainExactly listOf("初音未来", "初音手办", "初音韶华")
    }

    @Test
    fun onKeywordChange_withBlankKeyword_clearsSuggestionsAndCancelsDebounce() = runTest(testDispatcher) {
        val (viewModel, _, _) = setup()
        advanceUntilIdle()

        viewModel.onKeywordChange("初音")
        advanceTimeBy(350L)
        advanceUntilIdle()
        viewModel.uiState.value.suggestions shouldContainExactly listOf("初音未来", "初音手办", "初音韶华")

        // 输入变空白，立刻清空建议
        viewModel.onKeywordChange("")
        viewModel.uiState.value.keyword shouldBe ""
        viewModel.uiState.value.suggestions shouldBe emptyList()
    }

    @Test
    fun onClearKeyword_clearsKeywordAndSuggestionsImmediately() = runTest(testDispatcher) {
        val (viewModel, _, _) = setup()
        advanceUntilIdle()

        viewModel.onKeywordChange("初音")
        advanceTimeBy(350L)
        advanceUntilIdle()
        viewModel.uiState.value.suggestions.isNotEmpty() shouldBe true

        viewModel.onClearKeyword()
        viewModel.uiState.value.keyword shouldBe ""
        viewModel.uiState.value.suggestions shouldBe emptyList()
    }

    @Test
    fun refreshHotwords_reloadsHotwords() = runTest(testDispatcher) {
        val (viewModel, _, mockApi) = setup()
        advanceUntilIdle()

        // 切换 mock 返回内容（使用 routeWhenSuspend 覆盖初始普通路由）
        mockApi.routeWhenSuspend(
            predicate = { path, _, _ -> path.endsWith("/api/search/hotword") },
            body = { _, _, _ -> mockApi.ok("""{"list":[{"word":"雪初音","count":20}]}""") }
        )

        viewModel.refreshHotwords()
        advanceUntilIdle()

        viewModel.uiState.value.isLoadingHotwords shouldBe false
        viewModel.uiState.value.hotwords.map { it.word } shouldContainExactly listOf("雪初音")
    }

    @Test
    fun initParam_populatesInitialKeywordAndCategoryAndTriggersSuggestionsWithoutDebounce() = runTest(testDispatcher) {
        val (viewModel, _, _) = setup()
        advanceUntilIdle()

        viewModel.initParam(
            SearchNavParam(
                initialKeyword = "初音",
                initialCategory = 10000
            )
        )
        // 无需等待 300ms 防抖，直接拉取联想
        advanceUntilIdle()

        viewModel.uiState.value.keyword shouldBe "初音"
        viewModel.uiState.value.category shouldBe 10000
        viewModel.uiState.value.suggestions shouldContainExactly listOf("初音未来", "初音手办", "初音韶华")
    }

    @Test
    fun initParam_idempotency_doesNotOverwriteDraft() = runTest(testDispatcher) {
        val (viewModel, _, _) = setup()
        advanceUntilIdle()

        viewModel.initParam(SearchNavParam(initialKeyword = "初音"))
        advanceUntilIdle()

        // 用户更改为 "樱初音"
        viewModel.onKeywordChange("樱初音")
        viewModel.uiState.value.keyword shouldBe "樱初音"

        // 再次触发 initParam（如重组或配置变迁）不应覆写当前草稿
        viewModel.initParam(SearchNavParam(initialKeyword = "初音"))
        viewModel.uiState.value.keyword shouldBe "樱初音"
    }
}
