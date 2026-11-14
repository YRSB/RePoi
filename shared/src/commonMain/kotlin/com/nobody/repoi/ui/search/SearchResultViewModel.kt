package com.nobody.repoi.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.remote.model.AttributeItem
import com.nobody.repoi.data.remote.model.SearchCompanyAggregate
import com.nobody.repoi.data.remote.model.SearchResultItem
import com.nobody.repoi.data.remote.model.SearchUserItem
import com.nobody.repoi.data.remote.model.SearchYearAggregate
import com.nobody.repoi.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 搜索结果页状态模型。
 *
 * 专注管理 8 个分类（[SearchTab]）的搜索结果浏览、独立筛选快照与分页缓存，
 * 不包含搜索历史、热词、联想补全及草稿编辑等落地页职责。
 */
data class SearchResultUiState(
    val keyword: String = "",
    val companyNodeId: Long? = null,
    val companyName: String? = null,
    val selectedTab: SearchTab = SearchTab.HOBBY,
    val tabStates: Map<SearchTab, TabSearchState> = SearchTab.entries.associateWith { TabSearchState() },
    val searchSessionId: Int = 0,
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

    /** 指定分类的排序键；无排序能力的分类返回空串。 */
    fun orderFor(tab: SearchTab): String = when (tab) {
        SearchTab.HOBBY -> hobbyFilter.order
        SearchTab.ALBUM -> albumFilter.order
        else -> ""
    }

    /** 指定分类是否存在已激活的筛选或排序项（顶栏 Badge 按 Pager 当前页取）。 */
    fun isFilterActiveFor(tab: SearchTab): Boolean = com.nobody.repoi.ui.search.isFilterActive(
        tab = tab,
        order = orderFor(tab),
        hobbyCategory = hobbyFilter.categoryId,
        hasCompany = hobbyFilter.company != null,
        hasYear = hobbyFilter.year != null,
        albumCategory = albumFilter.category,
        elementsCategory = elementsFilter.category,
        attribute = elementsFilter.attribute
    )

    // 筛选只读派生访问器：按活动分类从对应快照取值，避免 UI 与快照双真相源
    val order: String get() = orderFor(selectedTab)
    val selectedCompany: SearchCompanyAggregate? get() = hobbyFilter.company
    val selectedYear: String? get() = hobbyFilter.year
    val selectedHobbyCategory: Int get() = hobbyFilter.categoryId
    val availableCompanies: List<SearchCompanyAggregate> get() = hobbyFilter.availableCompanies
    val availableYears: List<SearchYearAggregate> get() = hobbyFilter.availableYears
    val selectedAlbumCategory: AlbumSubCategory get() = albumFilter.category
    val selectedElementsCategory: ElementsSubCategory get() = elementsFilter.category
    val selectedAttribute: Int get() = elementsFilter.attribute

    val isFilterActive: Boolean get() = isFilterActiveFor(selectedTab)

    /** 顶栏展示文案：优先显示关键词，若关键词为空则显示厂商名称，均无时为空串。 */
    val displayTitle: String get() = keyword.ifBlank { companyName.orEmpty() }
}

/**
 * 专职搜索结果页 ViewModel：
 * 专注于 8 个分类搜索结果的按需加载、Tab 切换缓存复用、单 Tab 刷新/分页与独立筛选。
 */
class SearchResultViewModel(
    private val searchRepository: SearchRepository,
    private val categoryManager: CategoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchResultUiState())
    val uiState: StateFlow<SearchResultUiState> = _uiState.asStateFlow()

    private val tabJobs = mutableMapOf<SearchTab, Job>()
    private var currentParam: SearchResultNavParam? = null

    init {
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

    /**
     * 初始参数注入与导航状态同步。
     * 若入参相同则保持幂等，避免多余重载。
     */
    fun initParam(param: SearchResultNavParam) {
        if (currentParam == param) return
        currentParam = param

        val tab = SearchTab.fromCategoryId(param.category)
        val initialCompany = if (param.companyNodeId != null && param.companyNodeId > 0) {
            SearchCompanyAggregate(
                nodeId = param.companyNodeId,
                itemName = param.companyName ?: "厂商",
                docCount = 0
            )
        } else null

        cancelAllTabJobs()
        val sessionId = _uiState.value.searchSessionId + 1
        _uiState.update {
            it.copy(
                keyword = param.keyword,
                companyNodeId = param.companyNodeId,
                companyName = param.companyName,
                selectedTab = tab,
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

        val shouldSearch = param.keyword.isNotBlank() || initialCompany != null
        if (shouldSearch) {
            loadTabFirstPage(tab, sessionId, isRefresh = false)
        }
        if (tab == SearchTab.WORKS_ELEMENT) {
            loadElementAttributes(ElementsSubCategory.ALL.categoryId)
        }
    }

    /**
     * 用户切换分类页签：若未加载则懒加载，已加载则复用缓存。
     */
    fun onSelectTab(tab: SearchTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        val state = _uiState.value
        if (state.keyword.isNotBlank() || state.hobbyFilter.company != null) {
            ensureTabLoaded(tab)
        }
        if (tab == SearchTab.WORKS_ELEMENT) {
            loadElementAttributes(state.elementsFilter.category.categoryId)
        }
    }

    /**
     * Pager 呈现某分类（滑入可见）时触发首屏懒加载。
     * 只拉取该分类数据，不改变当前选中分类。
     */
    fun onTabVisible(tab: SearchTab) {
        val state = _uiState.value
        if (state.keyword.isBlank() && state.hobbyFilter.company == null) return
        ensureTabLoaded(tab)
    }

    fun onHobbyCategoryChange(categoryId: Int) {
        if (_uiState.value.hobbyFilter.categoryId == categoryId) return
        _uiState.update { it.copy(hobbyFilter = it.hobbyFilter.copy(categoryId = categoryId)) }
        reloadTab(SearchTab.HOBBY)
    }

    fun onFilterChange(company: SearchCompanyAggregate?, year: String?) {
        val current = _uiState.value.hobbyFilter
        if (current.company == company && current.year == year) return
        _uiState.update { it.copy(hobbyFilter = it.hobbyFilter.copy(company = company, year = year)) }
        reloadTab(SearchTab.HOBBY)
    }

    fun onAlbumCategoryChange(subCategory: AlbumSubCategory) {
        if (_uiState.value.albumFilter.category == subCategory) return
        _uiState.update { it.copy(albumFilter = it.albumFilter.copy(category = subCategory)) }
        reloadTab(SearchTab.ALBUM)
    }

    fun onElementsCategoryChange(subCategory: ElementsSubCategory) {
        if (_uiState.value.elementsFilter.category == subCategory) return
        _uiState.update {
            it.copy(elementsFilter = it.elementsFilter.copy(category = subCategory, attribute = 0))
        }
        loadElementAttributes(subCategory.categoryId)
        reloadTab(SearchTab.WORKS_ELEMENT)
    }

    fun onAttributeChange(attributeId: Int) {
        if (_uiState.value.elementsFilter.attribute == attributeId) return
        _uiState.update { it.copy(elementsFilter = it.elementsFilter.copy(attribute = attributeId)) }
        reloadTab(SearchTab.WORKS_ELEMENT)
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
        reloadTab(tab)
    }

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
        reloadTab(tab)
    }

    fun onRefresh(tab: SearchTab = _uiState.value.selectedTab) {
        val state = _uiState.value
        if (state.keyword.isBlank() && state.hobbyFilter.company == null) return
        val tabState = state.tabStates[tab] ?: return
        if (tabState.isLoading || tabState.isRefreshing) return
        loadTabFirstPage(tab, state.searchSessionId, isRefresh = true)
    }

    fun onLoadMore(tab: SearchTab = _uiState.value.selectedTab) {
        val state = _uiState.value
        if (state.keyword.isBlank() && state.hobbyFilter.company == null) return
        val tabState = state.tabStates[tab] ?: return
        if (!tabState.isLoaded || !tabState.hasMore) return
        if (tabState.isLoading || tabState.isRefreshing || tabState.isLoadingMore) return

        val nextPage = tabState.page + 1
        updateTabState(tab) { it.copy(isLoadingMore = true) }

        val sessionId = state.searchSessionId
        val keyword = state.keyword
        val criteria = criteriaFor(state, tab)

        tabJobs[tab]?.cancel()
        tabJobs[tab] = viewModelScope.launch {
            requestTabPage(tab, page = nextPage, aggregate = false, keyword = keyword, criteria = criteria)
                .onSuccess { data ->
                    if (!isCurrentSession(sessionId)) return@onSuccess
                    updateTabState(tab) { current ->
                        if (tab == SearchTab.USER) {
                            current.copy(
                                userItems = current.userItems + data.users,
                                page = nextPage,
                                totalCount = current.totalCount + data.users.size,
                                hasMore = data.users.size >= 10,
                                isLoadingMore = false
                            )
                        } else {
                            val pageCount = data.pageCount ?: current.pageCount
                            current.copy(
                                items = current.items + data.items,
                                page = nextPage,
                                totalCount = data.totalCount ?: current.totalCount,
                                pageCount = pageCount,
                                hasMore = nextPage < pageCount && data.items.isNotEmpty(),
                                isLoadingMore = false
                            )
                        }
                    }
                }.onFailure {
                    if (!isCurrentSession(sessionId)) return@onFailure
                    updateTabState(tab) { it.copy(isLoadingMore = false) }
                }
        }
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

    // ── 内部辅助函数 ──────────────────────────────────────────

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

    private fun ensureTabLoaded(tab: SearchTab) {
        val tabState = _uiState.value.tabStates[tab] ?: return
        if (tabState.isLoaded || tabState.isLoading || tabState.isRefreshing) return
        loadTabFirstPage(tab, _uiState.value.searchSessionId, isRefresh = false)
    }

    private fun reloadTab(tab: SearchTab) {
        val state = _uiState.value
        if (state.keyword.isBlank() && state.hobbyFilter.company == null) return
        _uiState.update { it.copy(tabStates = it.tabStates + (tab to TabSearchState())) }
        loadTabFirstPage(tab, state.searchSessionId, isRefresh = false)
    }

    private data class TabSearchCriteria(
        val category: Int,
        val order: String?,
        val company: Long?,
        val releaseYear: String?,
        val attribute: Int?
    )

    private data class TabPageData(
        val items: List<SearchResultItem> = emptyList(),
        val users: List<SearchUserItem> = emptyList(),
        val totalCount: Int? = null,
        val pageCount: Int? = null,
        val companies: List<SearchCompanyAggregate>? = null,
        val years: List<SearchYearAggregate>? = null
    )

    private fun criteriaFor(state: SearchResultUiState, tab: SearchTab): TabSearchCriteria {
        val hobby = state.hobbyFilter
        return TabSearchCriteria(
            category = resolveSearchCategory(
                tab,
                hobby.categoryId,
                state.albumFilter.category,
                state.elementsFilter.category
            ),
            order = when (tab) {
                SearchTab.HOBBY -> hobby.order
                SearchTab.ALBUM -> state.albumFilter.order
                else -> null
            },
            company = hobby.company?.nodeId.takeIf { tab == SearchTab.HOBBY },
            releaseYear = hobby.year.takeIf { tab == SearchTab.HOBBY },
            attribute = state.elementsFilter.attribute.takeIf { tab == SearchTab.WORKS_ELEMENT }
        )
    }

    private suspend fun requestTabPage(
        tab: SearchTab,
        page: Int,
        aggregate: Boolean,
        keyword: String,
        criteria: TabSearchCriteria
    ): Result<TabPageData> {
        if (tab == SearchTab.USER) {
            return searchRepository.searchUsers(keyword = keyword, page = page, pageSize = 10)
                .map { payload -> TabPageData(users = payload.userList.orEmpty()) }
        }
        return searchRepository.search(
            category = criteria.category,
            itemType = tab.itemType,
            keyword = keyword,
            page = page,
            pageSize = 30,
            order = criteria.order,
            company = criteria.company,
            releaseYear = criteria.releaseYear,
            attribute = criteria.attribute,
            aggregate = aggregate
        ).map { payload ->
            TabPageData(
                items = payload.result.orEmpty(),
                totalCount = payload.query?.totalCount,
                pageCount = payload.query?.pageCount,
                companies = if (tab == SearchTab.HOBBY) {
                    payload.companys?.filter { it.docCount > 0 && !it.itemName.isNullOrBlank() }.orEmpty()
                } else null,
                years = if (tab == SearchTab.HOBBY) {
                    payload.releaseYear?.filter { it.docCount > 0 && !it.keyAsString.isNullOrBlank() }.orEmpty()
                } else null
            )
        }
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
        val criteria = criteriaFor(state, tab)

        tabJobs[tab] = viewModelScope.launch {
            requestTabPage(tab, page = 1, aggregate = true, keyword = keyword, criteria = criteria)
                .onSuccess { data ->
                    if (!isCurrentSession(sessionId)) return@onSuccess
                    if (tab == SearchTab.USER) {
                        updateTabState(tab) {
                            it.copy(
                                userItems = data.users,
                                items = emptyList(),
                                page = 1,
                                totalCount = data.users.size,
                                pageCount = 1,
                                hasMore = data.users.size >= 10,
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                errorMessage = null,
                                isLoaded = true
                            )
                        }
                    } else {
                        val pageCount = data.pageCount ?: 1
                        _uiState.update { current ->
                            val tabState = current.tabStates[tab] ?: TabSearchState()
                            current.copy(
                                tabStates = current.tabStates + (tab to tabState.copy(
                                    items = data.items,
                                    userItems = emptyList(),
                                    page = 1,
                                    totalCount = data.totalCount ?: data.items.size,
                                    pageCount = pageCount,
                                    hasMore = 1 < pageCount && data.items.isNotEmpty(),
                                    isLoading = false,
                                    isRefreshing = false,
                                    isLoadingMore = false,
                                    errorMessage = null,
                                    isLoaded = true
                                )),
                                hobbyFilter = current.hobbyFilter.copy(
                                    availableCompanies = data.companies
                                        ?: current.hobbyFilter.availableCompanies,
                                    availableYears = data.years ?: current.hobbyFilter.availableYears
                                )
                            )
                        }
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
