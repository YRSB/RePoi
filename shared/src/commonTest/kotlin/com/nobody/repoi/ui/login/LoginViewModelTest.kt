package com.nobody.repoi.ui.login

import com.nobody.repoi.data.repository.FakeTokenManager
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * 登录 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `LoginViewModelTest`（逐用例；Mockito `mock(UserRepository)` →
 * 真实 UserRepository 背后 [MockApi] MockEngine（`verify never/login` →
 * `mock.requestCount` 断言）；`MainDispatcherRule` → `Dispatchers.setMain`；
 * JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun setup(): Triple<MockApi, FakeTokenManager, LoginViewModel> {
        val mock = MockApi()
        val tokenManager = FakeTokenManager("utoken-test")
        val repository = UserRepository(mock.api(), tokenManager)
        return Triple(mock, tokenManager, LoginViewModel(repository))
    }

    @Test
    fun emptyFields_rejectLoginWithoutRequest() = runTest(testDispatcher) {
        val (mock, _, viewModel) = setup()

        viewModel.login()
        advanceUntilIdle()

        assertEquals("请输入账号和密码", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun login_successTrimsAccountAndInvokesCallback() = runTest(testDispatcher) {
        val (mock, _, viewModel) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"utoken":"tok123","user":{"userName":"user"}}}""")
        var successCalled = false
        viewModel.onAccountChange("  user  ")
        viewModel.onPasswordChange("pass")

        viewModel.login { successCalled = true }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue(successCalled)
        assertEquals(1, mock.requestCount)
        val form = mock.takeRequest().formEntries()
        assertEquals("user", form["account"])
        assertEquals("pass", form["password"])
    }

    @Test
    fun login_failureShowsMessageAndKeepsForm() = runTest(testDispatcher) {
        val (mock, _, viewModel) = setup()
        mock.enqueue(mock.failure(400, "密码错误"))
        var successCalled = false
        viewModel.onAccountChange("user")
        viewModel.onPasswordChange("bad")

        viewModel.login { successCalled = true }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertFalse(state.isLoading)
        assertEquals("密码错误", state.errorMessage)
        assertEquals("user", state.account)
        assertFalse(successCalled)
    }

    @Test
    fun editingFields_clearsPreviousError() = runTest(testDispatcher) {
        val (_, _, viewModel) = setup()
        viewModel.login()
        assertEquals("请输入账号和密码", viewModel.uiState.value.errorMessage)

        viewModel.onAccountChange("user")
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.login()
        assertEquals("请输入账号和密码", viewModel.uiState.value.errorMessage)
        viewModel.onPasswordChange("pass")
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun togglePasswordVisibility_flipsFlag() = runTest(testDispatcher) {
        val (_, _, viewModel) = setup()
        assertFalse(viewModel.uiState.value.passwordVisible)

        viewModel.togglePasswordVisibility()
        assertTrue(viewModel.uiState.value.passwordVisible)

        viewModel.togglePasswordVisibility()
        assertFalse(viewModel.uiState.value.passwordVisible)
    }
}
