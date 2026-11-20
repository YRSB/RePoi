package com.nobody.repoi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.remote.model.ZoneTabItem
import com.nobody.repoi.data.remote.model.ZoneTabsSettings
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.AlbumCategoryOption
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardCapability
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.session.BoardScrollPosition
import com.nobody.repoi.ui.board.session.ZoneBoardIntent
import com.nobody.repoi.ui.board.session.ZoneBoardSession
import com.nobody.repoi.ui.board.session.ZoneBoardSessionImpl
import com.nobody.repoi.ui.board.session.ZoneBoardSnapshot
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
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

/** 冷启动判定输入：`combine` 四路流的聚合体（关注专区 / 设置 / 关注数据就绪 / 落盘偏好就绪）。 */
private data class ColdStartInputs(
    val focusZones: List<ZoneItem>,
    val settings: ZoneTabsSettings,
    val focusZonesLoaded: Boolean,
    val savedSelectionReady: Boolean
)

data class HomeFeedUiState(
    val categories: List<FeedCategory> = listOf(FeedCategory("all", "全部")),
    val selectedCategory: FeedCategory = FeedCategory("all", "全部"),
    /** 顶栏专区切换器分组（「快捷」恒含全部/关注，「我的专区」遵循首页设置排序与隐藏）。 */
    val switcherGroups: List<ZoneSwitcherGroup> = defaultZoneSwitcherGroups(),
    /**
     * 二级板块列表（顺序即 Tab 顺序），唯一来源为 `ZoneBoardCapability` 能力矩阵：
     * - 聚合态（全部）= `HOME_ORDER` 全 10 项；关注 = 去掉讨论/产品/二手的 7 项；
     * - 单专区 = 服务端 `zone/info.tabs` ∩ 白名单（请求中/失败先回退 `[ZoneBoard.Action]`）。
     *
     * 为空表示该专区没有可用板块，宿主据此隐藏二级 TabRow 并展示板块空态（不渲染空 Tab 条）。
     */
    val boards: List<ZoneBoard> = ZoneBoardCapability.ALL_BOARDS,
    /**
     * 单专区下各板块的服务端 Tab 文案（`zone/info.tabs[].title`）；聚合态恒为空 Map。
     */
    val boardTitles: Map<ZoneBoard, String> = emptyMap(),
    /** 当前选中的二级板块；切换专区时重置为该专区首板块。 */
    val selectedBoard: ZoneBoard = ZoneBoardCapability.ALL_BOARDS.firstOrNull() ?: ZoneBoard.Action,
    /** 当前单专区相册分类候选（单专区且服务端下发 ≥2 个有效分类时非空；聚合态恒为空）。 */
    val albumCategoryOptions: List<AlbumCategoryOption> = emptyList(),
    val userAvatar: String? = null,
    val unreadMessageCount: Int = 0,
    val actionItems: List<ActionItem> = emptyList(),
    /**
     * 当前活跃板块的只读快照镜像（由 `ZoneBoardSession.activeSnapshot` 收集驱动，避免散装字段）。
     */
    val currentBoardSnapshot: ZoneBoardSnapshot = ZoneBoardSnapshot(
        board = ZoneBoard.Action,
        content = ZoneBoardContent.Action(emptyList(), ZoneBoardPageView(isLoading = true)),
        filters = ZoneBoardFilters(),
        pageView = ZoneBoardPageView(isLoading = true)
    )
) {
    /** 二级 Tab 文案：单专区优先服务端 `tabTitle`，空则回退枚举文案；聚合态无服务端 title。 */
    fun boardTitle(board: ZoneBoard): String =
        boardTitles[board]?.takeIf { it.isNotBlank() } ?: board.label
}

class HomeFeedViewModel(
    val sessionFactory: ZoneBoardSession.Factory,
    private val zoneRepository: ZoneRepository,
    private val viewPreferencesManager: ViewPreferencesManager,
    private val userSession: UserSession,
    private val messageRepository: MessageRepository? = null,
    private val categoryManager: CategoryManager? = null,
    private val albumRepository: AlbumRepository? = null
) : ViewModel() {

    /**
     * 兼容存量构造函数（供存量测试桩与过渡调用，由 #169 最终收口）。
     */
    constructor(
        homeRepository: HomeRepository,
        albumRepository: AlbumRepository,
        resaleRepository: ResaleRepository,
        itemRepository: ItemRepository,
        zoneRepository: ZoneRepository,
        viewPreferencesManager: ViewPreferencesManager,
        userSession: UserSession,
        messageRepository: MessageRepository? = null,
        categoryManager: CategoryManager? = null
    ) : this(
        sessionFactory = ZoneBoardSessionImpl.Factory(
            homeRepository = homeRepository,
            albumRepository = albumRepository,
            zoneRepository = zoneRepository,
            itemRepository = itemRepository,
            resaleRepository = resaleRepository,
            viewPreferencesManager = viewPreferencesManager
        ),
        zoneRepository = zoneRepository,
        viewPreferencesManager = viewPreferencesManager,
        userSession = userSession,
        messageRepository = messageRepository,
        categoryManager = categoryManager,
        albumRepository = albumRepository
    )

    private val _uiState = MutableStateFlow(
        HomeFeedUiState(
            selectedCategory = FeedCategory(tabKey = ZONE_TAB_KEY_ALL, name = "全部"),
            selectedBoard = ZoneBoardCapability.ALL_BOARDS.firstOrNull() ?: ZoneBoard.Action
        )
    )
    val uiState: StateFlow<HomeFeedUiState> = _uiState.asStateFlow()

    /**
     * 专区子板块会话控制器（深模块）。
     *
     * 统合 10 类板块的生命周期、懒加载、分页状态机、筛选不变性与滚动记忆。
     */
    val session: ZoneBoardSession = sessionFactory.create(
        coroutineScope = viewModelScope,
        initialScope = _uiState.value.zoneScope,
        initialBoard = _uiState.value.selectedBoard,
        // issue #198：冷启动落盘偏好异步落定前挂起首屏加载，落定后 resumeInitialLoad()
        // 一次性以最终「专区 + 板块 + 子类」参数加载，杜绝乐观默认请求作废重发导致的双指示器闪烁。
        deferInitialLoad = true
    )

    /**
     * 动态筛选项（key = 协议键值 `subType`，value = 2.43 中文文案）。
     */
    val subTypes: List<Pair<String, String>> =
        ActionSubType.ordered.map { it.apiValue to it.label }

    /**
     * 产品分类树（`parentId -> 子分类`）：供筛选条子分类回显与分类级联弹层。
     */
    val hobbyCategoryTree: StateFlow<Map<Int, List<CategoryItem>>> =
        categoryManager?.categoryMap ?: MutableStateFlow<Map<Int, List<CategoryItem>>>(emptyMap())

    /** 冷启动落盘的一级 tabKey 与二级板块键（`{tabKey}|{type}`，未设置时为空串）。 */
    private var savedTabKey: String? = null
    private var savedBoardKey: String? = null

    /** 冷启动偏好是否已落定（预热完成且 saved* 已读取）：[savedSelectionReady] 对外供分类流触发重算。 */
    private val savedSelectionReady = MutableStateFlow(false)

    /** 关注专区数据就绪信号：`zone/home-tabs` 返回后置 true。 */
    private val focusZonesLoaded = MutableStateFlow(false)

    /** 一级冷启动判定是否已完成。 */
    private var coldStartTabResolved: Boolean = false

    /** 用户是否已主动切换过一级专区；是则预热落定后不再回填保存值。 */
    private var userSelectedCategory: Boolean = false

    /** 当前生效的二级板块偏好键（`{tabKey}|{type}`）：恢复与写回共用，避免出现两套编码。 */
    private var currentBoardSelectionKey: String = ""

    /** 手动切入单专区后，等 `zone/info.tabs` 返回再落到该专区首板块。 */
    private var pendingSingleZoneFirstBoard: Boolean = false

    /** 二级板块列表的异步请求（单专区 `zone/info`）；切专区时先取消，避免过期响应覆盖。 */
    private var boardsJob: Job? = null

    /** 分享板块分类候选请求（单专区；切专区时取消）。 */
    private var albumCategoriesJob: Job? = null

    // 各「作用域 + 板块」的滚动记忆备份（跨专区切换保留）
    private val boardScrollMemories = mutableMapOf<String, HomeBoardScrollMemory>()

    /** 当前生效的滚动记忆键：`{scope.stateKey()}|{board.type}`。 */
    val currentBoardMemoryKey: String
        get() = homeBoardMemoryKey(_uiState.value.zoneScope, _uiState.value.selectedBoard)

    private fun boardFromKey(boardKey: String): ZoneBoard {
        val boardType = boardKey.substringAfterLast('|', "")
        return ZoneBoard.fromType(boardType) ?: _uiState.value.selectedBoard
    }

    /** 读取指定键的滚动记忆（未记录时返回全 0 默认值）。 */
    fun scrollMemoryFor(boardKey: String): HomeBoardScrollMemory {
        return boardScrollMemories[boardKey] ?: session.snapshotFor(boardFromKey(boardKey)).scrollPosition.toHomeScrollMemory()
    }

    /** 读取指定键的滚动记忆；未记录过返回 `null`。 */
    fun scrollMemoryOrNull(boardKey: String): HomeBoardScrollMemory? = boardScrollMemories[boardKey]

    /** 保存指定「作用域 + 板块」的筛选行横向滚动位置。 */
    fun saveBoardFilterRowScroll(boardKey: String, scrollX: Int) {
        if (scrollX < 0) return
        val board = boardFromKey(boardKey)
        val updated = (boardScrollMemories[boardKey] ?: HomeBoardScrollMemory()).copy(filterRowScrollX = scrollX)
        boardScrollMemories[boardKey] = updated
        session.dispatch(ZoneBoardIntent.SaveFilterRowScroll(board, scrollX))
    }

    /** 保存指定「作用域 + 板块」的滚动位置。 */
    fun saveBoardScrollPosition(boardKey: String, index: Int, offset: Int) {
        if (index < 0) return
        val board = boardFromKey(boardKey)
        val updated = (boardScrollMemories[boardKey] ?: HomeBoardScrollMemory()).copy(index = index, offset = offset)
        boardScrollMemories[boardKey] = updated
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(board, index, offset))
    }

    /** 保存指定「作用域 + 板块」的筛选层折叠比例。 */
    fun saveBoardFilterCollapseFraction(boardKey: String, fraction: Float) {
        val board = boardFromKey(boardKey)
        val clamped = fraction.coerceIn(0f, 1f)
        val updated = (boardScrollMemories[boardKey] ?: HomeBoardScrollMemory()).copy(filterCollapseFraction = clamped)
        boardScrollMemories[boardKey] = updated
        session.dispatch(ZoneBoardIntent.SaveFilterCollapseFraction(board, clamped))
    }

    /** 清空指定「作用域 + 板块」的滚动记忆。 */
    fun resetBoardScrollMemory(boardKey: String) {
        val board = boardFromKey(boardKey)
        boardScrollMemories[boardKey] = HomeBoardScrollMemory()
        session.dispatch(ZoneBoardIntent.ResetScrollMemory(board))
    }

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
        // 收集会话控制器的活跃板块快照，同步更新 UI 状态快照镜像
        viewModelScope.launch {
            session.activeSnapshot.collect { snapshot ->
                _uiState.update { it.copy(currentBoardSnapshot = snapshot) }
            }
        }

        // 收集动态板块快照，同步维护 actionItems 兼容状态
        viewModelScope.launch {
            session.actionSnapshot.collect { snapshot ->
                val items = (snapshot.content as? ZoneBoardContent.Action)?.items.orEmpty()
                _uiState.update { it.copy(actionItems = items) }
            }
        }

        // 监听专区与设置变化，构建动态分类列表与顶栏切换器分组
        viewModelScope.launch {
            combine(
                zoneRepository.focusZonesFlow,
                zoneRepository.settingsFlow,
                focusZonesLoaded,
                savedSelectionReady
            ) { focusZones, settings, loaded, savedReady ->
                ColdStartInputs(focusZones, settings, loaded, savedReady)
            }.collect { (focusZones, settings, loaded, savedReady) ->
                val dynamicCategories = buildFeedCategories(focusZones, settings)
                val switcherGroups = buildZoneSwitcherGroups(dynamicCategories, settings)
                val current = _uiState.value

                var coldStartResolvedTabKey: String? = null
                if (!coldStartTabResolved && loaded && savedReady) {
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

                val scope = matchedCategory.toZoneQueryScope()
                val nextBoards = if (categoryChanged) ZoneBoardCapability.homeBoardsFor(scope) else current.boards
                val nextBoard = boardForSelection(matchedCategory.tabKey, nextBoards)
                    ?: current.selectedBoard

                _uiState.update {
                    it.copy(
                        categories = dynamicCategories,
                        selectedCategory = matchedCategory,
                        switcherGroups = switcherGroups,
                        boards = nextBoards,
                        boardTitles = if (categoryChanged) emptyMap() else it.boardTitles,
                        selectedBoard = nextBoard
                    )
                }

                val boardChanged = nextBoard != current.selectedBoard
                if (categoryChanged) {
                    session.updateScope(scope)
                    session.dispatch(ZoneBoardIntent.SelectBoard(nextBoard))
                    // 恢复该作用域目标板块已保存的滚动记忆
                    val memoryKey = homeBoardMemoryKey(scope, nextBoard)
                    boardScrollMemories[memoryKey]?.let { saved ->
                        session.dispatch(ZoneBoardIntent.SaveScrollPosition(nextBoard, saved.index, saved.offset))
                        session.dispatch(ZoneBoardIntent.SaveFilterCollapseFraction(nextBoard, saved.filterCollapseFraction))
                        session.dispatch(ZoneBoardIntent.SaveFilterRowScroll(nextBoard, saved.filterRowScrollX))
                    }
                    if (nextBoard == ZoneBoard.Album) {
                        syncAlbumCategories(scope)
                    }
                } else if (boardChanged) {
                    session.dispatch(ZoneBoardIntent.SelectBoard(nextBoard))
                    if (nextBoard == ZoneBoard.Album) {
                        syncAlbumCategories(scope)
                    }
                }

                if (coldStartResolvedTabKey != null) {
                    if (categoryChanged) syncBoards(scope)
                    // 单区冷启动落定（恢复 z{nodeId} 或不可用回落 all）：解闩恰好一次首屏请求
                    session.resumeInitialLoad()
                } else if (categoryChanged) {
                    syncBoards(scope)
                }
            }
        }

        // 启动时立即同步一次专区与设置
        viewModelScope.launch {
            zoneRepository.getZoneHomeTabs()
            focusZonesLoaded.value = true
        }

        setupAccountState()

        // 冷启动偏好只在预热落定后读取
        viewModelScope.launch {
            viewPreferencesManager.awaitPreheat()
            if (!userSelectedCategory) {
                savedTabKey = viewPreferencesManager.getInitialFeedZoneTabKey()
                savedBoardKey = viewPreferencesManager.getInitialFeedZoneBoardKey()
                currentBoardSelectionKey = savedBoardKey.orEmpty()
                val savedSubType =
                    ActionSubType.fromPersisted(viewPreferencesManager.getInitialFeedSubType()).apiValue
                session.dispatch(ZoneBoardIntent.SelectSubType(savedSubType))
            }
            savedSelectionReady.value = true
            if (!coldStartTabResolved && zoneTabKeyNodeId(savedTabKey) == null) {
                coldStartTabResolved = true
                // 落盘一级为聚合态（all/follow/缺失）：落盘值即最终值，读盘完成即解闩首屏
                session.resumeInitialLoad()
            }
        }
    }

    private fun boardForSelection(tabKey: String, boards: List<ZoneBoard>): ZoneBoard? {
        if (boards.isEmpty()) return null
        val restoredType = resolveInitialBoardType(currentBoardSelectionKey, tabKey, boards.map { it.type })
        return boards.firstOrNull { it.type == restoredType } ?: boards.first()
    }

    fun selectZoneTab(tabKey: String) {
        val category = resolveZoneSwitcherCategory(_uiState.value.categories, tabKey) ?: return
        selectCategory(category)
    }

    fun selectCategory(category: FeedCategory) {
        coldStartTabResolved = true
        userSelectedCategory = true
        if (_uiState.value.selectedCategory.tabKey == category.tabKey) {
            return
        }
        val scope = category.toZoneQueryScope()
        val fallbackBoards = ZoneBoardCapability.homeBoardsFor(scope)
        val firstBoard = fallbackBoards.firstOrNull() ?: ZoneBoard.Action
        pendingSingleZoneFirstBoard = scope is ZoneQueryScope.Single
        currentBoardSelectionKey = if (pendingSingleZoneFirstBoard) "" else {
            encodeBoardKey(category.tabKey, firstBoard.type)
        }
        _uiState.update {
            it.copy(
                selectedCategory = category,
                boards = fallbackBoards,
                boardTitles = emptyMap(),
                selectedBoard = firstBoard,
                albumCategoryOptions = emptyList()
            )
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(category.tabKey)
            viewPreferencesManager.setFeedCategory(category.id)
            persistBoardSelection()
        }

        session.dispatch(ZoneBoardIntent.SelectBoard(firstBoard))
        session.updateScope(scope)
        // 用户已显式选定专区：无需等待冷启动落定，立即解闩防 loading 悬挂
        session.resumeInitialLoad()
        val memoryKey = homeBoardMemoryKey(scope, firstBoard)
        boardScrollMemories[memoryKey]?.let { saved ->
            session.dispatch(ZoneBoardIntent.SaveScrollPosition(firstBoard, saved.index, saved.offset))
            session.dispatch(ZoneBoardIntent.SaveFilterCollapseFraction(firstBoard, saved.filterCollapseFraction))
            session.dispatch(ZoneBoardIntent.SaveFilterRowScroll(firstBoard, saved.filterRowScrollX))
        }

        syncBoards(scope)
        if (firstBoard == ZoneBoard.Album) {
            syncAlbumCategories(scope)
        }
    }

    fun selectBoard(board: ZoneBoard) {
        val current = _uiState.value
        if (!ZoneBoardCapability.isAvailableOnHome(current.zoneScope, board)) return
        if (current.selectedBoard == board || board !in current.boards) return
        currentBoardSelectionKey = encodeBoardKey(current.selectedCategory.tabKey, board.type)
        pendingSingleZoneFirstBoard = false
        _uiState.update { it.copy(selectedBoard = board) }
        viewModelScope.launch { persistBoardSelection() }
        session.dispatch(ZoneBoardIntent.SelectBoard(board))
        // 用户已显式切换板块：立即解闩防 loading 悬挂（落定前点击的极窄竞态兜底）
        session.resumeInitialLoad()
        if (board == ZoneBoard.Album) {
            syncAlbumCategories(current.zoneScope)
        }
    }

    fun ensureBoardLoaded(board: ZoneBoard) {
        val state = _uiState.value
        if (!ZoneBoardCapability.isAvailableOnHome(state.zoneScope, board)) return
        if (board !in state.boards) return
        session.snapshotFor(board)
    }

    // ── 产品（Hobby）板块意图转发 ──

    fun selectHobbyOrder(order: String) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectHobbyOrder(order))
    }

    fun selectHobbyYear(year: Int?) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectHobbyYear(year))
    }

    fun selectHobbyMonth(month: Int?) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectHobbyMonth(month))
    }

    fun selectHobbyCategory(categoryId: Int, subCategoryId: Int?) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectHobbyCategory(categoryId, subCategoryId))
    }

    fun applyHobbyAdvancedFilters(filterParam: HobbyAdvancedFilterParam) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.ApplyHobbyFilter(filterParam))
    }

    fun resetHobbyAdvancedFilters() {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.ResetHobbyFilter)
    }

    fun setHobbyFilterSheetVisible(show: Boolean) {
        session.dispatch(ZoneBoardIntent.SetHobbyFilterSheetVisible(show))
    }

    fun setHobbyCategoryPickerVisible(show: Boolean) {
        session.dispatch(ZoneBoardIntent.SetHobbyCategoryPickerVisible(show))
    }

    fun toggleHobbyViewMode() {
        session.dispatch(ZoneBoardIntent.ToggleHobbyViewMode)
    }

    fun updateHobbyKeyword(keyword: String) {
        session.dispatch(ZoneBoardIntent.UpdateHobbyKeyword(keyword))
    }

    fun submitHobbySearch() {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SubmitHobbySearch)
    }

    // ── 分享（Album）板块意图转发 ──

    fun selectAlbumOrder(order: String) {
        if (order !in AlbumBoardOptions.ORDER_KEYS) return
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectAlbumOrder(order))
    }

    fun selectAlbumCategory(categoryId: Int?) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectAlbumCategory(categoryId))
    }

    // ── 讨论（Posts）板块意图转发 ──

    fun selectPostsOrder(order: String) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectPostsOrder(order))
    }

    // ── 二手（Resale）板块意图转发 ──

    fun selectResaleType(type: Int) {
        if (!ResaleBoardOptions.isValidType(type)) return
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectResaleType(type))
    }

    fun selectResalePurity(purity: String?) {
        if (!ResaleBoardOptions.isValidPurity(purity)) return
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectResalePurity(purity))
    }

    fun updateResaleKeyword(keyword: String) {
        session.dispatch(ZoneBoardIntent.UpdateResaleKeyword(keyword))
    }

    fun submitResaleSearch() {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SubmitResaleSearch)
    }

    // ── 动态（Action）板块意图转发 ──

    fun selectSubType(subType: String) {
        val normalized = ActionSubType.fromPersisted(subType).apiValue
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectSubType(normalized))
        viewModelScope.launch {
            viewPreferencesManager.setFeedSubType(normalized)
        }
    }

    // ── 通用生命周期与翻页重试分发 ──

    fun retrySelectedBoard(board: ZoneBoard? = null) {
        session.dispatch(ZoneBoardIntent.RetryInitial(board))
    }

    fun loadMoreSelectedBoard(board: ZoneBoard? = null) {
        session.dispatch(ZoneBoardIntent.LoadMore(board))
    }

    fun refreshBoards() {
        syncBoards(_uiState.value.zoneScope)
        session.dispatch(ZoneBoardIntent.RetryInitial())
    }

    fun refreshFeed() = refresh()

    fun refresh() {
        resetScrollPosition()
        fetchUnreadMessages()
        session.dispatch(ZoneBoardIntent.Refresh())
    }

    /**
     * 恢复感知重拉未读数：从消息中心返回首页（重新进入组合 / RESUMED）时由界面层调用。
     *
     * 防坑契约：消息中心内「一键全部已读」或逐条已读只改服务端计数，
     * 首页 `unreadMessageCount` 是独立快照，不重拉会残留陈旧值导致红点不灭（issue #195）。
     */
    fun refreshUnreadMessages() {
        fetchUnreadMessages()
    }

    fun loadNextPage() {
        session.dispatch(ZoneBoardIntent.LoadMore(_uiState.value.selectedBoard))
    }

    fun resetFilters() {
        val defaultCategory = _uiState.value.categories.firstOrNull() ?: FeedCategory("all", "全部")
        val defaultSubType = ActionSubType.DEFAULT.apiValue
        coldStartTabResolved = true
        val scope = defaultCategory.toZoneQueryScope()
        val fallbackBoards = ZoneBoardCapability.homeBoardsFor(scope)
        val firstBoard = fallbackBoards.firstOrNull() ?: ZoneBoard.Action
        pendingSingleZoneFirstBoard = false
        currentBoardSelectionKey = encodeBoardKey(defaultCategory.tabKey, firstBoard.type)
        _uiState.update {
            it.copy(
                selectedCategory = defaultCategory,
                boards = fallbackBoards,
                boardTitles = emptyMap(),
                selectedBoard = firstBoard,
                albumCategoryOptions = emptyList()
            )
        }
        resetBoardScrollMemory(homeBoardMemoryKey(scope, firstBoard))
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(defaultCategory.tabKey)
            viewPreferencesManager.setFeedCategory(defaultCategory.id)
            viewPreferencesManager.setFeedSubType(defaultSubType)
            persistBoardSelection()
        }

        session.dispatch(ZoneBoardIntent.SelectBoard(firstBoard))
        session.updateScope(scope)
        session.dispatch(ZoneBoardIntent.SelectSubType(defaultSubType))
        syncBoards(scope)
    }

    private suspend fun persistBoardSelection() {
        currentBoardSelectionKey
            .takeIf { it.isNotBlank() }
            ?.let { viewPreferencesManager.setFeedZoneBoard(it) }
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
                            launch { zoneRepository.getZoneHomeTabs() }
                            fetchUnreadMessages()
                        }
                        _uiState.update { it.copy(userAvatar = state.profile?.header) }
                        if (state.profile == null && !profileRefreshRequested) {
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
            if (userSession.currentToken != token) return@launch
            _uiState.update { it.copy(unreadMessageCount = count.totalCount) }
        }
    }

    private fun syncBoards(scope: ZoneQueryScope) {
        boardsJob?.cancel()
        if (scope !is ZoneQueryScope.Single) return
        boardsJob = viewModelScope.launch {
            val result = zoneRepository.getZoneInfo(scope.nodeId, reportVisit = false)
            if (_uiState.value.zoneScope != scope) return@launch
            val info = result.getOrNull()
            val boards = info
                ?.let { payload -> ZoneBoardCapability.homeBoardsFor(scope, payload.tabs) }
                ?: ZoneBoardCapability.SINGLE_FALLBACK_BOARDS
            val titles = buildBoardTitles(info?.tabs.orEmpty())
            val before = _uiState.value
            if (before.zoneScope != scope) return@launch
            val restoredBoard = boardForSelection(before.selectedCategory.tabKey, boards)
            val targetBoard = when {
                pendingSingleZoneFirstBoard ->
                    boards.firstOrNull() ?: restoredBoard ?: before.selectedBoard
                restoredBoard != null -> restoredBoard
                before.selectedBoard in boards -> before.selectedBoard
                else -> boards.firstOrNull() ?: before.selectedBoard
            }
            if (pendingSingleZoneFirstBoard) {
                pendingSingleZoneFirstBoard = false
                currentBoardSelectionKey = encodeBoardKey(before.selectedCategory.tabKey, targetBoard.type)
                viewModelScope.launch { persistBoardSelection() }
            }
            _uiState.update { state ->
                if (state.zoneScope != scope) return@update state
                state.copy(
                    boards = boards,
                    boardTitles = titles,
                    selectedBoard = targetBoard
                )
            }
            session.dispatch(ZoneBoardIntent.SelectBoard(targetBoard))
            if (targetBoard == ZoneBoard.Album) {
                syncAlbumCategories(scope)
            }
        }
    }

    private fun syncAlbumCategories(scope: ZoneQueryScope) {
        albumCategoriesJob?.cancel()
        val albumRepo = albumRepository
        if (scope !is ZoneQueryScope.Single || albumRepo == null || _uiState.value.selectedBoard != ZoneBoard.Album) {
            _uiState.update { it.copy(albumCategoryOptions = emptyList()) }
            return
        }
        albumCategoriesJob = viewModelScope.launch {
            val result = albumRepo.getAlbumCategories(scope.nodeId)
            if (_uiState.value.zoneScope != scope || _uiState.value.selectedBoard != ZoneBoard.Album) return@launch
            val options = result.getOrNull().orEmpty()
                .map { AlbumCategoryOption(it.categoryId, it.name.orEmpty()) }
                .filter { it.id > 0 && it.name.isNotBlank() }
                .takeIf { it.size > 1 }
                .orEmpty()
            _uiState.update { it.copy(albumCategoryOptions = options) }
        }
    }

    private fun buildBoardTitles(serverTabs: List<ZoneTabItem>): Map<ZoneBoard, String> {
        if (serverTabs.isEmpty()) return emptyMap()
        val titles = mutableMapOf<ZoneBoard, String>()
        for (tab in serverTabs) {
            val board = ZoneBoard.fromType(tab.type) ?: continue
            if (board !in titles) titles[board] = board.tabTitle(tab.title)
        }
        return titles
    }
}
