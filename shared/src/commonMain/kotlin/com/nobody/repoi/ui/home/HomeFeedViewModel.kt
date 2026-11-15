package com.nobody.repoi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.AlbumCategoryOption
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

    // 分享（相册）板块状态：与动态板块完全独立、按板块懒加载（首次切到分享才请求）
    val albumItems: List<AlbumListItem> = emptyList(),
    val albumPage: Int = 1,
    val albumHasMore: Boolean = true,
    val isAlbumLoading: Boolean = false,
    val isLoadingMoreAlbum: Boolean = false,
    val albumErrorMessage: String? = null,
    val albumLoadMoreError: String? = null,
    /** 当前排序（键值见 [AlbumBoardOptions.ORDERS]）；按板块记忆，切走再切回保留。 */
    val albumSelectedOrder: String = AlbumBoardOptions.DEFAULT_ORDER,
    /** 当前分类；`null` = 全部分类。仅单专区作用域会产生候选值。 */
    val albumSelectedCategory: Int? = null,
    /** 分类候选（单专区且服务端下发 ≥2 个有效分类时非空；聚合态恒为空）。 */
    val albumCategoryOptions: List<AlbumCategoryOption> = emptyList(),
    /** 是否已发起过首屏请求（懒加载去重 + 失败/空态后不自动重试）。 */
    val isAlbumLoaded: Boolean = false,

    val userAvatar: String? = null,
    val unreadMessageCount: Int = 0
) {
    /** 查询作用域（由 [selectedCategory] 派生，避免两处状态漂移）。 */
    val zoneScope: ZoneQueryScope
        get() = selectedCategory.toZoneQueryScope()

    /** 当前板块条目数（内容区四态 / 触底翻页 / 折叠可滚动性共用，避免宿主散写 `when`）。 */
    val currentBoardItemCount: Int
        get() = when (selectedBoard) {
            ZoneBoard.Album -> albumItems.size
            else -> items.size
        }

    /** 当前板块是否还有下一页。 */
    val currentBoardHasMore: Boolean
        get() = when (selectedBoard) {
            ZoneBoard.Album -> albumHasMore
            else -> hasMore
        }

    /** 当前板块是否正在追加下一页。 */
    val currentBoardIsLoadingMore: Boolean
        get() = when (selectedBoard) {
            ZoneBoard.Album -> isLoadingMoreAlbum
            else -> isLoadingMore
        }

    /** 当前板块追加失败信息（页脚重试用）。 */
    val currentBoardLoadMoreError: String?
        get() = when (selectedBoard) {
            ZoneBoard.Album -> albumLoadMoreError
            else -> loadMoreError
        }

    /** 当前板块首屏加载中（宿主统一用 `PoiLoadingView` 展示）。 */
    val isCurrentBoardLoading: Boolean
        get() = when (selectedBoard) {
            ZoneBoard.Album -> isAlbumLoading && albumItems.isEmpty()
            else -> isInitialLoading
        }

    /** 当前板块首屏错误（仅在列表为空时展示错误态）。 */
    val currentBoardErrorMessage: String?
        get() = when (selectedBoard) {
            ZoneBoard.Album -> albumErrorMessage
            else -> errorMessage
        }

    /** 当前板块内容快照（传给共享面板 `ZoneBoardPane`，保证两屏同一渲染口径）。 */
    val currentBoardContent: ZoneBoardContent
        get() = when (selectedBoard) {
            ZoneBoard.Album -> ZoneBoardContent.Album(
                items = albumItems,
                page = ZoneBoardPageView(
                    isLoading = isAlbumLoading,
                    isLoadingMore = isLoadingMoreAlbum,
                    errorMessage = albumErrorMessage,
                    loadMoreError = albumLoadMoreError,
                    isEndReached = !albumHasMore
                )
            )
            else -> ZoneBoardContent.Action(
                items = items,
                page = ZoneBoardPageView(
                    isLoading = isInitialLoading,
                    isLoadingMore = isLoadingMore,
                    errorMessage = errorMessage,
                    loadMoreError = loadMoreError,
                    isEndReached = !hasMore
                )
            )
        }

    /** 当前板块筛选值快照（传给共享面板/筛选条）。 */
    val currentBoardFilters: ZoneBoardFilters
        get() = when (selectedBoard) {
            ZoneBoard.Album -> ZoneBoardFilters(
                albumOrder = albumSelectedOrder,
                albumCategory = albumSelectedCategory
            )
            else -> ZoneBoardFilters(subType = selectedSubType)
        }
}

class HomeFeedViewModel(
    private val homeRepository: HomeRepository,
    private val albumRepository: AlbumRepository,
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

    /**
     * 分享板块 6 档排序（key = 协议 `order`，value = 文案）。
     *
     * 唯一来源为共享定义 [AlbumBoardOptions]，与专区详情共用，禁止在首页另写一份。
     */
    val albumOrders: List<Pair<String, String>> = AlbumBoardOptions.ORDERS

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

    /** 二级板块列表的异步请求（单专区 `zone/info`）；切专区时先取消，避免过期响应覆盖。 */
    private var boardsJob: Job? = null

    /** 分享板块首屏 / 下一页请求（切排序、切分类、切专区时取消，避免过期响应覆盖新列表）。 */
    private var albumJob: Job? = null

    /** 分享板块分类候选请求（单专区；切专区时取消）。 */
    private var albumCategoriesJob: Job? = null

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
                zoneRepository.settingsFlow
            ) { focusZones, settings ->
                focusZones to settings
            }.collect { (focusZones, settings) ->
                val dynamicCategories = buildFeedCategories(focusZones, settings)
                val switcherGroups = buildZoneSwitcherGroups(dynamicCategories, settings)
                val current = _uiState.value
                val matchedCategory = dynamicCategories.find { it.tabKey == current.selectedCategory.tabKey }
                    ?: dynamicCategories.find { it.tabKey == initialTabKey }
                    ?: dynamicCategories.firstOrNull()
                    ?: FeedCategory("all", "全部")

                val categoryChanged = matchedCategory.tabKey != current.selectedCategory.tabKey ||
                    matchedCategory.zoneIds != current.selectedCategory.zoneIds

                // 切专区时二级板块先同步回退到该作用域的兜底列表（首板块），再按需异步取服务端 tabs。
                val scope = matchedCategory.toZoneQueryScope()
                val nextBoards = if (categoryChanged) immediateHomeBoards(scope) else current.boards
                val nextBoard = if (categoryChanged) immediateHomeSelectedBoard(scope) else current.selectedBoard

                _uiState.update {
                    it.copy(
                        categories = dynamicCategories,
                        selectedCategory = matchedCategory,
                        switcherGroups = switcherGroups,
                        boards = nextBoards,
                        selectedBoard = nextBoard
                    ).let { next -> if (categoryChanged) next.resetAlbumForScopeChange() else next }
                }

                if (categoryChanged) {
                    cancelAlbumWork()
                    if (!current.isInitialLoading) {
                        loadInitialFeed()
                    }
                    syncBoards(scope)
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
        // 不清理旧作用域的滚动记忆：切回时按「作用域 + 板块」键各自恢复
        val scope = category.toZoneQueryScope()
        val fallbackBoards = immediateHomeBoards(scope)
        _uiState.update {
            it.copy(
                selectedCategory = category,
                boards = fallbackBoards,
                selectedBoard = immediateHomeSelectedBoard(scope),
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            ).resetAlbumForScopeChange()
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(category.tabKey)
            viewPreferencesManager.setFeedCategory(category.id)
        }
        cancelAlbumWork()
        syncBoards(scope)
        loadInitialFeed()
    }

    /**
     * 切换二级板块：就地切换、不整页重建，不清空其它板块状态。
     *
     * 分享板块按「懒加载」语义：只有首次切到分享时才请求首屏；切走再切回直接复用已加载的
     * items / 排序 / 分类状态（滚动位置记忆键见 [homeBoardMemoryKey]）。
     */
    fun selectBoard(board: ZoneBoard) {
        val current = _uiState.value
        if (current.selectedBoard == board || board !in current.boards) return
        _uiState.update { it.copy(selectedBoard = board) }
        if (board == ZoneBoard.Album) ensureAlbumLoaded()
    }

    /** 分享板块懒加载入口：首次切到分享时拉首屏 + 单专区分类候选；已加载/加载中不重复请求。 */
    private fun ensureAlbumLoaded() {
        val state = _uiState.value
        if (state.isAlbumLoaded || state.isAlbumLoading) return
        // 同步置加载态，避免「切到分享」与协程启动之间渲染一帧空态卡片
        _uiState.update { it.copy(isAlbumLoading = true) }
        loadInitialAlbum()
        syncAlbumCategories()
    }

    /**
     * 切换分享排序：清空列表 + 回第一页 + 重新请求（与专区详情既有语义一致）。
     *
     * 不清理滚动记忆——宿主 UI 在筛选变化时统一复位到顶并展开筛选条。
     */
    fun selectAlbumOrder(order: String) {
        if (order !in AlbumBoardOptions.ORDER_KEYS) return
        if (_uiState.value.albumSelectedOrder == order) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                albumSelectedOrder = order,
                albumItems = emptyList(),
                albumPage = 1,
                albumHasMore = true,
                isAlbumLoading = true,
                isLoadingMoreAlbum = false,
                albumErrorMessage = null,
                albumLoadMoreError = null
            )
        }
        loadInitialAlbum()
    }

    /** 切换分享分类（`null` = 全部分类）：清空列表 + 回第一页 + 重新请求。 */
    fun selectAlbumCategory(categoryId: Int?) {
        if (_uiState.value.albumSelectedCategory == categoryId) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                albumSelectedCategory = categoryId,
                albumItems = emptyList(),
                albumPage = 1,
                albumHasMore = true,
                isAlbumLoading = true,
                isLoadingMoreAlbum = false,
                albumErrorMessage = null,
                albumLoadMoreError = null
            )
        }
        loadInitialAlbum()
    }

    /** 当前板块错误态的重试入口（动态 / 分享各自重载首屏）。 */
    fun retryCurrentBoard() {
        if (_uiState.value.selectedBoard == ZoneBoard.Album) {
            loadInitialAlbum()
        } else {
            loadInitialFeed()
        }
    }

    /** 板块列表空态的重试入口：重新解析当前作用域板块，并补一次内容刷新。 */
    fun refreshBoards() {
        syncBoards(_uiState.value.zoneScope)
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
        val scope = defaultCategory.toZoneQueryScope()
        val fallbackBoards = immediateHomeBoards(scope)
        _uiState.update {
            it.copy(
                selectedCategory = defaultCategory,
                selectedSubType = defaultSubType,
                boards = fallbackBoards,
                selectedBoard = immediateHomeSelectedBoard(scope),
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            ).resetAlbumForScopeChange().copy(
                albumSelectedOrder = AlbumBoardOptions.DEFAULT_ORDER,
                albumSelectedCategory = null
            )
        }
        resetBoardScrollMemory(homeBoardMemoryKey(scope, immediateHomeSelectedBoard(scope)))
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(defaultCategory.tabKey)
            viewPreferencesManager.setFeedCategory(defaultCategory.id)
            viewPreferencesManager.setFeedSubType(defaultSubType)
        }
        syncBoards(scope)
        loadInitialFeed()
    }

    fun refreshFeed() = refresh()

    /**
     * 刷新当前板块：动态走游标首屏，分享走页码第一页，两者共用 [HomeFeedUiState.isRefreshing]，
     * 下拉指示器 / FAB 无需按板块区分。
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                loadMoreError = null,
                albumErrorMessage = null,
                albumLoadMoreError = null
            )
        }
        fetchUnreadMessages()
        if (_uiState.value.selectedBoard == ZoneBoard.Album) {
            refreshAlbum()
        } else {
            refreshActionBoard()
        }
    }

    private fun refreshActionBoard() {
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

    /** 分享板块刷新：页码回第一页，参数与首屏一致（`zones` 作用域 + `order` + `category`）。 */
    private fun refreshAlbum() {
        if (_uiState.value.albumCategoryOptions.isEmpty()) syncAlbumCategories()
        albumJob?.cancel()
        albumJob = viewModelScope.launch {
            val state = _uiState.value
            val scope = state.zoneScope
            if (scope.isFollowEmpty) {
                _uiState.update {
                    it.copy(
                        albumItems = emptyList(),
                        albumPage = 1,
                        albumHasMore = false,
                        isAlbumLoading = false,
                        isLoadingMoreAlbum = false,
                        isRefreshing = false,
                        isAlbumLoaded = true,
                        albumLoadMoreError = null
                    )
                }
                return@launch
            }
            val result = albumRepository.getAlbums(
                page = 1,
                pageSize = AlbumBoardOptions.PAGE_SIZE,
                orderString = state.albumSelectedOrder,
                zones = scope.zonesParam(),
                category = state.albumSelectedCategory
            )
            result.onSuccess { page ->
                val distinctList = page.items.distinctBy { it.targetAlbumId }
                _uiState.update {
                    it.copy(
                        albumItems = distinctList,
                        albumPage = 1,
                        isRefreshing = false,
                        isAlbumLoading = false,
                        albumHasMore = page.hasMore,
                        isAlbumLoaded = true,
                        albumLoadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isAlbumLoading = false,
                        albumErrorMessage = error.message ?: "刷新失败"
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
        if (currentState.selectedBoard == ZoneBoard.Album) {
            loadNextAlbumPage()
            return
        }
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

    /** 分享板块首屏（懒加载 / 切排序 / 切分类 / 错误重试共用）：页码回第一页。 */
    fun loadInitialAlbum() {
        albumJob?.cancel()
        albumJob = viewModelScope.launch {
            val state = _uiState.value
            val scope = state.zoneScope
            // 关注为空：短路不发请求（空 zones 会被服务端当作全站，属假「关注」内容）
            if (scope.isFollowEmpty) {
                _uiState.update {
                    it.copy(
                        albumItems = emptyList(),
                        albumPage = 1,
                        albumHasMore = false,
                        isAlbumLoading = false,
                        isLoadingMoreAlbum = false,
                        isAlbumLoaded = true,
                        albumErrorMessage = null,
                        albumLoadMoreError = null
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isAlbumLoading = true, albumErrorMessage = null) }
            val result = albumRepository.getAlbums(
                page = 1,
                pageSize = AlbumBoardOptions.PAGE_SIZE,
                orderString = state.albumSelectedOrder,
                zones = scope.zonesParam(),
                category = state.albumSelectedCategory
            )
            result.onSuccess { page ->
                val distinctList = page.items.distinctBy { it.targetAlbumId }
                _uiState.update {
                    it.copy(
                        albumItems = distinctList,
                        albumPage = 1,
                        isAlbumLoading = false,
                        albumHasMore = page.hasMore,
                        isAlbumLoaded = true,
                        albumErrorMessage = null,
                        albumLoadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAlbumLoading = false,
                        isAlbumLoaded = true,
                        albumErrorMessage = error.message ?: "加载分享失败"
                    )
                }
            }
        }
    }

    /** 分享板块下一页：页码 +1，按 `targetAlbumId` 去重合并；切排序/分类后过期响应丢弃。 */
    fun loadNextAlbumPage() {
        val state = _uiState.value
        if (state.isLoadingMoreAlbum || !state.albumHasMore || state.isAlbumLoading || state.isRefreshing) return
        val scope = state.zoneScope
        if (!state.isAlbumLoaded || scope.isFollowEmpty) return
        val requestedPage = state.albumPage + 1
        val requestedOrder = state.albumSelectedOrder
        val requestedCategory = state.albumSelectedCategory
        _uiState.update { it.copy(isLoadingMoreAlbum = true, albumLoadMoreError = null) }
        viewModelScope.launch {
            val result = albumRepository.getAlbums(
                page = requestedPage,
                pageSize = AlbumBoardOptions.PAGE_SIZE,
                orderString = requestedOrder,
                zones = scope.zonesParam(),
                category = requestedCategory
            )
            result.onSuccess { page ->
                _uiState.update { current ->
                    // 期间切换了排序 / 分类 / 作用域：本批已过期，直接丢弃
                    if (current.albumSelectedOrder != requestedOrder ||
                        current.albumSelectedCategory != requestedCategory ||
                        current.albumPage != requestedPage - 1
                    ) {
                        return@update current
                    }
                    val existingIds = current.albumItems.map { it.targetAlbumId }.toSet()
                    val uniqueNewItems = page.items.filterNot { it.targetAlbumId in existingIds }
                    current.copy(
                        albumItems = current.albumItems + uniqueNewItems,
                        albumPage = requestedPage,
                        isLoadingMoreAlbum = false,
                        albumHasMore = page.hasMore && uniqueNewItems.isNotEmpty(),
                        albumLoadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingMoreAlbum = false,
                        albumLoadMoreError = error.message ?: "加载更多失败"
                    )
                }
            }
        }
    }

    /**
     * 同步分享分类候选：仅单专区可用（原版仅在 `zoneIds` 可解析为单个专区时展示分类入口）。
     *
     * 服务端候选少于 2 个时原版不展示分类入口，这里同样返回空列表；切换专区后分类 id 失效，
     * 由 [resetAlbumForScopeChange] 清空。
     */
    private fun syncAlbumCategories() {
        albumCategoriesJob?.cancel()
        val scope = _uiState.value.zoneScope
        if (scope !is ZoneQueryScope.Single) {
            _uiState.update { it.copy(albumCategoryOptions = emptyList(), albumSelectedCategory = null) }
            return
        }
        albumCategoriesJob = viewModelScope.launch {
            val result = albumRepository.getAlbumCategories(scope.nodeId)
            if (_uiState.value.zoneScope != scope) return@launch
            val options = result.getOrNull().orEmpty()
                .map { AlbumCategoryOption(it.categoryId, it.name.orEmpty()) }
                .filter { it.id > 0 && it.name.isNotBlank() }
                .takeIf { it.size > 1 }
                .orEmpty()
            _uiState.update {
                it.copy(
                    albumCategoryOptions = options,
                    albumSelectedCategory = it.albumSelectedCategory
                        .takeIf { selected -> options.any { option -> option.id == selected } }
                )
            }
        }
    }

    /** 作用域变化时丢弃分享板块的列表与分类候选（排序作为板块偏好保留）。 */
    private fun cancelAlbumWork() {
        albumJob?.cancel()
        albumCategoriesJob?.cancel()
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
                state.copy(
                    boards = boards,
                    selectedBoard = state.selectedBoard.takeIf { it in boards }
                        ?: boards.firstOrNull()
                        ?: ZoneBoard.Action
                )
            }
        }
    }

    private fun parseCursorTimestamp(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return DateTimeFormats.parseServerDateTime(dateStr) ?: 0L
    }
}

/**
 * 作用域切换时重置分享板块状态：列表/分页/错误/分类候选全部失效（分区 id 与专区相关）。
 *
 * 排序是板块级偏好（不随专区变化），故保留 [HomeFeedUiState.albumSelectedOrder]；
 * 懒加载标记 [HomeFeedUiState.isAlbumLoaded] 归零，等下次切到分享板块再请求。
 */
private fun HomeFeedUiState.resetAlbumForScopeChange(): HomeFeedUiState = copy(
    albumItems = emptyList(),
    albumPage = 1,
    albumHasMore = true,
    isAlbumLoading = false,
    isLoadingMoreAlbum = false,
    albumErrorMessage = null,
    albumLoadMoreError = null,
    albumSelectedCategory = null,
    albumCategoryOptions = emptyList(),
    isAlbumLoaded = false
)
