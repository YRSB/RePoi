package com.nobody.repoi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.ViewPreferencesManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel，管理展示偏好与第三方扩展开关。
 */
class SettingsViewModel(
    private val viewPreferencesManager: ViewPreferencesManager
) : ViewModel() {

    val isExHobbyEnabled: StateFlow<Boolean> = viewPreferencesManager.isExHobbyEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = viewPreferencesManager.isExHobbyEnabled
        )

    fun setExHobbyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            viewPreferencesManager.setExHobbyEnabled(enabled)
        }
    }
}
