package com.nobody.repoi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.platform.DateTimeFormats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedCategory(
    val tabKey: String = "all",
    val name: String = "全部",
    val nodeId: Long = 0L,
    val zoneIds: String = "",
    val id: Int = nodeId.toInt()
)

data class HomeFeedUiState(
    val categories: List<FeedCategory> = listOf(FeedCategory("all", "全部")),
    val selectedCategory: FeedCategory = FeedCategory("all", "全部"),
    /** 顶栏专区切换器分组（「快捷」恒含全部/关注，「我的专区」遵循首页设置排序与隐藏）。 */
    val switcherGroups: List<ZoneSwitcherGroup> = defaultZoneSwitcherGroups(),
    val selectedSubType: String = ActionSubType.DEFAULT.apiValue,
    val items: List<ActionItem> = emptyList(),
    val lastTime: Long = 0L,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val userAvatar: String? = null,
    val unreadMessageCount: Int = 0
)

class HomeFeedViewModel(
    private val homeRepository: HomeRepository,
    private val zoneRepository: ZoneRepository,
    private val viewPreferencesManager: ViewPreferencesManager,
    private val userSession: UserSession,
    private val messageRepository: MessageRepository? = null
) : ViewModel() {

    /**
     * 动态筛选项（key = 协议键值 `subType`，value = 2.43 中文文案）。
     *
     * 唯一来源为共享定义 [ActionSubType]，界面仍按有序 `(key, label)` 列表渲染，顺序即 2.43 顺序。
     */
    val subTypes: List<Pair<String, String>> =
        ActionSubType.ordered.map { it.apiValue to it.label }

    // 旧持久化值 `all` 在此归一为 `allInfo`（服务端对 `all` 静默回落「推荐」，不能原样发出）
    private val initialSubType: String =
        ActionSubType.fromPersisted(viewPreferencesManager.getInitialFeedSubType()).apiValue
    private val initialTabKey = viewPreferencesManager.getInitialFeedZoneTabKey()

    private val _uiState = MutableStateFlow(
        HomeFeedUiState(
            selectedCategory = FeedCategory(
                tabKey = initialTabKey,
                name = if (initialTabKey == "follow") "关注" else "全部",
                zoneIds = if (initialTabKey == "follow") "-1" else ""
            ),
            selectedSubType = initialSubType
        )
    )
    val uiState: StateFlow<HomeFeedUiState> = _uiState.asStateFlow()

    private var initialLoadJob: Job? = null

    // 记忆滚动位置（用于 Tab 切换保活）
    var savedScrollIndex: Int = 0
        private set
    var savedScrollOffset: Int = 0
        private set

    var savedFilterCollapseFraction: Float = 0f
        private set

    fun saveScrollPosition(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    fun saveFilterCollapseFraction(fraction: Float) {
        savedFilterCollapseFraction = fraction.coerceIn(0f, 1f)
    }

    fun resetScrollPosition() {
        savedScrollIndex = 0
        savedScrollOffset = 0
        savedFilterCollapseFraction = 0f
    }

    init {
        // 监听专区与设置变化，构建动态分类列表与顶栏切换器分组
        viewModelScope.launch {
            combine(
                zoneRepository.focusZonesFlow,
                zoneRepository.settingsFlow
            ) { focusZones, settings ->
                focusZones to settings
            }.collect { (focusZones, settings) ->
                val dynamicCategories = buildFeedCategories(focusZones, settings)
                val switcherGroups = buildZoneSwitcherGroups(dynamicCategories, settings)
                _uiState.update { current ->
                    val matchedCategory = dynamicCategories.find { it.tabKey == current.selectedCategory.tabKey }
                        ?: dynamicCategories.find { it.tabKey == initialTabKey }
                        ?: dynamicCategories.firstOrNull()
                        ?: FeedCategory("all", "全部")

                    val categoryChanged = matchedCategory.tabKey != current.selectedCategory.tabKey ||
                        matchedCategory.zoneIds != current.selectedCategory.zoneIds

                    current.copy(
                        categories = dynamicCategories,
                        selectedCategory = matchedCategory,
                        switcherGroups = switcherGroups
                    ).also {
                        if (categoryChanged && !current.isInitialLoading) {
                            loadInitialFeed()
                        }
                    }
                }
            }
        }

        // 启动时立即同步一次专区与设置，不阻塞等待会话预热
        viewModelScope.launch {
            zoneRepository.getZoneHomeTabs()
        }

        setupAccountState()

        loadInitialFeed()
    }

    /**
     * 顶栏切换器选择入口：把纯文本 tabKey 解析为分类后复用 [selectCategory]。
     *
     * 不新增第二套持久化——仍写 `feed_zone_tab_key` 与 `feed_category_id`；
     * 即使设置隐藏了「全部」，也能通过兜底分类切回「全部」（只切 tab，不改设置）。
     */
    fun selectZoneTab(tabKey: String) {
        val category = resolveZoneSwitcherCategory(_uiState.value.categories, tabKey) ?: return
        selectCategory(category)
    }

    fun selectCategory(category: FeedCategory) {
        if (_uiState.value.selectedCategory.tabKey == category.tabKey) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                selectedCategory = category,
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(category.tabKey)
            viewPreferencesManager.setFeedCategory(category.id)
        }
        loadInitialFeed()
    }

    fun selectSubType(subType: String) {
        // 边界归一：任何来源的筛选值都先转成协议键值，避免旧键 all 被发到接口
        val normalized = ActionSubType.fromPersisted(subType).apiValue
        if (_uiState.value.selectedSubType == normalized) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                selectedSubType = normalized,
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedSubType(normalized)
        }
        loadInitialFeed()
    }

    fun resetFilters() {
        val defaultCategory = _uiState.value.categories.firstOrNull() ?: FeedCategory("all", "全部")
        val defaultSubType = ActionSubType.DEFAULT.apiValue
        if (_uiState.value.selectedCategory.tabKey == defaultCategory.tabKey && _uiState.value.selectedSubType == defaultSubType) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                selectedCategory = defaultCategory,
                selectedSubType = defaultSubType,
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(defaultCategory.tabKey)
            viewPreferencesManager.setFeedCategory(defaultCategory.id)
            viewPreferencesManager.setFeedSubType(defaultSubType)
        }
        loadInitialFeed()
    }

    fun refreshFeed() = refresh()

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        resetScrollPosition()
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null, loadMoreError = null) }
        fetchUnreadMessages()

        viewModelScope.launch {
            val currentState = _uiState.value

            // 关注 Tab 但未关注任何专区：直接展示空状态，不发网络请求避免降级为查全量
            if (currentState.selectedCategory.tabKey == "follow" && currentState.selectedCategory.zoneIds == "-1") {
                _uiState.update {
                    it.copy(
                        items = emptyList(),
                        lastTime = 0L,
                        isRefreshing = false,
                        isInitialLoading = false,
                        hasMore = false,
                        loadMoreError = null
                    )
                }
                return@launch
            }

            val result = homeRepository.getHomeFeed(
                categoryId = 0,
                subType = currentState.selectedSubType,
                lastTime = 0L,
                zoneNodeId = if (currentState.selectedCategory.nodeId > 0L) currentState.selectedCategory.nodeId else null,
                zoneIds = currentState.selectedCategory.zoneIds.takeIf { it.isNotBlank() && it != "-1" }
            )

            result.onSuccess { newList ->
                val newLastTime = parseCursorTimestamp(newList.lastOrNull()?.actionDate)
                val distinctList = newList.distinctBy { "${it.id}_${it.actionDate}" }
                _uiState.update {
                    it.copy(
                        items = distinctList,
                        lastTime = newLastTime,
                        isRefreshing = false,
                        isInitialLoading = false,
                        hasMore = distinctList.isNotEmpty() && newLastTime > 0L,
                        loadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isInitialLoading = false,
                        errorMessage = error.message ?: "刷新失败"
                    )
                }
            }
        }
    }

    fun loadInitialFeed() {
        initialLoadJob?.cancel()
        initialLoadJob = viewModelScope.launch {
            val currentState = _uiState.value

            // 关注 Tab 但未关注任何专区：直接展示空状态，不发网络请求避免降级为查全量
            if (currentState.selectedCategory.tabKey == "follow" && currentState.selectedCategory.zoneIds == "-1") {
                _uiState.update {
                    it.copy(
                        items = emptyList(),
                        lastTime = 0L,
                        isInitialLoading = false,
                        hasMore = false,
                        errorMessage = null,
                        loadMoreError = null
                    )
                }
                return@launch
            }

            val result = homeRepository.getHomeFeed(
                categoryId = 0,
                subType = currentState.selectedSubType,
                lastTime = 0L,
                zoneNodeId = if (currentState.selectedCategory.nodeId > 0L) currentState.selectedCategory.nodeId else null,
                zoneIds = currentState.selectedCategory.zoneIds.takeIf { it.isNotBlank() && it != "-1" }
            )

            result.onSuccess { newList ->
                val newLastTime = parseCursorTimestamp(newList.lastOrNull()?.actionDate)
                val distinctList = newList.distinctBy { "${it.id}_${it.actionDate}" }
                _uiState.update {
                    it.copy(
                        items = distinctList,
                        lastTime = newLastTime,
                        isInitialLoading = false,
                        hasMore = distinctList.isNotEmpty() && newLastTime > 0L,
                        errorMessage = null,
                        loadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        errorMessage = error.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMore || currentState.isInitialLoading || currentState.isRefreshing) return

        if (currentState.selectedCategory.tabKey == "follow" && currentState.selectedCategory.zoneIds == "-1") {
            return
        }

        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }

        viewModelScope.launch {
            val result = homeRepository.getHomeFeed(
                categoryId = 0,
                subType = currentState.selectedSubType,
                lastTime = currentState.lastTime,
                zoneNodeId = if (currentState.selectedCategory.nodeId > 0L) currentState.selectedCategory.nodeId else null,
                zoneIds = currentState.selectedCategory.zoneIds.takeIf { it.isNotBlank() && it != "-1" }
            )

            result.onSuccess { nextPageList ->
                val newLastTime = parseCursorTimestamp(nextPageList.lastOrNull()?.actionDate)
                _uiState.update { state ->
                    val existingKeys = state.items.map { item -> "${item.id}_${item.actionDate}" }.toSet()
                    val uniqueNewItems = nextPageList.filterNot { item -> "${item.id}_${item.actionDate}" in existingKeys }
                    val nextCursor = if (newLastTime > 0L) newLastTime else state.lastTime
                    val hasMorePages = nextPageList.isNotEmpty() &&
                        uniqueNewItems.isNotEmpty() &&
                        newLastTime > 0L &&
                        newLastTime != state.lastTime
                    state.copy(
                        items = state.items + uniqueNewItems,
                        lastTime = nextCursor,
                        isLoadingMore = false,
                        hasMore = hasMorePages,
                        loadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        isLoadingMore = false,
                        loadMoreError = error.message ?: "加载更多失败"
                    )
                }
            }
        }
    }

    private fun setupAccountState() {
        viewModelScope.launch {
            var lastToken: String? = null
            var profileRefreshRequested = false
            userSession.state.collect { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        val tokenChanged = state.token != lastToken
                        if (tokenChanged) {
                            lastToken = state.token
                            profileRefreshRequested = false
                            // 令牌初次就绪或切换时，触发关注专区刷新以保障最终一致
                            launch { zoneRepository.getZoneHomeTabs() }
                            fetchUnreadMessages()
                        }
                        _uiState.update { it.copy(userAvatar = state.profile?.header) }
                        if (state.profile == null && !profileRefreshRequested) {
                            // 档案缺失时仅主动补拉一次，后续由会话内部增量刷新兜底
                            profileRefreshRequested = true
                            launch { userSession.refreshProfile() }
                        }
                    }
                    SessionState.Unauthenticated -> {
                        lastToken = null
                        profileRefreshRequested = false
                        _uiState.update { it.copy(userAvatar = null, unreadMessageCount = 0) }
                    }
                    SessionState.Prewarming -> {
                        // 预热中：保持中性状态，不提前刷掉可能已有的头像
                        lastToken = null
                        profileRefreshRequested = false
                    }
                }
            }
        }
    }

    private fun fetchUnreadMessages() {
        val messageRepo = messageRepository ?: return
        viewModelScope.launch {
            val token = userSession.currentToken
            if (token.isBlank()) return@launch
            val count = messageRepo.getUnreadPushCount().getOrNull() ?: return@launch
            // 请求期间令牌可能已切换/失效，二次校验避免污染新会话的未读数
            if (userSession.currentToken != token) return@launch
            _uiState.update { it.copy(unreadMessageCount = count.totalCount) }
        }
    }

    private fun parseCursorTimestamp(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return DateTimeFormats.parseServerDateTime(dateStr) ?: 0L
    }
}
