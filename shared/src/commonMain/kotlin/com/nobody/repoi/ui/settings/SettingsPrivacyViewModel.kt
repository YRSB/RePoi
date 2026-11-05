package com.nobody.repoi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object PrivacySettingKeys {
    const val COLLECT_HOBBY = "setting_collect_hobby"
    const val COLLECT_ALBUM = "setting_collect_album"
    const val COLLECT_STATISTICS = "setting_collect_statistics"
    const val COLLECT_OTHER = "setting_collect_other"
    const val PRIVACY_FRIEND = "setting_privacy_friend"
    const val OWN_ACTION = "setting_own_action"
    const val PRIVACY_POSTS = "setting_privacy_posts"
    const val STRANGER_MESSAGE = "setting_stranger_message"

    val mysqlKeys = setOf(
        COLLECT_HOBBY,
        COLLECT_ALBUM,
        COLLECT_STATISTICS,
        COLLECT_OTHER,
        PRIVACY_FRIEND
    )
}

data class SettingsPrivacyUiState(
    val isLoading: Boolean = true,
    val values: Map<String, Int> = emptyMap(),
    val savingKeys: Set<String> = emptySet(),
    val errorMessage: String? = null
)

class SettingsPrivacyViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsPrivacyUiState())
    val uiState: StateFlow<SettingsPrivacyUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val mysql = repository.getMysqlSettings()
            val riak = repository.getRiakSettings()
            val failure = if (mysql.isFailure) mysql else riak
            _uiState.update {
                val merged = it.values.toMutableMap()
                mysql.onSuccess { merged.putAll(it) }
                riak.onSuccess { merged.putAll(it) }
                it.copy(
                    isLoading = false,
                    values = merged,
                    errorMessage = failure.exceptionOrNull()?.message
                )
            }
        }
    }

    fun setOption(key: String, value: Int) {
        val previous = _uiState.value.values[key]
        var started = false
        _uiState.update {
            if (key in it.savingKeys) {
                it
            } else {
                started = true
                it.copy(
                    values = it.values + (key to value),
                    savingKeys = it.savingKeys + key,
                    errorMessage = null
                )
            }
        }
        if (!started) return
        viewModelScope.launch {
            val result = repository.updatePrivacySetting(key, value)
            _uiState.update {
                val restored = if (result.isFailure) {
                    if (previous == null) it.values - key else it.values + (key to previous)
                } else {
                    it.values
                }
                it.copy(
                    values = restored,
                    savingKeys = it.savingKeys - key,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
