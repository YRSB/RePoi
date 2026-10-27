package com.nobody.repoi.ui.search

import com.nobody.repoi.data.local.CategoryApi
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.SearchRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.test.TestDispatchers
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SearchViewModel 的 KMP 移植版（真实仓储 + MockEngine + 真 CategoryManager，替代 Mockito）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var stores: TestDataStores
    private lateinit var searchRepository: SearchRepository
    private lateinit var categoryManager: CategoryManager

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        searchRepository = SearchRepository(
            api,
            SearchHistoryManager(stores.createDataStore("search_history"), json)
        )
        categoryManager = CategoryManager(CategoryApi {
            throw UnsupportedOperationException("no network in test")
        })
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
        dispatchers.tearDown()
    }

    private fun createViewModel(): SearchViewModel {
        return SearchViewModel(searchRepository, categoryManager)
    }

    /**
     * 联想接口默认走规则返回空（onSearch 内 loadSuggestions 与 performSearch 并发，
     * 队列顺序不稳定，因此 autoFill 走规则、search 走队列）。
     */
    private fun stubAutoFillEmpty() {
        mock.respondWhen(
            Regex.fromLiteral("api/search/auto/fill"),
            """{"success":true,"code":200,"msg":"ok","data":{"keywords":[]}}"""
        )
    }

    private fun resultJson(id: Long, name: String = "结果$id"): String {
        return """{"id":$id,"itemId":${1000L + id},"nameCN":"$name"}"""
    }

    private fun searchJson(vararg items: String, totalCount: Int = items.size, pageCount: Int = 1): String {
        return """{"success":true,"code":200,"msg":"ok","data":{"result":[${items.joinToString(",")}],"query":{"totalCount":$totalCount,"pageCount":$pageCount}}}"""
    }

    private fun userJson(id: Long): String {
        return """{"userId":$id,"nickname":"用户$id"}"""
    }

    private fun userSearchJson(vararg items: String): String {
        return """{"success":true,"code":200,"msg":"ok","data":{"userList":[${items.joinToString(",")}]}}"""
    }

    @Test
    fun initParams_withKeyword_submitsSearch() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1), totalCount = 45, pageCount = 2))

        val viewModel = createViewModel()
        viewModel.initParams("初音", 10000)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("初音", state.keyword)
        assertEquals(SearchTab.HOBBY, state.selectedTab)
        assertTrue(state.isSubmitted)
        assertTrue(state.isParamsReady)
        assertEquals(listOf(1L), state.items.map { it.id })
        assertEquals(45, state.totalCount)
        assertEquals(2, state.pageCount)
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
        assertEquals(1, mock.requestCount)
        viewModel.close()
    }

    @Test
    fun initParams_emptyKeyword_loadsHotwordsInsteadOfSearch() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"word":"初音","count":9}]}}""")

        val viewModel = createViewModel()
        viewModel.initParams("", 10000)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSubmitted)
        assertEquals("初音", state.hotwordsMap[10000]?.first()?.word)
        assertFalse(state.isLoadingHotwords)
        // 只有热词 1 个请求，无搜索请求
        assertEquals(1, mock.requestCount)
        viewModel.close()
    }

    @Test
    fun initParams_companyOnly_submitsWithCompany() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1)))

        val viewModel = createViewModel()
        viewModel.initParams("", 40000, initialCompanyNodeId = 88L, initialCompanyName = "GSC")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SearchTab.COMPANY, state.selectedTab)
        assertTrue(state.isSubmitted)
        assertEquals(88L, state.selectedCompany?.nodeId)
        assertEquals("GSC", state.selectedCompany?.itemName)
        assertEquals(1, state.availableCompanies.size)
        viewModel.close()
    }

    @Test
    fun initParams_isIdempotent() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1)))

        val viewModel = createViewModel()
        viewModel.initParams("初音", 10000)
        advanceUntilIdle()
        viewModel.initParams("第二次", 70000)
        advanceUntilIdle()

        assertEquals("初音", viewModel.uiState.value.keyword)
        assertEquals(SearchTab.HOBBY, viewModel.uiState.value.selectedTab)
        assertEquals(1, mock.requestCount)
        viewModel.close()
    }

    @Test
    fun onKeywordChange_debouncesSuggestionsUntilDelayElapsed() = runTest(dispatchers.testDispatcher) {
        val viewModel = createViewModel()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"keywords":["初音未来"]}}""")
        viewModel.onKeywordChange("初")
        advanceTimeBy(299)
        assertEquals(0, mock.requestCount)

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(1, mock.requestCount)
        assertEquals(listOf("初音未来"), viewModel.uiState.value.suggestions)
        assertTrue(viewModel.uiState.value.isSuggestionsVisible)
        viewModel.close()
    }

    @Test
    fun onKeywordChange_blank_clearsSuggestionsAndResults() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1)))
        stubAutoFillEmpty()
        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()

        viewModel.onKeywordChange("   ")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.suggestions.isEmpty())
        assertFalse(state.isSuggestionsVisible)
        assertTrue(state.items.isEmpty())
        assertFalse(state.isSubmitted)
        viewModel.close()
    }

    @Test
    fun onSearch_blank_isNoOp() = runTest(dispatchers.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onSearch("   ")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSubmitted)
        assertEquals(0, mock.requestCount)
        viewModel.close()
    }

    @Test
    fun onSearch_recordsHistoryAndSubmits() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"keywords":["初音未来"]}}""")
        mock.enqueue(searchJson(resultJson(1)))

        val viewModel = createViewModel()
        viewModel.onSearch("  初音  ")
        advanceUntilIdle()

        assertEquals("初音", viewModel.uiState.value.keyword)
        assertTrue(viewModel.uiState.value.isSubmitted)
        // 真历史仓储验证
        assertTrue(searchRepository.searchHistoryFlow.first().contains("初音"))
        viewModel.close()
    }

    @Test
    fun onSelectTab_afterSubmission_resubmitsWithNewTab() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1)))
        stubAutoFillEmpty()

        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(2)))
        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SearchTab.ALBUM, state.selectedTab)
        assertEquals(listOf(2L), state.items.map { it.id })
        viewModel.close()
    }

    @Test
    fun onSelectTab_withoutSubmission_loadsThatTabsHotwords() = runTest(dispatchers.testDispatcher) {
        val countBefore = mock.requestCount
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"word":"相册热词","count":1}]}}""")

        val viewModel = createViewModel()
        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()

        assertEquals("相册热词", viewModel.uiState.value.hotwordsMap[70000]?.first()?.word)
        assertEquals(countBefore + 1, mock.requestCount)
        viewModel.close()
    }

    @Test
    fun onSortChange_resubmitsWithOrder() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1)))
        stubAutoFillEmpty()

        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(2)))
        viewModel.onSortChange("hits")
        advanceUntilIdle()

        assertEquals("hits", viewModel.uiState.value.order)
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })
        viewModel.close()
    }

    @Test
    fun onFilterChange_passesCompanyAndYear() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1)))
        stubAutoFillEmpty()

        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(2)))
        viewModel.onFilterChange(
            com.nobody.repoi.data.remote.model.SearchCompanyAggregate(nodeId = 88L, itemName = "GSC", docCount = 3),
            "2025"
        )
        advanceUntilIdle()

        assertEquals(88L, viewModel.uiState.value.selectedCompany?.nodeId)
        assertEquals("2025", viewModel.uiState.value.selectedYear)
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })
        viewModel.close()
    }

    @Test
    fun onAlbumCategoryChange_usesSubCategoryId() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        val viewModel = createViewModel()
        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(1)))
        stubAutoFillEmpty()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(2)))
        viewModel.onAlbumCategoryChange(AlbumSubCategory.FIGURE)
        advanceUntilIdle()

        assertEquals(AlbumSubCategory.FIGURE, viewModel.uiState.value.selectedAlbumCategory)
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })
        viewModel.close()
    }

    @Test
    fun onElementsCategoryChange_loadsAttributesAndSearches() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"attributeList":[]}}""")
        val viewModel = createViewModel()
        viewModel.onSelectTab(SearchTab.WORKS_ELEMENT)
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(1)))
        stubAutoFillEmpty()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"attributeList":[{"id":15,"name":"主角","categoryId":40100}]}}""")
        mock.enqueue(searchJson(resultJson(2)))
        viewModel.onElementsCategoryChange(ElementsSubCategory.CHARACTER)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ElementsSubCategory.CHARACTER, state.selectedElementsCategory)
        assertEquals("主角", state.attributeMap[40100]?.first()?.name)
        assertEquals(listOf(2L), state.items.map { it.id })
        viewModel.close()
    }

    @Test
    fun onAttributeChange_passesAttributeOnlyForElementsTab() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"attributeList":[]}}""")
        val viewModel = createViewModel()
        viewModel.onSelectTab(SearchTab.WORKS_ELEMENT)
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(1)))
        stubAutoFillEmpty()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(2)))
        viewModel.onAttributeChange(15)
        advanceUntilIdle()

        assertEquals(15, viewModel.uiState.value.selectedAttribute)
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })
        viewModel.close()
    }

    @Test
    fun userTab_searchesUsersAndAppendsNextPage() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        val viewModel = createViewModel()
        viewModel.onSelectTab(SearchTab.USER)
        advanceUntilIdle()

        val firstPage = (1L..10L).map { userJson(it) }.toTypedArray()
        mock.enqueue(userSearchJson(*firstPage))
        stubAutoFillEmpty()
        viewModel.onSearch("胶友")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasMore)
        mock.enqueue(userSearchJson(userJson(11), userJson(12)))
        viewModel.onLoadMore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(12, state.userItems.size)
        assertEquals(12L, state.userItems.last().userId)
        assertEquals(2, state.page)
        assertFalse(state.hasMore)
        viewModel.close()
    }

    @Test
    fun loadMore_appendsItemsAndComputesHasMore() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1), totalCount = 40, pageCount = 2))
        stubAutoFillEmpty()

        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(2), resultJson(3), totalCount = 40, pageCount = 2))
        viewModel.onLoadMore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L, 3L), state.items.map { it.id })
        assertEquals(2, state.page)
        assertEquals(40, state.totalCount)
        assertFalse(state.hasMore)
        viewModel.close()
    }

    @Test
    fun loadMore_withoutMorePages_skipsRequest() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1), pageCount = 1))
        stubAutoFillEmpty()

        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        val countBefore = mock.requestCount
        viewModel.onLoadMore()
        advanceUntilIdle()

        assertEquals(countBefore, mock.requestCount)
        viewModel.close()
    }

    @Test
    fun searchFailure_setsErrorMessage() = runTest(dispatchers.testDispatcher) {
        stubAutoFillEmpty()
        mock.enqueue("""{"success":false,"code":500,"msg":"搜索服务不可用","data":null}""")

        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage?.contains("搜索服务不可用") == true)
        viewModel.close()
    }

    @Test
    fun onRefresh_reloadsFirstPage() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1), pageCount = 2))
        stubAutoFillEmpty()

        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()
        mock.enqueue(searchJson(resultJson(9), pageCount = 2))
        viewModel.onRefresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(9L), state.items.map { it.id })
        assertFalse(state.isRefreshing)
        viewModel.close()
    }

    @Test
    fun onClearKeyword_resetsResultsAndSuggestions() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(searchJson(resultJson(1)))
        stubAutoFillEmpty()
        val viewModel = createViewModel()
        viewModel.onSearch("初音")
        advanceUntilIdle()

        viewModel.onClearKeyword()

        val state = viewModel.uiState.value
        assertEquals("", state.keyword)
        assertFalse(state.isSubmitted)
        assertTrue(state.items.isEmpty())
        assertTrue(state.userItems.isEmpty())
        assertTrue(state.suggestions.isEmpty())
        assertFalse(state.isSuggestionsVisible)
        assertNull(state.errorMessage)
        viewModel.close()
    }

    @Test
    fun loadElementAttributes_cachesByCategory() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"attributeList":[{"id":15,"name":"主角","categoryId":40100}]}}""")

        val viewModel = createViewModel()
        viewModel.loadElementAttributes(40100)
        advanceUntilIdle()
        viewModel.loadElementAttributes(40100)
        advanceUntilIdle()

        assertEquals(1, mock.requestCount)
        assertEquals(1, viewModel.uiState.value.attributeMap[40100]?.size)
        viewModel.close()
    }

    @Test
    fun refreshHotwords_reloadsCachedCategory() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"word":"旧热词","count":1}]}}""")

        val viewModel = createViewModel()
        viewModel.initParams("", 10000)
        advanceUntilIdle()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"word":"新热词","count":2}]}}""")
        viewModel.refreshHotwords()
        advanceUntilIdle()

        assertEquals("新热词", viewModel.uiState.value.hotwordsMap[10000]?.first()?.word)
        assertEquals(2, mock.requestCount)
        viewModel.close()
    }

    @Test
    fun historyAndSuggestionHelpers_delegateAndDismiss() = runTest(dispatchers.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onSearchExpanded()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"keywords":["初音未来"]}}""")
        viewModel.onKeywordChange("初")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSuggestionsVisible)

        viewModel.dismissSuggestions()
        assertFalse(viewModel.uiState.value.isSuggestionsVisible)

        searchRepository.addSearchHistory("初音")
        assertTrue(searchRepository.searchHistoryFlow.first().contains("初音"))
        viewModel.onRemoveHistory("初音")
        advanceUntilIdle()
        assertFalse(searchRepository.searchHistoryFlow.first().contains("初音"))
        searchRepository.addSearchHistory("初音2")
        viewModel.onClearHistory()
        advanceUntilIdle()
        assertTrue(searchRepository.searchHistoryFlow.first().isEmpty())
        viewModel.close()
    }
}
