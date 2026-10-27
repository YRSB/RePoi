package com.nobody.repoi.ui.item.home

import com.nobody.repoi.data.local.CategoryApi
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.test.recordedBody
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ItemHomeViewModel 的 KMP 移植版（真实仓储 + MockEngine，替代 Mockito）。
 *
 * `getItemHomePreview` 内部并发发 2 个请求（预订/出荷），用 dynamicResponder
 * 按表单是否有 releaseEnd 区分返回。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemHomeViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: ItemRepository
    private lateinit var categoryManager: CategoryManager

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = ItemRepository(api)
        categoryManager = CategoryManager(CategoryApi {
            throw UnsupportedOperationException("no network in test")
        })
    }

    @AfterTest
    fun tearDown() {
        client.close()
        dispatchers.tearDown()
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

    private fun hobbyJson(id: Long, name: String = "商品$id"): String {
        return """{"id":$id,"itemId":${1000L + id},"nameCN":"$name"}"""
    }

    private fun stubPreview(
        preOrderIds: List<Long> = listOf(1L),
        releaseIds: List<Long> = listOf(2L)
    ) {
        mock.dynamicResponder = { request ->
            val form = parseForm(request.recordedBody())
            val ids = if (form.containsKey("releaseEnd")) releaseIds else preOrderIds
            val items = ids.joinToString(",") { hobbyJson(it) }
            """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}""" to HttpStatusCode.OK
        }
    }

    private fun stubPreviewFailure(message: String = "网络异常") {
        mock.dynamicResponder = {
            """{"success":false,"code":500,"msg":"$message","data":null}""" to HttpStatusCode.OK
        }
    }

    private fun createViewModel(): ItemHomeViewModel {
        return ItemHomeViewModel(repository, categoryManager)
    }

    @Test
    fun initialLoad_populatesCategoriesAndBothSections() = runTest(dispatchers.testDispatcher) {
        stubPreview()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.categories.isNotEmpty())
        assertEquals(10000, state.selectedCategoryId)
        assertEquals(listOf(1L), state.preOrderItems.map { it.id })
        assertEquals(listOf(2L), state.releaseItems.map { it.id })
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.errorMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun initialLoad_failureSetsError() = runTest(dispatchers.testDispatcher) {
        stubPreviewFailure()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.errorMessage?.contains("网络异常") == true)
        assertTrue(state.preOrderItems.isEmpty())
        assertTrue(state.releaseItems.isEmpty())
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun selectCategory_reloadsWithNewCategory() = runTest(dispatchers.testDispatcher) {
        stubPreview()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.saveScrollPosition(5, 50)
        stubPreview(preOrderIds = listOf(3L), releaseIds = listOf(4L))
        viewModel.selectCategory(100)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(100, state.selectedCategoryId)
        assertEquals(listOf(3L), state.preOrderItems.map { it.id })
        assertEquals(listOf(4L), state.releaseItems.map { it.id })
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun selectSameCategory_doesNotReload() = runTest(dispatchers.testDispatcher) {
        stubPreview()

        val viewModel = createViewModel()
        advanceUntilIdle()
        val countBefore = mock.requestCount
        viewModel.selectCategory(10000)
        advanceUntilIdle()

        assertEquals(countBefore, mock.requestCount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun refresh_keepsItemsWhileReloadingAndUpdatesThem() = runTest(dispatchers.testDispatcher) {
        stubPreview()

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.saveScrollPosition(3, 30)
        stubPreview(preOrderIds = listOf(5L), releaseIds = listOf(6L))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(5L), state.preOrderItems.map { it.id })
        assertEquals(listOf(6L), state.releaseItems.map { it.id })
        assertFalse(state.isRefreshing)
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun refresh_failureKeepsPreviousItems() = runTest(dispatchers.testDispatcher) {
        stubPreview()

        val viewModel = createViewModel()
        advanceUntilIdle()
        stubPreviewFailure("刷新失败")
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.preOrderItems.map { it.id })
        assertEquals(listOf(2L), state.releaseItems.map { it.id })
        assertTrue(state.errorMessage?.contains("刷新失败") == true)
        assertFalse(state.isRefreshing)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun retry_reloadsCurrentCategory() = runTest(dispatchers.testDispatcher) {
        stubPreviewFailure()

        val viewModel = createViewModel()
        advanceUntilIdle()
        stubPreview()
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.preOrderItems.map { it.id })
        assertNull(state.errorMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun savingScrollPosition_isIndependentFromReload() = runTest(dispatchers.testDispatcher) {
        stubPreview()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveScrollPosition(7, 70)
        assertEquals(7, viewModel.savedScrollIndex)
        assertEquals(70, viewModel.savedScrollOffset)

        viewModel.resetScrollPosition()
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
        viewModel.viewModelScope.cancel()
    }
}
