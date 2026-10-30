package com.nobody.repoi.ui.follow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.local.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 关注屏内页签（条目动态/用户动态）。
 */
enum class FollowTab(val apiType: String, val label: String) {
    ITEMS("item", "条目动态"),
    USERS("user", "用户动态")
}

data class FollowUiState(
    val selectedTab: FollowTab = FollowTab.ITEMS,
    val selectedFilter: String = "any",
    val items: List<ActionItem> = emptyList(),
    val lastId: Long = 0L,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val loginRequired: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

/**
 * 关注屏状态机（顶层关注目的地，复用关注条目/用户接口）。
 */
class FollowViewModel(
    private val followRepository: FollowRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    val filters = listOf(
        Pair("any", "全部"),
        Pair("comment", "评论"),
        Pair("all", "全部情报"),
        Pair("confirm", "制作"),
        Pair("official_pic", "更图"),
        Pair("preorder", "开订"),
        Pair("delay", "延期"),
        Pair("release", "出荷"),
        Pair("reorder", "再版")
    )

    private val _uiState = MutableStateFlow(FollowUiState())
    val uiState: StateFlow<FollowUiState> = _uiState.asStateFlow()

    private var feedJob: Job? = null

    var savedScrollIndex: Int = 0
        private set
    var savedScrollOffset: Int = 0
        private set

    fun saveScrollPosition(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    fun resetScrollPosition() {
        savedScrollIndex = 0
        savedScrollOffset = 0
    }

    fun loadInitial() {
        if (tokenManager.currentToken.isBlank()) {
            feedJob?.cancel()
            resetScrollPosition()
            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    loginRequired = true,
                    errorMessage = null
                )
            }
            return
        }
        if (!uiState.value.isInitialLoading && uiState.value.items.isNotEmpty()) return
        loadFeedPage(refreshing = false, appending = false)
    }

    fun switchTab(tab: FollowTab) {
        if (tab == uiState.value.selectedTab && uiState.value.items.isNotEmpty()) return
        feedJob?.cancel()
        resetScrollPosition()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                items = emptyList(),
                lastId = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null,
                loginRequired = false
            )
        }
        loadFeedPage(refreshing = false, appending = false)
    }

    fun selectFilter(filter: String) {
        if (uiState.value.selectedTab != FollowTab.ITEMS) return
        if (filter == uiState.value.selectedFilter) return
        feedJob?.cancel()
        resetScrollPosition()
        _uiState.update {
            it.copy(
                selectedFilter = filter,
                items = emptyList(),
                lastId = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            )
        }
        loadFeedPage(refreshing = false, appending = false)
    }

    fun refresh() {
        if (uiState.value.isRefreshing || uiState.value.loginRequired) return
        resetScrollPosition()
        loadFeedPage(refreshing = true, appending = false)
    }

    fun loadMore() {
        val state = uiState.value
        if (state.isLoadingMore || !state.hasMore || state.isInitialLoading || state.isRefreshing) return
        loadFeedPage(refreshing = false, appending = true)
    }

    fun retry() {
        _uiState.update { it.copy(loginRequired = false) }
        loadInitial()
    }

    private fun loadFeedPage(refreshing: Boolean, appending: Boolean) {
        if (tokenManager.currentToken.isBlank()) {
            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    loginRequired = true
                )
            }
            return
        }
        feedJob?.cancel()
        val snapshot = uiState.value
        val requestTab = snapshot.selectedTab
        val requestFilter = snapshot.selectedFilter
        val requestLastId = if (refreshing || !appending) 0L else snapshot.lastId
        feedJob = viewModelScope.launch {
            if (refreshing) {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null, loadMoreError = null) }
            } else if (!appending) {
                _uiState.update {
                    it.copy(isInitialLoading = true, errorMessage = null, loadMoreError = null, loginRequired = false)
                }
            } else {
                _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            }
            val (actionType, subType) = queryParams(requestTab, requestFilter)
            val result = followRepository.getFollowFeed(
                type = requestTab.apiType,
                actionType = actionType,
                subType = subType,
                lastId = requestLastId
            )
            val current = uiState.value
            if (current.selectedTab != requestTab || current.selectedFilter != requestFilter) return@launch
            result.fold(
                onSuccess = { page ->
                    _uiState.update { state ->
                        val merged = if (appending) {
                            val known = state.items.map { item -> "${item.id}_${item.actionDate}" }.toSet()
                            state.items + page.items.filter { "${it.id}_${it.actionDate}" !in known }
                        } else {
                            page.items
                        }
                        state.copy(
                            items = merged,
                            lastId = if (page.lastId > 0L) page.lastId else state.lastId,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            hasMore = page.hasMore,
                            errorMessage = null,
                            loadMoreError = null,
                            loginRequired = false
                        )
                    }
                },
                onFailure = { error ->
                    val loginExpired = (error as? ApiException)?.code == 4001
                    _uiState.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = if (!appending) (error.message ?: "加载失败") else it.errorMessage,
                            loadMoreError = if (appending) (error.message ?: "翻页加载失败，点击重试") else null,
                            loginRequired = loginExpired
                        )
                    }
                }
            )
        }
    }

    /**
     * 条目动态筛选映射（沿用原 App 分支：any 去 subType 且 actionType=0）。
     */
    internal fun queryParams(tab: FollowTab, filter: String): Pair<Int?, String?> {
        if (tab != FollowTab.ITEMS) return Pair(null, null)
        return when (filter) {
            "any" -> Pair(0, null)
            "comment" -> Pair(101, null)
            else -> Pair(401, filter)
        }
    }
}
