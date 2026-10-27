package com.nobody.repoi.ui.item.list

import com.nobody.repoi.data.local.CategoryApi
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.test.recordedBody
import io.ktor.client.HttpClient
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * HobbyListViewModel 分类逻辑的 KMP 移植版（真实仓储 + MockEngine，替代 Mockito）。
 *
 * 请求参数断言改为解析 MockEngine 捕获的表单体。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyListViewModelCategoryTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var stores: TestDataStores
    private lateinit var repository: ItemRepository
    private lateinit var categoryManager: CategoryManager
    private lateinit var preferences: ViewPreferencesManager

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        repository = ItemRepository(api)
        categoryManager = CategoryManager(CategoryApi {
            throw UnsupportedOperationException("no network in test")
        })
        preferences = ViewPreferencesManager(stores.createDataStore("view_prefs"))
        // 默认空列表响应（PagedListLoader 每次 loadInitial 消费 1 个）
        mock.respondWhen(
            Regex.fromLiteral("api/hobby/query-v2"),
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
        dispatchers.tearDown()
    }

    private fun viewModel(): HobbyListViewModel {
        return HobbyListViewModel(repository, categoryManager, preferences)
    }

    private fun parseForm(body: String): Map<String, String> {
        return body.split("&")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].decodeURLPart() to
                    (parts.getOrElse(1) { "" }).decodeURLPart()
            }
    }

    private fun lastForm(): Map<String, String> {
        return parseForm(mock.lastRequest().recordedBody())
    }

    @Test
    fun subCategoryLink_splitsIntoPrimaryAndSub() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(HobbyListNavParam(title = "比例人形", categoryId = 1))
        advanceUntilIdle()
        assertEquals(100, vm.uiState.value.categoryId)
        assertEquals(1, vm.uiState.value.subCategoryId)
        vm.close()
    }

    @Test
    fun primaryLink_keepsNoSub() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(HobbyListNavParam(title = "手办", categoryId = 100))
        advanceUntilIdle()
        assertEquals(100, vm.uiState.value.categoryId)
        assertNull(vm.uiState.value.subCategoryId)
        vm.close()
    }

    @Test
    fun unknownCategory_passesThroughWithoutSub() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(HobbyListNavParam(categoryId = 9999))
        advanceUntilIdle()
        assertEquals(9999, vm.uiState.value.categoryId)
        assertNull(vm.uiState.value.subCategoryId)
        vm.close()
    }

    @Test
    fun setSubCategory_queriesWithSubId() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(HobbyListNavParam(categoryId = 100))
        advanceUntilIdle()
        vm.setSubCategory(1)
        assertEquals(1, vm.uiState.value.subCategoryId)
        advanceUntilIdle()
        assertEquals("1", lastForm()["category"])
        vm.close()
    }

    @Test
    fun setCategory_clearsSub() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(HobbyListNavParam(categoryId = 1))
        advanceUntilIdle()
        vm.setCategory(100)
        advanceUntilIdle()
        assertNull(vm.uiState.value.subCategoryId)
        vm.close()
    }

    @Test
    fun applyAdvancedFilters_sendsR18AndMultiSpecs() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(HobbyListNavParam(categoryId = 100))
        advanceUntilIdle()
        vm.applyAdvancedFilters(0, "649231,649234", "6.0", 320)
        advanceUntilIdle()
        assertEquals(320, vm.uiState.value.r18)
        val last = lastForm()
        assertEquals("320", last["r18"])
        assertEquals("649231,649234", last["specs"])
        assertEquals("24", last["pageSize"])
        vm.close()
    }

    @Test
    fun resetAdvancedFilters_clearsR18WorkersTag() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(HobbyListNavParam(categoryId = 100, r18 = 320, workers = "1", tag = "联动"))
        advanceUntilIdle()
        vm.resetAdvancedFilters()
        advanceUntilIdle()
        assertNull(vm.uiState.value.r18)
        assertNull(vm.uiState.value.workers)
        assertNull(vm.uiState.value.tag)
        vm.close()
    }

    @Test
    fun resetAllFilters_clearsAllFiltersAndResetsDefaults() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(
            HobbyListNavParam(
                categoryId = 100,
                releaseYear = 2024,
                releaseMonth = 5,
                keyword = "初音",
                sex = 0,
                specs = "649223",
                scale = "7.0",
                r18 = 300
            )
        )
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.hasAdvancedFilters)
        assertEquals("初音", vm.uiState.value.keyword)

        vm.resetAllFilters()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.hasAdvancedFilters)
        assertEquals("", vm.uiState.value.keyword)
        assertNull(vm.uiState.value.releaseYear)
        assertNull(vm.uiState.value.releaseMonth)
        assertNull(vm.uiState.value.sex)
        assertNull(vm.uiState.value.specs)
        assertNull(vm.uiState.value.scale)
        assertNull(vm.uiState.value.r18)
        assertEquals(10000, vm.uiState.value.categoryId)
        assertNull(vm.uiState.value.subCategoryId)
        vm.close()
    }

    @Test
    fun selectCategory_updatesPrimaryAndSubAtomically() = runTest(dispatchers.testDispatcher) {
        val vm = viewModel()
        vm.initParams(HobbyListNavParam(categoryId = 10000))
        advanceUntilIdle()

        // 选中手办 -> 比例人形 (1)
        vm.selectCategory(100, 1)
        advanceUntilIdle()
        assertEquals(100, vm.uiState.value.categoryId)
        assertEquals(1, vm.uiState.value.subCategoryId)

        // 选中手办 -> 全部手办 (null)
        vm.selectCategory(100, null)
        advanceUntilIdle()
        assertEquals(100, vm.uiState.value.categoryId)
        assertNull(vm.uiState.value.subCategoryId)
        vm.close()
    }
}
