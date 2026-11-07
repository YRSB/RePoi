package com.nobody.repoi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.disk.DiskCache
import com.nobody.repoi.data.local.ViewPreferencesManager
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
    val maxSizeBytes: Long = ViewPreferencesManager.DEFAULT_IMAGE_CACHE_MAX_BYTES,
    val isLoading: Boolean = true,
    val isClearing: Boolean = false,
    val actionMessage: String? = null
)

class SettingsCacheViewModel(
    private val diskCache: DiskCache,
    private val imageLoader: ImageLoader? = null,
    private val viewPreferencesManager: ViewPreferencesManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    constructor(
        diskCache: DiskCache,
        ioDispatcher: CoroutineDispatcher
    ) : this(diskCache, null, null, ioDispatcher)

    private val _uiState = MutableStateFlow(
        SettingsCacheUiState(
            maxSizeBytes = viewPreferencesManager?.imageCacheMaxBytes ?: ViewPreferencesManager.DEFAULT_IMAGE_CACHE_MAX_BYTES
        )
    )
    val uiState: StateFlow<SettingsCacheUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewPreferencesManager?.let { prefs ->
            viewModelScope.launch {
                prefs.imageCacheMaxBytesFlow.collect { maxBytes ->
                    _uiState.update { it.copy(maxSizeBytes = maxBytes) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val size = withContext(ioDispatcher) { runCatching { diskCache.size }.getOrDefault(-1L) }
            _uiState.update { it.copy(sizeBytes = size, isLoading = false) }
        }
    }

    fun updateMaxSizeBytes(bytes: Long) {
        _uiState.update { it.copy(maxSizeBytes = bytes) }
        viewModelScope.launch {
            viewPreferencesManager?.setImageCacheMaxBytes(bytes)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true, actionMessage = null) }
            val ok = withContext(ioDispatcher) {
                runCatching {
                    imageLoader?.memoryCache?.clear()
                    diskCache.clear()
                }.isSuccess
            }
            val size = withContext(ioDispatcher) { runCatching { diskCache.size }.getOrElse { if (ok) 0L else -1L } }
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
