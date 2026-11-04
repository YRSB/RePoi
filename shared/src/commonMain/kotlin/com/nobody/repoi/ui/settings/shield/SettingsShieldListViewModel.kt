package com.nobody.repoi.ui.settings.shield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.MaskItem
import com.nobody.repoi.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShieldListTab(val title: String, val itemType: String) {
    User("用户", "user"),
    Vendor("厂商", "company"),
    Entry("条目", "hobby")
}

data class SettingsShieldListUiState(
    val tab: ShieldListTab = ShieldListTab.User,
    val items: List<MaskItem> = emptyList(),
    val isLoading: Boolean = true,
    val isUnblocking: Boolean = false,
    val unblockTarget: MaskItem? = null,
    val errorMessage: String? = null
)

class SettingsShieldListViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsShieldListUiState())
    val uiState: StateFlow<SettingsShieldListUiState> = _uiState.asStateFlow()

    init {
        loadTab(ShieldListTab.User)
    }

    fun switchTab(tab: ShieldListTab) {
        if (tab == _uiState.value.tab && _uiState.value.items.isNotEmpty()) return
        loadTab(tab)
    }

    fun confirmUnblock(item: MaskItem) {
        _uiState.update { it.copy(unblockTarget = item) }
    }

    fun dismissUnblockDialog() {
        _uiState.update { it.copy(unblockTarget = null) }
    }

    fun executeUnblock() {
        val target = _uiState.value.unblockTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isUnblocking = true, errorMessage = null) }
            settingsRepository.deleteMask(itemNodeId = target.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            items = it.items.filterNot { item -> item.id == target.id },
                            isUnblocking = false,
                            unblockTarget = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isUnblocking = false,
                            errorMessage = throwable.message ?: "解除屏蔽失败"
                        )
                    }
                }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun loadTab(tab: ShieldListTab) {
        viewModelScope.launch {
            _uiState.update { it.copy(tab = tab, isLoading = true, errorMessage = null) }
            settingsRepository.getMaskList(itemType = tab.itemType)
                .onSuccess { list ->
                    _uiState.update { it.copy(items = list, isLoading = false) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            items = emptyList(),
                            isLoading = false,
                            errorMessage = throwable.message ?: "获取屏蔽列表失败"
                        )
                    }
                }
        }
    }
}
