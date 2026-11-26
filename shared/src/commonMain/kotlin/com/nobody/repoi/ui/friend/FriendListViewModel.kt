package com.nobody.repoi.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.UserFellowItem
import com.nobody.repoi.data.remote.model.UserPraiseItem
import com.nobody.repoi.data.remote.model.UserProfileSummary
import com.nobody.repoi.data.repository.FriendChangeEvent
import com.nobody.repoi.data.repository.FriendState
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.data.remote.model.RelateItem

data class FriendListItemModel(
    val id: Long = 0L,
    val userId: Long = 0L,
    val nickname: String = "",
    val header: String? = null,
    val level: Int = 0,
    val signature: String? = null,
    val both: Int = -1,
    val isFollowed: Boolean = false,
    val isMutual: Boolean = false,
    val isFollowLoading: Boolean = false,
    val praiseUser: UserProfileSummary? = null,
    val relateItem: RelateItem? = null
) {
    companion object {
        fun fromFellowItem(item: UserFellowItem, currentTab: Int = 0): FriendListItemModel {
            val uid = item.userId
            val isMutual = item.both == 1 || item.both == 2
            val isFollowed = if (currentTab == 1) {

                isMutual
            } else {

                item.both == 0 || item.both == 1 || item.both == 2
            }
            return FriendListItemModel(
                id = uid,
                userId = uid,
                nickname = item.displayName,
                header = item.avatarUrl,
                level = item.user?.level ?: 0,
                signature = item.signature,
                both = item.both,
                isFollowed = isFollowed,
                isMutual = isMutual
            )
        }

        fun fromPraiseItem(item: UserPraiseItem, stableId: Long = 0L): FriendListItemModel {
            val praiseUser = item.user
            val relate = item.item
            val targetItemId = relate?.itemId?.takeIf { it > 0L } ?: relate?.id ?: 0L
            return FriendListItemModel(
                id = if (stableId != 0L) stableId else targetItemId,
                userId = praiseUser?.actualUserId ?: 0L,
                nickname = praiseUser?.nickname?.ifBlank { null } ?: praiseUser?.userName ?: "收藏家",
                header = praiseUser?.header,
                level = praiseUser?.level ?: 0,
                signature = praiseUser?.sign,
                both = -1,
                isFollowed = false,
                isMutual = false,
                praiseUser = praiseUser,
                relateItem = relate
            )
        }
    }
}

data class FriendListUiState(
    val currentTab: Int = 0,
    val items: List<FriendListItemModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val errorMessage: String? = null,
    val userMessage: String? = null
)

class FriendListViewModel(
    private val userRepository: UserRepository,
    private val followRepository: FollowRepository,
    userId: Long = 0L,
    initialType: Int = 0,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendListUiState())
    val uiState: StateFlow<FriendListUiState> = _uiState.asStateFlow()

    private var appliedInitParams: Pair<Long, Int>? = null
    var currentUserId: Long = 0L
        private set

    private var loadJob: Job? = null

    init {
        if (userId > 0L) {
            init(userId = userId, initialType = initialType)
        }

        viewModelScope.launch {
            followRepository.friendChangeEvent.collect { event ->
                _uiState.update { state ->
                    val targetIndex = state.items.indexOfFirst { it.userId == event.userId }
                    if (targetIndex >= 0) {
                        val targetItem = state.items[targetIndex]
                        if (targetItem.isFollowed != event.isFollow) {
                            val isFansTab = state.currentTab == 1
                            val updatedItem = targetItem.copy(
                                isFollowed = event.isFollow,
                                both = if (event.isFollow) {
                                    if (isFansTab) 1 else FriendState.FOLLOWING.code
                                } else {
                                    if (isFansTab) 0 else FriendState.NOT_FOLLOWING.code
                                },
                                isMutual = if (isFansTab) event.isFollow else (if (!event.isFollow) false else targetItem.isMutual)
                            )
                            val updatedItems = state.items.toMutableList()
                            updatedItems[targetIndex] = updatedItem
                            state.copy(items = updatedItems)
                        } else {
                            state
                        }
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun init(userId: Long, initialType: Int = 0) {
        val validTab = if (initialType in listOf(0, 1, 3)) initialType else 0
        val targetParams = userId to validTab
        if (appliedInitParams == targetParams) {
            return
        }
        val isSameUser = appliedInitParams != null && currentUserId == userId
        appliedInitParams = targetParams
        if (isSameUser) {
            switchTab(validTab)
            return
        }
        currentUserId = userId
        _uiState.update {
            it.copy(
                currentTab = validTab,
                items = emptyList(),
                isLoading = true,
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = true,
                page = 1,
                errorMessage = null,
                userMessage = null
            )
        }
        loadData(page = 1, isRefresh = false)
    }

    fun switchTab(type: Int) {
        val validTab = if (type in listOf(0, 1, 3)) type else 0
        if (_uiState.value.currentTab == validTab && _uiState.value.items.isNotEmpty()) {
            return
        }
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                currentTab = validTab,
                items = emptyList(),
                isLoading = true,
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = true,
                page = 1,
                errorMessage = null,
                userMessage = null
            )
        }
        loadData(page = 1, isRefresh = false)
    }

    fun refresh() {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null
            )
        }
        loadData(page = 1, isRefresh = true)
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore) {
            return
        }
        _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
        loadData(page = current.page + 1, isRefresh = false, isLoadMore = true)
    }

    fun toggleFollow(targetUserId: Long, currentFollowState: Boolean) {
        val currentItems = _uiState.value.items
        val targetIndex = currentItems.indexOfFirst { it.userId == targetUserId }
        if (targetIndex < 0) return

        val targetItem = currentItems[targetIndex]
        val newFollowState = !currentFollowState
        val isFansTab = _uiState.value.currentTab == 1

        val optimisticItem = targetItem.copy(
            isFollowed = newFollowState,
            both = if (newFollowState) {
                if (isFansTab) 1 else FriendState.FOLLOWING.code
            } else {
                if (isFansTab) 0 else FriendState.NOT_FOLLOWING.code
            },
            isMutual = if (isFansTab) newFollowState else (if (!newFollowState) false else targetItem.isMutual)
        )
        _uiState.update { state ->
            state.copy(
                items = state.items.map { if (it.userId == targetUserId) optimisticItem else it }
            )
        }

        viewModelScope.launch {
            val result = followRepository.updateFriend(userId = targetUserId, isFollow = newFollowState)
            if (result.isFailure) {

                val ex = result.exceptionOrNull()
                _uiState.update { state ->
                    state.copy(
                        items = state.items.map { if (it.userId == targetUserId) targetItem else it },
                        userMessage = "关注操作失败，已还原状态",
                        errorMessage = ex?.message
                    )
                }
            }
        }
    }

    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun loadData(page: Int, isRefresh: Boolean, isLoadMore: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val tab = _uiState.value.currentTab
            val result = if (tab == 3) {
                userRepository.getUserPraiseList(userId = currentUserId, page = page, pageSize = PAGE_SIZE)
                    .map { list ->
                        list.mapIndexed { index, item ->
                            FriendListItemModel.fromPraiseItem(
                                item = item,
                                stableId = (page - 1L) * PAGE_SIZE + index + 1L
                            )
                        }
                    }
            } else {
                followRepository.getUserFellowList(userId = currentUserId, type = tab, page = page, pageSize = PAGE_SIZE)
                    .map { list -> list.map { FriendListItemModel.fromFellowItem(it, currentTab = tab) } }
            }

            result.fold(
                onSuccess = { newItems ->
                    _uiState.update { state ->
                        val combinedItems = if (isLoadMore) state.items + newItems else newItems
                        state.copy(
                            items = combinedItems,
                            page = page,
                            hasMore = newItems.size >= PAGE_SIZE,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = error.message ?: "加载失败，请重试"
                        )
                    }
                }
            )
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
