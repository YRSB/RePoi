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

/**
 * 周边分类的筛选快照：排序、分类、厂商与年份随 Tab 独立保存。
 * 厂商/年份候选列表也归属该快照，避免不同分类串用聚合结果。
 */
data class HobbySearchFilterState(
    val order: String = "",
    val categoryId: Int = HobbyAllCategory.id,
    val company: SearchCompanyAggregate? = null,
    val year: String? = null,
    val availableCompanies: List<SearchCompanyAggregate> = emptyList(),
    val availableYears: List<SearchYearAggregate> = emptyList()
)

/** 相册分类的筛选快照。 */
data class AlbumSearchFilterState(
    val order: String = "",
    val category: AlbumSubCategory = AlbumSubCategory.ALL
)

/** 角色/要素分类的筛选快照。 */
data class ElementsSearchFilterState(
    val category: ElementsSubCategory = ElementsSubCategory.ALL,
    val attribute: Int = 0
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

    // 排序与筛选：每个可筛分类各自持有独立快照，互不覆盖
    val hobbyFilter: HobbySearchFilterState = HobbySearchFilterState(),
    val albumFilter: AlbumSearchFilterState = AlbumSearchFilterState(),
    val elementsFilter: ElementsSearchFilterState = ElementsSearchFilterState(),
    val hobbyCategories: List<CategoryItem> = emptyList(),
    val attributeMap: Map<Int, List<AttributeItem>> = emptyMap(),
    val isLoadingAttributes: Boolean = false
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

    // 筛选只读派生访问器：按活动分类从对应快照取值，避免 UI 与快照双真相源
    val order: String
        get() = when (selectedTab) {
            SearchTab.HOBBY -> hobbyFilter.order
            SearchTab.ALBUM -> albumFilter.order
            else -> ""
        }
    val selectedCompany: SearchCompanyAggregate? get() = hobbyFilter.company
    val selectedYear: String? get() = hobbyFilter.year
    val selectedHobbyCategory: Int get() = hobbyFilter.categoryId
    val availableCompanies: List<SearchCompanyAggregate> get() = hobbyFilter.availableCompanies
    val availableYears: List<SearchYearAggregate> get() = hobbyFilter.availableYears
    val selectedAlbumCategory: AlbumSubCategory get() = albumFilter.category
    val selectedElementsCategory: ElementsSubCategory get() = elementsFilter.category
    val selectedAttribute: Int get() = elementsFilter.attribute

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
                hobbyFilter = HobbySearchFilterState(
                    company = initialCompany,
                    availableCompanies = if (initialCompany != null) listOf(initialCompany) else emptyList()
                ),
                albumFilter = AlbumSearchFilterState(),
                elementsFilter = ElementsSearchFilterState()
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
            loadElementAttributes(state.elementsFilter.category.categoryId)
        }
    }

    fun onHobbyCategoryChange(categoryId: Int) {
        if (_uiState.value.hobbyFilter.categoryId == categoryId) return
        _uiState.update { it.copy(hobbyFilter = it.hobbyFilter.copy(categoryId = categoryId)) }
        reloadTabIfSubmitted(SearchTab.HOBBY)
    }

    fun onElementsCategoryChange(subCategory: ElementsSubCategory) {
        if (_uiState.value.elementsFilter.category == subCategory) return
        _uiState.update {
            it.copy(elementsFilter = it.elementsFilter.copy(category = subCategory, attribute = 0))
        }
        loadElementAttributes(subCategory.categoryId)
        reloadTabIfSubmitted(SearchTab.WORKS_ELEMENT)
    }

    fun onAttributeChange(attributeId: Int) {
        if (_uiState.value.elementsFilter.attribute == attributeId) return
        _uiState.update { it.copy(elementsFilter = it.elementsFilter.copy(attribute = attributeId)) }
        reloadTabIfSubmitted(SearchTab.WORKS_ELEMENT)
    }

    fun onAlbumCategoryChange(subCategory: AlbumSubCategory) {
        if (_uiState.value.albumFilter.category == subCategory) return
        _uiState.update { it.copy(albumFilter = it.albumFilter.copy(category = subCategory)) }
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
                tabStates = emptyTabStates()
            )
        }
        viewModelScope.launch {
            searchRepository.addSearchHistory(searchWord)
        }
        loadSuggestions(searchWord, debounce = false)
        loadTabFirstPage(tab, sessionId, isRefresh = false)
    }

    fun onSortChange(newOrder: String) {
        val state = _uiState.value
        val tab = state.selectedTab
        val currentOrder = when (tab) {
            SearchTab.HOBBY -> state.hobbyFilter.order
            SearchTab.ALBUM -> state.albumFilter.order
            else -> return
        }
        if (currentOrder == newOrder) return
        _uiState.update { current ->
            when (tab) {
                SearchTab.HOBBY -> current.copy(hobbyFilter = current.hobbyFilter.copy(order = newOrder))
                SearchTab.ALBUM -> current.copy(albumFilter = current.albumFilter.copy(order = newOrder))
                else -> current
            }
        }
        reloadTabIfSubmitted(tab)
    }

    fun onFilterChange(company: SearchCompanyAggregate?, year: String?) {
        val current = _uiState.value.hobbyFilter
        if (current.company == company && current.year == year) return
        _uiState.update { it.copy(hobbyFilter = it.hobbyFilter.copy(company = company, year = year)) }
        reloadTabIfSubmitted(SearchTab.HOBBY)
    }

    /**
     * 重置当前活动可筛分类的筛选值（保留已加载的厂商/年份候选），只重载该分类一次。
     * 相比逐个调用 setter，可避免连续多次请求。
     */
    fun onResetFilters() {
        val state = _uiState.value
        val tab = state.selectedTab
        val changed = when (tab) {
            SearchTab.HOBBY -> with(state.hobbyFilter) {
                order.isNotEmpty() || categoryId != HobbyAllCategory.id || company != null || year != null
            }
            SearchTab.ALBUM -> state.albumFilter != AlbumSearchFilterState()
            SearchTab.WORKS_ELEMENT -> state.elementsFilter != ElementsSearchFilterState()
            else -> false
        }
        if (!changed) return
        _uiState.update { current ->
            when (tab) {
                SearchTab.HOBBY -> current.copy(
                    hobbyFilter = current.hobbyFilter.copy(
                        order = "",
                        categoryId = HobbyAllCategory.id,
                        company = null,
                        year = null
                    )
                )
                SearchTab.ALBUM -> current.copy(albumFilter = AlbumSearchFilterState())
                SearchTab.WORKS_ELEMENT -> current.copy(elementsFilter = ElementsSearchFilterState())
                else -> current
            }
        }
        if (tab == SearchTab.WORKS_ELEMENT) {
            loadElementAttributes(ElementsSubCategory.ALL.categoryId)
        }
        reloadTabIfSubmitted(tab)
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
        // 冻结目标 Tab 归属的筛选快照，后台重载也不会串用其他分类的条件
        val hobby = state.hobbyFilter
        val album = state.albumFilter
        val elements = state.elementsFilter
        val order = when (tab) {
            SearchTab.HOBBY -> hobby.order
            SearchTab.ALBUM -> album.order
            else -> null
        }
        val company = hobby.company?.nodeId.takeIf { tab == SearchTab.HOBBY }
        val releaseYear = hobby.year.takeIf { tab == SearchTab.HOBBY }
        val targetCategory = resolveSearchCategory(
            tab,
            hobby.categoryId,
            album.category,
            elements.category
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
                    attribute = elements.attribute.takeIf { tab == SearchTab.WORKS_ELEMENT },
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
        // 冻结目标 Tab 归属的筛选快照，后台重载也不会串用其他分类的条件
        val hobby = state.hobbyFilter
        val album = state.albumFilter
        val elements = state.elementsFilter
        val order = when (tab) {
            SearchTab.HOBBY -> hobby.order
            SearchTab.ALBUM -> album.order
            else -> null
        }
        val company = hobby.company?.nodeId.takeIf { tab == SearchTab.HOBBY }
        val releaseYear = hobby.year.takeIf { tab == SearchTab.HOBBY }
        val targetCategory = resolveSearchCategory(
            tab,
            hobby.categoryId,
            album.category,
            elements.category
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
                    attribute = elements.attribute.takeIf { tab == SearchTab.WORKS_ELEMENT },
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
                            // 厂商/年份候选只归属周边快照
                            hobbyFilter = current.hobbyFilter.copy(
                                availableCompanies = companies ?: current.hobbyFilter.availableCompanies,
                                availableYears = years ?: current.hobbyFilter.availableYears
                            )
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
