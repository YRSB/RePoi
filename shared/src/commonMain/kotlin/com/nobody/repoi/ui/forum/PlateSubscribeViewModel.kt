package com.nobody.repoi.ui.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.repository.ForumRepository
import com.nobody.repoi.data.remote.model.ForumItem
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

data class PlateSubscribeUiState(
    val selectedTab: Int = PlateSubscribeViewModel.TAB_FOCUS,
    val focusPlates: List<ForumItem> = emptyList(),
    val isFocusLoading: Boolean = false,
    val isFocusRefreshing: Boolean = false,
    val isFocusLoadingMore: Boolean = false,
    val focusHasMore: Boolean = true,
    val focusErrorMessage: String? = null,
    val focusLoadMoreError: String? = null,
    val unfocusPlates: List<ForumItem> = emptyList(),
    val isUnfocusLoading: Boolean = false,
    val isUnfocusRefreshing: Boolean = false,
    val isUnfocusLoadingMore: Boolean = false,
    val unfocusHasMore: Boolean = true,
    val unfocusErrorMessage: String? = null,
    val unfocusLoadMoreError: String? = null,
    val isSort: Boolean = false,
    val isSavingOrder: Boolean = false,
    val userLevel: Int = 0,
    val isLoggedIn: Boolean = false,
    val mutatingIds: Set<Long> = emptySet(),
    val actionMessage: String? = null,
    /** 关注关系或排序是否发生变更，用于返回讨论板主页时刷新动态 Tabs */
    val hasChanged: Boolean = false
)

/**
 * 版块订阅管理 ViewModel。
 * 管理已关注/未关注双 Tab、关注取关与已关注排序（上移/下移 + 保存）。
 */
class PlateSubscribeViewModel(
    private val forumRepository: ForumRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlateSubscribeUiState())
    val uiState: StateFlow<PlateSubscribeUiState> = _uiState.asStateFlow()

    private val focusLoader: PagedListLoader<ForumItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,
        keySelector = { it.id }
    ) { page, _ ->
        forumRepository.getFocusForums(forumPage = page)
    }

    private val unfocusLoader: PagedListLoader<ForumItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,
        keySelector = { it.id }
    ) { page, _ ->
        forumRepository.getUnfocusForums(forumPage = page)
    }

    init {
        focusLoader.state
            .onEach { listState ->
                _uiState.update { current ->
                    current.copy(
                        focusPlates = listState.items,
                        isFocusLoading = listState.isLoading,
                        isFocusRefreshing = listState.isRefreshing,
                        isFocusLoadingMore = listState.isLoadingMore,
                        focusHasMore = listState.hasMore,
                        focusErrorMessage = listState.errorMessage,
                        focusLoadMoreError = listState.loadMoreError
                    )
                }
            }
            .launchIn(viewModelScope)

        unfocusLoader.state
            .onEach { listState ->
                _uiState.update { current ->
                    current.copy(
                        unfocusPlates = listState.items,
                        isUnfocusLoading = listState.isLoading,
                        isUnfocusRefreshing = listState.isRefreshing,
                        isUnfocusLoadingMore = listState.isLoadingMore,
                        unfocusHasMore = listState.hasMore,
                        unfocusErrorMessage = listState.errorMessage,
                        unfocusLoadMoreError = listState.loadMoreError
                    )
                }
            }
            .launchIn(viewModelScope)

        userRepository.tokenFlow
            .map { it.isNotBlank() }
            .distinctUntilChanged()
            .onEach { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
                if (loggedIn) {
                    ensureUserLevel()
                } else {
                    _uiState.update { it.copy(userLevel = 0) }
                }
            }
            .launchIn(viewModelScope)

        focusLoader.loadInitial()
        unfocusLoader.loadInitial()
    }

    fun selectTab(tab: Int) {
        if (_uiState.value.selectedTab == tab) return
        // 排序态仅归属已关注 Tab，切页自动退出并不保存
        _uiState.update { it.copy(selectedTab = tab, isSort = false) }
    }

    fun refresh(tab: Int = _uiState.value.selectedTab) {
        if (tab == TAB_FOCUS) focusLoader.refresh() else unfocusLoader.refresh()
    }

    fun loadMore(tab: Int = _uiState.value.selectedTab) {
        if (tab == TAB_FOCUS) focusLoader.loadMore() else unfocusLoader.loadMore()
    }

    fun retry(tab: Int = _uiState.value.selectedTab) {
        if (tab == TAB_FOCUS) focusLoader.loadInitial() else unfocusLoader.loadInitial()
    }

    /**
     * 关注或取关指定版块，乐观更新本地状态，失败回滚并提示。
     */
    fun toggleFocus(item: ForumItem) {
        if (_uiState.value.mutatingIds.contains(item.id)) return
        val follow = !item.isFollowed
        if (follow && _uiState.value.userLevel < item.levelLimit) {
            _uiState.update { it.copy(actionMessage = "等级不足，无法关注该版块") }
            return
        }
        val loader = if (_uiState.value.selectedTab == TAB_FOCUS) focusLoader else unfocusLoader
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingIds = it.mutatingIds + item.id) }
            loader.updateItem({ it.id == item.id }) {
                it.copy(
                    plateState = if (follow) 1 else 0,
                    focusUserCount = max(0, it.effectiveFocusCount + if (follow) 1 else -1)
                )
            }
            forumRepository.toggleFocus(item.id, follow)
                .onSuccess {
                    _uiState.update { it.copy(hasChanged = true) }
                }
                .onFailure { err ->
                    loader.updateItem({ it.id == item.id }) { item }
                    _uiState.update { it.copy(actionMessage = err.message ?: "操作失败") }
                }
            _uiState.update { it.copy(mutatingIds = it.mutatingIds - item.id) }
        }
    }

    /**
     * 切换已关注排序模式：进入时要求列表非空，退出时提交保存。
     */
    fun toggleSortMode() {
        val state = _uiState.value
        if (!state.isSort) {
            if (state.focusPlates.isEmpty() || state.focusErrorMessage != null) {
                _uiState.update { it.copy(actionMessage = "暂无可排序的版块") }
                return
            }
            _uiState.update { it.copy(isSort = true) }
        } else {
            saveOrder()
        }
    }

    fun cancelSort() {
        _uiState.update { it.copy(isSort = false) }
        focusLoader.refresh()
    }

    fun movePlate(from: Int, to: Int) {
        val size = _uiState.value.focusPlates.size
        if (from !in 0 until size || to !in 0 until size || from == to) return
        focusLoader.mutateItems { list ->
            list.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    private fun saveOrder() {
        if (_uiState.value.isSavingOrder) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingOrder = true) }
            val orderedIds = _uiState.value.focusPlates.map { it.id }
            forumRepository.updateFocusOrder(orderedIds)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSort = false, isSavingOrder = false, hasChanged = true, actionMessage = "排序已保存")
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isSavingOrder = false, actionMessage = err.message ?: "保存排序失败")
                    }
                }
        }
    }

    private fun ensureUserLevel() {
        viewModelScope.launch {
            userRepository.getUserProfile()
                .onSuccess { profile ->
                    _uiState.update { it.copy(userLevel = profile.level) }
                }
                .onFailure {
                    _uiState.update { it.copy(userLevel = 0) }
                }
        }
    }

    companion object {
        const val TAB_FOCUS = 0
        const val TAB_UNFOCUS = 1
        const val PAGE_SIZE = 20
    }
}
