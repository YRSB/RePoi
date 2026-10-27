package com.nobody.repoi.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nobody.repoi.data.local.CategoryApi
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.SearchRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.ui.common.comment.FakePlatformActions
import com.nobody.repoi.ui.common.comment.startTestKoin
import com.nobody.repoi.ui.common.comment.stopTestKoin
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SearchScreen 的 KMP 移植版（jvmTest + 真 ViewModel + MockEngine，替代 Mockito + Robolectric）。
 *
 * `verify(vm)` 翻译为请求计数/状态收敛断言；Screen 内部 LaunchedEffect 会自行调 initParams。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var mock: MockRepoEngine
    private lateinit var stores: TestDataStores
    private lateinit var searchRepository: SearchRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        startTestKoin(FakePlatformActions())
        mock = MockRepoEngine()
        stores = TestDataStores()
    }

    @After
    fun tearDown() {
        stopTestKoin()
        stores.cleanUp()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SearchViewModel {
        val json = appJson()
        val client = HttpClient(mock.engine) { expectSuccess = true }
        val api = HpoiApi(client, json)
        searchRepository = SearchRepository(
            api,
            SearchHistoryManager(stores.createDataStore("search_history"), json)
        )
        val categoryManager = CategoryManager(CategoryApi {
            throw UnsupportedOperationException("no network in test")
        })
        return SearchViewModel(searchRepository, categoryManager)
    }

    private fun stubAutoFillEmpty() {
        mock.respondWhen(
            Regex.fromLiteral("api/search/auto/fill"),
            """{"success":true,"code":200,"msg":"ok","data":{"keywords":[]}}"""
        )
    }

    private fun setScreen(vm: SearchViewModel, keyword: String) {
        composeRule.setContent {
            SearchScreen(
                navParam = SearchNavParam(initialKeyword = keyword),
                onBack = {},
                onNavigateToDetail = {},
                onNavigateToAlbum = {},
                viewModel = vm
            )
        }
    }

    @Test
    fun errorState_showsMessageAndRetries() {
        mock.enqueue("""{"success":false,"code":500,"msg":"搜索超时","data":null}""")
        mock.enqueue("""{"success":false,"code":500,"msg":"搜索超时","data":null}""")
        stubAutoFillEmpty()
        val vm = createViewModel()
        setScreen(vm, "手办")
        runBlocking { vm.uiState.first { it.errorMessage != null } }

        composeRule.onNodeWithText("搜索超时").assertIsDisplayed()
        val countBefore = mock.requestCount
        composeRule.onNodeWithText("重试").performClick()
        runBlocking { vm.uiState.first { !it.isRefreshing } }

        assertTrue(mock.requestCount > countBefore)
    }

    @Test
    fun emptyState_showsNoResultPlaceholder() {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"result":[],"query":{"totalCount":0,"pageCount":0}}}""")
        stubAutoFillEmpty()
        val vm = createViewModel()
        setScreen(vm, "手办")
        runBlocking { vm.uiState.first { it.isParamsReady && !it.isLoading } }

        composeRule.onNodeWithText("未找到相关搜索结果").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }

    @Test
    fun inputMode_rendersHistoryAndDispatchesSearch() {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"result":[],"query":{"totalCount":0,"pageCount":0}}}""")
        stubAutoFillEmpty()
        val vm = createViewModel()
        runBlocking {
            searchRepository.addSearchHistory("初音未来")
            vm.uiState.first { it.searchHistory.contains("初音未来") }
        }
        setScreen(vm, "")

        composeRule.onNodeWithText("初音未来").assertIsDisplayed()
        val countBefore = mock.requestCount
        composeRule.onNodeWithText("初音未来").performClick()
        runBlocking { vm.uiState.first { !it.isLoading } }

        assertTrue(mock.requestCount > countBefore)
        assertEquals("初音未来", vm.uiState.value.keyword)
    }

    @Test
    fun inputMode_forwardsTypedKeyword() {
        stubAutoFillEmpty()
        mock.respondWhen(
            Regex.fromLiteral("api/search"),
            """{"success":true,"code":200,"msg":"ok","data":{"result":[],"query":{"totalCount":0,"pageCount":0}}}"""
        )
        val vm = createViewModel()
        setScreen(vm, "")

        composeRule.onNode(hasSetTextAction()).performTextInput("新手办")
        runBlocking { vm.uiState.first { it.keyword == "新手办" } }

        assertEquals("新手办", vm.uiState.value.keyword)
    }

    @Test
    fun loadingState_keepsResultPlaceholdersHidden() {
        // 冻结 Main：initParams 同步置 isLoading=true 后网络协程挂起，停留在加载态。
        Dispatchers.setMain(StandardTestDispatcher())
        stubAutoFillEmpty()
        val vm = createViewModel()
        setScreen(vm, "手办")

        composeRule.onNodeWithText("未找到相关搜索结果").assertDoesNotExist()
        composeRule.onNodeWithText("重试").assertDoesNotExist()
    }
}
