package com.nobody.repoi.ui.vendor.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CompanyActionItem
import com.nobody.repoi.data.repository.VendorDetailRepository
import com.nobody.repoi.ui.navigation.VendorActionsNavParam
import com.nobody.repoi.util.paging.KeyedListLoader
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VendorActionTab(
    val name: String,
    val subType: String
)

data class VendorActionCategoryOption(
    val id: Int,
    val label: String
)

data class VendorActionsUiState(
    val companyNodeId: Long = 0L,
    val companyName: String? = null,
    val selectedTabIndex: Int = 0,
    val selectedCategoryId: Int = 0,
    val isCategoryMenuOpen: Boolean = false,
    val pagedState: PagedListState<CompanyActionItem> = PagedListState()
) {
    val selectedCategoryLabel: String
        get() = VendorActionsViewModel.CATEGORY_OPTIONS.firstOrNull { it.id == selectedCategoryId }?.label ?: "全制品类"
}

class VendorActionsViewModel(
    private val repository: VendorDetailRepository
) : ViewModel() {

    companion object {

        val TABS = listOf(
            VendorActionTab(name = "全部", subType = "all"),
            VendorActionTab(name = "制作决定", subType = "confirm"),
            VendorActionTab(name = "官图更新", subType = "official_pic"),
            VendorActionTab(name = "开订", subType = "preorder"),
            VendorActionTab(name = "延期", subType = "delay"),
            VendorActionTab(name = "出荷", subType = "release"),
            VendorActionTab(name = "再贩", subType = "reorder")
        )

        val CATEGORY_OPTIONS = listOf(
            VendorActionCategoryOption(id = 0, label = "全制品类"),
            VendorActionCategoryOption(id = 100, label = "手办"),
            VendorActionCategoryOption(id = 200, label = "模型"),
            VendorActionCategoryOption(id = 300, label = "娃娃"),
            VendorActionCategoryOption(id = 400, label = "毛绒"),
            VendorActionCategoryOption(id = 500, label = "特摄实物")
        )
    }

    private val _uiState = MutableStateFlow(VendorActionsUiState())
    val uiState: StateFlow<VendorActionsUiState> = _uiState.asStateFlow()

    private val pagedLoader = KeyedListLoader<CompanyActionItem, Long>(
        scope = viewModelScope,
        initialKey = 0L,
        keySelector = { it.id },
        nextKeyProvider = { _, state ->
            val lastItem = state.items.lastOrNull()
            if (lastItem != null && lastItem.actionDate > 0L) lastItem.actionDate else null
        },
        fetcher = { lastId ->
            val currentState = _uiState.value
            val subType = TABS.getOrNull(currentState.selectedTabIndex)?.subType ?: "all"
            val category = currentState.selectedCategoryId
            repository.getCompanyActions(
                companyNodeId = currentState.companyNodeId,
                subType = subType,
                category = category,
                lastId = lastId
            ).map { list ->
                PageChunk(
                    items = list,
                    hasMore = list.size >= 5
                )
            }
        }
    )

    init {
        viewModelScope.launch {
            pagedLoader.state.collect { listState ->
                _uiState.update { it.copy(pagedState = listState) }
            }
        }
    }

    fun init(param: VendorActionsNavParam) {
        if (_uiState.value.companyNodeId == param.companyNodeId && param.companyNodeId > 0L) {
            return
        }
        _uiState.update {
            it.copy(
                companyNodeId = param.companyNodeId,
                companyName = param.companyName,
                selectedTabIndex = 0,
                selectedCategoryId = 0
            )
        }
        pagedLoader.loadInitial()
    }

    fun selectTab(index: Int) {
        if (_uiState.value.selectedTabIndex == index) return
        _uiState.update { it.copy(selectedTabIndex = index) }
        pagedLoader.loadInitial()
    }

    fun selectCategory(categoryId: Int) {
        if (_uiState.value.selectedCategoryId == categoryId) {
            _uiState.update { it.copy(isCategoryMenuOpen = false) }
            return
        }
        _uiState.update {
            it.copy(
                selectedCategoryId = categoryId,
                isCategoryMenuOpen = false
            )
        }
        pagedLoader.loadInitial()
    }

    fun toggleCategoryMenu(expanded: Boolean? = null) {
        _uiState.update {
            it.copy(isCategoryMenuOpen = expanded ?: !it.isCategoryMenuOpen)
        }
    }

    fun refresh() {
        pagedLoader.refresh()
    }

    fun loadMore() {
        pagedLoader.loadMore()
    }

    fun retry() {
        if (_uiState.value.pagedState.items.isEmpty()) {
            pagedLoader.loadInitial()
        } else {
            pagedLoader.loadMore()
        }
    }
}
