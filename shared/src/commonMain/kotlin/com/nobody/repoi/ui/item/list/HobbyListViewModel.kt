package com.nobody.repoi.ui.item.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.pagedListLoader
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HobbyListUiState(
    val title: String = "周边列表",
    val categoryId: Int = 10000,
    val subCategoryId: Int? = null,
    val order: String = "hits7Day",
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val releaseStart: String? = null,
    val releaseEnd: String? = null,
    val hasShopItem: Int? = null,
    val keyword: String = "",
    val sex: Int? = null,
    val specs: String? = null,
    val scale: String? = null,
    val r18: Int? = null,
    val workers: String? = null,
    val works: String? = null,
    val tag: String? = null,
    val character: String? = null,
    val items: List<HobbyItemSummary> = emptyList(),
    val page: Int = 1,
    val isGridMode: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEndReached: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val showFilterSheet: Boolean = false
) {
    /** 是否有高级筛选条件生效（用于顶栏徽标指示） */
    val hasAdvancedFilters: Boolean
        get() = sex != null || !specs.isNullOrBlank() || !scale.isNullOrBlank() || r18 != null
}

class HobbyListViewModel(
    private val itemRepository: ItemRepository,
    private val categoryManager: CategoryManager,
    private val viewPreferencesManager: ViewPreferencesManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HobbyListUiState(isGridMode = viewPreferencesManager.isHobbyListGridMode))
    val uiState: StateFlow<HobbyListUiState> = _uiState.asStateFlow()
    val categoryTree: StateFlow<Map<Int, List<CategoryItem>>> = categoryManager.categoryMap

    private var currentParam: HobbyListNavParam? = null

    private val loader: PagedListLoader<HobbyItemSummary> = pagedListLoader(
        scope = viewModelScope,
        pageSize = 24,
        keySelector = { if (it.id > 0) it.id else it.itemId }
    ) { page, size ->
        val curState = _uiState.value
        val params = mutableMapOf<String, String>()
        params["page"] = page.toString()
        params["pageSize"] = size.toString()
        params["category"] = (curState.subCategoryId ?: curState.categoryId).toString()
        params["order"] = curState.order

        curState.releaseYear?.let { if (it > 0) params["releaseYear"] = it.toString() }
        curState.releaseMonth?.let { if (it > 0) params["releaseMonth"] = it.toString() }
        curState.releaseStart?.let { params["releaseStart"] = it }
        curState.releaseEnd?.let { params["releaseEnd"] = it }
        curState.hasShopItem?.let { params["hasShopItem"] = it.toString() }
        if (curState.keyword.isNotBlank()) params["keyword"] = curState.keyword.trim()
        curState.sex?.let { if (it != -1) params["sex"] = it.toString() }
        curState.specs?.let { if (it.isNotBlank()) params["specs"] = it }
        curState.scale?.let { if (it.isNotBlank() && it != "0") params["scale"] = it }
        curState.r18?.let { if (it != -1) params["r18"] = it.toString() }
        curState.workers?.let { if (it.isNotBlank()) params["workers"] = it }
        curState.works?.let { if (it.isNotBlank()) params["works"] = it }
        curState.character?.let { if (it.isNotBlank()) { params["charactar"] = it; params["character"] = it } }
        curState.tag?.let { if (it.isNotBlank()) params["tag"] = it }

        itemRepository.queryHobbyList(params)
    }

    init {
        viewModelScope.launch {
            viewPreferencesManager.isHobbyListGridModeFlow.collect { isGrid ->
                _uiState.update { it.copy(isGridMode = isGrid) }
            }
        }
        viewModelScope.launch {
            loader.state.collect { listState ->
                _uiState.update { state ->
                    state.copy(
                        items = listState.items,
                        page = listState.page,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        isEndReached = listState.isEndReached,
                        errorMessage = listState.errorMessage,
                        loadMoreError = listState.loadMoreError
                    )
                }
            }
        }
    }

    fun initParams(param: HobbyListNavParam) {
        if (currentParam == param) return
        currentParam = param

        val primaryId = resolvePrimary(param.categoryId)
        _uiState.update {
            HobbyListUiState(
                title = param.title,
                order = param.order,
                categoryId = primaryId,
                subCategoryId = if (primaryId != param.categoryId) param.categoryId else null,
                releaseYear = param.releaseYear,
                releaseMonth = param.releaseMonth,
                releaseStart = param.releaseStart,
                keyword = param.keyword ?: "",
                sex = param.sex,
                specs = param.specs,
                scale = param.scale,
                r18 = param.r18,
                workers = param.workers,
                works = param.works,
                character = param.character,
                tag = param.tag,
                isGridMode = viewPreferencesManager.isHobbyListGridMode
            )
        }
        loader.loadInitial()
    }

    fun toggleViewMode() {
        val newMode = !_uiState.value.isGridMode
        _uiState.update { it.copy(isGridMode = newMode) }
        viewModelScope.launch {
            viewPreferencesManager.setHobbyListGridMode(newMode)
        }
    }

    fun openFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = true) }
    }

    fun closeFilterSheet() {
        _uiState.update { it.copy(showFilterSheet = false) }
    }

    fun updateKeyword(keyword: String) {
        _uiState.update { it.copy(keyword = keyword) }
    }

    fun submitSearch() {
        loader.loadInitial()
    }

    fun setYear(year: Int?) {
        if (_uiState.value.releaseYear == year) return
        _uiState.update { it.copy(releaseYear = year) }
        loader.loadInitial()
    }

    fun setMonth(month: Int?) {
        if (_uiState.value.releaseMonth == month) return
        _uiState.update { it.copy(releaseMonth = month) }
        loader.loadInitial()
    }

    fun setOrder(order: String) {
        if (_uiState.value.order == order) return
        _uiState.update { it.copy(order = order) }
        loader.loadInitial()
    }

    fun setCategory(categoryId: Int) {
        val state = _uiState.value
        if (state.categoryId == categoryId && state.subCategoryId == null) return
        _uiState.update { it.copy(categoryId = categoryId, subCategoryId = null) }
        loader.loadInitial()
    }

    fun selectCategory(categoryId: Int, subCategoryId: Int?) {
        val state = _uiState.value
        if (state.categoryId == categoryId && state.subCategoryId == subCategoryId) return
        _uiState.update { it.copy(categoryId = categoryId, subCategoryId = subCategoryId) }
        loader.loadInitial()
    }

    fun setSubCategory(subCategoryId: Int?) {
        if (_uiState.value.subCategoryId == subCategoryId) return
        _uiState.update { it.copy(subCategoryId = subCategoryId) }
        loader.loadInitial()
    }

    fun applyAdvancedFilters(filterParam: HobbyAdvancedFilterParam) {
        applyAdvancedFilters(filterParam.sex, filterParam.specs, filterParam.scale, filterParam.r18)
    }

    fun applyAdvancedFilters(sex: Int?, specs: String?, scale: String?, r18: Int?) {
        _uiState.update {
            it.copy(
                sex = sex,
                specs = specs,
                scale = scale,
                r18 = r18,
                showFilterSheet = false
            )
        }
        loader.loadInitial()
    }

    fun resetAdvancedFilters() {
        _uiState.update {
            it.copy(
                sex = null,
                specs = null,
                scale = null,
                r18 = null,
                workers = null,
                works = null,
                tag = null,
                showFilterSheet = false
            )
        }
        loader.loadInitial()
    }

    fun resetAllFilters() {
        _uiState.update {
            it.copy(
                releaseYear = null,
                releaseMonth = null,
                keyword = "",
                sex = null,
                specs = null,
                scale = null,
                r18 = null,
                workers = null,
                works = null,
                tag = null,
                order = "hits7Day",
                categoryId = 10000,
                subCategoryId = null,
                showFilterSheet = false
            )
        }
        loader.loadInitial()
    }

    fun refresh() {
        loader.refresh()
    }

    fun loadNextPage() {
        loader.loadMore()
    }

    fun getSubCategories(parentId: Int): List<CategoryItem> {
        return categoryManager.getSubCategories(parentId)
    }

    val primaryCategories: List<CategoryItem>
        get() = categoryManager.defaultPrimaryCategories

    private fun resolvePrimary(categoryId: Int): Int {
        if (primaryCategories.any { it.id == categoryId }) return categoryId
        return categoryManager.getParentId(categoryId) ?: categoryId
    }
}
