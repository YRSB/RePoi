package com.nobody.poishelf.ui.forum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.repository.UserRepository
import com.nobody.poishelf.data.repository.ForumRepository
import com.nobody.poishelf.data.remote.model.ForumDetailData
import com.nobody.poishelf.data.remote.model.PostItem
import com.nobody.poishelf.util.paging.LoadStage
import com.nobody.poishelf.util.paging.PagedListLoader
import com.nobody.poishelf.util.paging.pagedListLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 讨论板动态 Tab：-1 关注、0 综合、正数为版块节点。
 */
data class ForumTab(
    val id: Long = 0L,
    val name: String = ""
)

/**
 * 讨论板排序档位（对标原版 updTime/addTime/hits）。
 */
object ForumOrders {
    const val REPLY = "updTime"
    const val NEW = "addTime"
    const val HOT = "hits"
    const val DEFAULT = REPLY
    val all = listOf(REPLY to "回复", NEW to "新帖", HOT to "推荐")
}

/**
 * 讨论板站内搜索条目类型（对标原版 ForumHomeActivity.type）。
 */
object ForumSearchTypes {
    const val DEFAULT = ""
    val all = listOf(
        "" to "帖子",
        "hobby" to "周边",
        "works" to "作品",
        "works_element" to "要素/角色",
        "company" to "厂商",
        "person" to "人物"
    )
}

data class ForumHomeUiState(
    val tabs: List<ForumTab> = emptyList(),
    val selectedTabId: Long = ForumHomeViewModel.ALL_TAB_ID,
    val isTabsLoading: Boolean = false,
    val tabsErrorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val userAvatar: String? = null,
    val searchInput: String = "",
    val submittedKeyword: String = "",
    val selectedItemType: String = ForumSearchTypes.DEFAULT,
    val selectedOrder: String = ForumOrders.DEFAULT,
    val posts: List<PostItem> = emptyList(),
    val pinnedPosts: List<PostItem> = emptyList(),
    val forumHeader: ForumDetailData? = null,
    val isPostsLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val postsErrorMessage: String? = null,
    val loadMoreError: String? = null
)

/**
 * 讨论板主页 ViewModel。
 * 管理动态 Tabs（关注/综合/已关注版块）、排序、站内搜索与每 Tab 独立的帖子分页流。
 */
class ForumHomeViewModel(
    private val forumRepository: ForumRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForumHomeUiState())
    val uiState: StateFlow<ForumHomeUiState> = _uiState.asStateFlow()

    private val postLoaders = mutableMapOf<Long, PagedListLoader<PostItem>>()
    private val pinnedByTab = mutableMapOf<Long, List<PostItem>>()
    private val headerByTab = mutableMapOf<Long, ForumDetailData?>()
    private val headerLoadingIds = mutableSetOf<Long>()

    private var postsCollectJob: Job? = null
    private var tabsJob: Job? = null
    private var avatarRequested = false

    init {
        userRepository.tokenFlow
            .map { it.isNotBlank() }
            .distinctUntilChanged()
            .onEach { loggedIn ->
                val changed = loggedIn != _uiState.value.isLoggedIn
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
                if (!loggedIn) {
                    avatarRequested = false
                    _uiState.update { it.copy(userAvatar = null) }
                } else {
                    ensureAvatar()
                }
                if (changed || _uiState.value.tabs.isEmpty()) {
                    loadTabs()
                }
            }
            .launchIn(viewModelScope)

        userRepository.currentUserProfile
            .onEach { profile ->
                if (profile?.header != null) {
                    _uiState.update { it.copy(userAvatar = profile.header) }
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 加载动态 Tabs：登录态附加关注 Tab 与已关注版块，综合 Tab 恒存在。
     * 已关注版块拉取失败时静默降级为基础 Tabs，避免阻塞帖子流。
     */
    fun loadTabs() {
        tabsJob?.cancel()
        tabsJob = viewModelScope.launch {
            _uiState.update { it.copy(isTabsLoading = true, tabsErrorMessage = null) }
            val loggedIn = _uiState.value.isLoggedIn
            val tabs = mutableListOf<ForumTab>()
            if (loggedIn) tabs += ForumTab(FOLLOW_TAB_ID, "关注")
            tabs += ForumTab(ALL_TAB_ID, "综合")
            var focusError: String? = null
            if (loggedIn) {
                forumRepository.getFocusForums(forumPage = 1)
                    .onSuccess { focus ->
                        tabs += focus.filter { it.id > 0L }.map { ForumTab(it.id, it.name.ifBlank { "版块" }) }
                    }
                    .onFailure { focusError = it.message ?: "已关注版块加载失败" }
            }
            val currentSelected = _uiState.value.selectedTabId
            val resolvedSelected = if (tabs.any { it.id == currentSelected }) currentSelected else ALL_TAB_ID
            _uiState.update {
                it.copy(
                    tabs = tabs,
                    selectedTabId = resolvedSelected,
                    isTabsLoading = false,
                    tabsErrorMessage = focusError
                )
            }
            observePosts(resolvedSelected)
        }
    }

    fun retryTabs() {
        loadTabs()
    }

    fun clearTabsError() {
        _uiState.update { it.copy(tabsErrorMessage = null) }
    }

    fun selectTab(tabId: Long) {
        if (_uiState.value.selectedTabId == tabId) return
        if (_uiState.value.tabs.none { it.id == tabId }) return
        _uiState.update { it.copy(selectedTabId = tabId) }
        observePosts(tabId)
    }

    fun selectOrder(order: String) {
        if (_uiState.value.selectedOrder == order) return
        _uiState.update { it.copy(selectedOrder = order) }
        resetPostLoaders()
    }

    fun updateSearchInput(input: String) {
        _uiState.update { it.copy(searchInput = input) }
    }

    fun submitSearch() {
        val keyword = _uiState.value.searchInput.trim()
        if (_uiState.value.submittedKeyword == keyword) return
        _uiState.update { it.copy(submittedKeyword = keyword) }
        resetPostLoaders()
    }

    fun clearSearch() {
        if (_uiState.value.searchInput.isEmpty() && _uiState.value.submittedKeyword.isEmpty()) return
        _uiState.update { it.copy(searchInput = "", submittedKeyword = "") }
        resetPostLoaders()
    }

    fun selectItemType(itemType: String) {
        if (_uiState.value.selectedItemType == itemType) return
        _uiState.update { it.copy(selectedItemType = itemType) }
        // 空关键词下条目类型不影响帖子流，仅记录选中态随下次搜索生效，避免无意义整页重载
        if (_uiState.value.submittedKeyword.isNotBlank()) {
            resetPostLoaders()
        }
    }

    fun refresh() {
        postLoaders[_uiState.value.selectedTabId]?.refresh()
    }

    fun loadMore() {
        postLoaders[_uiState.value.selectedTabId]?.loadMore()
    }

    fun retryPosts() {
        postLoaders[_uiState.value.selectedTabId]?.loadInitial()
    }

    private fun getPostsLoader(tabId: Long): PagedListLoader<PostItem> =
        postLoaders.getOrPut(tabId) {
            pagedListLoader(
                scope = viewModelScope,
                pageSize = PAGE_SIZE,
                keySelector = { it.id }
            ) { page, size ->
                forumRepository.getForumPosts(
                    forumNodeId = tabId,
                    order = _uiState.value.selectedOrder,
                    keyword = _uiState.value.submittedKeyword.ifBlank { null },
                    itemType = _uiState.value.selectedItemType.ifBlank { null },
                    page = page,
                    pageSize = size
                )
            }
        }

    private fun observePosts(tabId: Long) {
        postsCollectJob?.cancel()
        val loader = getPostsLoader(tabId)
        _uiState.update {
            it.copy(
                forumHeader = headerByTab[tabId],
                pinnedPosts = pinnedByTab[tabId].orEmpty()
            )
        }
        postsCollectJob = viewModelScope.launch {
            loader.state.collect { listState ->
                if (_uiState.value.selectedTabId != tabId) return@collect
                // 首屏 settled 时快照置顶帖（最多 5 条），追加页不再重算
                if (listState.page <= 1 && listState.stage == LoadStage.Idle && listState.initialError == null) {
                    pinnedByTab[tabId] = listState.items.filter { it.isPinned }.take(MAX_PINNED_COUNT)
                }
                val pinnedIds = pinnedByTab[tabId].orEmpty().map { it.id }.toSet()
                _uiState.update { current ->
                    current.copy(
                        posts = if (pinnedIds.isEmpty()) {
                            listState.items
                        } else {
                            listState.items.filterNot { it.id in pinnedIds }
                        },
                        pinnedPosts = pinnedByTab[tabId].orEmpty(),
                        isPostsLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        postsErrorMessage = listState.errorMessage,
                        loadMoreError = listState.loadMoreError
                    )
                }
            }
        }
        if (loader.state.value.items.isEmpty() && loader.state.value.stage != LoadStage.InitialLoading) {
            loader.loadInitial()
        }
        loadForumHeader(tabId)
    }

    private fun resetPostLoaders() {
        postsCollectJob?.cancel()
        postLoaders.clear()
        pinnedByTab.clear()
        _uiState.update {
            it.copy(
                posts = emptyList(),
                pinnedPosts = emptyList(),
                postsErrorMessage = null,
                loadMoreError = null,
                hasMore = true
            )
        }
        observePosts(_uiState.value.selectedTabId)
    }

    private fun loadForumHeader(tabId: Long) {
        if (tabId <= 0L) {
            if (_uiState.value.selectedTabId == tabId) {
                _uiState.update { it.copy(forumHeader = null) }
            }
            return
        }
        if (headerByTab.containsKey(tabId)) {
            if (_uiState.value.selectedTabId == tabId) {
                _uiState.update { it.copy(forumHeader = headerByTab[tabId]) }
            }
            return
        }
        if (!headerLoadingIds.add(tabId)) return
        viewModelScope.launch {
            val header = forumRepository.getForumDetail(tabId).getOrNull()
            headerByTab[tabId] = header
            headerLoadingIds.remove(tabId)
            if (_uiState.value.selectedTabId == tabId) {
                _uiState.update { it.copy(forumHeader = header) }
            }
        }
    }

    private fun ensureAvatar() {
        if (!_uiState.value.isLoggedIn || !_uiState.value.userAvatar.isNullOrBlank() || avatarRequested) return
        avatarRequested = true
        viewModelScope.launch {
            userRepository.getUserProfile().onSuccess { profile ->
                _uiState.update { it.copy(userAvatar = profile.header) }
            }
        }
    }

    companion object {
        const val FOLLOW_TAB_ID = -1L
        const val ALL_TAB_ID = 0L
        const val MAX_PINNED_COUNT = 5
        const val PAGE_SIZE = 20
    }
}
