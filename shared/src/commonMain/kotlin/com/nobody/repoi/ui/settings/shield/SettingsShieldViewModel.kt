package com.nobody.repoi.ui.settings.shield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsShieldUiState(
    val nsfwVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class SettingsShieldViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsShieldUiState(isLoading = settingsRepository.isLoggedIn)
    )
    val uiState: StateFlow<SettingsShieldUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.tokenFlow.collect { token ->
                if (token.isNotBlank()) {
                    reload()
                } else {
                    _uiState.value = SettingsShieldUiState(isLoading = false, nsfwVisible = false)
                }
            }
        }
    }

    fun reload() {
        if (!settingsRepository.isLoggedIn) {
            _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            settingsRepository.getMysqlSettings()
                .onSuccess { state ->
                    _uiState.update {
                        it.copy(
                            nsfwVisible = (state["setting_r18"] ?: 1) == 2,
                            isLoading = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "获取屏蔽设置失败"
                        )
                    }
                }
        }
    }

    fun setNsfwVisible(visible: Boolean) {
        if (!settingsRepository.isLoggedIn) return
        val previous = _uiState.value.nsfwVisible
        var started = false
        _uiState.update {
            if (it.isSaving) {
                it
            } else {
                started = true
                it.copy(nsfwVisible = visible, isSaving = true, errorMessage = null)
            }
        }
        if (!started) return
        viewModelScope.launch {
            settingsRepository.updateMysqlSetting("setting_r18", if (visible) 2 else 1)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            nsfwVisible = previous,
                            isSaving = false,
                            errorMessage = throwable.message ?: "更新屏蔽设置失败"
                        )
                    }
                }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
