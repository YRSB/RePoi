package com.nobody.poishelf.ui.item.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.local.CategoryManager
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.data.repository.ItemRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemHomeUiState(
    val selectedCategoryId: Int = 10000,
    val categories: List<CategoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val preOrderItems: List<HobbyItemSummary> = emptyList(),
    val releaseItems: List<HobbyItemSummary> = emptyList(),
    val errorMessage: String? = null
)

class ItemHomeViewModel(
    private val itemRepository: ItemRepository,
    private val categoryManager: CategoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ItemHomeUiState(
            categories = categoryManager.defaultPrimaryCategories
        )
    )
    val uiState: StateFlow<ItemHomeUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    var savedScrollIndex: Int = 0
        private set
    var savedScrollOffset: Int = 0
        private set

    fun saveScrollPosition(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    fun resetScrollPosition() {
        savedScrollIndex = 0
        savedScrollOffset = 0
    }

    init {
        loadData(isRefresh = false)
    }

    fun selectCategory(categoryId: Int) {
        if (_uiState.value.selectedCategoryId == categoryId) return
        resetScrollPosition()
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        loadData(isRefresh = false)
    }

    fun refresh() {
        resetScrollPosition()
        loadData(isRefresh = true)
    }

    fun retry() {
        loadData(isRefresh = false)
    }

    private fun loadData(isRefresh: Boolean) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            val categoryId = _uiState.value.selectedCategoryId
            itemRepository.getItemHomePreview(categoryId)
                .onSuccess { previewData ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            preOrderItems = previewData.preOrderItems,
                            releaseItems = previewData.releaseItems,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "加载失败，请检查网络"
                        )
                    }
                }
        }
    }
}
