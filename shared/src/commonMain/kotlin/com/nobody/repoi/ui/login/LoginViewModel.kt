package com.nobody.repoi.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 登录方式：账号密码或登录令牌。 */
enum class LoginMode { ACCOUNT, TOKEN }

data class LoginUiState(
    val account: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loginMode: LoginMode = LoginMode.ACCOUNT,
    val token: String = "",
    val tokenVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onAccountChange(account: String) {
        _uiState.update { it.copy(account = account, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onModeChange(mode: LoginMode) {
        _uiState.update { it.copy(loginMode = mode, errorMessage = null) }
    }

    fun onTokenChange(token: String) {
        _uiState.update { it.copy(token = token, errorMessage = null) }
    }

    fun toggleTokenVisibility() {
        _uiState.update { it.copy(tokenVisible = !it.tokenVisible) }
    }

    fun clearTokenInput() {
        _uiState.update { it.copy(token = "", errorMessage = null) }
    }

    /**
     * Token 登录：空输入直接报错不发请求；失败保留输入供修改，成功回调通知跳转。
     */
    fun loginWithToken(onSuccess: () -> Unit = {}) {
        val candidate = _uiState.value.token.trim()
        if (candidate.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入登录令牌") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            userRepository.loginWithToken(candidate).onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true, errorMessage = null) }
                onSuccess()
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Token 登录失败") }
            }
        }
    }

    fun login(onSuccess: () -> Unit = {}) {
        val current = _uiState.value
        if (current.account.isBlank() || current.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入账号和密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = userRepository.login(
                account = current.account.trim(),
                password = current.password
            )
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true, errorMessage = null) }
                onSuccess()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "登录失败，请检查账号密码"
                    )
                }
            }
        }
    }
}
