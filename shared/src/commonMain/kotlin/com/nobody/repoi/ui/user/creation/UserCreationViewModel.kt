package com.nobody.repoi.ui.user.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.repository.CreationPageResult
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserCreationViewModel(
    private val userRepository: UserRepository,
    private val tokenManager: TokenManager,
    initialCategory: Int? = null,
) : ViewModel() {

    private val initialCategoryKey: Int? = initialCategory
    private val _uiState = MutableStateFlow(
        UserCreationUiState(
            selectedCategory = initialCategoryKey?.let { UserCreationCategory.fromKey(it) } ?: UserCreationCategory.ALBUM
        )
    )
    val uiState: StateFlow<UserCreationUiState> = _uiState.asStateFlow()

    private var isInitialized = false

    companion object {
        private const val PAGE_SIZE = 20
    }

    /**
     * 根据导航参数初始化页面状态。
     */
    fun initParam(param: UserCreationNavParam) {
        if (isInitialized && _uiState.value.userId == param.userId) {
            return
        }
        isInitialized = true

        val currentUserId = tokenManager.currentUserId
        val isSelf = currentUserId > 0L && currentUserId == param.userId
        val initialCategory = UserCreationCategory.fromKey(param.initialCategory)

        _uiState.update { state ->
            state.copy(
                userId = param.userId,
                isSelf = isSelf,
                isInitialized = true,
                selectedCategory = initialCategory
            )
        }

        // 加载选中的一级分类
        loadCategoryData(category = initialCategory, isRefresh = true)
    }

    /**
     * 切换一级分类（相册 / 图片）。
     */
    fun switchCategory(category: UserCreationCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }

        val currentState = when (category) {
            UserCreationCategory.ALBUM -> _uiState.value.albumState
            UserCreationCategory.PICTURE -> _uiState.value.picState
        }
        if (currentState.items.isEmpty() && !currentState.isLoading && currentState.errorMessage == null) {
            loadCategoryData(category = category, isRefresh = true)
        }
    }

    /**
     * 切换二级排序。
     */
    fun switchOrder(category: UserCreationCategory, orderKey: String) {
        val currentOrder = when (category) {
            UserCreationCategory.ALBUM -> _uiState.value.albumState.order
            UserCreationCategory.PICTURE -> _uiState.value.picState.order
        }
        if (currentOrder == orderKey) return

        when (category) {
            UserCreationCategory.ALBUM -> {
                _uiState.update { state ->
                    state.copy(
                        albumState = state.albumState.copy(
                            order = orderKey,
                            page = 1,
                            items = emptyList(),
                            hasMore = true,
                            errorMessage = null
                        )
                    )
                }
            }
            UserCreationCategory.PICTURE -> {
                _uiState.update { state ->
                    state.copy(
                        picState = state.picState.copy(
                            order = orderKey,
                            page = 1,
                            items = emptyList(),
                            hasMore = true,
                            errorMessage = null
                        )
                    )
                }
            }
        }

        loadCategoryData(category = category, isRefresh = true)
    }

    /**
     * 下拉刷新指定分类。
     */
    fun refresh(category: UserCreationCategory) {
        loadCategoryData(category = category, isRefresh = true)
    }

    /**
     * 加载下一页。
     */
    fun loadNextPage(category: UserCreationCategory) {
        val currentState = when (category) {
            UserCreationCategory.ALBUM -> _uiState.value.albumState
            UserCreationCategory.PICTURE -> _uiState.value.picState
        }

        if (currentState.isLoading || currentState.isRefreshing || currentState.isPaging || !currentState.hasMore) {
            return
        }

        loadCategoryData(category = category, isRefresh = false)
    }

    /**
     * 重试当前失败的分类。
     */
    fun retry(category: UserCreationCategory) {
        loadCategoryData(category = category, isRefresh = true)
    }

    private fun loadCategoryData(category: UserCreationCategory, isRefresh: Boolean) {
        val userId = _uiState.value.userId
        if (userId <= 0L) return

        viewModelScope.launch {
            when (category) {
                UserCreationCategory.ALBUM -> loadPagedData(
                    getTabState = { it.albumState },
                    updateTabState = { state, tab -> state.copy(albumState = tab) },
                    fetcher = { page, order ->
                        userRepository.getUserCreationAlbumList(
                            userId = userId,
                            page = page,
                            pageSize = PAGE_SIZE,
                            order = order
                        )
                    },
                    defaultErrorMessage = "获取相册列表失败",
                    isRefresh = isRefresh
                )
                UserCreationCategory.PICTURE -> loadPagedData(
                    getTabState = { it.picState },
                    updateTabState = { state, tab -> state.copy(picState = tab) },
                    fetcher = { page, order ->
                        userRepository.getUserCreationPicList(
                            userId = userId,
                            page = page,
                            pageSize = PAGE_SIZE,
                            order = order
                        )
                    },
                    defaultErrorMessage = "获取图片列表失败",
                    isRefresh = isRefresh
                )
            }
        }
    }

    private suspend fun <T> loadPagedData(
        getTabState: (UserCreationUiState) -> CreationTabState<T>,
        updateTabState: (UserCreationUiState, CreationTabState<T>) -> UserCreationUiState,
        fetcher: suspend (page: Int, order: String) -> Result<CreationPageResult<T>>,
        defaultErrorMessage: String,
        isRefresh: Boolean
    ) {
        val currentTabState = getTabState(_uiState.value)
        val targetPage = if (isRefresh) 1 else currentTabState.page + 1

        _uiState.update { state ->
            val tab = getTabState(state)
            updateTabState(
                state,
                tab.copy(
                    isLoading = isRefresh && tab.items.isEmpty(),
                    isRefreshing = isRefresh && tab.items.isNotEmpty(),
                    isPaging = !isRefresh,
                    errorMessage = null
                )
            )
        }

        val result = fetcher(targetPage, currentTabState.order)

        result.onSuccess { pageResult ->
            _uiState.update { state ->
                val tab = getTabState(state)
                val combined = if (isRefresh) pageResult.items else tab.items + pageResult.items
                updateTabState(
                    state,
                    tab.copy(
                        items = combined,
                        page = targetPage,
                        isLoading = false,
                        isRefreshing = false,
                        isPaging = false,
                        hasMore = pageResult.hasMore,
                        errorMessage = null
                    )
                )
            }
        }.onFailure { throwable ->
            _uiState.update { state ->
                val tab = getTabState(state)
                updateTabState(
                    state,
                    tab.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isPaging = false,
                        errorMessage = throwable.message ?: defaultErrorMessage
                    )
                )
            }
        }
    }
}
