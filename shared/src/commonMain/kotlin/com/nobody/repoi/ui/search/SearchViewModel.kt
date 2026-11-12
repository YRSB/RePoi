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

/**
 * 单个搜索分类的独立状态：列表数据、分页游标、加载态与失败信息。
 *
 * 每个 Tab 各自持有一份，切回已加载分类时直接复用，不重复请求。
 */
data class TabSearchState(
    val items: List<SearchResultItem> = emptyList(),
    val userItems: List<SearchUserItem> = emptyList(),
    val page: Int = 1,
    val totalCount: Int = 0,
    val pageCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val isLoaded: Boolean = false
)

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

    // 每个分类独立的搜索结果与分页状态
    val tabStates: Map<SearchTab, TabSearchState> = SearchTab.entries.associateWith { TabSearchState() },
    val searchSessionId: Int = 0,

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
    /** 当前前台分类的独立状态（不存在时返回默认空状态）。 */
    val currentTabState: TabSearchState
        get() = tabStates[selectedTab] ?: TabSearchState()

    // 向后兼容的只读派生访问器：统一从 currentTabState 取值，避免双真相源
    val items: List<SearchResultItem> get() = currentTabState.items
    val userItems: List<SearchUserItem> get() = currentTabState.userItems
    val page: Int get() = currentTabState.page
    val totalCount: Int get() = currentTabState.totalCount
    val pageCount: Int get() = currentTabState.pageCount
    val hasMore: Boolean get() = currentTabState.hasMore
    val isLoading: Boolean get() = currentTabState.isLoading
    val isRefreshing: Boolean get() = currentTabState.isRefreshing
    val isLoadingMore: Boolean get() = currentTabState.isLoadingMore
    val errorMessage: String? get() = currentTabState.errorMessage

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
    private val tabJobs = mutableMapOf<SearchTab, Job>()
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

        cancelAllTabJobs()
        val sessionId = _uiState.value.searchSessionId + 1
        _uiState.update {
            it.copy(
                keyword = initialKeyword,
                selectedTab = tab,
                isSubmitted = shouldSubmit,
                isParamsReady = true,
                isSuggestionsVisible = false,
                searchSessionId = sessionId,
                tabStates = emptyTabStates(),
                order = "",
                selectedCompany = initialCompany,
                selectedYear = null,
                selectedHobbyCategory = HobbyAllCategory.id,
                selectedAlbumCategory = AlbumSubCategory.ALL,
                selectedElementsCategory = ElementsSubCategory.ALL,
                selectedAttribute = 0,
                availableCompanies = if (initialCompany != null) listOf(initialCompany) else emptyList(),
                availableYears = emptyList()
            )
        }
        if (shouldSubmit) {
            loadTabFirstPage(tab, sessionId, isRefresh = false)
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
        if (newText.isBlank()) {
            autoFillJob?.cancel()
            cancelAllTabJobs()
            _uiState.update {
                it.copy(
                    keyword = newText,
                    isSubmitted = false,
                    suggestions = emptyList(),
                    isSuggestionsVisible = false,
                    tabStates = emptyTabStates()
                )
            }
            return
        }
        _uiState.update { it.copy(keyword = newText, isSubmitted = false) }
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
        cancelAllTabJobs()
        _uiState.update {
            it.copy(
                keyword = "",
                isSubmitted = false,
                suggestions = emptyList(),
                isSuggestionsVisible = false,
                tabStates = emptyTabStates()
            )
        }
    }

    fun onSelectTab(tab: SearchTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update {
            it.copy(
                selectedTab = tab,
                isSuggestionsVisible = false
            )
        }
        val state = _uiState.value
        if (state.isSubmitted && state.keyword.isNotBlank()) {
            ensureTabLoaded(tab)
        } else {
            loadHotwordsForTab(tab)
        }
        if (tab == SearchTab.WORKS_ELEMENT) {
            loadElementAttributes(state.selectedElementsCategory.categoryId)
        }
    }

    fun onHobbyCategoryChange(categoryId: Int) {
        if (_uiState.value.selectedHobbyCategory == categoryId) return
        _uiState.update { it.copy(selectedHobbyCategory = categoryId) }
        reloadTabIfSubmitted(SearchTab.HOBBY)
    }

    fun onElementsCategoryChange(subCategory: ElementsSubCategory) {
        if (_uiState.value.selectedElementsCategory == subCategory) return
        _uiState.update { it.copy(selectedElementsCategory = subCategory, selectedAttribute = 0) }
        loadElementAttributes(subCategory.categoryId)
        reloadTabIfSubmitted(SearchTab.WORKS_ELEMENT)
    }

    fun onAttributeChange(attributeId: Int) {
        if (_uiState.value.selectedAttribute == attributeId) return
        _uiState.update { it.copy(selectedAttribute = attributeId) }
        reloadTabIfSubmitted(SearchTab.WORKS_ELEMENT)
    }

    fun onAlbumCategoryChange(subCategory: AlbumSubCategory) {
        if (_uiState.value.selectedAlbumCategory == subCategory) return
        _uiState.update { it.copy(selectedAlbumCategory = subCategory) }
        reloadTabIfSubmitted(SearchTab.ALBUM)
    }

    fun onSearch(targetKeyword: String? = null) {
        val searchWord = (targetKeyword ?: _uiState.value.keyword).trim()
        if (searchWord.isBlank()) return

        cancelAllTabJobs()
        val sessionId = _uiState.value.searchSessionId + 1
        val tab = _uiState.value.selectedTab
        _uiState.update {
            it.copy(
                keyword = searchWord,
                isSubmitted = true,
                isSuggestionsVisible = false,
                searchSessionId = sessionId,
                tabStates = emptyTabStates(),
                availableCompanies = emptyList(),
                availableYears = emptyList()
            )
        }
        viewModelScope.launch {
            searchRepository.addSearchHistory(searchWord)
        }
        loadSuggestions(searchWord, debounce = false)
        loadTabFirstPage(tab, sessionId, isRefresh = false)
    }

    fun onSortChange(newOrder: String) {
        if (_uiState.value.order == newOrder) return
        _uiState.update { it.copy(order = newOrder) }
        val tab = _uiState.value.selectedTab
        if (isTabFilterable(tab)) {
            reloadTabIfSubmitted(tab)
        }
    }

    fun onFilterChange(company: SearchCompanyAggregate?, year: String?) {
        _uiState.update {
            it.copy(
                selectedCompany = company,
                selectedYear = year
            )
        }
        reloadTabIfSubmitted(SearchTab.HOBBY)
    }

    fun onRefresh(tab: SearchTab = _uiState.value.selectedTab) {
        val state = _uiState.value
        if (!state.isSubmitted || state.keyword.isBlank()) return
        val tabState = state.tabStates[tab] ?: return
        if (tabState.isLoading || tabState.isRefreshing) return
        loadTabFirstPage(tab, state.searchSessionId, isRefresh = true)
    }

    fun onLoadMore(tab: SearchTab = _uiState.value.selectedTab) {
        val state = _uiState.value
        if (!state.isSubmitted || state.keyword.isBlank()) return
        val tabState = state.tabStates[tab] ?: return
        if (!tabState.isLoaded || !tabState.hasMore) return
        if (tabState.isLoading || tabState.isRefreshing || tabState.isLoadingMore) return

        val nextPage = tabState.page + 1
        updateTabState(tab) { it.copy(isLoadingMore = true) }

        val sessionId = state.searchSessionId
        val keyword = state.keyword
        // 冻结该 Tab 归属的筛选/排序快照，避免全局字段跨分类泄漏（按 Tab 存储留给 #131）
        val order = state.order.takeIf { tab == SearchTab.HOBBY || tab == SearchTab.ALBUM }
        val company = state.selectedCompany?.nodeId.takeIf { tab == SearchTab.HOBBY }
        val releaseYear = state.selectedYear.takeIf { tab == SearchTab.HOBBY }
        val attribute = state.selectedAttribute
        val targetCategory = resolveSearchCategory(
            tab,
            state.selectedHobbyCategory,
            state.selectedAlbumCategory,
            state.selectedElementsCategory
        )

        tabJobs[tab]?.cancel()
        tabJobs[tab] = viewModelScope.launch {
            if (tab == SearchTab.USER) {
                val result = searchRepository.searchUsers(
                    keyword = keyword,
                    page = nextPage,
                    pageSize = 10
                )
                result.onSuccess { payload ->
                    if (!isCurrentSession(sessionId)) return@onSuccess
                    val newUsers = payload.userList.orEmpty()
                    updateTabState(tab) { current ->
                        current.copy(
                            userItems = current.userItems + newUsers,
                            page = nextPage,
                            totalCount = current.totalCount + newUsers.size,
                            hasMore = newUsers.size >= 10,
                            isLoadingMore = false
                        )
                    }
                }.onFailure {
                    if (!isCurrentSession(sessionId)) return@onFailure
                    updateTabState(tab) { it.copy(isLoadingMore = false) }
                }
            } else {
                val result = searchRepository.search(
                    category = targetCategory,
                    itemType = tab.itemType,
                    keyword = keyword,
                    page = nextPage,
                    pageSize = 30,
                    order = order,
                    company = company,
                    releaseYear = releaseYear,
                    attribute = attribute.takeIf { tab == SearchTab.WORKS_ELEMENT },
                    aggregate = false
                )
                result.onSuccess { payload ->
                    if (!isCurrentSession(sessionId)) return@onSuccess
                    val newItems = payload.result.orEmpty()
                    updateTabState(tab) { current ->
                        val pageCount = payload.query?.pageCount ?: current.pageCount
                        current.copy(
                            items = current.items + newItems,
                            page = nextPage,
                            totalCount = payload.query?.totalCount ?: current.totalCount,
                            pageCount = pageCount,
                            hasMore = nextPage < pageCount && newItems.isNotEmpty(),
                            isLoadingMore = false
                        )
                    }
                }.onFailure {
                    if (!isCurrentSession(sessionId)) return@onFailure
                    updateTabState(tab) { it.copy(isLoadingMore = false) }
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

    // ── 分类状态与请求编排 ──────────────────────────────────────────

    private fun emptyTabStates(): Map<SearchTab, TabSearchState> =
        SearchTab.entries.associateWith { TabSearchState() }

    private fun cancelAllTabJobs() {
        tabJobs.values.forEach { it.cancel() }
        tabJobs.clear()
    }

    private fun isCurrentSession(sessionId: Int): Boolean =
        _uiState.value.searchSessionId == sessionId

    private fun updateTabState(tab: SearchTab, transform: (TabSearchState) -> TabSearchState) {
        _uiState.update { state ->
            val current = state.tabStates[tab] ?: TabSearchState()
            state.copy(tabStates = state.tabStates + (tab to transform(current)))
        }
    }

    /** 懒加载：仅当目标 Tab 尚未加载且没有进行中的请求时拉取第 1 页。 */
    private fun ensureTabLoaded(tab: SearchTab) {
        val tabState = _uiState.value.tabStates[tab] ?: return
        if (tabState.isLoaded || tabState.isLoading || tabState.isRefreshing) return
        loadTabFirstPage(tab, _uiState.value.searchSessionId, isRefresh = false)
    }

    /**
     * 筛选/排序变更：只重置并重载其归属分类的第 1 页，其他分类状态与请求不受影响。
     * 未提交搜索时仅更新筛选字段，不发请求。
     */
    private fun reloadTabIfSubmitted(tab: SearchTab) {
        val state = _uiState.value
        if (!state.isSubmitted || state.keyword.isBlank()) return
        _uiState.update { it.copy(tabStates = it.tabStates + (tab to TabSearchState())) }
        loadTabFirstPage(tab, state.searchSessionId, isRefresh = false)
    }

    private fun loadTabFirstPage(tab: SearchTab, sessionId: Int, isRefresh: Boolean) {
        tabJobs[tab]?.cancel()

        if (isRefresh) {
            updateTabState(tab) {
                it.copy(isRefreshing = true, isLoadingMore = false, errorMessage = null)
            }
        } else {
            updateTabState(tab) {
                it.copy(
                    isLoading = true,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    page = 1,
                    hasMore = false
                )
            }
        }

        val state = _uiState.value
        val keyword = state.keyword
        // 冻结该 Tab 归属的筛选/排序快照，避免全局字段跨分类泄漏（按 Tab 存储留给 #131）
        val order = state.order.takeIf { tab == SearchTab.HOBBY || tab == SearchTab.ALBUM }
        val company = state.selectedCompany?.nodeId.takeIf { tab == SearchTab.HOBBY }
        val releaseYear = state.selectedYear.takeIf { tab == SearchTab.HOBBY }
        val attribute = state.selectedAttribute
        val targetCategory = resolveSearchCategory(
            tab,
            state.selectedHobbyCategory,
            state.selectedAlbumCategory,
            state.selectedElementsCategory
        )

        tabJobs[tab] = viewModelScope.launch {
            if (tab == SearchTab.USER) {
                val result = searchRepository.searchUsers(
                    keyword = keyword,
                    page = 1,
                    pageSize = 10
                )
                result.onSuccess { payload ->
                    if (!isCurrentSession(sessionId)) return@onSuccess
                    val userList = payload.userList.orEmpty()
                    updateTabState(tab) {
                        it.copy(
                            userItems = userList,
                            items = emptyList(),
                            page = 1,
                            totalCount = userList.size,
                            pageCount = 1,
                            hasMore = userList.size >= 10,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            isLoaded = true
                        )
                    }
                }.onFailure { error ->
                    if (!isCurrentSession(sessionId)) return@onFailure
                    updateTabState(tab) {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = error.message ?: "搜索失败，请稍后重试"
                        )
                    }
                }
            } else {
                val result = searchRepository.search(
                    category = targetCategory,
                    itemType = tab.itemType,
                    keyword = keyword,
                    page = 1,
                    pageSize = 30,
                    order = order,
                    company = company,
                    releaseYear = releaseYear,
                    attribute = attribute.takeIf { tab == SearchTab.WORKS_ELEMENT },
                    aggregate = true
                )
                result.onSuccess { payload ->
                    if (!isCurrentSession(sessionId)) return@onSuccess
                    val resultItems = payload.result.orEmpty()
                    val total = payload.query?.totalCount ?: resultItems.size
                    val pageCount = payload.query?.pageCount ?: 1
                    val companies = if (tab == SearchTab.HOBBY) {
                        payload.companys?.filter { it.docCount > 0 && !it.itemName.isNullOrBlank() }.orEmpty()
                    } else null
                    val years = if (tab == SearchTab.HOBBY) {
                        payload.releaseYear?.filter { it.docCount > 0 && !it.keyAsString.isNullOrBlank() }.orEmpty()
                    } else null
                    _uiState.update { current ->
                        val tabState = current.tabStates[tab] ?: TabSearchState()
                        current.copy(
                            tabStates = current.tabStates + (tab to tabState.copy(
                                items = resultItems,
                                userItems = emptyList(),
                                page = 1,
                                totalCount = total,
                                pageCount = pageCount,
                                hasMore = 1 < pageCount && resultItems.isNotEmpty(),
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                errorMessage = null,
                                isLoaded = true
                            )),
                            availableCompanies = companies ?: current.availableCompanies,
                            availableYears = years ?: current.availableYears
                        )
                    }
                }.onFailure { error ->
                    if (!isCurrentSession(sessionId)) return@onFailure
                    updateTabState(tab) {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = error.message ?: "搜索失败，请稍后重试"
                        )
                    }
                }
            }
        }
    }
}
