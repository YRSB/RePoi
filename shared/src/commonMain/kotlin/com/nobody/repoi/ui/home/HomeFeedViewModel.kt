package com.nobody.repoi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.ZoneQueryScope
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
    /** 首屏/刷新空态判定：置顶帖与普通帖都为空。 */
    val isEmpty: Boolean
        get() = items.isEmpty() && pinnedPosts.isEmpty()

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

data class HomeFeedUiState(
    val categories: List<FeedCategory> = listOf(FeedCategory("all", "全部")),
    val selectedCategory: FeedCategory = FeedCategory("all", "全部"),
    /** 顶栏专区切换器分组（「快捷」恒含全部/关注，「我的专区」遵循首页设置排序与隐藏）。 */
    val switcherGroups: List<ZoneSwitcherGroup> = defaultZoneSwitcherGroups(),
    /**
     * 二级板块列表（顺序即 Tab 顺序）：
     * - 聚合态（全部/关注）= `HOME_ORDER ∩ HOME_IMPLEMENTED`；
     * - 单专区 = 服务端 `zone/info.tabs` ∩ `HOME_IMPLEMENTED`（请求中/失败先回退 `[ZoneBoard.Action]`）。
     *
     * 为空表示该专区没有可用板块，宿主据此隐藏二级 TabRow 并展示板块空态（不渲染空 Tab 条）。
     */
    val boards: List<ZoneBoard> = HOME_AGGREGATE_BOARDS,
    /** 当前选中的二级板块；切换专区时重置为该专区首板块。 */
    val selectedBoard: ZoneBoard = HOME_AGGREGATE_BOARDS.firstOrNull() ?: ZoneBoard.Action,
    val selectedSubType: String = ActionSubType.DEFAULT.apiValue,
    val items: List<ActionItem> = emptyList(),
    val lastTime: Long = 0L,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    /** 讨论板块状态（仅聚合·全部与单专区可用；关注聚合已由 `homeBoardsFor` 隐藏该板块）。 */
    val posts: HomePostsState = HomePostsState(),
    val userAvatar: String? = null,
    val unreadMessageCount: Int = 0
) {
    /** 查询作用域（由 [selectedCategory] 派生，避免两处状态漂移）。 */
    val zoneScope: ZoneQueryScope
        get() = selectedCategory.toZoneQueryScope()

    /** 当前二级板块是否为讨论板块。 */
    val isPostsBoard: Boolean
        get() = selectedBoard == ZoneBoard.Posts

    /**
     * 讨论板块 → 共享面板内容快照（宿主投影，pane 只读；请求/去重全部在 ViewModel 完成）。
     *
     * 置顶帖只在单专区投影（聚合态无置顶语义），与 `ZoneBoardPane(showPinnedPosts = ...)` 对齐。
     */
    val postsBoardContent: ZoneBoardContent.Posts
        get() = ZoneBoardContent.Posts(
            pinnedPosts = posts.pinnedPosts,
            items = posts.items,
            page = ZoneBoardPageView(
                isLoading = posts.isInitialLoading,
                isLoadingMore = posts.isLoadingMore,
                errorMessage = posts.errorMessage,
                loadMoreError = posts.loadMoreError,
                isEndReached = !posts.hasMore
            )
        )

    /** 当前板块筛选值快照（动态子分类 + 讨论排序；讨论没有分类字段，见 `PostsBoardOptions` KDoc）。 */
    val boardFilters: ZoneBoardFilters
        get() = ZoneBoardFilters(subType = selectedSubType, postsOrder = posts.order)
}

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

    /** 冷启动落盘的一级 tabKey 与二级板块键（`{tabKey}|{type}`，未设置时为空串）。 */
    private val savedTabKey: String? = viewPreferencesManager.getInitialFeedZoneTabKey()
    private val savedBoardKey: String? = viewPreferencesManager.getInitialFeedZoneBoardKey()

    /**
     * 关注专区数据就绪信号：`zone/home-tabs` 返回后置 true（请求失败也算就绪，按不可用回落）。
     *
     * 一级恢复规则里 `z{nodeId}` 必须先确认「仍在已关注集合中」，因此不能拿首帧空的
     * `focusZonesFlow` 直接判定，否则合法单专区会被误判为不可用而回落「全部」（#155 移交点）。
     */
    private val focusZonesLoaded = MutableStateFlow(false)

    /** 一级冷启动判定是否已完成；单专区需等 [focusZonesLoaded] 后再判定。 */
    private var coldStartTabResolved: Boolean = zoneTabKeyNodeId(savedTabKey) == null

    /** 当前生效的二级板块偏好键（`{tabKey}|{type}`）：恢复与写回共用，避免出现两套编码。 */
    private var currentBoardSelectionKey: String = savedBoardKey.orEmpty()

    private val _uiState = MutableStateFlow(
        HomeFeedUiState(
            // 冷启动统一先落「全部」：`follow`/不可用由纯函数立即回落；单专区在关注专区数据
            // 就绪后再切换到保存的专区（见 init 中的分类流收集）。
            selectedCategory = FeedCategory(tabKey = ZONE_TAB_KEY_ALL, name = "全部"),
            selectedSubType = initialSubType
        )
    )
    val uiState: StateFlow<HomeFeedUiState> = _uiState.asStateFlow()

    private var initialLoadJob: Job? = null

    /** 二级板块列表的异步请求（单专区 `zone/info`）；切专区时先取消，避免过期响应覆盖。 */
    private var boardsJob: Job? = null

    /** 讨论板块的请求 Job（首屏 / 刷新 / 分页共用；切板块不取消，切回时复用已有状态）。 */
    private var postsJob: Job? = null

    // 各「作用域 + 板块」的滚动记忆（仅内存；persist 到 DataStore 属 #156）
    private val boardScrollMemories = mutableMapOf<String, HomeBoardScrollMemory>()

    /** 当前生效的滚动记忆键：`{scope.stateKey()}|{board.type}`。 */
    val currentBoardMemoryKey: String
        get() = homeBoardMemoryKey(_uiState.value.zoneScope, _uiState.value.selectedBoard)

    /** 读取指定键的滚动记忆（未记录时返回全 0 默认值，不写回）。 */
    fun scrollMemoryFor(boardKey: String): HomeBoardScrollMemory =
        boardScrollMemories[boardKey] ?: HomeBoardScrollMemory()

    /** 保存指定「作用域 + 板块」的滚动位置。 */
    fun saveBoardScrollPosition(boardKey: String, index: Int, offset: Int) {
        if (index < 0) return
        boardScrollMemories[boardKey] = scrollMemoryFor(boardKey).copy(index = index, offset = offset)
    }

    /** 保存指定「作用域 + 板块」的筛选层折叠比例。 */
    fun saveBoardFilterCollapseFraction(boardKey: String, fraction: Float) {
        boardScrollMemories[boardKey] =
            scrollMemoryFor(boardKey).copy(filterCollapseFraction = fraction.coerceIn(0f, 1f))
    }

    /** 清空指定「作用域 + 板块」的滚动记忆（切筛选 / 回顶刷新 / 切换选择时复位）。 */
    fun resetBoardScrollMemory(boardKey: String) {
        boardScrollMemories[boardKey] = HomeBoardScrollMemory()
    }

    /** 当前键的滚动记忆（宿主首屏恢复与现有单键测试使用的薄封装）。 */
    val savedScrollIndex: Int
        get() = scrollMemoryFor(currentBoardMemoryKey).index
    val savedScrollOffset: Int
        get() = scrollMemoryFor(currentBoardMemoryKey).offset
    val savedFilterCollapseFraction: Float
        get() = scrollMemoryFor(currentBoardMemoryKey).filterCollapseFraction

    fun saveScrollPosition(index: Int, offset: Int) {
        saveBoardScrollPosition(currentBoardMemoryKey, index, offset)
    }

    fun saveFilterCollapseFraction(fraction: Float) {
        saveBoardFilterCollapseFraction(currentBoardMemoryKey, fraction)
    }

    fun resetScrollPosition() {
        resetBoardScrollMemory(currentBoardMemoryKey)
    }

    init {
        // 监听专区与设置变化，构建动态分类列表与顶栏切换器分组
        viewModelScope.launch {
            combine(
                zoneRepository.focusZonesFlow,
                zoneRepository.settingsFlow,
                focusZonesLoaded
            ) { focusZones, settings, loaded ->
                Triple(focusZones, settings, loaded)
            }.collect { (focusZones, settings, loaded) ->
                val dynamicCategories = buildFeedCategories(focusZones, settings)
                val switcherGroups = buildZoneSwitcherGroups(dynamicCategories, settings)
                val current = _uiState.value

                // 一级冷启动判定：`follow`/非法值立即回落「全部」；单专区要等关注专区数据就绪
                // 后才判定「是否仍在已关注集合中」，避免首帧空列表误判回落（#156 恢复规则 1）。
                var coldStartResolvedTabKey: String? = null
                if (!coldStartTabResolved && loaded) {
                    coldStartTabResolved = true
                    coldStartResolvedTabKey = resolveInitialTabKey(
                        savedTabKey,
                        dynamicCategories.map { it.tabKey }
                    )
                }
                val desiredTabKey = coldStartResolvedTabKey ?: current.selectedCategory.tabKey
                val matchedCategory = resolveZoneSwitcherCategory(dynamicCategories, desiredTabKey)
                    ?: dynamicCategories.firstOrNull()
                    ?: FeedCategory(ZONE_TAB_KEY_ALL, "全部")

                val categoryChanged = matchedCategory.tabKey != current.selectedCategory.tabKey ||
                    matchedCategory.zoneIds != current.selectedCategory.zoneIds

                // 切专区时二级板块先同步回退到该作用域的兜底列表，再按需异步取服务端 tabs；
                // 落点按恢复规则 2：持久化键前缀匹配且板块可用才恢复，否则取该专区首板块。
                val scope = matchedCategory.toZoneQueryScope()
                val nextBoards = if (categoryChanged) immediateHomeBoards(scope) else current.boards
                val nextBoard = boardForSelection(matchedCategory.tabKey, nextBoards)
                    ?: current.selectedBoard

                _uiState.update {
                    it.copy(
                        categories = dynamicCategories,
                        selectedCategory = matchedCategory,
                        switcherGroups = switcherGroups,
                        boards = nextBoards,
                        selectedBoard = nextBoard,
                        // 作用域变了：讨论板块内容属于旧作用域，必须整体作废，避免把别的专区帖子当成本专区数据
                        posts = if (categoryChanged) it.posts.resetForScope() else it.posts
                    )
                }

                // 冷启动恢复可能直接把落点定在讨论板块（`feed_zone_board` 命中）：补一次懒加载
                ensurePostsLoaded()

                if (coldStartResolvedTabKey != null) {
                    // 冷启动落点确定（含回落「全部」）：补上被推迟的首屏加载，专区变化时装配板块
                    loadInitialFeed()
                    if (categoryChanged) syncBoards(scope)
                } else if (categoryChanged) {
                    if (!current.isInitialLoading) {
                        loadInitialFeed()
                    }
                    syncBoards(scope)
                }
            }
        }

        // 启动时立即同步一次专区与设置，不阻塞等待会话预热；返回后标记关注专区数据就绪，
        // 供一级恢复判定「保存的单专区是否仍被关注」。
        viewModelScope.launch {
            zoneRepository.getZoneHomeTabs()
            focusZonesLoaded.value = true
        }

        setupAccountState()

        // 单专区冷启动需等关注专区数据；其余情况（全部/关注/非法值）可立即加载首屏。
        if (coldStartTabResolved) {
            loadInitialFeed()
        }
    }

    /**
     * 依据恢复规则给出二级板块落点：`feed_zone_board` 前缀与当前一级 tabKey 匹配且该板块在
     * [boards] 中才恢复，否则取该专区首板块；[boards] 为空时返回 null。
     *
     * [currentBoardSelectionKey] 在用户切换专区 / 板块时同步更新，因此该函数对运行期选择同样幂等。
     */
    private fun boardForSelection(tabKey: String, boards: List<ZoneBoard>): ZoneBoard? {
        if (boards.isEmpty()) return null
        val restoredType = resolveInitialBoardType(currentBoardSelectionKey, tabKey, boards.map { it.type })
        return boards.firstOrNull { it.type == restoredType } ?: boards.first()
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
        // 用户已主动选专区：冷启动恢复不再回填，避免异步判定的保存值覆盖当前选择
        coldStartTabResolved = true
        // 不清理旧作用域的滚动记忆：切回时按「作用域 + 板块」键各自恢复
        val scope = category.toZoneQueryScope()
        val fallbackBoards = immediateHomeBoards(scope)
        // 切专区：二级板块重置为该专区首板块，并把「一级|首板块」写回偏好（#156 恢复规则 4）
        val firstBoard = immediateHomeSelectedBoard(scope)
        currentBoardSelectionKey = encodeBoardKey(category.tabKey, firstBoard.type)
        _uiState.update {
            it.copy(
                selectedCategory = category,
                boards = fallbackBoards,
                selectedBoard = firstBoard,
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null,
                posts = it.posts.resetForScope()
            )
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(category.tabKey)
            viewPreferencesManager.setFeedCategory(category.id)
            persistBoardSelection()
        }
        syncBoards(scope)
        loadInitialFeed()
    }

    /**
     * 切换二级板块：就地切换、不整页重建，不清空其它板块状态。
     *
     * 讨论板块**按需懒加载**：首次切到该板块时才发起首屏请求；切走再切回复用已有内容与分页游标
     * （滚动位置 / 折叠比例由宿主 UI 按「作用域 + 板块」记忆）。
     * 选择同时写回 `feed_zone_board = {tabKey}|{type}`（#156 冷启动恢复规则）；
     * 后续 #157–#161 接入新板块时在此按板块补懒加载。
     */
    fun selectBoard(board: ZoneBoard) {
        val current = _uiState.value
        if (current.selectedBoard == board || board !in current.boards) return
        currentBoardSelectionKey = encodeBoardKey(current.selectedCategory.tabKey, board.type)
        _uiState.update { it.copy(selectedBoard = board) }
        if (board == ZoneBoard.Posts) ensurePostsLoaded()
        viewModelScope.launch { persistBoardSelection() }
    }

    /** 把 [currentBoardSelectionKey] 写入偏好（空键表示尚未选择，不落盘）。 */
    private suspend fun persistBoardSelection() {
        currentBoardSelectionKey
            .takeIf { it.isNotBlank() }
            ?.let { viewPreferencesManager.setFeedZoneBoard(it) }
    }

    /**
     * 讨论板块排序切换：重置首屏（清空条目 + 页码回 1）并重新请求。
     *
     * 与动态板块 `selectSubType` 同语义：切换筛选必须回顶（宿主 `LaunchedEffect` 监听
     * `posts.order` 复位滚动），且不得沿用旧排序的分页游标。
     */
    fun selectPostsOrder(order: String) {
        if (_uiState.value.posts.order == order) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                posts = it.posts.copy(
                    order = order,
                    items = emptyList(),
                    pinnedPosts = emptyList(),
                    page = 1,
                    isLoaded = false,
                    isInitialLoading = true,
                    isLoadingMore = false,
                    hasMore = true,
                    errorMessage = null,
                    loadMoreError = null
                )
            )
        }
        loadInitialPosts()
    }

    /** 当前板块首屏错误态「重试」：按板块分派，避免讨论板块重试时误刷动态流。 */
    fun retrySelectedBoard() {
        if (_uiState.value.isPostsBoard) loadInitialPosts() else loadInitialFeed()
    }

    /** 当前板块触底翻页：按板块分派，避免讨论板块触底时把动态游标推错。 */
    fun loadMoreSelectedBoard() {
        if (_uiState.value.isPostsBoard) loadNextPostsPage() else loadNextPage()
    }

    /** 懒加载哨兵：仅在讨论板块被选中、当前作用域允许且尚未请求过时触发首屏。 */
    private fun ensurePostsLoaded() {
        val state = _uiState.value
        if (!state.isPostsBoard) return
        // 安全闸门：板块列表里没有讨论（如关注聚合按 homeBoardsFor 隐藏）时绝不发请求
        if (ZoneBoard.Posts !in state.boards) return
        if (state.zoneScope.isFollowEmpty) return
        if (state.posts.isLoaded || state.posts.isInitialLoading) return
        loadInitialPosts()
    }

    /**
     * 讨论板块首屏 / 排序切换后的重载。
     *
     * 请求参数按作用域派生：单专区 `zoneNodeId=<nodeId>`；聚合·全部省略（`zoneNodeId=null` 即全站）。
     * 关注聚合没有多专区通道，本方法不会被调用（`homeBoardsFor` 已隐藏该板块 + [ensurePostsLoaded] 兜底）。
     */
    fun loadInitialPosts() {
        val state = _uiState.value
        if (ZoneBoard.Posts !in state.boards) return
        if (state.zoneScope.isFollowEmpty) return
        postsJob?.cancel()
        postsJob = viewModelScope.launch {
            _uiState.update { it.copy(posts = it.posts.copy(isInitialLoading = true, errorMessage = null)) }
            val scope = _uiState.value.zoneScope
            val result = zoneRepository.getZonePosts(
                zoneNodeId = scope.zoneNodeId(),
                order = _uiState.value.posts.order,
                page = 1,
                pageSize = PostsBoardOptions.PAGE_SIZE
            )
            // 请求期间作用域已切换：丢弃过期响应，避免把别的专区的帖子写进当前状态
            if (_uiState.value.zoneScope != scope) return@launch
            result.onSuccess { rawList ->
                applyPostsFirstPage(rawList)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        posts = it.posts.copy(
                            isInitialLoading = false,
                            errorMessage = error.message ?: "加载讨论失败"
                        )
                    )
                }
            }
        }
    }

    /** 讨论板块触底翻页（页码 + 与置顶/普通帖按 id 去重，与专区详情语义一致）。 */
    fun loadNextPostsPage() {
        val state = _uiState.value
        val posts = state.posts
        if (!state.isPostsBoard) return
        if (!posts.isLoaded || posts.isInitialLoading || posts.isLoadingMore || !posts.hasMore) return
        if (state.isRefreshing) return
        val nextPage = posts.page + 1
        _uiState.update { it.copy(posts = it.posts.copy(isLoadingMore = true, loadMoreError = null)) }
        viewModelScope.launch {
            val scope = _uiState.value.zoneScope
            val result = zoneRepository.getZonePosts(
                zoneNodeId = scope.zoneNodeId(),
                order = _uiState.value.posts.order,
                page = nextPage,
                pageSize = PostsBoardOptions.PAGE_SIZE
            )
            if (_uiState.value.zoneScope != scope) return@launch
            result.onSuccess { newItems ->
                _uiState.update { current ->
                    val existingIds = (current.posts.pinnedPosts.map { it.id } +
                        current.posts.items.map { it.id }).toSet()
                    val uniqueNewItems = newItems.filterNot { it.id in existingIds }
                    current.copy(
                        posts = current.posts.copy(
                            items = current.posts.items + uniqueNewItems,
                            page = nextPage,
                            isLoadingMore = false,
                            hasMore = newItems.size >= PostsBoardOptions.PAGE_SIZE &&
                                uniqueNewItems.isNotEmpty(),
                            loadMoreError = null
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        posts = it.posts.copy(
                            isLoadingMore = false,
                            loadMoreError = error.message ?: "加载更多失败"
                        )
                    )
                }
            }
        }
    }

    /** 讨论首屏结果落地：单专区切分置顶帖，聚合·全部所有条目都进普通列表（无置顶语义）。 */
    private fun applyPostsFirstPage(rawList: List<PostItem>) {
        val showPinned = _uiState.value.zoneScope is ZoneQueryScope.Single
        val (pinned, unpinned) = if (showPinned) {
            PostsBoardOptions.partition(rawList)
        } else {
            emptyList<PostItem>() to rawList
        }
        val distinctItems = unpinned.distinctBy { it.id }
        _uiState.update {
            it.copy(
                posts = it.posts.copy(
                    items = distinctItems,
                    pinnedPosts = pinned,
                    page = 1,
                    isLoaded = true,
                    isInitialLoading = false,
                    isLoadingMore = false,
                    hasMore = rawList.size >= PostsBoardOptions.PAGE_SIZE,
                    errorMessage = null,
                    loadMoreError = null
                )
            )
        }
    }

    /** 板块列表空态的重试入口：重新解析当前作用域板块，并补一次当前板块内容刷新。 */
    fun refreshBoards() {
        syncBoards(_uiState.value.zoneScope)
        retrySelectedBoard()
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
        // 用户已主动重置选择：冷启动恢复不再回填
        coldStartTabResolved = true
        val scope = defaultCategory.toZoneQueryScope()
        val fallbackBoards = immediateHomeBoards(scope)
        val firstBoard = immediateHomeSelectedBoard(scope)
        // 重置同时回到默认专区首板块，并保持二级偏好键与一级/板块选中态一致
        currentBoardSelectionKey = encodeBoardKey(defaultCategory.tabKey, firstBoard.type)
        _uiState.update {
            it.copy(
                selectedCategory = defaultCategory,
                selectedSubType = defaultSubType,
                boards = fallbackBoards,
                selectedBoard = firstBoard,
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null,
                // 讨论排序属板块级筛选，resetFilters 不重置它（与「筛选状态按板块记忆」一致）；
                // 但作用域/内容必须作废，切回讨论板块时按当前作用域重新拉取。
                posts = it.posts.resetForScope()
            )
        }
        resetBoardScrollMemory(homeBoardMemoryKey(scope, firstBoard))
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(defaultCategory.tabKey)
            viewPreferencesManager.setFeedCategory(defaultCategory.id)
            viewPreferencesManager.setFeedSubType(defaultSubType)
            persistBoardSelection()
        }
        syncBoards(scope)
        loadInitialFeed()
    }

    fun refreshFeed() = refresh()

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                loadMoreError = null,
                posts = it.posts.copy(errorMessage = null, loadMoreError = null)
            )
        }
        fetchUnreadMessages()

        // 按当前板块分派刷新：讨论板块是独立请求链路，不能借用动态流的游标
        if (_uiState.value.isPostsBoard) {
            refreshPosts()
            return
        }

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

    /** 讨论板块下拉刷新 / 回顶刷新：首屏重载并结束 `isRefreshing`。 */
    private fun refreshPosts() {
        postsJob?.cancel()
        postsJob = viewModelScope.launch {
            val scope = _uiState.value.zoneScope
            val result = zoneRepository.getZonePosts(
                zoneNodeId = scope.zoneNodeId(),
                order = _uiState.value.posts.order,
                page = 1,
                pageSize = PostsBoardOptions.PAGE_SIZE
            )
            if (_uiState.value.zoneScope != scope) {
                _uiState.update { it.copy(isRefreshing = false) }
                return@launch
            }
            result.onSuccess { rawList ->
                applyPostsFirstPage(rawList)
                _uiState.update { it.copy(isRefreshing = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        posts = it.posts.copy(
                            isInitialLoading = false,
                            errorMessage = error.message ?: "刷新失败"
                        )
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

    /**
     * 同步当前作用域的二级板块列表（顺序即 Tab 顺序）。
     *
     * - 聚合态（全部 / 关注）：客户端固定列表，无网络请求；
     * - 单专区：异步取 `zone/info.tabs`，用 `ZoneBoard.tabsFor` 与服务端顺序求交；请求中 / 失败保持
     *   兜底 `[ZoneBoard.Action]`；请求期间用户切走则丢弃过期响应。
     *
     * 首页只借用 `zone/info` 的 tabs，不是「进入专区」，因此 `reportVisit = false`，
     * 避免每次切专区都上报访问记录、污染「最近访问」排序（专区详情保持默认 true）。
     */
    private fun syncBoards(scope: ZoneQueryScope) {
        boardsJob?.cancel()
        if (scope !is ZoneQueryScope.Single) return
        boardsJob = viewModelScope.launch {
            val result = zoneRepository.getZoneInfo(scope.nodeId, reportVisit = false)
            if (_uiState.value.zoneScope != scope) return@launch
            val boards = result.getOrNull()
                ?.let { info -> ZoneBoard.tabsFor(info.tabs, ZoneBoard.HOME_IMPLEMENTED) }
                ?: HOME_BOARD_FALLBACK
            _uiState.update { state ->
                if (state.zoneScope != scope) return@update state
                // 服务端 tabs 到达后重新套用恢复规则：冷启动保存的板块此时才可能出现在列表中
                val restoredBoard = boardForSelection(state.selectedCategory.tabKey, boards)
                state.copy(
                    boards = boards,
                    selectedBoard = restoredBoard ?: state.selectedBoard
                )
            }
            // 板块列表落定后，若当前仍停在讨论板块（例如刷新板块列表），补一次懒加载
            ensurePostsLoaded()
        }
    }

    private fun parseCursorTimestamp(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return DateTimeFormats.parseServerDateTime(dateStr) ?: 0L
    }
}
