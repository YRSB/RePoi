package com.nobody.repoi.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.data.repository.ZoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ZoneSettingsUiState(
    val showAll: Boolean = true,
    val sort: String = "default",
    val focusZones: List<ZoneItem> = emptyList(),
    val hiddenZoneIds: Set<Long> = emptySet(),
    val customOrder: List<Long> = emptyList(),
    val isSaving: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val errorMessage: String? = null
)

class ZoneSettingsViewModel(
    private val zoneRepository: ZoneRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ZoneSettingsUiState())
    val uiState: StateFlow<ZoneSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                zoneRepository.focusZonesFlow,
                zoneRepository.settingsFlow
            ) { focusList, settings ->
                val hiddenSet = settings.hiddenZoneIds.toSet()
                val orderList = settings.customOrder

                // 根据排序规则重新组织展示列表
                val orderedZones = when (settings.sort) {
                    "custom" -> {
                        val zoneMap = focusList.associateBy { it.nodeId }
                        val result = mutableListOf<ZoneItem>()
                        orderList.forEach { id ->
                            zoneMap[id]?.let { result.add(it) }
                        }
                        focusList.forEach { if (!result.contains(it)) result.add(it) }
                        result
                    }
                    else -> focusList
                }

                ZoneSettingsUiState(
                    showAll = settings.showAll,
                    sort = settings.sort,
                    focusZones = orderedZones,
                    hiddenZoneIds = hiddenSet,
                    customOrder = if (orderList.isNotEmpty()) orderList else focusList.map { it.nodeId }
                )
            }.collect { newState ->
                _uiState.update { current ->
                    if (current.isSaving) current else newState
                }
            }
        }
    }

    fun setShowAll(showAll: Boolean) {
        _uiState.update { it.copy(showAll = showAll) }
    }

    fun setSort(sort: String) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun toggleZoneVisibility(nodeId: Long) {
        _uiState.update { current ->
            val updated = current.hiddenZoneIds.toMutableSet()
            if (updated.contains(nodeId)) {
                updated.remove(nodeId)
            } else {
                updated.add(nodeId)
            }
            current.copy(hiddenZoneIds = updated)
        }
    }

    fun moveZone(fromIndex: Int, toIndex: Int) {
        _uiState.update { current ->
            if (fromIndex !in current.focusZones.indices || toIndex !in current.focusZones.indices) {
                return@update current
            }
            val list = current.focusZones.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            current.copy(
                focusZones = list,
                customOrder = list.map { it.nodeId }
            )
        }
    }

    fun saveSettings() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            val currentState = _uiState.value
            val result = zoneRepository.updateSettings(
                showAll = currentState.showAll,
                sort = currentState.sort,
                hiddenZoneIds = currentState.hiddenZoneIds.toList(),
                customOrder = currentState.customOrder
            )

            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, isSavedSuccess = true) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "保存设置失败"
                    )
                }
            }
        }
    }
}
