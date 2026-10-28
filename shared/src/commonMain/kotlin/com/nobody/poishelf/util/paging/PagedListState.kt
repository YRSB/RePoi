package com.nobody.poishelf.util.paging

/**
 * 分页加载阶段枚举。
 */
enum class LoadStage {
    Idle,
    InitialLoading,
    Refreshing,
    Appending
}

/**
 * 分页列表状态数据模型。
 *
 * 封装分页数据列表、互斥加载阶段与首尾错误，同时暴露便捷只读属性供 UI 消费。
 *
 * @param items 当前已加载的所有条目列表
 * @param stage 当前互斥加载阶段
 * @param hasMore 是否还有后续数据
 * @param page 当前已加载到的页码（基于整型页码时有效）
 * @param initialError 首屏装载或下拉刷新错误提示
 * @param appendError 尾部加载更多错误提示
 */
data class PagedListState<T>(
    val items: List<T> = emptyList(),
    val stage: LoadStage = LoadStage.Idle,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val initialError: String? = null,
    val appendError: String? = null,
    val isSilent: Boolean = false
) {
    /** 是否处于首屏初始装载阶段 */
    val isLoading: Boolean get() = stage == LoadStage.InitialLoading

    /** 是否处于下拉刷新阶段 */
    val isRefreshing: Boolean get() = stage == LoadStage.Refreshing

    /** 是否处于加载更多追加阶段（静默翻页时自动抑制可见追加状态） */
    val isLoadingMore: Boolean get() = stage == LoadStage.Appending && !isSilent

    /** 统一错误信息（首屏或刷新错误），与现有 Compose 属性命名保持兼容 */
    val errorMessage: String? get() = initialError

    /** 追加更多错误信息，与现有 Compose 属性命名保持兼容 */
    val loadMoreError: String? get() = appendError

    /** 首屏为空且发生错误 */
    val isInitialError: Boolean get() = initialError != null && items.isEmpty()

    /** 列表是否已全部加载完成 */
    val isEndReached: Boolean get() = !hasMore
}
