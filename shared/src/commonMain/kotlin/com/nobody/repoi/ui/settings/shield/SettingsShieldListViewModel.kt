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
    val loadFailed: Boolean = false,
    val isUnblocking: Boolean = false,
    val unblockTarget: MaskItem? = null,
    val isBlocking: Boolean = false,
    val errorMessage: String? = null
)

class SettingsShieldListViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsShieldListUiState())
    val uiState: StateFlow<SettingsShieldListUiState> = _uiState.asStateFlow()
    private var loadSequence = 0L

    init {
        loadTab(ShieldListTab.User)
    }

    fun switchTab(tab: ShieldListTab) {
        if (tab == _uiState.value.tab && _uiState.value.items.isNotEmpty()) return
        loadTab(tab)
    }

    fun retry() {
        loadTab(_uiState.value.tab)
    }

    fun confirmUnblock(item: MaskItem) {
        _uiState.update { it.copy(unblockTarget = item) }
    }

    fun dismissUnblockDialog() {
        _uiState.update { it.copy(unblockTarget = null) }
    }

    fun blockItem(itemNodeId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBlocking = true, errorMessage = null) }
            settingsRepository.addMask(itemNodeId = itemNodeId)
                .onSuccess {
                    _uiState.update { it.copy(isBlocking = false) }
                    loadTab(_uiState.value.tab)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isBlocking = false,
                            errorMessage = throwable.message ?: "屏蔽失败"
                        )
                    }
                }
        }
    }


    fun executeUnblock() {
        val target = _uiState.value.unblockTarget ?: return
        val originTab = _uiState.value.tab
        viewModelScope.launch {
            _uiState.update { it.copy(isUnblocking = true, errorMessage = null) }
            settingsRepository.deleteMask(itemNodeId = target.id)
                .onSuccess {
                    _uiState.update {
                        if (it.tab != originTab) {
                            it.copy(isUnblocking = false)
                        } else {
                            it.copy(
                                items = it.items.filterNot { item -> item.id == target.id },
                                isUnblocking = false,
                                unblockTarget = null
                            )
                        }
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
        val sequence = ++loadSequence
        viewModelScope.launch {
            _uiState.update { it.copy(tab = tab, isLoading = true, loadFailed = false, errorMessage = null) }
            val result = settingsRepository.getMaskList(itemType = tab.itemType)
            if (sequence != loadSequence) return@launch
            result
                .onSuccess { list ->
                    _uiState.update {
                        if (it.tab != tab) it else it.copy(items = list, isLoading = false)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        if (it.tab != tab) it else it.copy(
                            items = emptyList(),
                            isLoading = false,
                            loadFailed = true,
                            errorMessage = throwable.message ?: "获取屏蔽列表失败"
                        )
                    }
                }
        }
    }
}
