package com.nobody.repoi.util.paging

enum class LoadStage {
    Idle,
    InitialLoading,
    Refreshing,
    Appending
}

data class PagedListState<T>(
    val items: List<T> = emptyList(),
    val stage: LoadStage = LoadStage.Idle,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val initialError: String? = null,
    val appendError: String? = null,
    val isSilent: Boolean = false
) {

    val isLoading: Boolean get() = stage == LoadStage.InitialLoading

    val isRefreshing: Boolean get() = stage == LoadStage.Refreshing

    val isLoadingMore: Boolean get() = stage == LoadStage.Appending && !isSilent

    val errorMessage: String? get() = initialError

    val loadMoreError: String? get() = appendError

    val isInitialError: Boolean get() = initialError != null && items.isEmpty()

    val isEndReached: Boolean get() = !hasMore
}
