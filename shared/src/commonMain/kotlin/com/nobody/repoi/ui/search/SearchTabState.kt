package com.nobody.repoi.ui.search

import com.nobody.repoi.data.remote.model.SearchCompanyAggregate
import com.nobody.repoi.data.remote.model.SearchResultItem
import com.nobody.repoi.data.remote.model.SearchUserItem
import com.nobody.repoi.data.remote.model.SearchYearAggregate

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

data class HobbySearchFilterState(
    val order: String = "",
    val categoryId: Int = HobbyAllCategory.id,
    val company: SearchCompanyAggregate? = null,
    val year: String? = null,
    val availableCompanies: List<SearchCompanyAggregate> = emptyList(),
    val availableYears: List<SearchYearAggregate> = emptyList()
)

data class AlbumSearchFilterState(
    val order: String = "",
    val category: AlbumSubCategory = AlbumSubCategory.ALL
)

data class ElementsSearchFilterState(
    val category: ElementsSubCategory = ElementsSubCategory.ALL,
    val attribute: Int = 0
)
