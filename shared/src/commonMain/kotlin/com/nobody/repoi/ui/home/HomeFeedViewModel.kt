package com.nobody.repoi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.AlbumCategoryOption
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.ZoneQueryScope
import com.nobody.repoi.util.paging.LoadStage
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

    /**
     * 二手（`resale_item`）板块状态（issue #160）：独立懒加载、游标分页 + 出售/求购/成色筛选。
     *
     * 状态与请求构造封装在 `ResaleBoardState.kt`，本 VM 只做注册、委派与投影，避免中心状态类膨胀。
     */
    val resale: HomeResaleBoardState = HomeResaleBoardState(),

    /**
     * 实体 5 类板块状态：按 [ZoneBoard]（仅实体板块有键）懒加载，切走再切回保留。
     *
     * 首次切到某实体板块才请求；作用域切换时整体清空（见 `EntityBoardLoader.reset`）。
     */
    val entityBoards: Map<ZoneBoard, EntityBoardState> = emptyMap(),

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

    /** 当前板块条目数（内容区四态 / 触底翻页 / 折叠可滚动性共用，避免宿主散写 `when`）。 */
    val currentBoardItemCount: Int
        get() = when (selectedBoard) {
            ZoneBoard.Album -> albumItems.size
            ZoneBoard.Posts -> posts.items.size + posts.pinnedPosts.size
            ZoneBoard.Resale -> resale.items.size
            else -> if (selectedBoard.isEntity) currentEntityBoardState.items.size else items.size
        }

    /** 实体板块的只读状态快照（非实体板块取默认值，调用方仅在实体板块消费）。 */
    private val currentEntityBoardState: EntityBoardState
        get() = entityBoards[selectedBoard] ?: EntityBoardState()

    /** 当前板块是否还有下一页。 */
    val currentBoardHasMore: Boolean
        get() = when (selectedBoard) {
            ZoneBoard.Album -> albumHasMore
            ZoneBoard.Posts -> posts.hasMore
            ZoneBoard.Resale -> resale.hasMore
            else -> if (selectedBoard.isEntity) !currentEntityBoardState.isEndReached else hasMore
        }

    /** 当前板块是否正在追加下一页。 */
    val currentBoardIsLoadingMore: Boolean
        get() = when (selectedBoard) {
            ZoneBoard.Album -> isLoadingMoreAlbum
            ZoneBoard.Posts -> posts.isLoadingMore
            ZoneBoard.Resale -> resale.isLoadingMore
            else -> if (selectedBoard.isEntity) currentEntityBoardState.isLoadingMore else isLoadingMore
        }

    /** 当前板块追加失败信息（页脚重试用）。 */
    val currentBoardLoadMoreError: String?
        get() = when (selectedBoard) {
            ZoneBoard.Album -> albumLoadMoreError
            ZoneBoard.Posts -> posts.loadMoreError
            ZoneBoard.Resale -> resale.loadMoreError
            else -> if (selectedBoard.isEntity) currentEntityBoardState.loadMoreError else loadMoreError
        }

    /** 当前板块首屏加载中（宿主统一用 `PoiLoadingView` 展示）。 */
    val isCurrentBoardLoading: Boolean
        get() = when (selectedBoard) {
            ZoneBoard.Album -> isAlbumLoading && albumItems.isEmpty()
            // 讨论板块懒加载：尚未请求过首屏（且无错误）时也按加载态处理，避免闪一帧空态卡片
            ZoneBoard.Posts -> (posts.isInitialLoading || !posts.isLoaded) && posts.errorMessage == null
            // 二手板块同样按懒加载处理，避免切到二手瞬间闪一帧空态卡片
            ZoneBoard.Resale -> (resale.isLoading || !resale.isLoaded) && resale.errorMessage == null
            else -> if (selectedBoard.isEntity) {
                currentEntityBoardState.isLoading && currentEntityBoardState.items.isEmpty()
            } else {
                isInitialLoading
            }
        }

    /** 当前板块首屏错误（仅在列表为空时展示错误态）。 */
    val currentBoardErrorMessage: String?
        get() = when (selectedBoard) {
            ZoneBoard.Album -> albumErrorMessage
            ZoneBoard.Posts -> posts.errorMessage
            ZoneBoard.Resale -> resale.errorMessage
            else -> if (selectedBoard.isEntity) currentEntityBoardState.errorMessage else errorMessage
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
            ZoneBoard.Posts -> postsBoardContent
            ZoneBoard.Resale -> ZoneBoardContent.Resale(
                items = resale.items,
                page = ZoneBoardPageView(
                    isLoading = resale.isLoading,
                    isLoadingMore = resale.isLoadingMore,
                    errorMessage = resale.errorMessage,
                    loadMoreError = resale.loadMoreError,
                    isEndReached = !resale.hasMore
                )
            )
            else -> if (selectedBoard.isEntity) {
                val entityState = currentEntityBoardState
                ZoneBoardContent.Entity(
                    items = entityState.items,
                    page = ZoneBoardPageView(
                        isLoading = entityState.isLoading,
                        isLoadingMore = entityState.isLoadingMore,
                        errorMessage = entityState.errorMessage,
                        loadMoreError = entityState.loadMoreError,
                        isEndReached = entityState.isEndReached
                    )
                )
            } else {
                ZoneBoardContent.Action(
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
        }

    /** 当前板块筛选值快照（传给共享面板/筛选条）。实体 5 类暂无筛选项，保持默认值。 */
    val currentBoardFilters: ZoneBoardFilters
        get() = when (selectedBoard) {
            ZoneBoard.Album -> ZoneBoardFilters(
                albumOrder = albumSelectedOrder,
                albumCategory = albumSelectedCategory
            )
            ZoneBoard.Posts -> ZoneBoardFilters(subType = selectedSubType, postsOrder = posts.order)
            ZoneBoard.Resale -> ZoneBoardFilters(
                resaleType = resale.type,
                resalePurity = resale.purity,
                resaleKeyword = resale.keyword
            )
            else -> if (selectedBoard.isEntity) ZoneBoardFilters() else ZoneBoardFilters(subType = selectedSubType)
        }
}

class HomeFeedViewModel(
    private val homeRepository: HomeRepository,
    private val albumRepository: AlbumRepository,
    private val resaleRepository: ResaleRepository,
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

    /**
     * 实体 5 类板块的懒加载 / 分页控制器（#161）。
     *
     * 状态经回调写回 [HomeFeedUiState.entityBoards]；请求作用域在懒加载/分页调用点传入，
     * 作用域切换时由 [resetEntityBoards] 整体重建（不同 `zones` 不可复用同一 loader）。
     */
    private val entityBoardLoader: EntityBoardLoader = EntityBoardLoader(
        coroutineScope = viewModelScope,
        repository = zoneRepository,
        onStateChanged = { board, state ->
            _uiState.update { it.copy(entityBoards = it.entityBoards + (board to state)) }
        }
    )

    private var initialLoadJob: Job? = null

    /** 二级板块列表的异步请求（单专区 `zone/info`）；切专区时先取消，避免过期响应覆盖。 */
    private var boardsJob: Job? = null

    /** 讨论板块的请求 Job（首屏 / 刷新 / 分页共用；切板块不取消，切回时复用已有状态）。 */
    private var postsJob: Job? = null

    /** 分享板块首屏 / 下一页请求（切排序、切分类、切专区时取消，避免过期响应覆盖新列表）。 */
    private var albumJob: Job? = null

    /** 分享板块分类候选请求（单专区；切专区时取消）。 */
    private var albumCategoriesJob: Job? = null

    /**
     * 二手板块游标分页控制器（#160）：请求拼参与分页状态机封装在
     * `ResaleBoardState.kt`，本 VM 只做状态投影与作用域/筛选委派。
     */
    private val resaleBoardLoader = ResaleBoardLoader(viewModelScope, resaleRepository)

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
        // 二手板块 loader 状态投影（#160）：items/分页/错误 + 下拉刷新完成后收起刷新指示器。
        // 分页状态机由 ResaleBoardState.kt 承担，本 VM 只读投影，不重复去重或改游标。
        viewModelScope.launch {
            resaleBoardLoader.state.collect { listState ->
                _uiState.update { state ->
                    val refreshFinished = state.isRefreshing &&
                        state.selectedBoard == ZoneBoard.Resale &&
                        listState.stage == LoadStage.Idle
                    state.copy(
                        resale = state.resale.copy(
                            items = listState.items,
                            hasMore = listState.hasMore,
                            isLoading = listState.isLoading,
                            isLoadingMore = listState.isLoadingMore,
                            errorMessage = listState.initialError,
                            loadMoreError = listState.appendError
                        ),
                        isRefreshing = if (refreshFinished) false else state.isRefreshing
                    )
                }
            }
        }

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
                    ).let { next ->
                        if (categoryChanged) {
                            next.resetAlbumForScopeChange().resetResaleForScopeChange()
                        } else {
                            next
                        }
                    }
                }

                if (categoryChanged) {
                    // 切专区：弃掉分享/二手板块的旧列表与分类候选，等下次切到对应板块再懒加载
                    cancelAlbumWork()
                    // 切专区：弃掉分享/二手/实体板块的旧列表与分类候选，等下次切到对应板块再懒加载
                    cancelAlbumWork()
                    resaleBoardLoader.reset()
                    // 实体 5 类同样按作用域失效：旧 zones 的 loader 与条目不可跨专区复用
                    resetEntityBoards()
                }
                // 冷启动恢复可能直接把落点定在讨论/分享/二手/实体板块（`feed_zone_board` 命中）：补一次懒加载
                ensurePostsLoaded()
                when {
                    nextBoard == ZoneBoard.Album -> ensureAlbumLoaded()
                    nextBoard == ZoneBoard.Resale -> ensureResaleLoaded()
                    nextBoard.isEntity -> ensureEntityLoaded(nextBoard)
                }
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
            ).resetAlbumForScopeChange().resetResaleForScopeChange()
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(category.tabKey)
            viewPreferencesManager.setFeedCategory(category.id)
            persistBoardSelection()
        }
        cancelAlbumWork()
        resaleBoardLoader.reset()
        resetEntityBoards()
        if (firstBoard.isEntity) ensureEntityLoaded(firstBoard)
        syncBoards(scope)
        loadInitialFeed()
    }

    /**
     * 切换二级板块：就地切换、不整页重建，不清空其它板块状态。
     *
     * 讨论 / 分享板块**按需懒加载**：首次切到该板块时才发起首屏请求；切走再切回复用已有内容与
     * 分页游标（滚动位置 / 折叠比例由宿主 UI 按「作用域 + 板块」记忆）。
     * 选择同时写回 `feed_zone_board = {tabKey}|{type}`（#156 冷启动恢复规则）；
     * 后续 #159–#161 接入新板块时在此按板块补懒加载。
     */
    fun selectBoard(board: ZoneBoard) {
        val current = _uiState.value
        if (current.selectedBoard == board || board !in current.boards) return
        currentBoardSelectionKey = encodeBoardKey(current.selectedCategory.tabKey, board.type)
        _uiState.update { it.copy(selectedBoard = board) }
        if (board == ZoneBoard.Posts) ensurePostsLoaded()
        viewModelScope.launch { persistBoardSelection() }
        when {
            board == ZoneBoard.Album -> ensureAlbumLoaded()
            board == ZoneBoard.Resale -> ensureResaleLoaded()
            board.isEntity -> ensureEntityLoaded(board)
        }
    }

    /** 实体板块懒加载入口：首次切到才请求首屏；已加载/已请求过不重复（失败后也不自动重试）。 */
    private fun ensureEntityLoaded(board: ZoneBoard) {
        if (!board.isEntity) return
        entityBoardLoader.ensureLoaded(board, _uiState.value.zoneScope)
    }

    /**
     * 切专区 / 重置筛选时丢弃实体板块的 loader 与状态。
     *
     * 实体请求参数由 `scope.zonesParam()` 决定，旧 loader 的闭包仍持有旧作用域，必须整体重建；
     * 不清滚动记忆（切回同一作用域时仍按「作用域+板块」键恢复）。
     */
    private fun resetEntityBoards() {
        entityBoardLoader.reset()
        _uiState.update { if (it.entityBoards.isEmpty()) it else it.copy(entityBoards = emptyMap()) }
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

    /** 当前板块首屏错误态「重试」：按板块分派，避免讨论/分享/二手/实体板块重试时误刷动态流。 */
    fun retrySelectedBoard() {
        when (val board = _uiState.value.selectedBoard) {
            ZoneBoard.Album -> loadInitialAlbum()
            ZoneBoard.Posts -> loadInitialPosts()
            ZoneBoard.Resale -> retryResale()
            ZoneBoard.Company,
            ZoneBoard.Series,
            ZoneBoard.Works,
            ZoneBoard.Character,
            ZoneBoard.Person -> entityBoardLoader.retry(board)
            else -> loadInitialFeed()
        }
    }

    /** 当前板块触底翻页：按板块分派，避免讨论/分享/二手/实体板块触底时把动态游标推错。 */
    fun loadMoreSelectedBoard() {
        when (val board = _uiState.value.selectedBoard) {
            ZoneBoard.Album -> loadNextAlbumPage()
            ZoneBoard.Posts -> loadNextPostsPage()
            ZoneBoard.Resale -> loadNextResalePage()
            ZoneBoard.Company,
            ZoneBoard.Series,
            ZoneBoard.Works,
            ZoneBoard.Character,
            ZoneBoard.Person -> entityBoardLoader.loadMore(board)
            else -> loadNextPage()
        }
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
            ).resetAlbumForScopeChange().resetResaleForScopeChange().let { next ->
                next.copy(
                    albumSelectedOrder = AlbumBoardOptions.DEFAULT_ORDER,
                    albumSelectedCategory = null,
                    // 二手交易类型/成色属板块级筛选：重置回默认值并作废内容（作用域变化必须重拉）
                    resale = next.resale.copy(
                        type = ResaleBoardOptions.DEFAULT_TYPE,
                        purity = null,
                        keyword = ""
                    )
                )
            }
        }
        resetBoardScrollMemory(homeBoardMemoryKey(scope, firstBoard))
        resaleBoardLoader.reset()
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(defaultCategory.tabKey)
            viewPreferencesManager.setFeedCategory(defaultCategory.id)
            viewPreferencesManager.setFeedSubType(defaultSubType)
            persistBoardSelection()
        }
        resetEntityBoards()
        syncBoards(scope)
        loadInitialFeed()
    }

    fun refreshFeed() = refresh()

    /**
     * 刷新当前板块：动态走游标首屏，分享/讨论走各自独立首屏，实体走页码第一页，
     * 共用 [HomeFeedUiState.isRefreshing]，下拉指示器 / FAB 无需按板块区分。
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                loadMoreError = null,
                posts = it.posts.copy(errorMessage = null, loadMoreError = null),
                albumErrorMessage = null,
                albumLoadMoreError = null,
                resale = it.resale.copy(errorMessage = null, loadMoreError = null)
            )
        }
        fetchUnreadMessages()
        // 按当前板块分派刷新：讨论/分享/二手/实体是独立请求链路，不能借用动态流的游标
        when (_uiState.value.selectedBoard) {
            ZoneBoard.Album -> refreshAlbum()
            ZoneBoard.Posts -> refreshPosts()
            ZoneBoard.Resale -> refreshResale()
            ZoneBoard.Company,
            ZoneBoard.Series,
            ZoneBoard.Works,
            ZoneBoard.Character,
            ZoneBoard.Person -> refreshEntityBoard()
            else -> refreshActionBoard()
        }
    }

    /**
     * 实体板块刷新：页码回第一页，参数与首屏一致（`scope` + `itemType`）。
     *
     * 刷新态交给板块 loader（与专区详情既有语义一致），全局 [HomeFeedUiState.isRefreshing]
     * 立即复位，避免下拉指示器长期悬挂。
     */
    private fun refreshEntityBoard() {
        val board = _uiState.value.selectedBoard
        if (!board.isEntity) return
        ensureEntityLoaded(board)
        entityBoardLoader.refresh(board)
        _uiState.update { it.copy(isRefreshing = false) }
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
        if (currentState.selectedBoard.isEntity) {
            // 实体板块：分页守卫（hasMore / 阶段）与去重由 loader 统一负责
            entityBoardLoader.loadMore(currentState.selectedBoard)
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

    // ==================== 二手板块（#160） ====================

    /** 组装二手板块当前请求参数：作用域（全部省略 / 单区 nodeId）+ 出售求购 + 成色 + 关键词。 */
    private fun HomeFeedUiState.resaleQuery(): ResaleQuery = ResaleQuery(
        zones = zoneScope.zonesParam(),
        type = resale.type,
        purity = resale.purity,
        keyword = resale.keyword
    )

    /** 二手板块懒加载入口：首次切到二手才拉首屏；已加载/加载中不重复请求。 */
    private fun ensureResaleLoaded() {
        val state = _uiState.value
        if (state.selectedBoard != ZoneBoard.Resale) return
        // 安全闸门：板块列表里没有二手（关注聚合按 homeBoardsFor 隐藏）时绝不发请求
        if (ZoneBoard.Resale !in state.boards) return
        if (state.resale.isLoaded || state.resale.isLoading) return
        loadInitialResale()
    }

    /**
     * 二手首屏（懒加载 / 切筛选 / 错误重试共用）：清空列表并回第一页。
     *
     * 关注作用域下二手板块已被隐藏（`zones` 逗号串为交集语义，无法表达关注并集），这里同时做
     * 板块可用性与空关注短路，绝不下发会被服务端当作全站的空 `zones`。
     */
    fun loadInitialResale() {
        val state = _uiState.value
        if (ZoneBoard.Resale !in state.boards || state.zoneScope.isFollowEmpty) {
            _uiState.update {
                it.copy(
                    resale = it.resale.copy(
                        items = emptyList(),
                        hasMore = false,
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        loadMoreError = null,
                        isLoaded = true
                    )
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                resale = it.resale.copy(
                    items = emptyList(),
                    hasMore = true,
                    isLoading = true,
                    isLoadingMore = false,
                    errorMessage = null,
                    loadMoreError = null,
                    isLoaded = true
                )
            )
        }
        resaleBoardLoader.loadInitial(state.resaleQuery())
    }

    /** 错误态手动重试：与首屏一致，但忽略「已加载」去重。 */
    private fun retryResale() = loadInitialResale()

    /** 二手板块下一页：游标 = 上一批末条 `addDate`，由 loader 计算并按 id 去重追加。 */
    fun loadNextResalePage() {
        val state = _uiState.value
        if (state.selectedBoard != ZoneBoard.Resale) return
        if (ZoneBoard.Resale !in state.boards) return
        if (state.resale.isLoadingMore || !state.resale.hasMore || state.resale.isLoading || state.isRefreshing) return
        if (!state.resale.isLoaded || state.zoneScope.isFollowEmpty) return
        resaleBoardLoader.loadMore()
    }

    /** 二手板块刷新：保持列表可见，参数与首屏一致；完成后由 loader 状态投影收起刷新指示器。 */
    private fun refreshResale() {
        val state = _uiState.value
        if (ZoneBoard.Resale !in state.boards || state.zoneScope.isFollowEmpty) {
            _uiState.update { it.copy(isRefreshing = false) }
            return
        }
        if (!state.resale.isLoaded) {
            loadInitialResale()
            return
        }
        resaleBoardLoader.refresh(state.resaleQuery())
    }

    /** 二手板块交易类型切换（1 出售 / 2 求购）：清列表 + 回第一页重载。 */
    fun selectResaleType(type: Int) {
        if (!ResaleBoardOptions.isValidType(type) || _uiState.value.resale.type == type) return
        resetScrollPosition()
        _uiState.update {
            it.copy(resale = it.resale.copy(type = type))
        }
        loadInitialResale()
    }

    /** 二手板块成色切换（`null` = 全部成色）：清列表 + 回第一页重载。 */
    fun selectResalePurity(purity: String?) {
        if (!ResaleBoardOptions.isValidPurity(purity) || _uiState.value.resale.purity == purity) return
        resetScrollPosition()
        _uiState.update {
            it.copy(resale = it.resale.copy(purity = purity))
        }
        loadInitialResale()
    }

    /** 二手搜索关键词输入（仅专区详情渲染搜索栏；首页不产生该输入）。 */
    fun updateResaleKeyword(keyword: String) {
        _uiState.update { it.copy(resale = it.resale.copy(keyword = keyword)) }
    }

    /** 二手搜索提交：按当前关键词回第一页重载。 */
    fun submitResaleSearch() {
        loadInitialResale()
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
            // 冷启动恢复到分享/二手/实体板块：tabs 到达后补触发懒加载（恢复路径绕过 selectBoard）
            when {
                _uiState.value.selectedBoard == ZoneBoard.Album -> ensureAlbumLoaded()
                _uiState.value.selectedBoard == ZoneBoard.Resale -> ensureResaleLoaded()
                _uiState.value.selectedBoard.isEntity -> ensureEntityLoaded(_uiState.value.selectedBoard)
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
