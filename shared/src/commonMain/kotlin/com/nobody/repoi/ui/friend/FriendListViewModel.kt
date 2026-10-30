package com.nobody.repoi.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.UserFellowItem
import com.nobody.repoi.data.remote.model.UserPraiseItem
import com.nobody.repoi.data.remote.model.UserProfileSummary
import com.nobody.repoi.data.repository.FriendChangeEvent
import com.nobody.repoi.data.repository.FriendState
import com.nobody.repoi.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.data.remote.model.RelateItem

/**
 * 社交列表 UI 项数据模型。
 * 兼容关注列表 (0)、粉丝列表 (1) 以及获赞条目列表 (3)。
 */
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
                // 粉丝列表中：只有 both == 1 或 both == 2 才表示我也关注了该粉丝（互相关注）；both == 0 表示我尚未关注
                isMutual
            } else {
                // 关注列表中：both == 0（已关注单向）或 both == 1 / 2（互相关注双向）
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

/**
 * 社交关系列表界面 UI 状态。
 * @property currentTab 当前选中的分类 Tab（0=关注, 1=粉丝, 3=获赞）
 * @property items 列表项集合
 * @property isLoading 初始加载中状态
 * @property isRefreshing 下拉刷新中状态
 * @property isLoadingMore 底部加载更多中状态
 * @property hasMore 是否还有下一页数据
 * @property page 当前页码
 * @property errorMessage 全局或加载失败异常信息
 * @property userMessage 一次性提示信息（如关注失败回滚通知）
 */
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

/**
 * 好友与社交关系 ViewModel。
 * 驱动关注列表、粉丝列表与获赞条目列表的展示、分页、下拉刷新与关注/取关乐观更新。
 */
class FriendListViewModel(
    private val userRepository: UserRepository,
    userId: Long = 0L,
    initialType: Int = 0,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendListUiState())
    val uiState: StateFlow<FriendListUiState> = _uiState.asStateFlow()

    private var initialized = false
    var currentUserId: Long = 0L
        private set

    private var loadJob: Job? = null

    init {
        if (userId > 0L) {
            init(userId = userId, initialType = initialType)
        }

        viewModelScope.launch {
            userRepository.friendChangeEvent.collect { event ->
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

    /**
     * 初始化社交列表数据。
     * @param userId 目标用户 ID
     * @param initialType 初始分类（0=关注, 1=粉丝, 3=获赞）
     */
    fun init(userId: Long, initialType: Int = 0) {
        if (initialized && currentUserId == userId) {
            return
        }
        initialized = true
        currentUserId = userId
        val validTab = if (initialType in listOf(0, 1, 3)) initialType else 0
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

    /**
     * 切换分类 Tab。
     * @param type 目标分类（0=关注, 1=粉丝, 3=获赞）
     */
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

    /**
     * 下拉刷新当前分类数据。
     */
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

    /**
     * 滑动触底加载更多。
     */
    fun loadMore() {
        val current = _uiState.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore) {
            return
        }
        _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
        loadData(page = current.page + 1, isRefresh = false, isLoadMore = true)
    }

    /**
     * 切换用户关注状态。
     * 执行即时乐观更新（立即翻转 UI 状态），随后发起网络请求；若请求失败则自动回滚原状态并提示。
     * @param targetUserId 目标用户 ID
     * @param currentFollowState 当前是否已关注
     */
    fun toggleFollow(targetUserId: Long, currentFollowState: Boolean) {
        val currentItems = _uiState.value.items
        val targetIndex = currentItems.indexOfFirst { it.userId == targetUserId }
        if (targetIndex < 0) return

        val targetItem = currentItems[targetIndex]
        val newFollowState = !currentFollowState
        val isFansTab = _uiState.value.currentTab == 1

        // 1. 立即乐观更新 UI
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

        // 2. 异步调用后端更新 API
        viewModelScope.launch {
            val result = userRepository.updateFriend(userId = targetUserId, isFollow = newFollowState)
            if (result.isFailure) {
                // 失败时回滚状态并提示用户
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

    /**
     * 消费一次性用户提示信息。
     */
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
                userRepository.getUserFellowList(userId = currentUserId, type = tab, page = page, pageSize = PAGE_SIZE)
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
