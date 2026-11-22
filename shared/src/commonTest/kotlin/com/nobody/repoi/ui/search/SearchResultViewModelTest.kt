package com.nobody.repoi.ui.search

import app.cash.turbine.test
import com.nobody.repoi.data.remote.model.SearchCompanyAggregate
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
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

@OptIn(ExperimentalCoroutinesApi::class)
class SearchResultViewModelTest {

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

    private fun TestScope.createEnv(): SearchResultTestEnv =
        createSearchResultTestEnv(testDispatcher, json)

    @Test
    fun initParam_withKeyword_loadsInitialTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.initParam(
            SearchResultNavParam(
                keyword = "初音未来",
                category = HobbyAllCategory.id
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.keyword shouldBe "初音未来"
        state.displayTitle shouldBe "初音未来"
        state.selectedTab shouldBe SearchTab.HOBBY
        state.tabStates.getValue(SearchTab.HOBBY).isLoaded shouldBe true
        state.tabStates.getValue(SearchTab.HOBBY).items.size shouldBe 2

        SearchTab.entries.filter { it != SearchTab.HOBBY }.forEach { tab ->
            state.tabStates.getValue(tab).isLoaded shouldBe false
        }

        env.mock.takeAllRequests().searchCategories() shouldBe listOf("10000")
    }

    @Test
    fun initParam_withCompany_loadsInitialTabWithCompany() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.initParam(
            SearchResultNavParam(
                keyword = "",
                category = HobbyAllCategory.id,
                companyNodeId = 42L,
                companyName = "GoodSmile"
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        state.keyword shouldBe ""
        state.companyNodeId shouldBe 42L
        state.companyName shouldBe "GoodSmile"
        state.displayTitle shouldBe "GoodSmile"
        state.selectedTab shouldBe SearchTab.HOBBY
        state.hobbyFilter.company?.nodeId shouldBe 42L
        state.hobbyFilter.company?.itemName shouldBe "GoodSmile"

        val hobbyState = state.tabStates.getValue(SearchTab.HOBBY)
        hobbyState.isLoaded shouldBe true
        hobbyState.items.size shouldBe 2
    }

    @Test
    fun initParam_idempotency_doesNotReloadWhenSameParam() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        val param = SearchResultNavParam(keyword = "雪初音")
        viewModel.initParam(param)
        advanceUntilIdle()
        env.mock.takeAllRequests()

        viewModel.initParam(param)
        advanceUntilIdle()
        env.mock.takeAllRequests().size shouldBe 0
    }

    @Test
    fun onTabVisible_loadsTargetTabWithoutChangingSelection() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        advanceUntilIdle()
        env.mock.takeAllRequests()

        viewModel.onTabVisible(SearchTab.ALBUM)
        advanceUntilIdle()

        viewModel.uiState.value.selectedTab shouldBe SearchTab.HOBBY
        val album = viewModel.tabState(SearchTab.ALBUM)
        album.isLoaded shouldBe true
        album.items.size shouldBe 2
        env.mock.takeAllRequests().searchCategories() shouldBe listOf("70000")

        viewModel.onTabVisible(SearchTab.ALBUM)
        advanceUntilIdle()
        env.mock.takeAllRequests().searchCategories().size shouldBe 0
    }

    @Test
    fun onSelectTab_loadsOnlyTargetTabLazilyAndReusesCache() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        advanceUntilIdle()
        env.mock.takeAllRequests()

        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()

        val album = viewModel.tabState(SearchTab.ALBUM)
        album.isLoaded shouldBe true
        album.items.size shouldBe 2
        album.page shouldBe 1
        env.mock.takeAllRequests().searchCategories() shouldBe listOf("70000")

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

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        viewModel.onSelectTab(SearchTab.ALBUM)
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
    fun onRefresh_reloadsOnlyTargetTabAndKeepsOtherTabCache() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        viewModel.onSelectTab(SearchTab.ALBUM)
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

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        viewModel.onSelectTab(SearchTab.ALBUM)
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

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        viewModel.onSelectTab(SearchTab.ALBUM)
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
    fun onResetFilters_restoresDefaultAndReloadsCurrentTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        advanceUntilIdle()

        viewModel.onHobbyCategoryChange(100)
        advanceUntilIdle()
        env.mock.takeAllRequests()

        viewModel.uiState.value.selectedHobbyCategory shouldBe 100

        viewModel.onResetFilters()
        advanceUntilIdle()

        viewModel.uiState.value.selectedHobbyCategory shouldBe HobbyAllCategory.id
        viewModel.tabState(SearchTab.HOBBY).isLoaded shouldBe true
        env.mock.takeAllRequests().searchCategories() shouldBe listOf("10000")
    }

    @Test
    fun filters_areIsolatedPerTabAndSurviveSwitching() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel
        val company = SearchCompanyAggregate(nodeId = 42, itemName = "GoodSmile", docCount = 5)

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        advanceUntilIdle()

        viewModel.onSortChange("hits")
        viewModel.onHobbyCategoryChange(100)
        viewModel.onFilterChange(company, "2024")
        advanceUntilIdle()

        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()
        viewModel.onSortChange("addTime")
        viewModel.onAlbumCategoryChange(AlbumSubCategory.FIGURE)
        advanceUntilIdle()

        viewModel.onSelectTab(SearchTab.HOBBY)
        advanceUntilIdle()

        val hobby = viewModel.uiState.value.hobbyFilter
        hobby.order shouldBe "hits"
        hobby.categoryId shouldBe 100
        hobby.company shouldBe company
        hobby.year shouldBe "2024"
        hobby.availableCompanies shouldBe listOf(company)
        hobby.availableYears.mapNotNull { it.keyAsString } shouldBe listOf("2024")

        val album = viewModel.uiState.value.albumFilter
        album.order shouldBe "addTime"
        album.category shouldBe AlbumSubCategory.FIGURE

        viewModel.uiState.value.order shouldBe "hits"
        viewModel.uiState.value.selectedCompany shouldBe company
        viewModel.uiState.value.selectedYear shouldBe "2024"
    }

    @Test
    fun elementsFilterChange_sendsElementsCategoryAndAttribute() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        advanceUntilIdle()
        env.mock.takeAllRequests()

        viewModel.onSelectTab(SearchTab.WORKS_ELEMENT)
        advanceUntilIdle()
        viewModel.onElementsCategoryChange(ElementsSubCategory.CHARACTER)
        viewModel.onAttributeChange(7)
        advanceUntilIdle()

        val elementsForm = env.mock.takeAllRequests().lastSearchForm()
        elementsForm["category"] shouldBe "40100"
        elementsForm["attribute"] shouldBe "7"
        elementsForm["order"] shouldBe null
        elementsForm["company"] shouldBe null
        elementsForm["releaseYear"] shouldBe null
    }

    @Test
    fun isFilterActive_andTabScopedAccessors_followTargetTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        advanceUntilIdle()

        viewModel.uiState.value.isFilterActive shouldBe false

        viewModel.onHobbyCategoryChange(100)
        advanceUntilIdle()
        viewModel.uiState.value.isFilterActive shouldBe true

        viewModel.onSelectTab(SearchTab.COMPANY)
        advanceUntilIdle()
        viewModel.uiState.value.isFilterActive shouldBe false

        viewModel.onSelectTab(SearchTab.HOBBY)
        advanceUntilIdle()
        viewModel.uiState.value.isFilterActive shouldBe true

        viewModel.onResetFilters()
        advanceUntilIdle()
        viewModel.uiState.value.isFilterActive shouldBe false

        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()
        viewModel.onSortChange("hits")
        advanceUntilIdle()
        viewModel.uiState.value.isFilterActive shouldBe true

        viewModel.onSelectTab(SearchTab.HOBBY)
        advanceUntilIdle()
        viewModel.uiState.value.isFilterActive shouldBe false

        viewModel.uiState.value.orderFor(SearchTab.ALBUM) shouldBe "hits"
        viewModel.uiState.value.isFilterActiveFor(SearchTab.ALBUM) shouldBe true
        viewModel.uiState.value.orderFor(SearchTab.HOBBY) shouldBe ""
        viewModel.uiState.value.isFilterActiveFor(SearchTab.HOBBY) shouldBe false
        viewModel.uiState.value.orderFor(SearchTab.COMPANY) shouldBe ""
        viewModel.uiState.value.isFilterActiveFor(SearchTab.COMPANY) shouldBe false
    }

    @Test
    fun onResetFilters_onlyResetsAndReloadsActiveTab_keepingOtherTabs() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel
        val company = SearchCompanyAggregate(nodeId = 42, itemName = "GoodSmile", docCount = 5)

        viewModel.initParam(SearchResultNavParam(keyword = "初音未来"))
        viewModel.onSortChange("hits")
        viewModel.onHobbyCategoryChange(100)
        viewModel.onFilterChange(company, "2024")
        advanceUntilIdle()

        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()
        viewModel.onSortChange("addTime")
        viewModel.onAlbumCategoryChange(AlbumSubCategory.FIGURE)
        advanceUntilIdle()

        viewModel.onSelectTab(SearchTab.HOBBY)
        advanceUntilIdle()
        val albumFilterBefore = viewModel.uiState.value.albumFilter
        val albumStateBefore = viewModel.tabState(SearchTab.ALBUM)
        env.mock.takeAllRequests()

        viewModel.onResetFilters()
        advanceUntilIdle()

        val hobby = viewModel.uiState.value.hobbyFilter
        hobby.order shouldBe ""
        hobby.categoryId shouldBe HobbyAllCategory.id
        hobby.company shouldBe null
        hobby.year shouldBe null

        hobby.availableCompanies shouldBe listOf(company)
        hobby.availableYears.mapNotNull { it.keyAsString } shouldBe listOf("2024")

        viewModel.uiState.value.albumFilter shouldBe albumFilterBefore
        viewModel.tabState(SearchTab.ALBUM) shouldBe albumStateBefore
        env.mock.takeAllRequests().searchCategories() shouldBe listOf("10000")
    }
}
