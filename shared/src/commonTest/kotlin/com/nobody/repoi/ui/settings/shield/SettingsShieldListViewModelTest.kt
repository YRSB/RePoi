package com.nobody.repoi.ui.settings.shield

import com.nobody.repoi.data.remote.model.MaskItem
import com.nobody.repoi.data.repository.FakeTokenManager
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsShieldListViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun repositoryOf(mock: MockApi, token: String = "utoken-test"): SettingsRepository =
        SettingsRepository(mock.api(testDispatcher), FakeTokenManager(token))

    private fun listBody(items: List<Pair<Long, String>>): String {
        val list = items.joinToString(",") { (id, name) ->
            """{"id":$id,"itemType":"hobby","name":"$name"}"""
        }
        return """{"success":true,"code":200,"msg":"ok","data":{"blockList":[$list]}}"""
    }

    private fun viewModelOf(mock: MockApi, token: String = "utoken-test"): SettingsShieldListViewModel {
        mock.routeWhen(
            predicate = { path, form, _ -> path.contains("api/mask/list") },
            body = { _, form, _ ->
                when (form["itemType"]) {
                    "user" -> listBody(listOf(1L to "用户1", 2L to "用户2"))
                    "company" -> listBody(listOf(3L to "厂商3"))
                    else -> listBody(listOf(7L to "条目7"))
                }
            }
        )
        return SettingsShieldListViewModel(repositoryOf(mock, token))
    }

    @Test
    fun initialLoad_populatesUserTab() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MockApi())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertEquals(ShieldListTab.User, state.tab)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
    }

    @Test
    fun unauthenticated_skipsNetworkAndDefaultsEmpty() = runTest(testDispatcher) {
        val mock = MockApi()
        val viewModel = viewModelOf(mock, token = "")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertTrue(state.items.isEmpty())
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun tokenChanges_triggersReloadAndReset() = runTest(testDispatcher) {
        val mock = MockApi()
        val tokenManager = FakeTokenManager("")
        val repo = SettingsRepository(mock.api(testDispatcher), tokenManager)
        mock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/mask/list") },
            body = { _, _, _ -> listBody(listOf(1L to "用户1")) }
        )
        val viewModel = SettingsShieldListViewModel(repo)
        advanceUntilIdle()
        assertEquals(0, mock.requestCount)

        tokenManager.currentToken = "new-token"
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.items.size)

        tokenManager.clearToken()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun switchTab_loadsDifferentCategory() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MockApi())
        advanceUntilIdle()

        viewModel.switchTab(ShieldListTab.Vendor)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ShieldListTab.Vendor, state.tab)
        assertEquals(listOf(3L), state.items.map { it.id })
    }

    @Test
    fun switchTab_sameTabWithItems_doesNotReload() = runTest(testDispatcher) {
        val mock = MockApi()
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.switchTab(ShieldListTab.User)
        advanceUntilIdle()

        assertEquals(0, mock.requestCount)
    }

    @Test
    fun retry_reloadsCurrentTab() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/list", mock.failure(500, "加载异常"))
        val viewModel = SettingsShieldListViewModel(repositoryOf(mock))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loadFailed)

        mock.replaceRoute("api/mask/list", listBody(listOf(8L to "条目8")))
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loadFailed)
        assertEquals(listOf(8L), state.items.map { it.id })
    }

    @Test
    fun executeUnblock_optimisticRemovalFromCurrentTab() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/del", mock.ok("{}"))
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        val target = MaskItem(id = 1L, itemType = "user", name = "用户1")
        viewModel.confirmUnblock(target)
        assertEquals(target, viewModel.uiState.value.unblockTarget)

        viewModel.executeUnblock()
        advanceUntilIdle()

        val deleteRequest = mock.takeRequest()
        assertEquals("POST", deleteRequest.method)
        assertEquals("/api/mask/del", deleteRequest.encodedPath)
        assertEquals("1", deleteRequest.formEntries()["itemNodeId"])

        val state = viewModel.uiState.value
        assertNull(state.unblockTarget)
        assertFalse(state.isUnblocking)
        assertEquals(listOf(2L), state.items.map { it.id })
    }

    @Test
    fun executeUnblock_failureSurfacesErrorMessageAndRetainsItem() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/del", mock.failure(500, "网络异常"))
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.confirmUnblock(MaskItem(id = 1L, itemType = "user", name = "用户1"))
        viewModel.executeUnblock()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUnblocking)
        assertEquals("网络异常", state.errorMessage)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
    }

    @Test
    fun blockItem_successReloadsCurrentTab() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/add", mock.ok("{}"))
        var returnThree = false
        mock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/mask/list") },
            body = { _, _, _ ->
                if (returnThree) {
                    listBody(listOf(1L to "用户1", 2L to "用户2", 99L to "用户99"))
                } else {
                    listBody(listOf(1L to "用户1", 2L to "用户2"))
                }
            }
        )
        val viewModel = SettingsShieldListViewModel(repositoryOf(mock))
        advanceUntilIdle()
        mock.takeRequest()

        returnThree = true
        viewModel.blockItem(99L)
        advanceUntilIdle()

        val addRequest = mock.takeRequest()
        assertEquals("/api/mask/add", addRequest.encodedPath)
        assertEquals("99", addRequest.formEntries()["itemNodeId"])
        assertEquals(listOf(1L, 2L, 99L), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun blockItem_failureSurfacesError() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/add", mock.failure(500, "屏蔽失败"))
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.blockItem(88L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isBlocking)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertEquals("屏蔽失败", state.errorMessage)
    }

    @Test
    fun dialogsAndMessages_canBeDismissed() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/del", mock.failure(500, "网络异常"))
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.confirmUnblock(MaskItem(id = 1L, itemType = "hobby", name = "用户1"))
        viewModel.dismissUnblockDialog()
        assertNull(viewModel.uiState.value.unblockTarget)

        viewModel.confirmUnblock(MaskItem(id = 1L, itemType = "hobby", name = "用户1"))
        viewModel.executeUnblock()
        advanceUntilIdle()
        viewModel.clearErrorMessage()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
