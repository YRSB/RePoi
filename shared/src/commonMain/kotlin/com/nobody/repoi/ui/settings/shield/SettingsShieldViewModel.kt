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
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class SettingsShieldViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsShieldUiState())
    val uiState: StateFlow<SettingsShieldUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
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
        val previous = _uiState.value.nsfwVisible
        viewModelScope.launch {
            _uiState.update { it.copy(nsfwVisible = visible, isSaving = true, errorMessage = null) }
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
