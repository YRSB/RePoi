package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.ui.board.PostsBoardOptions

/**
 * 首页讨论板块状态（issue #158）：与动态板块完全独立、按板块懒加载。
 *
 * 归属首页 ViewModel 而不是 loader：讨论板块现状是手写 Job + 页码分页（与专区详情一致），
 * 行为需与详情页保持同一套语义（`hasMore = 原始页大小 >= PAGE_SIZE`、按 id 去重、置顶切分复用
 * [PostsBoardOptions.partition]），不额外引入分页 loader 以免两端漂移。
 *
 * @param order 排序档位（[PostsBoardOptions.ORDERS] 的键）
 * @param isLoaded 是否已请求过首屏成功——懒加载哨兵，false 表示尚未拿到数据（首屏失败后切回会重试）
 * @param pinnedPosts 仅单专区有值；聚合态（全部）不切分置顶、始终为空
 */
data class HomePostsState(
    val order: String = PostsBoardOptions.DEFAULT_ORDER,
    val items: List<PostItem> = emptyList(),
    val pinnedPosts: List<PostItem> = emptyList(),
    val page: Int = 1,
    val isLoaded: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
) {
    /** 作用域切换时清空内容与分页游标，但保留用户选择的排序（按板块记忆的筛选状态）。 */
    fun resetForScope(): HomePostsState = copy(
        items = emptyList(),
        pinnedPosts = emptyList(),
        page = 1,
        isLoaded = false,
        isInitialLoading = false,
        isLoadingMore = false,
        hasMore = true,
        errorMessage = null,
        loadMoreError = null
    )
}
