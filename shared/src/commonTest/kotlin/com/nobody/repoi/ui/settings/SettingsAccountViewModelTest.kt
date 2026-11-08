package com.nobody.repoi.ui.settings

import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
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

    private fun repository(mock: MockApi, token: String = "utoken-test"): SettingsRepository =
        SettingsRepository(mock.api(testDispatcher), FakeUserSession(token))

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
    fun unauthenticated_skipsNetworkAndDefaultsEmpty() = runTest(testDispatcher) {
        val mock = MockApi()
        val viewModel = SettingsAccountViewModel(repository(mock, token = ""))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertFalse(state.passwordBound)
        assertFalse(state.phoneBound)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun tokenChanges_triggersRefreshAndReset() = runTest(testDispatcher) {
        val mock = MockApi()
        val session = FakeUserSession()
        val repo = SettingsRepository(mock.api(testDispatcher), session)
        val viewModel = SettingsAccountViewModel(repo)
        advanceUntilIdle()
        assertEquals(0, mock.requestCount)

        mock.enqueue(mock.ok("""{"result":{"verify":true,"password":true,"phone":true,"email":false,"wechat":true}}"""))
        mock.enqueue(mock.ok("""{"phone":"138****8000","email":""}"""))
        session.setState(SessionState.Authenticated(userId = 0L, token = "new-token"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.passwordBound)
        assertEquals(2, mock.requestCount)

        session.setState(SessionState.Unauthenticated)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.passwordBound)
        assertFalse(viewModel.uiState.value.isLoading)
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
        assertEquals(1, mock.requestCount)
    }

    @Test
    fun refresh_whenPrivacyFailsAfterSuccess_retainsMaskedContacts() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue(mock.ok("""{"result":{"verify":true,"password":true,"phone":true,"email":true,"wechat":false}}"""))
        mock.enqueue(mock.ok("""{"phone":"138****8000","email":"a****@x.com"}"""))
        val viewModel = SettingsAccountViewModel(repository(mock))
        advanceUntilIdle()
        assertEquals("138****8000", viewModel.uiState.value.maskedPhone)

        mock.enqueue(mock.ok("""{"result":{"verify":true,"password":true,"phone":true,"email":true,"wechat":false}}"""))
        mock.enqueue(mock.failure(500, "服务端异常"))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loadFailed)
        assertEquals("138****8000", state.maskedPhone)
        assertEquals("a****@x.com", state.maskedEmail)
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

    @Test
    fun exportDialog_openDismissToggleVisibility() = runTest(testDispatcher) {
        val mock = MockApi()
        val viewModel = SettingsAccountViewModel(repository(mock))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showExportDialog)
        viewModel.openExportDialog()
        assertTrue(viewModel.uiState.value.showExportDialog)
        assertFalse(viewModel.uiState.value.exportTokenRevealed)

        viewModel.toggleExportTokenVisibility()
        assertTrue(viewModel.uiState.value.exportTokenRevealed)

        viewModel.dismissExportDialog()
        assertFalse(viewModel.uiState.value.showExportDialog)
        assertFalse(viewModel.uiState.value.exportTokenRevealed)
    }

    @Test
    fun activeToken_exposesCurrentSessionToken() = runTest(testDispatcher) {
        val mock = MockApi()
        val viewModel = SettingsAccountViewModel(repository(mock, token = "utoken-export"))
        advanceUntilIdle()

        assertEquals("utoken-export", viewModel.activeToken)
    }

    @Test
    fun maskToken_masksMiddleKeepsEnds() {
        assertEquals("c4ca••••••••8f9e", com.nobody.repoi.ui.settings.panes.maskToken("c4ca123456788f9e"))
        assertEquals("••••••••", com.nobody.repoi.ui.settings.panes.maskToken("short"))
        assertEquals("暂无可用令牌，请重新登录", com.nobody.repoi.ui.settings.panes.maskToken(""))
    }
}
