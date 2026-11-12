package com.nobody.repoi.ui.search

import app.cash.turbine.test
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.remote.model.SearchCompanyAggregate
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.RecordedHttpRequest
import com.nobody.repoi.data.repository.SearchRepository
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

/**
 * 搜索筛选状态按分类隔离的接口级测试。
 *
 * 覆盖：周边/相册/角色要素筛选互不覆盖、筛选变更只重载归属分类、
 * 请求参数取自目标分类快照、Badge 随活动分类切换、重置只作用于活动分类。
 *
 * 路由按 `path == api/search` + form `category` 分发，请求顺序无关。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchTabFilterTest {

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
        // 聚合响应附带厂商/年份候选，用于验证候选列表只归属周边快照
        val aggregates =
            ""","companys":[{"nodeId":42,"itemName":"GoodSmile","docCount":5}],"releaseYear":[{"keyAsString":"2024","docCount":3}]"""
        return ok(
            """{"result":[$items],"query":{"category":${category},"page":$page,"pageCount":3,"totalCount":3}$aggregates}"""
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
        routeWhen(
            predicate = { path, _ -> path.contains("/api/search/attribute") },
            body = { _, _ -> ok("""{"attributeList":[]}""") }
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

    private fun MockApi.takeAllRequests(): List<RecordedHttpRequest> = buildList {
        while (requestCount > 0) {
            add(takeRequest())
        }
    }

    private fun List<RecordedHttpRequest>.searchRequests(): List<RecordedHttpRequest> =
        filter { it.encodedPath?.endsWith("/api/search") == true }

    private fun List<RecordedHttpRequest>.searchCategories(): List<String> =
        searchRequests().mapNotNull { it.formEntries()["category"] }

    private fun List<RecordedHttpRequest>.lastSearchForm(): Map<String, String> =
        searchRequests().last().formEntries()

    private fun SearchViewModel.tabState(tab: SearchTab): TabSearchState =
        uiState.value.tabStates.getValue(tab)

    private fun SearchViewModel.submitAndLoadHobby() {
        onKeywordChange("初音未来")
        onSearch()
    }

    private val company = SearchCompanyAggregate(nodeId = 42, itemName = "GoodSmile", docCount = 5)

    // ── 用例 ─────────────────────────────────────────────────────

    @Test
    fun filters_areIsolatedPerTabAndSurviveSwitching() = runTest(testDispatcher) {
        val viewModel = createEnv().viewModel
        viewModel.submitAndLoadHobby()
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

        // 切回周边后派生访问器取的是周边快照
        viewModel.uiState.value.order shouldBe "hits"
        viewModel.uiState.value.selectedCompany shouldBe company
        viewModel.uiState.value.selectedYear shouldBe "2024"
    }

    @Test
    fun hobbyFilterChange_reloadsOnlyHobbyWithHobbySnapshot() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel
        viewModel.submitAndLoadHobby()
        viewModel.onSortChange("hits")
        viewModel.onFilterChange(company, "2024")
        advanceUntilIdle()

        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()
        env.mock.takeAllRequests()

        val albumBefore = viewModel.tabState(SearchTab.ALBUM)
        val elementsBefore = viewModel.tabState(SearchTab.WORKS_ELEMENT)

        // 活动分类为相册时修改周边筛选：后台只重载周边，且请求取周边快照
        viewModel.uiState.test {
            skipItems(1)
            viewModel.onHobbyCategoryChange(100)
            val loading = awaitItem()
            loading.tabStates.getValue(SearchTab.ALBUM) shouldBe albumBefore
            loading.tabStates.getValue(SearchTab.WORKS_ELEMENT) shouldBe elementsBefore
        }
        advanceUntilIdle()

        viewModel.tabState(SearchTab.HOBBY).items.size shouldBe 2
        viewModel.tabState(SearchTab.ALBUM) shouldBe albumBefore
        viewModel.tabState(SearchTab.WORKS_ELEMENT) shouldBe elementsBefore

        val form = env.mock.takeAllRequests().lastSearchForm()
        form["category"] shouldBe "100"
        form["order"] shouldBe "hits"
        form["company"] shouldBe "42"
        form["releaseYear"] shouldBe "2024"
        form["attribute"] shouldBe null

        val hobbyBefore = viewModel.tabState(SearchTab.HOBBY)
        viewModel.onSortChange("addTime")
        advanceUntilIdle()
        env.mock.takeAllRequests()

        // 反向：修改相册筛选只重载相册，周边快照与列表不变
        viewModel.onAlbumCategoryChange(AlbumSubCategory.FIGURE)
        advanceUntilIdle()

        val albumForm = env.mock.takeAllRequests().lastSearchForm()
        albumForm["category"] shouldBe "60001"
        albumForm["order"] shouldBe "addTime"
        albumForm["company"] shouldBe null
        albumForm["releaseYear"] shouldBe null
        viewModel.tabState(SearchTab.HOBBY) shouldBe hobbyBefore
    }

    @Test
    fun requestParams_comeFromTargetTabSnapshot() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel
        viewModel.submitAndLoadHobby()
        advanceUntilIdle()

        viewModel.onSortChange("hits")
        viewModel.onHobbyCategoryChange(100)
        viewModel.onFilterChange(company, "2024")
        advanceUntilIdle()

        val hobbyForm = env.mock.takeAllRequests().lastSearchForm()
        hobbyForm["category"] shouldBe "100"
        hobbyForm["order"] shouldBe "hits"
        hobbyForm["company"] shouldBe "42"
        hobbyForm["releaseYear"] shouldBe "2024"
        hobbyForm["attribute"] shouldBe null

        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()
        viewModel.onSortChange("addTime")
        viewModel.onAlbumCategoryChange(AlbumSubCategory.FIGURE)
        advanceUntilIdle()

        val albumForm = env.mock.takeAllRequests().lastSearchForm()
        albumForm["category"] shouldBe "60001"
        albumForm["order"] shouldBe "addTime"
        albumForm["company"] shouldBe null
        albumForm["releaseYear"] shouldBe null
        albumForm["attribute"] shouldBe null

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
    fun isFilterActive_followsActiveTabSnapshot() = runTest(testDispatcher) {
        val viewModel = createEnv().viewModel
        viewModel.submitAndLoadHobby()
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

        // 相册筛选独立生效，不影响周边 Badge
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
    }

    @Test
    fun onResetFilters_onlyResetsAndReloadsActiveTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel
        viewModel.submitAndLoadHobby()
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
        // 候选列表保留，避免重置后筛选面板选项消失
        hobby.availableCompanies shouldBe listOf(company)
        hobby.availableYears.mapNotNull { it.keyAsString } shouldBe listOf("2024")

        viewModel.uiState.value.albumFilter shouldBe albumFilterBefore
        viewModel.tabState(SearchTab.ALBUM) shouldBe albumStateBefore
        env.mock.takeAllRequests().searchCategories() shouldBe listOf("10000")
    }

    @Test
    fun onRefreshAndLoadMore_onlyMutateTargetTab() = runTest(testDispatcher) {
        val env = createEnv()
        val viewModel = env.viewModel
        viewModel.submitAndLoadHobby()
        advanceUntilIdle()
        viewModel.onSelectTab(SearchTab.ALBUM)
        advanceUntilIdle()
        env.mock.takeAllRequests()

        val albumBefore = viewModel.tabState(SearchTab.ALBUM)
        viewModel.onRefresh(SearchTab.HOBBY)
        advanceUntilIdle()

        viewModel.tabState(SearchTab.ALBUM) shouldBe albumBefore
        env.mock.takeAllRequests().searchCategories() shouldBe listOf("10000")

        val hobbyBefore = viewModel.tabState(SearchTab.HOBBY)
        viewModel.onLoadMore(SearchTab.ALBUM)
        advanceUntilIdle()

        viewModel.tabState(SearchTab.HOBBY) shouldBe hobbyBefore
        viewModel.tabState(SearchTab.ALBUM).page shouldBe 2
        env.mock.takeAllRequests().searchCategories() shouldBe listOf("70000")
    }
}
