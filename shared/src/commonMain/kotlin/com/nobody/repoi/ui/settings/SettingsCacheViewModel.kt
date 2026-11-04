package com.nobody.repoi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.disk.DiskCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsCacheUiState(
    val sizeBytes: Long = -1L,
    val isLoading: Boolean = true,
    val isClearing: Boolean = false,
    val actionMessage: String? = null
)

class SettingsCacheViewModel(
    private val diskCache: DiskCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsCacheUiState())
    val uiState: StateFlow<SettingsCacheUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, actionMessage = null) }
            val size = withContext(ioDispatcher) { runCatching { diskCache.size }.getOrDefault(-1L) }
            _uiState.update { it.copy(sizeBytes = size, isLoading = false) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true, actionMessage = null) }
            val ok = withContext(ioDispatcher) { runCatching { diskCache.clear() }.isSuccess }
            val size = withContext(ioDispatcher) { runCatching { diskCache.size }.getOrDefault(-1L) }
            _uiState.update {
                it.copy(
                    sizeBytes = size,
                    isClearing = false,
                    actionMessage = if (ok) "图片缓存已清理" else "缓存清理失败请重试"
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
