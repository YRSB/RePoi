package com.nobody.repoi.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneDetailData
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.data.remote.model.ZoneInfoPayload
import com.nobody.repoi.data.remote.model.ZoneTabItem
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.ui.board.AlbumBoardLoader
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.AlbumBoardQuery
import com.nobody.repoi.ui.board.AlbumCategoryOption
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.home.ENTITY_BOARD_PAGE_SIZE
import com.nobody.repoi.data.remote.model.ZoneHobbyFilter
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.util.paging.KeyedListLoader
import com.nobody.repoi.util.paging.LoadStage
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.pagedChunkLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.data.remote.model.ZoneItem

/**
 * 专区产品 (hobby) 分类状态
 */
data class ZoneHobbyTabState(
    val categoryId: Int = HobbyBoardOptions.DEFAULT_CATEGORY_ID,
    val subCategoryId: Int? = null,
    val order: String = HobbyBoardOptions.DEFAULT_ORDER,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val keyword: String = "",
    val sex: Int? = null,
    val specs: String? = null,
    val scale: String? = null,
    val r18: Int? = null,
    val items: List<HobbyItemSummary> = emptyList(),
    val page: Int = 1,
    val isGridMode: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEndReached: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val showFilterSheet: Boolean = false,
    val showCategoryPickerSheet: Boolean = false,
    val isSearchActive: Boolean = false
) {
    val hasAdvancedFilters: Boolean
        get() = sex != null || !specs.isNullOrBlank() || !scale.isNullOrBlank() || r18 != null
}

/**
 * 产品状态 → 共享筛选快照（pane / 筛选条只读）。
 *
 * 两处弹层显隐也来自 ViewModel（专区详情顶栏控制栏打开），由共享 pane 渲染。
 */
fun ZoneHobbyTabState.toZoneBoardFilters(): ZoneBoardFilters = ZoneBoardFilters(
    hobby = ZoneHobbyFilter(
        categoryId = categoryId,
        subCategoryId = subCategoryId,
        order = order,
        releaseYear = releaseYear,
        releaseMonth = releaseMonth,
        keyword = keyword,
        sex = sex,
        specs = specs,
        scale = scale,
        r18 = r18
    ),
    showHobbyFilterSheet = showFilterSheet,
    showHobbyCategoryPickerSheet = showCategoryPickerSheet
)

/**
 * 产品状态 → 共享内容快照（专区详情产品分支改由 `ZoneBoardPane` 渲染）。
 */
fun ZoneHobbyTabState.toZoneBoardContent(): ZoneBoardContent.Hobby = ZoneBoardContent.Hobby(
    items = items,
    isGridMode = isGridMode,
    page = ZoneBoardPageView(
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        errorMessage = errorMessage,
        loadMoreError = loadMoreError,
        isEndReached = isEndReached
    )
)

/**
 * 专区二手 (resale_item) 分类状态
 */
data class ZoneResaleTabState(
    val type: Int = 1, // 1: 出售, 2: 求购
    val keyword: String = "",
    val purity: String? = null,
    val items: List<ResaleSearchItem> = emptyList(),
    val lastTime: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEndReached: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

/**
 * 专区通用实体分类 (company, series, works, charactar, person) 状态
 */
data class ZoneEntityTabState(
    val items: List<ZoneEntityItem> = emptyList(),
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isEndReached: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

data class ZoneDetailUiState(
    val nodeId: Long = 0L,
    val zoneInfo: ZoneInfoPayload? = null,
    val isInfoLoading: Boolean = true,
    val isFocusToggling: Boolean = false,
    val focusState: Boolean = false,
    val followCount: Long = 0L,
    val availableTabs: List<Pair<String, String>> = listOf("action" to "动态"),
    val selectedTab: String = "action",

    // 动态 Tab 状态（默认筛选值取共享定义的默认项，旧键 all 不再出现）
    val selectedSubType: String = ActionSubType.DEFAULT.apiValue,
    val items: List<ActionItem> = emptyList(),
    val lastTime: Long = 0L,
    val isFeedLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,

    // 分享/相册 Tab 状态
    val albumSelectedOrder: String = AlbumBoardOptions.DEFAULT_ORDER,
    /** 相册分类；`null` = 全部分类（原版仅单专区展示分类入口）。 */
    val albumSelectedCategory: Int? = null,
    /** 相册分类候选（服务端 `api/zone/categories?itemType=album`；少于 2 个有效项时为空）。 */
    val albumCategoryOptions: List<AlbumCategoryOption> = emptyList(),
    val albumItems: List<AlbumListItem> = emptyList(),
    val albumPage: Int = 1,
    val isAlbumLoading: Boolean = false,
    val isAlbumLoadingMore: Boolean = false,
    val hasMoreAlbum: Boolean = true,
    val albumErrorMessage: String? = null,
    val albumLoadMoreError: String? = null,

    // 讨论/帖子 Tab 状态
    val postsSelectedOrder: String = PostsBoardOptions.DEFAULT_ORDER,
    val postsItems: List<PostItem> = emptyList(),
    val pinnedPosts: List<PostItem> = emptyList(),
    val postsPage: Int = 1,
    val isPostsLoading: Boolean = false,
    val isPostsLoadingMore: Boolean = false,
    val hasMorePosts: Boolean = true,
    val postsErrorMessage: String? = null,
    val postsLoadMoreError: String? = null,

    // 7 个新增分类状态
    val hobbyState: ZoneHobbyTabState = ZoneHobbyTabState(),
    val resaleState: ZoneResaleTabState = ZoneResaleTabState(),
    val entityTabs: Map<String, ZoneEntityTabState> = emptyMap(),

    // 悬浮发布弹窗显隐
    val showPublishSheet: Boolean = false
) {
    val isAnyRefreshing: Boolean
        get() = isRefreshing || hobbyState.isRefreshing || resaleState.isRefreshing || currentEntityState.isRefreshing

    val isAlbumTab: Boolean
        get() = selectedTab == "album" || selectedTab == "share"

    val isPostsTab: Boolean
        get() = selectedTab == "posts"

    val isHobbyTab: Boolean
        get() = selectedTab == "hobby"

    val isResaleTab: Boolean
        get() = selectedTab == "resale_item"

    val isEntityTab: Boolean
        get() = selectedTab in ENTITY_TAB_TYPES

    val currentEntityState: ZoneEntityTabState
        get() = entityTabs[selectedTab] ?: ZoneEntityTabState()

    /**
     * 讨论板块 → 共享面板内容快照（宿主投影，`ZoneBoardPane` 只读）。
     *
     * 置顶帖始终投影：专区详情固定单专区，不涉及聚合态的「无置顶语义」问题。
     */
    val postsBoardContent: ZoneBoardContent.Posts
        get() = ZoneBoardContent.Posts(
            pinnedPosts = pinnedPosts,
            items = postsItems,
            page = ZoneBoardPageView(
                isLoading = isPostsLoading && postsItems.isEmpty() && pinnedPosts.isEmpty(),
                isLoadingMore = isPostsLoadingMore,
                errorMessage = postsErrorMessage,
                loadMoreError = postsLoadMoreError,
                isEndReached = !hasMorePosts
            )
        )

    /** 讨论板块筛选值快照（3 档排序；讨论没有分类字段，见 `PostsBoardOptions` KDoc）。 */
    val postsBoardFilters: ZoneBoardFilters
        get() = ZoneBoardFilters(postsOrder = postsSelectedOrder)

    /** 产品板块内容快照（喂给共享面板 `ZoneBoardPane`，#159）。 */
    val hobbyBoardContent: ZoneBoardContent.Hobby
        get() = hobbyState.toZoneBoardContent()

    /** 产品板块筛选快照（喂给共享筛选条 / 面板，含两个弹层显隐）。 */
    val hobbyBoardFilters: ZoneBoardFilters
        get() = hobbyState.toZoneBoardFilters()

    companion object {
        val ENTITY_TAB_TYPES = setOf("company", "series", "works", "charactar", "person")
    }
}

class ZoneDetailViewModel(
    private val zoneRepository: ZoneRepository,
    private val homeRepository: HomeRepository,
    private val albumRepository: AlbumRepository,
    private val itemRepository: ItemRepository,
    private val resaleRepository: ResaleRepository,
    private val collectRepository: CollectRepository,
    private val categoryManager: CategoryManager,
    private val viewPreferencesManager: ViewPreferencesManager
) : ViewModel() {

    var nodeId: Long = 0L
        private set

    /**
     * 当前专区查询作用域：专区详情固定单专区。
     *
     * 必须写成 getter——[nodeId] 在 [setNodeId] 中变化，构造期缓存会一直停留在 0。
     */
    val zoneScope: ZoneQueryScope
        get() = ZoneQueryScope.Single(nodeId)

    /**
     * 动态筛选项（key = 协议键值 `subType`，value = 2.43 中文文案）。
     *
     * 与首页共用唯一共享定义 [ActionSubType]，界面仍按有序 `(key, label)` 列表渲染。
     */
    val subTypes: List<Pair<String, String>> =
        ActionSubType.ordered.map { it.apiValue to it.label }

    /** 分享板块 6 档排序（共享定义 [AlbumBoardOptions]，与首页共用，禁止两处硬编码）。 */
    val albumOrders: List<Pair<String, String>> = AlbumBoardOptions.ORDERS

    /** 二手交易类型（共享定义 [ResaleBoardOptions.TYPES]，首页/详情/市场唯一来源）。 */
    val resaleTypes: List<Pair<Int, String>> = ResaleBoardOptions.TYPES

    /** 二手成色（共享定义 [ResaleBoardOptions.PURITIES]，含 `null` 全部成色）。 */
    val resalePurities: List<Pair<String?, String>> = ResaleBoardOptions.PURITIES

    val categoryTree: StateFlow<Map<Int, List<CategoryItem>>> = categoryManager.categoryMap

    fun getSubCategories(parentId: Int): List<CategoryItem> {
        return categoryManager.getSubCategories(parentId)
    }

    private val _uiState = MutableStateFlow(
        ZoneDetailUiState(
            hobbyState = ZoneHobbyTabState(isGridMode = viewPreferencesManager.isHobbyListGridMode)
        )
    )
    val uiState: StateFlow<ZoneDetailUiState> = _uiState.asStateFlow()

    private var feedJob: Job? = null
    private var albumCategoriesJob: Job? = null
    private var postsJob: Job? = null

    // 实体分类加载控制器缓存
    private val entityLoaders = mutableMapOf<String, PagedListLoader<ZoneEntityItem>>()

    // 分享（相册）分页控制器：与首页共用 AlbumBoardLoader，避免两屏各写一份首屏/翻页循环。
    private val albumBoardLoader = AlbumBoardLoader(
        scope = viewModelScope,
        repository = albumRepository
    )

    /** 分享刷新是否在途：完成时无条件收起刷新指示器，避免刷新途中切 Tab 后永久卡住。 */
    private var albumRefreshInFlight = false

    // 产品 (hobby) 分页加载控制器
    // 请求参数统一由 ItemRepository.queryHobbyBoard 拼装（单专区 zones=nodeId、无 homeScope），
    // 与首页产品板块共用同一构造，保证三作用域参数不会漂移（#159）。
    private val hobbyLoader: PagedListLoader<HobbyItemSummary> = PagedListLoader.fromList(
        scope = viewModelScope,
        initialPage = 1,
        pageSize = HobbyBoardOptions.PAGE_SIZE,
        keySelector = { if (it.id > 0) it.id else it.itemId }
    ) { page, size ->
        val curHobby = _uiState.value.hobbyState
        itemRepository.queryHobbyBoard(
            scope = zoneScope,
            page = page,
            pageSize = size,
            filters = ZoneHobbyFilter(
                categoryId = curHobby.categoryId,
                subCategoryId = curHobby.subCategoryId,
                order = curHobby.order,
                releaseYear = curHobby.releaseYear,
                releaseMonth = curHobby.releaseMonth,
                keyword = curHobby.keyword,
                sex = curHobby.sex,
                specs = curHobby.specs,
                scale = curHobby.scale,
                r18 = curHobby.r18
            )
        )
    }

    // 二手 (resale_item) 游标加载控制器
    private val resaleLoader: KeyedListLoader<ResaleSearchItem, String?> = KeyedListLoader(
        scope = viewModelScope,
        initialKey = null,
        keySelector = { it.id },
        nextKeyProvider = { _, state -> state.items.lastOrNull()?.addDate },
        fetcher = { cursor ->
            val curResale = _uiState.value.resaleState
            resaleRepository.searchResale(
                // #150 审查 A4：专区参数统一收 ZoneQueryScope，禁止调用点散拼
                scope = ZoneQueryScope.Single(nodeId),
                type = curResale.type,
                keyword = curResale.keyword.takeIf { it.isNotBlank() },
                puritys = curResale.purity,
                lastTime = cursor,
                pageSize = ResaleBoardOptions.PAGE_SIZE
            ).map { payload ->
                val list = payload.list
                PageChunk(
                    items = list,
                    hasMore = list.size >= 24 && !list.lastOrNull()?.addDate.isNullOrBlank()
                )
            }
        }
    )

    init {
        viewModelScope.launch {
            viewPreferencesManager.isHobbyListGridModeFlow.collect { isGrid ->
                _uiState.update { it.copy(hobbyState = it.hobbyState.copy(isGridMode = isGrid)) }
            }
        }
        // 分享板块状态投影：分页状态机由 AlbumBoardLoader 承担（与首页共用同一实现）
        viewModelScope.launch {
            albumBoardLoader.state.collect { listState ->
                val refreshDone = albumRefreshInFlight && listState.stage == LoadStage.Idle
                if (refreshDone) albumRefreshInFlight = false
                _uiState.update { state ->
                    state.copy(
                        albumItems = listState.items,
                        albumPage = listState.page,
                        isAlbumLoading = listState.isLoading,
                        isAlbumLoadingMore = listState.isLoadingMore,
                        hasMoreAlbum = listState.hasMore,
                        albumErrorMessage = listState.initialError,
                        albumLoadMoreError = listState.appendError,
                        isRefreshing = if (refreshDone) false else state.isRefreshing
                    )
                }
            }
        }
        viewModelScope.launch {
            hobbyLoader.state.collect { listState ->
                _uiState.update { state ->
                    state.copy(
                        hobbyState = state.hobbyState.copy(
                            items = listState.items,
                            page = listState.page,
                            isLoading = listState.isLoading,
                            isRefreshing = listState.isRefreshing,
                            isLoadingMore = listState.isLoadingMore,
                            isEndReached = listState.isEndReached,
                            errorMessage = listState.errorMessage,
                            loadMoreError = listState.loadMoreError
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            resaleLoader.state.collect { listState ->
                _uiState.update { state ->
                    state.copy(
                        resaleState = state.resaleState.copy(
                            items = listState.items,
                            lastTime = listState.items.lastOrNull()?.addDate,
                            isLoading = listState.isLoading,
                            isRefreshing = listState.isRefreshing,
                            isLoadingMore = listState.isLoadingMore,
                            isEndReached = listState.isEndReached,
                            errorMessage = listState.errorMessage,
                            loadMoreError = listState.loadMoreError
                        )
                    )
                }
            }
        }
    }

    private fun getEntityLoader(tabType: String): PagedListLoader<ZoneEntityItem> {
        return entityLoaders.getOrPut(tabType) {
            val loader = pagedChunkLoader<ZoneEntityItem>(
                scope = viewModelScope,
                initialPage = 1,
                pageSize = ENTITY_BOARD_PAGE_SIZE,
                keySelector = { it.targetId }
            ) { page, size ->
                zoneRepository.getZoneItems(scope = zoneScope, itemType = tabType, page = page, pageSize = size)
                    .map { PageChunk(items = it.list, hasMore = it.hasMore) }
            }
            viewModelScope.launch {
                loader.state.collect { listState ->
                    _uiState.update { state ->
                        val curTabState = state.entityTabs[tabType] ?: ZoneEntityTabState()
                        val updatedTabState = curTabState.copy(
                            items = listState.items,
                            page = listState.page,
                            isLoading = listState.isLoading,
                            isRefreshing = listState.isRefreshing,
                            isLoadingMore = listState.isLoadingMore,
                            isEndReached = listState.isEndReached,
                            errorMessage = listState.errorMessage,
                            loadMoreError = listState.loadMoreError
                        )
                        state.copy(entityTabs = state.entityTabs + (tabType to updatedTabState))
                    }
                }
            }
            loader
        }
    }

    // 各分类 Tab 的滚动位置记忆（tabKey -> (firstVisibleItemIndex, firstVisibleItemScrollOffset)）
    private val tabScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    fun saveTabScrollPosition(tab: String, index: Int, offset: Int) {
        if (index >= 0) {
            tabScrollPositions[tab] = Pair(index, offset)
        }
    }

    fun getTabScrollPosition(tab: String): Pair<Int, Int> {
        return tabScrollPositions[tab] ?: Pair(0, 0)
    }

    fun setNodeId(
        id: Long,
        initialName: String? = null,
        initialIcon: String? = null,
        initialCover: String? = null
    ) {
        if (id <= 0L || (nodeId == id && _uiState.value.zoneInfo != null)) return
        nodeId = id
        tabScrollPositions.clear()
        entityLoaders.clear()
        albumBoardLoader.reset()
        albumCategoriesJob?.cancel()
        hobbyLoader.reset(clearItems = true)
        resaleLoader.reset(clearItems = true)
        _uiState.update {
            it.copy(
                nodeId = id,
                zoneInfo = ZoneInfoPayload(
                    zone = ZoneDetailData(
                        nodeId = id,
                        nameCN = initialName,
                        icon = initialIcon,
                        cover = initialCover
                    )
                ),
                selectedTab = "action",
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isFeedLoading = true,
                errorMessage = null,
                loadMoreError = null,
                albumItems = emptyList(),
                albumPage = 1,
                isAlbumLoading = false,
                hasMoreAlbum = true,
                albumErrorMessage = null,
                albumLoadMoreError = null,
                albumSelectedCategory = null,
                albumCategoryOptions = emptyList(),
                postsSelectedOrder = PostsBoardOptions.DEFAULT_ORDER,
                postsItems = emptyList(),
                pinnedPosts = emptyList(),
                postsPage = 1,
                isPostsLoading = false,
                isPostsLoadingMore = false,
                hasMorePosts = true,
                postsErrorMessage = null,
                postsLoadMoreError = null,
                hobbyState = ZoneHobbyTabState(isGridMode = viewPreferencesManager.isHobbyListGridMode),
                resaleState = ZoneResaleTabState(),
                entityTabs = emptyMap(),
                showPublishSheet = false
            )
        }
        loadZoneInfo()
        loadInitialFeed()
    }

    fun loadZoneInfo() {
        if (nodeId <= 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInfoLoading = true) }
            val result = zoneRepository.getZoneInfo(nodeId)
            result.onSuccess { info ->
                val tabs = parseTabs(info.tabs)
                _uiState.update {
                    it.copy(
                        zoneInfo = info,
                        focusState = info.focusState,
                        followCount = info.followCount,
                        availableTabs = tabs,
                        isInfoLoading = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isInfoLoading = false) }
            }
        }
    }

    private fun parseTabs(serverTabs: List<ZoneTabItem>?): List<Pair<String, String>> {
        val rawTabs = serverTabs.orEmpty().filter { it.type.isNotBlank() }

        // 服务端未下发任何板块时，回退客户端固定 10 项（现状兜底行为，保持）
        if (rawTabs.isEmpty()) {
            return ZoneBoard.HOME_ORDER.map { it.type to it.label }
        }

        // 能力矩阵：服务端顺序 ∩ 客户端白名单，未知/未实现（shop_item、community）丢弃
        val boards = ZoneBoard.tabsFor(rawTabs, ZoneBoard.CLIENT_WHITELIST)
        if (boards.isEmpty()) {
            return ZoneBoard.HOME_ORDER.map { it.type to it.label }
        }

        // Tab 文案来自服务端 title，为空才回退枚举文案（产品板块恒为「产品」，与现状一致）
        return boards.map { board ->
            val serverTitle = rawTabs.firstOrNull { ZoneBoard.fromType(it.type) == board }?.title
            board.type to board.tabTitle(serverTitle)
        }
    }

    fun selectTab(tabType: String) {
        if (_uiState.value.selectedTab == tabType) return
        _uiState.update { it.copy(selectedTab = tabType) }
        val isAlbum = tabType == "album" || tabType == "share"
        val isPosts = tabType == "posts"
        if (isAlbum) {
            // 分类候选懒加载：仅单专区接口可用，切到分享时同步一次（已加载则复用）
            syncAlbumCategories()
        }
        if (isAlbum && _uiState.value.albumItems.isEmpty() && !_uiState.value.isAlbumLoading) {
            loadInitialAlbum()
        } else if (isPosts && _uiState.value.postsItems.isEmpty() && !_uiState.value.isPostsLoading) {
            loadInitialPosts()
        } else if (tabType == "action" && _uiState.value.items.isEmpty() && !_uiState.value.isFeedLoading) {
            loadInitialFeed()
        } else if (tabType == "hobby" && _uiState.value.hobbyState.items.isEmpty() && !_uiState.value.hobbyState.isLoading) {
            loadInitialHobby()
        } else if (tabType == "resale_item" && _uiState.value.resaleState.items.isEmpty() && !_uiState.value.resaleState.isLoading) {
            loadInitialResale()
        } else if (tabType in ZoneDetailUiState.ENTITY_TAB_TYPES) {
            val cur = _uiState.value.entityTabs[tabType]
            if ((cur == null || cur.items.isEmpty()) && cur?.isLoading != true) {
                loadInitialEntity(tabType)
            }
        }
    }

    fun toggleFocus() {
        if (nodeId <= 0L || _uiState.value.isFocusToggling) return
        val currentInfo = _uiState.value.zoneInfo
        viewModelScope.launch {
            _uiState.update { it.copy(isFocusToggling = true) }
            val result = zoneRepository.toggleFocus(
                nodeId = nodeId,
                zoneName = currentInfo?.displayName,
                zoneIcon = currentInfo?.zone?.icon
            )
            result.onSuccess { payload ->
                _uiState.update {
                    it.copy(
                        focusState = payload.focus,
                        followCount = payload.count,
                        isFocusToggling = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isFocusToggling = false) }
            }
        }
    }

    fun selectSubType(subType: String) {
        // 边界归一：与首页同口径，旧键 all 一律转成协议键值 allInfo
        val normalized = ActionSubType.fromPersisted(subType).apiValue
        if (_uiState.value.selectedSubType == normalized) return
        _uiState.update {
            it.copy(
                selectedSubType = normalized,
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isFeedLoading = true,
                errorMessage = null,
                loadMoreError = null
            )
        }
        loadInitialFeed()
    }

    fun selectAlbumOrder(order: String) {
        if (order !in AlbumBoardOptions.ORDER_KEYS) return
        if (_uiState.value.albumSelectedOrder == order) return
        _uiState.update {
            it.copy(
                albumSelectedOrder = order,
                albumItems = emptyList(),
                albumPage = 1,
                hasMoreAlbum = true,
                isAlbumLoading = true,
                albumErrorMessage = null,
                albumLoadMoreError = null
            )
        }
        loadInitialAlbum()
    }

    /** 切换相册分类（`null` = 全部分类）：清列表 + 回第一页 + 重新请求。 */
    fun selectAlbumCategory(categoryId: Int?) {
        if (_uiState.value.albumSelectedCategory == categoryId) return
        _uiState.update {
            it.copy(
                albumSelectedCategory = categoryId,
                albumItems = emptyList(),
                albumPage = 1,
                hasMoreAlbum = true,
                isAlbumLoading = true,
                albumErrorMessage = null,
                albumLoadMoreError = null
            )
        }
        loadInitialAlbum()
    }

    /**
     * 相册分类候选：`GET api/zone/categories?nodeId=&itemType=album`（反编译原版分类菜单来源）。
     *
     * 有效候选少于 2 个时原版不展示分类入口，此处同样置空；请求完成后校验专区未切换，避免串区。
     */
    private fun syncAlbumCategories() {
        val requestNodeId = nodeId
        if (requestNodeId <= 0L) return
        if (_uiState.value.albumCategoryOptions.isNotEmpty()) return
        albumCategoriesJob?.cancel()
        albumCategoriesJob = viewModelScope.launch {
            val result = albumRepository.getAlbumCategories(requestNodeId)
            if (nodeId != requestNodeId) return@launch
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

    fun selectPostsOrder(order: String) {
        if (_uiState.value.postsSelectedOrder == order) return
        _uiState.update {
            it.copy(
                postsSelectedOrder = order,
                postsItems = emptyList(),
                pinnedPosts = emptyList(),
                postsPage = 1,
                hasMorePosts = true,
                isPostsLoading = true,
                postsErrorMessage = null,
                postsLoadMoreError = null
            )
        }
        loadInitialPosts()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update {
            it.copy(
                isRefreshing = true,
                errorMessage = null,
                loadMoreError = null,
                albumErrorMessage = null,
                albumLoadMoreError = null,
                postsErrorMessage = null,
                postsLoadMoreError = null
            )
        }
        loadZoneInfo()

        when {
            _uiState.value.isAlbumTab -> {
                // 分类候选懒加载兜底：切到分享前请求失败/未触发时再补一次
                if (_uiState.value.albumCategoryOptions.isEmpty()) syncAlbumCategories()
                val state = _uiState.value
                albumRefreshInFlight = true
                albumBoardLoader.refresh(
                    AlbumBoardQuery(
                        scope = ZoneQueryScope.Single(nodeId),
                        order = state.albumSelectedOrder,
                        category = state.albumSelectedCategory
                    )
                )
            }
            _uiState.value.isPostsTab -> {
                viewModelScope.launch {
                    val currentOrder = _uiState.value.postsSelectedOrder
                    val result = zoneRepository.getZonePosts(
                        zoneNodeId = nodeId,
                        order = currentOrder,
                        page = 1,
                        pageSize = PostsBoardOptions.PAGE_SIZE
                    )
                    result.onSuccess { rawList ->
                        val (pinned, unpinned) = PostsBoardOptions.partition(rawList)
                        val distinctItems = unpinned.distinctBy { it.id }
                        _uiState.update {
                            it.copy(
                                postsItems = distinctItems,
                                pinnedPosts = pinned,
                                postsPage = 1,
                                isRefreshing = false,
                                isPostsLoading = false,
                                hasMorePosts = rawList.size >= 20,
                                postsLoadMoreError = null
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                isPostsLoading = false,
                                postsErrorMessage = error.message ?: "刷新讨论失败"
                            )
                        }
                    }
                }
            }
            _uiState.value.isHobbyTab -> {
                hobbyLoader.refresh()
                _uiState.update { it.copy(isRefreshing = false) }
            }
            _uiState.value.isResaleTab -> {
                resaleLoader.refresh()
                _uiState.update { it.copy(isRefreshing = false) }
            }
            _uiState.value.isEntityTab -> {
                getEntityLoader(_uiState.value.selectedTab).refresh()
                _uiState.update { it.copy(isRefreshing = false) }
            }
            else -> {
                viewModelScope.launch {
                    val currentState = _uiState.value
                    val result = homeRepository.getHomeFeed(
                        categoryId = 0,
                        subType = currentState.selectedSubType,
                        lastTime = 0L,
                        zoneNodeId = nodeId,
                        zoneIds = null
                    )

                    result.onSuccess { newList ->
                        val newLastTime = parseCursorTimestamp(newList.lastOrNull()?.actionDate)
                        val distinctList = newList.distinctBy { "${it.id}_${it.actionDate}" }
                        _uiState.update {
                            it.copy(
                                items = distinctList,
                                lastTime = newLastTime,
                                isRefreshing = false,
                                isFeedLoading = false,
                                hasMore = distinctList.isNotEmpty() && newLastTime > 0L,
                                loadMoreError = null
                            )
                        }
                    }.onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                isFeedLoading = false,
                                errorMessage = error.message ?: "刷新失败"
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadInitialFeed() {
        if (nodeId <= 0L) return
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            _uiState.update { it.copy(isFeedLoading = true, errorMessage = null) }
            val currentState = _uiState.value
            val result = homeRepository.getHomeFeed(
                categoryId = 0,
                subType = currentState.selectedSubType,
                lastTime = 0L,
                zoneNodeId = nodeId,
                zoneIds = null
            )

            result.onSuccess { newList ->
                val newLastTime = parseCursorTimestamp(newList.lastOrNull()?.actionDate)
                val distinctList = newList.distinctBy { "${it.id}_${it.actionDate}" }
                _uiState.update {
                    it.copy(
                        items = distinctList,
                        lastTime = newLastTime,
                        isFeedLoading = false,
                        hasMore = distinctList.isNotEmpty() && newLastTime > 0L,
                        errorMessage = null,
                        loadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isFeedLoading = false,
                        errorMessage = error.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMore || currentState.isFeedLoading || currentState.isRefreshing || nodeId <= 0L) return

        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }

        viewModelScope.launch {
            val result = homeRepository.getHomeFeed(
                categoryId = 0,
                subType = currentState.selectedSubType,
                lastTime = currentState.lastTime,
                zoneNodeId = nodeId,
                zoneIds = null
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
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        loadMoreError = error.message ?: "加载更多失败"
                    )
                }
            }
        }
    }

    fun loadInitialAlbum() {
        if (nodeId <= 0L) return
        _uiState.update { it.copy(isAlbumLoading = true, albumErrorMessage = null) }
        val state = _uiState.value
        albumBoardLoader.loadInitial(
            AlbumBoardQuery(
                scope = ZoneQueryScope.Single(nodeId),
                order = state.albumSelectedOrder,
                category = state.albumSelectedCategory
            )
        )
    }

    fun loadNextAlbumPage() {
        val state = _uiState.value
        if (state.isAlbumLoadingMore || !state.hasMoreAlbum || state.isAlbumLoading || state.isRefreshing) return
        if (nodeId <= 0L) return
        albumBoardLoader.loadMore()
    }

    fun loadInitialPosts() {
        if (nodeId <= 0L) return
        postsJob?.cancel()
        postsJob = viewModelScope.launch {
            _uiState.update { it.copy(isPostsLoading = true, postsErrorMessage = null) }
            val currentOrder = _uiState.value.postsSelectedOrder
            val result = zoneRepository.getZonePosts(
                zoneNodeId = nodeId,
                order = currentOrder,
                page = 1,
                pageSize = PostsBoardOptions.PAGE_SIZE
            )
            result.onSuccess { rawList ->
                val (pinned, unpinned) = PostsBoardOptions.partition(rawList)
                val distinctItems = unpinned.distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        postsItems = distinctItems,
                        pinnedPosts = pinned,
                        postsPage = 1,
                        isPostsLoading = false,
                        hasMorePosts = rawList.size >= 20,
                        postsErrorMessage = null,
                        postsLoadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isPostsLoading = false,
                        postsErrorMessage = error.message ?: "加载讨论失败"
                    )
                }
            }
        }
    }

    fun loadNextPostsPage() {
        val currentState = _uiState.value
        if (currentState.isPostsLoadingMore || !currentState.hasMorePosts || currentState.isPostsLoading || currentState.isRefreshing || nodeId <= 0L) return

        val nextPage = currentState.postsPage + 1
        _uiState.update { it.copy(isPostsLoadingMore = true, postsLoadMoreError = null) }

        viewModelScope.launch {
            val result = zoneRepository.getZonePosts(
                zoneNodeId = nodeId,
                order = currentState.postsSelectedOrder,
                page = nextPage,
                pageSize = PostsBoardOptions.PAGE_SIZE
            )
            result.onSuccess { newItems ->
                _uiState.update { state ->
                    val existingIds = (state.pinnedPosts.map { it.id } + state.postsItems.map { it.id }).toSet()
                    val uniqueNewItems = newItems.filterNot { it.id in existingIds }
                    val hasMore = newItems.size >= 20 && uniqueNewItems.isNotEmpty()
                    state.copy(
                        postsItems = state.postsItems + uniqueNewItems,
                        postsPage = nextPage,
                        isPostsLoadingMore = false,
                        hasMorePosts = hasMore,
                        postsLoadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isPostsLoadingMore = false,
                        postsLoadMoreError = error.message ?: "加载更多失败"
                    )
                }
            }
        }
    }

    // ==================== 产品 (Hobby) 分类交互方法 ====================

    fun loadInitialHobby() {
        if (nodeId <= 0L) return
        hobbyLoader.loadInitial()
    }

    fun loadNextHobbyPage() {
        if (nodeId <= 0L) return
        hobbyLoader.loadMore()
    }

    fun refreshHobby() {
        if (nodeId <= 0L) return
        hobbyLoader.refresh()
    }

    fun toggleHobbyViewMode() {
        val newMode = !_uiState.value.hobbyState.isGridMode
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(isGridMode = newMode)) }
        viewModelScope.launch {
            viewPreferencesManager.setHobbyListGridMode(newMode)
        }
    }

    fun setHobbyYear(year: Int?) {
        if (_uiState.value.hobbyState.releaseYear == year) return
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(releaseYear = year)) }
        hobbyLoader.loadInitial()
    }

    fun setHobbyMonth(month: Int?) {
        if (_uiState.value.hobbyState.releaseMonth == month) return
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(releaseMonth = month)) }
        hobbyLoader.loadInitial()
    }

    fun setHobbyOrder(order: String) {
        if (_uiState.value.hobbyState.order == order) return
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(order = order)) }
        hobbyLoader.loadInitial()
    }

    fun selectHobbyCategory(categoryId: Int, subCategoryId: Int?) {
        val cur = _uiState.value.hobbyState
        if (cur.categoryId == categoryId && cur.subCategoryId == subCategoryId) return
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(categoryId = categoryId, subCategoryId = subCategoryId)) }
        hobbyLoader.loadInitial()
    }

    fun updateHobbyKeyword(keyword: String) {
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(keyword = keyword)) }
    }

    fun submitHobbySearch() {
        hobbyLoader.loadInitial()
    }

    fun applyHobbyAdvancedFilters(sex: Int?, specs: String?, scale: String?, r18: Int?) {
        _uiState.update {
            it.copy(
                hobbyState = it.hobbyState.copy(
                    sex = sex,
                    specs = specs,
                    scale = scale,
                    r18 = r18,
                    showFilterSheet = false
                )
            )
        }
        hobbyLoader.loadInitial()
    }

    fun resetHobbyAdvancedFilters() {
        _uiState.update {
            it.copy(
                hobbyState = it.hobbyState.copy(
                    sex = null,
                    specs = null,
                    scale = null,
                    r18 = null,
                    showFilterSheet = false
                )
            )
        }
        hobbyLoader.loadInitial()
    }

    fun openHobbyFilterSheet(open: Boolean) {
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(showFilterSheet = open)) }
    }

    fun openHobbyCategoryPicker(open: Boolean) {
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(showCategoryPickerSheet = open)) }
    }

    fun setHobbySearchActive(active: Boolean) {
        _uiState.update { it.copy(hobbyState = it.hobbyState.copy(isSearchActive = active)) }
    }

    // ==================== 二手 (Resale) 分类交互方法 ====================

    fun loadInitialResale() {
        if (nodeId <= 0L) return
        resaleLoader.loadInitial()
    }

    fun loadNextResalePage() {
        if (nodeId <= 0L) return
        resaleLoader.loadMore()
    }

    fun refreshResale() {
        if (nodeId <= 0L) return
        resaleLoader.refresh()
    }

    fun setResaleType(type: Int) {
        if (_uiState.value.resaleState.type == type) return
        _uiState.update { it.copy(resaleState = it.resaleState.copy(type = type)) }
        resaleLoader.loadInitial()
    }

    fun updateResaleKeyword(keyword: String) {
        _uiState.update { it.copy(resaleState = it.resaleState.copy(keyword = keyword)) }
    }

    fun submitResaleSearch() {
        resaleLoader.loadInitial()
    }

    fun setResalePurity(purity: String?) {
        if (_uiState.value.resaleState.purity == purity) return
        _uiState.update { it.copy(resaleState = it.resaleState.copy(purity = purity)) }
        resaleLoader.loadInitial()
    }

    /**
     * 提交专区内二手交易举报。
     */
    fun submitResaleReport(resaleId: Long, reason: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            collectRepository.submitReport(
                nodeId = resaleId,
                content = reason,
                type = CollectConstants.REPORT_TYPE_RESALE
            ).onSuccess {
                onResult(true, "举报成功，感谢您的反馈")
            }.onFailure { err ->
                onResult(false, err.message ?: "举报提交失败，请稍后重试")
            }
        }
    }

    // ==================== 5 个实体分类交互方法 ====================

    fun loadInitialEntity(tabType: String) {
        if (nodeId <= 0L) return
        getEntityLoader(tabType).loadInitial()
    }

    fun loadNextEntityPage(tabType: String) {
        if (nodeId <= 0L) return
        getEntityLoader(tabType).loadMore()
    }

    fun refreshEntity(tabType: String) {
        if (nodeId <= 0L) return
        getEntityLoader(tabType).refresh()
    }

    // ==================== 悬浮发布弹窗 ====================

    fun showPublishSheet(show: Boolean) {
        _uiState.update { it.copy(showPublishSheet = show) }
    }

    private fun parseCursorTimestamp(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return DateTimeFormats.parseServerDateTime(dateStr) ?: 0L
    }
}
