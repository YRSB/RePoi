package com.nobody.repoi.ui.search

import com.nobody.repoi.data.remote.model.SearchCompanyAggregate
import com.nobody.repoi.data.remote.model.SearchResultItem
import com.nobody.repoi.data.remote.model.SearchUserItem
import com.nobody.repoi.data.remote.model.SearchYearAggregate

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
