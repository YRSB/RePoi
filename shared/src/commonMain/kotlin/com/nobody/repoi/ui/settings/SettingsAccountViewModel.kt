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
    val isLoading: Boolean = true,
    val passwordBound: Boolean = false,
    val phoneBound: Boolean = false,
    val emailBound: Boolean = false,
    val wechatBound: Boolean = false,
    val maskedPhone: String = "",
    val maskedEmail: String = "",
    val loadFailed: Boolean = false
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
    private val _uiState = MutableStateFlow(SettingsAccountSecurityUiState())
    val uiState: StateFlow<SettingsAccountSecurityUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            val check: AccountCheckResult? = settingsRepository.checkAccount().getOrNull()
            val privacy: UserPrivacyPayload? = settingsRepository.getUserPrivacy().getOrNull()
            if (check == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    passwordBound = check.password,
                    phoneBound = check.phone,
                    emailBound = check.email,
                    wechatBound = check.wechat,
                    maskedPhone = privacy?.phone.orEmpty(),
                    maskedEmail = privacy?.email.orEmpty()
                )
            }
        }
    }
}
