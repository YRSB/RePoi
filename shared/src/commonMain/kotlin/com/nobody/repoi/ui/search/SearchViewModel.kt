package com.nobody.repoi.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.remote.model.AttributeItem
import com.nobody.repoi.data.remote.model.HotwordItem
import com.nobody.repoi.data.remote.model.SearchCompanyAggregate
import com.nobody.repoi.data.remote.model.SearchResultItem
import com.nobody.repoi.data.remote.model.SearchUserItem
import com.nobody.repoi.data.remote.model.SearchYearAggregate
import com.nobody.repoi.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val keyword: String = "",
    val selectedTab: SearchTab = SearchTab.HOBBY,
    val isSubmitted: Boolean = false,
    val isParamsReady: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val hotwordsMap: Map<Int, List<HotwordItem>> = emptyMap(),
    val isLoadingHotwords: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val isSuggestionsVisible: Boolean = false,

    // 搜索结果数据
    val items: List<SearchResultItem> = emptyList(),
    val userItems: List<SearchUserItem> = emptyList(),
    val page: Int = 1,
    val searchSessionId: Int = 0,
    val totalCount: Int = 0,
    val pageCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,

    // 排序与筛选
    val order: String = "",
    val selectedCompany: SearchCompanyAggregate? = null,
    val selectedYear: String? = null,
    val selectedHobbyCategory: Int = HobbyAllCategory.id,
    val hobbyCategories: List<CategoryItem> = emptyList(),
    val selectedAlbumCategory: AlbumSubCategory = AlbumSubCategory.ALL,
    val selectedElementsCategory: ElementsSubCategory = ElementsSubCategory.ALL,
    val selectedAttribute: Int = 0,
    val attributeMap: Map<Int, List<AttributeItem>> = emptyMap(),
    val isLoadingAttributes: Boolean = false,
    val availableCompanies: List<SearchCompanyAggregate> = emptyList(),
    val availableYears: List<SearchYearAggregate> = emptyList()
) {
    val isFilterActive: Boolean
        get() = com.nobody.repoi.ui.search.isFilterActive(
            tab = selectedTab,
            order = order,
            hobbyCategory = selectedHobbyCategory,
            hasCompany = selectedCompany != null,
            hasYear = selectedYear != null,
            albumCategory = selectedAlbumCategory,
            elementsCategory = selectedElementsCategory,
            attribute = selectedAttribute
        )
}

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val categoryManager: CategoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var autoFillJob: Job? = null
    private var searchJob: Job? = null
    private var paramsInitialized = false

    init {
        viewModelScope.launch {
            searchRepository.searchHistoryFlow.collect { historyList ->
                _uiState.update { it.copy(searchHistory = historyList) }
            }
        }
        viewModelScope.launch {
            categoryManager.categoryMap.collect { tree ->
                val hobbyTree = tree[HobbyAllCategory.id] ?: categoryManager.getSubCategories(HobbyAllCategory.id)
                val withoutAll = hobbyTree.filter { it.id != HobbyAllCategory.id }
                _uiState.update { it.copy(hobbyCategories = listOf(HobbyAllCategory) + withoutAll) }
            }
        }
        val initialTree = categoryManager.getSubCategories(HobbyAllCategory.id).filter { it.id != HobbyAllCategory.id }
        _uiState.update { it.copy(hobbyCategories = listOf(HobbyAllCategory) + initialTree) }
    }

    fun initParams(
        initialKeyword: String,
        initialCategory: Int,
        initialCompanyNodeId: Long? = null,
        initialCompanyName: String? = null
    ) {
        if (paramsInitialized) return
        paramsInitialized = true
        val tab = SearchTab.fromCategoryId(initialCategory)
        val initialCompany = if (initialCompanyNodeId != null && initialCompanyNodeId > 0) {
            SearchCompanyAggregate(nodeId = initialCompanyNodeId, itemName = initialCompanyName ?: "厂商", docCount = 0)
        } else null
        val shouldSubmit = initialKeyword.isNotBlank() || initialCompany != null
        _uiState.update {
            it.copy(
                keyword = initialKeyword,
                selectedTab = tab,
                isSubmitted = shouldSubmit,
                isParamsReady = true,
                isSuggestionsVisible = false,
                items = emptyList(),
                userItems = emptyList(),
                order = "",
                selectedCompany = initialCompany,
                selectedYear = null,
                selectedHobbyCategory = HobbyAllCategory.id,
                selectedAlbumCategory = AlbumSubCategory.ALL,
                selectedElementsCategory = ElementsSubCategory.ALL,
                selectedAttribute = 0,
                availableCompanies = if (initialCompany != null) listOf(initialCompany) else emptyList(),
                availableYears = emptyList(),
                errorMessage = null
            )
        }
        if (shouldSubmit) {
            performSearch(initialKeyword)
        } else {
            loadHotwordsForTab(tab)
        }
        if (tab == SearchTab.WORKS_ELEMENT) {
            loadElementAttributes(ElementsSubCategory.ALL.categoryId)
        }
    }
    fun onSearchExpanded() {
        val currentKeyword = _uiState.value.keyword.trim()
        if (currentKeyword.isNotBlank()) {
            loadSuggestions(currentKeyword, debounce = false)
        }
    }

    fun onKeywordChange(newText: String) {
        _uiState.update {
            it.copy(
                keyword = newText,
                isSubmitted = false,
                items = if (newText.isBlank()) emptyList() else it.items,
                userItems = if (newText.isBlank()) emptyList() else it.userItems
            )
        }
        if (newText.isBlank()) {
            autoFillJob?.cancel()
            _uiState.update { it.copy(suggestions = emptyList(), isSuggestionsVisible = false) }
            return
        }
        loadSuggestions(newText, debounce = true)
    }

    private fun loadSuggestions(query: String, debounce: Boolean) {
        autoFillJob?.cancel()
        autoFillJob = viewModelScope.launch {
            if (debounce) {
                delay(300L)
            }
            val tab = _uiState.value.selectedTab
            val result = searchRepository.getAutoFill(tab.categoryId, query)
            result.onSuccess { keywords ->
                if (_uiState.value.keyword.trim() == query.trim()) {
                    _uiState.update { it.copy(suggestions = keywords, isSuggestionsVisible = keywords.isNotEmpty()) }
                }
            }
        }
    }

    fun onClearKeyword() {
        autoFillJob?.cancel()
        _uiState.update {
            it.copy(
                keyword = "",
                isSubmitted = false,
                suggestions = emptyList(),
                isSuggestionsVisible = false,
                items = emptyList(),
                userItems = emptyList(),
                errorMessage = null
            )
        }
    }

    fun onSelectTab(tab: SearchTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update {
            it.copy(
                selectedTab = tab,
                isSuggestionsVisible = false,
                order = "",
                selectedCompany = null,
                selectedYear = null,
                selectedHobbyCategory = HobbyAllCategory.id,
                selectedAlbumCategory = AlbumSubCategory.ALL,
                selectedElementsCategory = ElementsSubCategory.ALL,
                selectedAttribute = 0,
                items = emptyList(),
                userItems = emptyList(),
                availableCompanies = emptyList(),
                availableYears = emptyList()
            )
        }
        if (_uiState.value.isSubmitted && _uiState.value.keyword.isNotBlank()) {
            performSearch(_uiState.value.keyword)
        } else {
            loadHotwordsForTab(tab)
        }
        if (tab == SearchTab.WORKS_ELEMENT) {
            loadElementAttributes(_uiState.value.selectedElementsCategory.categoryId)
        }
    }

    fun onHobbyCategoryChange(categoryId: Int) {
        if (_uiState.value.selectedHobbyCategory == categoryId) return
        _uiState.update { it.copy(selectedHobbyCategory = categoryId) }
        if (_uiState.value.isSubmitted && _uiState.value.keyword.isNotBlank()) {
            performSearch(_uiState.value.keyword)
        }
    }

    fun onElementsCategoryChange(subCategory: ElementsSubCategory) {
        if (_uiState.value.selectedElementsCategory == subCategory) return
        _uiState.update { it.copy(selectedElementsCategory = subCategory, selectedAttribute = 0) }
        loadElementAttributes(subCategory.categoryId)
        if (_uiState.value.isSubmitted && _uiState.value.keyword.isNotBlank()) {
            performSearch(_uiState.value.keyword)
        }
    }

    fun onAttributeChange(attributeId: Int) {
        if (_uiState.value.selectedAttribute == attributeId) return
        _uiState.update { it.copy(selectedAttribute = attributeId) }
        if (_uiState.value.isSubmitted && _uiState.value.keyword.isNotBlank()) {
            performSearch(_uiState.value.keyword)
        }
    }
    fun onAlbumCategoryChange(subCategory: AlbumSubCategory) {
        if (_uiState.value.selectedAlbumCategory == subCategory) return
        _uiState.update { it.copy(selectedAlbumCategory = subCategory) }
        if (_uiState.value.isSubmitted && _uiState.value.keyword.isNotBlank()) {
            performSearch(_uiState.value.keyword)
        }
    }

    fun onSearch(targetKeyword: String? = null) {
        val searchWord = (targetKeyword ?: _uiState.value.keyword).trim()
        if (searchWord.isBlank()) return

        _uiState.update {
            it.copy(
                keyword = searchWord,
                isSubmitted = true,
                isSuggestionsVisible = false
            )
        }
        viewModelScope.launch {
            searchRepository.addSearchHistory(searchWord)
        }
        loadSuggestions(searchWord, debounce = false)
        performSearch(searchWord)
    }

    fun onSortChange(newOrder: String) {
        if (_uiState.value.order == newOrder) return
        _uiState.update { it.copy(order = newOrder) }
        if (_uiState.value.isSubmitted && _uiState.value.keyword.isNotBlank()) {
            performSearch(_uiState.value.keyword)
        }
    }

    fun onFilterChange(company: SearchCompanyAggregate?, year: String?) {
        _uiState.update {
            it.copy(
                selectedCompany = company,
                selectedYear = year
            )
        }
        if (_uiState.value.isSubmitted && _uiState.value.keyword.isNotBlank()) {
            performSearch(_uiState.value.keyword)
        }
    }

    fun onRefresh() {
        if (!_uiState.value.isSubmitted || _uiState.value.keyword.isBlank()) return
        _uiState.update { it.copy(isRefreshing = true) }
        performSearch(_uiState.value.keyword, isRefresh = true)
    }

    fun onLoadMore() {
        val state = _uiState.value
        if (!state.isSubmitted || state.isLoading || state.isLoadingMore || !state.hasMore) return

        val nextPage = state.page + 1
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            if (state.selectedTab == SearchTab.USER) {
                val result = searchRepository.searchUsers(
                    keyword = state.keyword,
                    page = nextPage,
                    pageSize = 10
                )
                result.onSuccess { payload ->
                    val newUsers = payload.userList.orEmpty()
                    _uiState.update { current ->
                        val combined = current.userItems + newUsers
                        current.copy(
                            userItems = combined,
                            page = nextPage,
                            isLoadingMore = false,
                            hasMore = newUsers.size >= 10
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            } else {
                val targetCategory = resolveSearchCategory(
                    state.selectedTab,
                    state.selectedHobbyCategory,
                    state.selectedAlbumCategory,
                    state.selectedElementsCategory
                )
                val result = searchRepository.search(
                    category = targetCategory,
                    itemType = state.selectedTab.itemType,
                    keyword = state.keyword,
                    page = nextPage,
                    pageSize = 30,
                    order = state.order,
                    company = state.selectedCompany?.nodeId,
                    releaseYear = state.selectedYear,
                    attribute = state.selectedAttribute.takeIf { state.selectedTab == SearchTab.WORKS_ELEMENT },
                    aggregate = false
                )
                result.onSuccess { payload ->
                    val newItems = payload.result.orEmpty()
                    _uiState.update { current ->
                        val combined = current.items + newItems
                        val total = payload.query?.totalCount ?: current.totalCount
                        val pageCount = payload.query?.pageCount ?: current.pageCount
                        current.copy(
                            items = combined,
                            page = nextPage,
                            totalCount = total,
                            pageCount = pageCount,
                            isLoadingMore = false,
                            hasMore = nextPage < pageCount && newItems.isNotEmpty()
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun onRemoveHistory(item: String) {
        viewModelScope.launch {
            searchRepository.removeSearchHistory(item)
        }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            searchRepository.clearSearchHistory()
        }
    }

    fun dismissSuggestions() {
        _uiState.update { it.copy(isSuggestionsVisible = false) }
    }

    fun loadElementAttributes(categoryId: Int) {
        if (_uiState.value.attributeMap.containsKey(categoryId)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAttributes = true) }
            val result = searchRepository.getElementAttributes(categoryId)
            result.onSuccess { attrs ->
                _uiState.update { current ->
                    val updatedMap = current.attributeMap.toMutableMap().apply {
                        put(categoryId, attrs)
                    }
                    current.copy(attributeMap = updatedMap, isLoadingAttributes = false)
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingAttributes = false) }
            }
        }
    }
    private fun loadHotwordsForTab(tab: SearchTab) {
        if (_uiState.value.hotwordsMap.containsKey(tab.categoryId)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHotwords = true) }
            val result = searchRepository.getHotwords(tab.categoryId)
            result.onSuccess { words ->
                _uiState.update { current ->
                    val updatedMap = current.hotwordsMap.toMutableMap().apply {
                        put(tab.categoryId, words)
                    }
                    current.copy(
                        hotwordsMap = updatedMap,
                        isLoadingHotwords = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingHotwords = false) }
            }
        }
    }

    fun refreshHotwords() {
        val tab = _uiState.value.selectedTab
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHotwords = true) }
            val result = searchRepository.getHotwords(tab.categoryId)
            result.onSuccess { words ->
                _uiState.update { current ->
                    val updatedMap = current.hotwordsMap.toMutableMap().apply {
                        put(tab.categoryId, words)
                    }
                    current.copy(
                        hotwordsMap = updatedMap,
                        isLoadingHotwords = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingHotwords = false) }
            }
        }
    }

    private fun performSearch(keyword: String, isRefresh: Boolean = false) {
        searchJob?.cancel()
        val currentTab = _uiState.value.selectedTab

        if (!isRefresh) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    page = 1,
                    hasMore = false,
                    searchSessionId = it.searchSessionId + 1
                )
            }
        }

        searchJob = viewModelScope.launch {
            if (currentTab == SearchTab.USER) {
                val result = searchRepository.searchUsers(
                    keyword = keyword,
                    page = 1,
                    pageSize = 10
                )
                result.onSuccess { payload ->
                    val userList = payload.userList.orEmpty()
                    _uiState.update {
                        it.copy(
                            userItems = userList,
                            items = emptyList(),
                            page = 1,
                            isLoading = false,
                            isRefreshing = false,
                            hasMore = userList.size >= 10,
                            errorMessage = null
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "搜索失败，请稍后重试"
                        )
                    }
                }
            } else {
                val state = _uiState.value
                val targetCategory = resolveSearchCategory(
                    currentTab,
                    state.selectedHobbyCategory,
                    state.selectedAlbumCategory,
                    state.selectedElementsCategory
                )
                val result = searchRepository.search(
                    category = targetCategory,
                    itemType = currentTab.itemType,
                    keyword = keyword,
                    page = 1,
                    pageSize = 30,
                    order = state.order,
                    company = state.selectedCompany?.nodeId,
                    releaseYear = state.selectedYear,
                    attribute = state.selectedAttribute.takeIf { currentTab == SearchTab.WORKS_ELEMENT },
                    aggregate = true
                )
                result.onSuccess { payload ->
                    val resultItems = payload.result.orEmpty()
                    val total = payload.query?.totalCount ?: resultItems.size
                    val pageCount = payload.query?.pageCount ?: 1
                    _uiState.update { current ->
                        current.copy(
                            items = resultItems,
                            userItems = emptyList(),
                            page = 1,
                            totalCount = total,
                            pageCount = pageCount,
                            hasMore = 1 < pageCount && resultItems.isNotEmpty(),
                            availableCompanies = if (state.selectedTab == SearchTab.HOBBY) {
                                payload.companys?.filter { it.docCount > 0 && !it.itemName.isNullOrBlank() }.orEmpty()
                            } else {
                                emptyList()
                            },
                            availableYears = if (state.selectedTab == SearchTab.HOBBY) {
                                payload.releaseYear?.filter { it.docCount > 0 && !it.keyAsString.isNullOrBlank() }.orEmpty()
                            } else {
                                emptyList()
                            },
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "搜索失败，请稍后重试"
                        )
                    }
                }
            }
        }
    }
}
