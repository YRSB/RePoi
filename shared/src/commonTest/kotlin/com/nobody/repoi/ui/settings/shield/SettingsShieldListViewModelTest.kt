package com.nobody.repoi.ui.settings.shield

import com.nobody.repoi.data.remote.model.MaskItem
import com.nobody.repoi.data.repository.FakeTokenManager
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.SettingsRepository
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

    private fun repositoryOf(mock: MockApi): SettingsRepository =
        SettingsRepository(mock.api(testDispatcher), FakeTokenManager("utoken-test"))

    private fun listBody(items: List<Pair<Long, String>>): String {
        val list = items.joinToString(",") { (id, name) ->
            """{"id":$id,"itemType":"hobby","name":"$name"}"""
        }
        return """{"success":true,"code":200,"msg":"ok","data":{"blockList":[$list]}}"""
    }

    private fun viewModelOf(mock: MockApi): SettingsShieldListViewModel {
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
        return SettingsShieldListViewModel(repositoryOf(mock))
    }

    @Test
    fun initialLoad_populatesUserTab() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MockApi())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun switchTab_requestsMappedItemType() = runTest(testDispatcher) {
        val mock = MockApi()
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.switchTab(ShieldListTab.Vendor)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ShieldListTab.Vendor, state.tab)
        assertEquals(listOf(3L), state.items.map { it.id })
        val recorded = mock.takeRequest()
        assertEquals("company", recorded.formEntries()["itemType"])
    }

    @Test
    fun switchTab_hobbyTabListsEntries() = runTest(testDispatcher) {
        val viewModel = viewModelOf(MockApi())
        advanceUntilIdle()

        viewModel.switchTab(ShieldListTab.Entry)
        advanceUntilIdle()

        assertEquals(listOf(7L), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun unblock_removesItemAndPostsDelete() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/del", mock.ok("{}"))
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.confirmUnblock(MaskItem(id = 1L, itemType = "hobby", name = "用户1"))
        viewModel.executeUnblock()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(2L), state.items.map { it.id })
        assertNull(state.unblockTarget)
        val recorded = mock.takeRequest()
        assertEquals("/api/mask/del", recorded.encodedPath)
        assertEquals("1", recorded.formEntries()["itemNodeId"])
    }

    @Test
    fun unblock_failureKeepsListAndSetsError() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/del", mock.failure(500, "解除屏蔽失败"))
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.confirmUnblock(MaskItem(id = 1L, itemType = "hobby", name = "用户1"))
        viewModel.executeUnblock()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertEquals("解除屏蔽失败", state.errorMessage)
    }

    @Test
    fun block_postsAddAndReloadsCurrentTab() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/add", mock.ok("{}"))
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.blockItem(9L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isBlocking)
        assertNull(state.errorMessage)
        val recorded = mock.takeRequest()
        assertEquals("/api/mask/add", recorded.encodedPath)
        assertEquals("9", recorded.formEntries()["itemNodeId"])
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
    }

    @Test
    fun block_failureKeepsListAndSetsError() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/mask/add", mock.failure(500, "屏蔽失败"))
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.blockItem(9L)
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
