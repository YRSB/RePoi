package com.nobody.repoi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.AccountCheckResult
import com.nobody.repoi.data.remote.model.UserPrivacyPayload
import com.nobody.repoi.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsAccountSecurityUiState(
    val isLoading: Boolean = false,
    val passwordBound: Boolean = false,
    val phoneBound: Boolean = false,
    val emailBound: Boolean = false,
    val wechatBound: Boolean = false,
    val maskedPhone: String = "",
    val maskedEmail: String = "",
    val loadFailed: Boolean = false,
    val showExportDialog: Boolean = false,
    val exportTokenRevealed: Boolean = false
)

data class SettingsAccountBinding(
    val title: String,
    val statusText: String,
    val isBound: Boolean
)

fun SettingsAccountSecurityUiState.bindings(): List<SettingsAccountBinding> = listOf(
    SettingsAccountBinding("登录密码", if (passwordBound) "已设置" else "未设置", passwordBound),
    SettingsAccountBinding("手机", if (phoneBound && maskedPhone.isNotBlank()) maskedPhone else if (phoneBound) "已绑定" else "未绑定", phoneBound),
    SettingsAccountBinding("邮箱", if (emailBound && maskedEmail.isNotBlank()) maskedEmail else if (emailBound) "已绑定" else "未绑定", emailBound),
    SettingsAccountBinding("微信", if (wechatBound) "已绑定" else "未绑定", wechatBound)
)

class SettingsAccountViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsAccountSecurityUiState(isLoading = settingsRepository.isLoggedIn)
    )
    val uiState: StateFlow<SettingsAccountSecurityUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.tokenFlow.collect { token ->
                if (token.isNotBlank()) {
                    refresh()
                } else {
                    _uiState.value = SettingsAccountSecurityUiState(isLoading = false)
                }
            }
        }
    }

    fun refresh() {
        if (!settingsRepository.isLoggedIn) {
            _uiState.update { it.copy(isLoading = false, loadFailed = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val check: AccountCheckResult? = settingsRepository.checkAccount().getOrNull()
            if (check == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            val privacy: UserPrivacyPayload? = settingsRepository.getUserPrivacy().getOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    passwordBound = check.password,
                    phoneBound = check.phone,
                    emailBound = check.email,
                    wechatBound = check.wechat,
                    maskedPhone = privacy?.phone ?: it.maskedPhone,
                    maskedEmail = privacy?.email ?: it.maskedEmail
                )
            }
        }
    }

    val activeToken: String get() = settingsRepository.activeToken

    fun openExportDialog() {
        _uiState.update { it.copy(showExportDialog = true, exportTokenRevealed = false) }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false, exportTokenRevealed = false) }
    }

    fun toggleExportTokenVisibility() {
        _uiState.update { it.copy(exportTokenRevealed = !it.exportTokenRevealed) }
    }
}
