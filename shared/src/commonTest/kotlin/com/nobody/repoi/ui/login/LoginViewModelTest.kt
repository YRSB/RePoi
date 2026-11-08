package com.nobody.repoi.ui.login

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.LoginData
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.remote.model.UserProfileSummary
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.session.FakeUserSession
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
 * 登录动作已收敛到 `UserSession`：本测试以 [FakeUserSession] 预置成功/失败结果，
 * 断言 ViewModel 的加载态、错误文案、输入保留与成功回调；网络语义见 `UserSessionTest`。
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

    private fun setup(): Pair<FakeUserSession, LoginViewModel> {
        val session = FakeUserSession("utoken-test")
        val repository = UserRepository(MockApi().api(), session)
        return session to LoginViewModel(repository)
    }

    @Test
    fun emptyFields_rejectLoginWithoutRequest() = runTest(testDispatcher) {
        val (session, viewModel) = setup()

        viewModel.login()
        advanceUntilIdle()

        assertEquals("请输入账号和密码", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(0, session.loginCalls)
    }

    @Test
    fun login_successTrimsAccountAndInvokesCallback() = runTest(testDispatcher) {
        val (session, viewModel) = setup()
        session.loginResult = Result.success(
            LoginData(utoken = "tok123", user = UserProfileSummary(userName = "user"))
        )
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
        assertEquals(1, session.loginCalls)
        assertEquals("user", session.lastLoginAccount)
        assertEquals("pass", session.lastLoginPassword)
    }

    @Test
    fun login_failureShowsMessageAndKeepsForm() = runTest(testDispatcher) {
        val (session, viewModel) = setup()
        session.loginResult = Result.failure(ApiException(400, "密码错误"))
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
        val (_, viewModel) = setup()
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
        val (_, viewModel) = setup()
        assertFalse(viewModel.uiState.value.passwordVisible)

        viewModel.togglePasswordVisibility()
        assertTrue(viewModel.uiState.value.passwordVisible)

        viewModel.togglePasswordVisibility()
        assertFalse(viewModel.uiState.value.passwordVisible)
    }

    @Test
    fun tokenLogin_blankToken_showsErrorWithoutRequest() = runTest(testDispatcher) {
        val (session, viewModel) = setup()
        viewModel.onTokenChange("  \n ")

        viewModel.loginWithToken {}
        advanceUntilIdle()

        assertEquals("请输入登录令牌", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(0, session.tokenLoginCalls)
    }

    @Test
    fun tokenLogin_success_invokesCallbackWithTrimmedCandidate() = runTest(testDispatcher) {
        val (session, viewModel) = setup()
        session.tokenLoginResult = Result.success(
            UserProfile(id = 88888L, nickname = "资深胶佬", userName = "collector888")
        )
        var successCalled = false
        viewModel.onTokenChange(" candidate-token ")

        viewModel.loginWithToken { successCalled = true }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSuccess)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue(successCalled)
        assertEquals(1, session.tokenLoginCalls)
        assertEquals("candidate-token", session.lastTokenCandidate)
    }

    @Test
    fun tokenLogin_invalidToken4001_showsFriendlyErrorAndRetainsInput() = runTest(testDispatcher) {
        val (session, viewModel) = setup()
        session.tokenLoginResult = Result.failure(ApiException(4001, "登录令牌已失效，请重新获取"))
        var successCalled = false
        viewModel.onTokenChange("expired-token")

        viewModel.loginWithToken { successCalled = true }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertFalse(state.isLoading)
        assertEquals("登录令牌已失效，请重新获取", state.errorMessage)
        assertEquals("expired-token", state.token)
        assertFalse(successCalled)
    }

    @Test
    fun tokenLogin_serverError_showsServerMessageAndRetainsInput() = runTest(testDispatcher) {
        val (session, viewModel) = setup()
        session.tokenLoginResult = Result.failure(ApiException(503, "服务暂不可用"))
        var successCalled = false
        viewModel.onTokenChange("candidate-token")

        viewModel.loginWithToken { successCalled = true }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSuccess)
        assertFalse(state.isLoading)
        assertEquals("服务暂不可用", state.errorMessage)
        assertEquals("candidate-token", state.token)
        assertFalse(successCalled)
    }

    @Test
    fun modeSwitch_preservesEnteredValues() = runTest(testDispatcher) {
        val (_, viewModel) = setup()
        viewModel.onAccountChange("user")
        viewModel.onTokenChange("candidate-token")

        viewModel.onModeChange(LoginMode.TOKEN)
        assertEquals("user", viewModel.uiState.value.account)
        assertEquals("candidate-token", viewModel.uiState.value.token)

        viewModel.onModeChange(LoginMode.ACCOUNT)
        assertEquals("user", viewModel.uiState.value.account)
        assertEquals("candidate-token", viewModel.uiState.value.token)
    }
}
