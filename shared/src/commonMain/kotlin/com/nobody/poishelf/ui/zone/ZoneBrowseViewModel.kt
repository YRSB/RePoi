package com.nobody.poishelf.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.ZoneItem
import com.nobody.poishelf.data.repository.ZoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ZoneBrowseUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val focusZones: List<ZoneItem> = emptyList(),
    val groups: List<ZoneGroup> = emptyList(),
    val errorMessage: String? = null
)

class ZoneBrowseViewModel(
    private val zoneRepository: ZoneRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZoneBrowseUiState())
    val uiState: StateFlow<ZoneBrowseUiState> = _uiState.asStateFlow()

    init {
        // 响应式监听关注列表变化（如在专区详情页关注/取关）
        viewModelScope.launch {
            zoneRepository.focusZonesFlow.collect { focusList ->
                _uiState.update { it.copy(focusZones = focusList) }
            }
        }
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            } else if (_uiState.value.groups.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            val result = zoneRepository.getZoneHomeTabs(forceRefresh = forceRefresh)
            result.onSuccess { payload ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        focusZones = payload.focusZones ?: it.focusZones,
                        groups = payload.groups ?: emptyList(),
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message ?: "加载专区数据失败"
                    )
                }
            }
        }
    }

    fun refresh() {
        loadData(forceRefresh = true)
    }
}
