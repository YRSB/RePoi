package com.nobody.repoi.ui.settings

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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsAccountViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun repository(mock: MockApi): SettingsRepository =
        SettingsRepository(mock.api(), FakeTokenManager("utoken-test"))

    @Test
    fun initialState_loadsBindingStatus() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue(mock.ok("""{"result":{"verify":true,"password":true,"phone":true,"email":false,"wechat":true}}"""))
        mock.enqueue(mock.ok("""{"phone":"138****8000","email":""}"""))
        val viewModel = SettingsAccountViewModel(repository(mock))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertTrue(state.passwordBound)
        assertTrue(state.phoneBound)
        assertFalse(state.emailBound)
        assertTrue(state.wechatBound)
        assertEquals("138****8000", state.maskedPhone)
        val bindings = state.bindings()
        assertEquals(4, bindings.size)
        assertEquals("已设置", bindings[0].statusText)
        assertEquals("138****8000", bindings[1].statusText)
        assertEquals("未绑定", bindings[2].statusText)
        assertEquals("已绑定", bindings[3].statusText)
    }

    @Test
    fun initialState_whenCheckFails_reportsLoadFailed() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue(mock.failure(403, "无权访问"))
        val viewModel = SettingsAccountViewModel(repository(mock))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
    }

    @Test
    fun initialState_whenPrivacyFails_keepsBindingsWithoutMask() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue(mock.ok("""{"result":{"verify":false,"password":false,"phone":false,"email":false,"wechat":false}}"""))
        mock.enqueue(mock.failure(500, "服务端异常"))
        val viewModel = SettingsAccountViewModel(repository(mock))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertFalse(state.passwordBound)
        assertEquals("", state.maskedPhone)
        val bindings = state.bindings()
        assertEquals("未设置", bindings[0].statusText)
        assertEquals("未绑定", bindings[1].statusText)
    }

    @Test
    fun refresh_reloadsBindingStatus() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue(mock.ok("""{"result":{"verify":false,"password":false,"phone":false,"email":false,"wechat":false}}"""))
        mock.enqueue(mock.ok("""{"phone":"","email":""}"""))
        val viewModel = SettingsAccountViewModel(repository(mock))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.phoneBound)

        mock.enqueue(mock.ok("""{"result":{"verify":false,"password":true,"phone":true,"email":false,"wechat":false}}"""))
        mock.enqueue(mock.ok("""{"phone":"139****9000","email":""}"""))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.passwordBound)
        assertTrue(state.phoneBound)
        assertEquals("139****9000", state.maskedPhone)
    }
}
