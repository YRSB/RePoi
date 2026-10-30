package com.nobody.repoi.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ActionItem
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
import com.nobody.repoi.util.paging.KeyedListLoader
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
    val categoryId: Int = 10000,
    val subCategoryId: Int? = null,
    val order: String = "hits7Day",
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

    // 动态 Tab 状态
    val selectedSubType: String = "recommend",
    val items: List<ActionItem> = emptyList(),
    val lastTime: Long = 0L,
    val isFeedLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,

    // 分享/相册 Tab 状态
    val albumSelectedOrder: String = "top",
    val albumItems: List<AlbumListItem> = emptyList(),
    val albumPage: Int = 1,
    val isAlbumLoading: Boolean = false,
    val isAlbumLoadingMore: Boolean = false,
    val hasMoreAlbum: Boolean = true,
    val albumErrorMessage: String? = null,
    val albumLoadMoreError: String? = null,

    // 讨论/帖子 Tab 状态
    val postsSelectedOrder: String = "upd",
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

    val subTypes = listOf(
        Pair("recommend", "推荐"),
        Pair("all", "全部"),
        Pair("comment", "讨论"),
        Pair("confirm", "制作"),
        Pair("official_pic", "官图"),
        Pair("preorder", "预定"),
        Pair("delay", "延期"),
        Pair("release", "出荷"),
        Pair("reorder", "再版")
    )

    val albumOrders = listOf(
        Pair("top", "推荐"),
        Pair("add", "最新创建"),
        Pair("update", "最近更新"),
        Pair("picCount", "图片数量"),
        Pair("hits", "总热度"),
        Pair("hitsDay", "一天热度")
    )

    val postsOrders = listOf(
        Pair("upd", "回复"),
        Pair("add", "新贴"),
        Pair("top", "推荐")
    )

    val resaleTypes = listOf(
        Pair(1, "出售"),
        Pair(2, "求购")
    )

    val resalePurities = listOf(
        Pair(null, "全部成色"),
        Pair("11", "全新未拆"),
        Pair("9", "仅拆封"),
        Pair("8", "二手好相"),
        Pair("7", "瑕疵品")
    )

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
    private var albumJob: Job? = null
    private var postsJob: Job? = null

    // 实体分类加载控制器缓存
    private val entityLoaders = mutableMapOf<String, PagedListLoader<ZoneEntityItem>>()

    // 产品 (hobby) 分页加载控制器
    private val hobbyLoader: PagedListLoader<HobbyItemSummary> = PagedListLoader.fromList(
        scope = viewModelScope,
        initialPage = 1,
        pageSize = 24,
        keySelector = { if (it.id > 0) it.id else it.itemId }
    ) { page, size ->
        val curHobby = _uiState.value.hobbyState
        val params = mutableMapOf<String, String>()
        params["page"] = page.toString()
        params["pageSize"] = size.toString()
        params["category"] = (curHobby.subCategoryId ?: curHobby.categoryId).toString()
        params["order"] = curHobby.order
        params["zones"] = nodeId.toString()

        curHobby.releaseYear?.let { if (it > 0) params["releaseYear"] = it.toString() }
        curHobby.releaseMonth?.let { if (it > 0) params["releaseMonth"] = it.toString() }
        if (curHobby.keyword.isNotBlank()) params["keyword"] = curHobby.keyword.trim()
        curHobby.sex?.let { if (it != -1) params["sex"] = it.toString() }
        curHobby.specs?.let { if (it.isNotBlank()) params["specs"] = it }
        curHobby.scale?.let { if (it.isNotBlank() && it != "0") params["scale"] = it }
        curHobby.r18?.let { if (it != -1) params["r18"] = it.toString() }

        itemRepository.queryHobbyList(params)
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
                zones = nodeId,
                type = curResale.type,
                keyword = curResale.keyword.takeIf { it.isNotBlank() },
                puritys = curResale.purity,
                lastTime = cursor,
                pageSize = 24
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
                pageSize = 20,
                keySelector = { it.targetId }
            ) { page, size ->
                zoneRepository.getZoneItems(nodeId = nodeId, itemType = tabType, page = page, pageSize = size)
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
                postsSelectedOrder = "upd",
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
        val tabTitleMap = mapOf(
            "action" to "动态",
            "album" to "分享",
            "share" to "分享",
            "posts" to "讨论",
            "hobby" to "产品",
            "resale_item" to "二手",
            "company" to "厂商",
            "series" to "系列",
            "works" to "作品",
            "charactar" to "角色",
            "person" to "人物",
            "community" to "社区"
        )

        val rawTabs = serverTabs?.filter { it.type.isNotBlank() && it.type != "shop_item" }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(
                ZoneTabItem("action", "动态"),
                ZoneTabItem("album", "分享"),
                ZoneTabItem("posts", "讨论"),
                ZoneTabItem("hobby", "产品"),
                ZoneTabItem("resale_item", "二手"),
                ZoneTabItem("company", "厂商"),
                ZoneTabItem("series", "系列"),
                ZoneTabItem("works", "作品"),
                ZoneTabItem("charactar", "角色"),
                ZoneTabItem("person", "人物")
            )
        val result = rawTabs.filter { it.type != "shop_item" }.map { tab ->
            val label = if (tab.type == "hobby") {
                "产品"
            } else {
                tab.title.takeIf { it.isNotBlank() } ?: (tabTitleMap[tab.type] ?: tab.type)
            }
            tab.type to label
        }
        // 确保动态 tab 始终在首位
        return if (result.any { it.first == "action" }) {
            result
        } else {
            listOf("action" to "动态") + result
        }
    }

    fun selectTab(tabType: String) {
        if (_uiState.value.selectedTab == tabType) return
        _uiState.update { it.copy(selectedTab = tabType) }
        val isAlbum = tabType == "album" || tabType == "share"
        val isPosts = tabType == "posts"
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
        if (_uiState.value.selectedSubType == subType) return
        _uiState.update {
            it.copy(
                selectedSubType = subType,
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
                viewModelScope.launch {
                    val currentOrder = _uiState.value.albumSelectedOrder
                    val result = albumRepository.getAlbums(
                        page = 1,
                        pageSize = 24,
                        orderString = currentOrder,
                        zones = nodeId.toString()
                    )
                    result.onSuccess { page ->
                        val distinctList = page.items.distinctBy { it.targetAlbumId }
                        _uiState.update {
                            it.copy(
                                albumItems = distinctList,
                                albumPage = 1,
                                isRefreshing = false,
                                isAlbumLoading = false,
                                hasMoreAlbum = page.hasMore,
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
            _uiState.value.isPostsTab -> {
                viewModelScope.launch {
                    val currentOrder = _uiState.value.postsSelectedOrder
                    val result = zoneRepository.getZonePosts(
                        zoneNodeId = nodeId,
                        order = currentOrder,
                        page = 1,
                        pageSize = 20
                    )
                    result.onSuccess { rawList ->
                        val (pinned, unpinned) = partitionPosts(rawList)
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
        albumJob?.cancel()
        albumJob = viewModelScope.launch {
            _uiState.update { it.copy(isAlbumLoading = true, albumErrorMessage = null) }
            val currentOrder = _uiState.value.albumSelectedOrder
            val result = albumRepository.getAlbums(
                page = 1,
                pageSize = 24,
                orderString = currentOrder,
                zones = nodeId.toString()
            )
            result.onSuccess { page ->
                val distinctList = page.items.distinctBy { it.targetAlbumId }
                _uiState.update {
                    it.copy(
                        albumItems = distinctList,
                        albumPage = 1,
                        isAlbumLoading = false,
                        hasMoreAlbum = page.hasMore,
                        albumErrorMessage = null,
                        albumLoadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAlbumLoading = false,
                        albumErrorMessage = error.message ?: "加载分享失败"
                    )
                }
            }
        }
    }

    fun loadNextAlbumPage() {
        val currentState = _uiState.value
        if (currentState.isAlbumLoadingMore || !currentState.hasMoreAlbum || currentState.isAlbumLoading || currentState.isRefreshing || nodeId <= 0L) return

        val nextPage = currentState.albumPage + 1
        _uiState.update { it.copy(isAlbumLoadingMore = true, albumLoadMoreError = null) }

        viewModelScope.launch {
            val result = albumRepository.getAlbums(
                page = nextPage,
                pageSize = 24,
                orderString = currentState.albumSelectedOrder,
                zones = nodeId.toString()
            )
            result.onSuccess { page ->
                _uiState.update { state ->
                    val existingIds = state.albumItems.map { it.targetAlbumId }.toSet()
                    val uniqueNewItems = page.items.filterNot { it.targetAlbumId in existingIds }
                    val hasMore = page.hasMore && uniqueNewItems.isNotEmpty()
                    state.copy(
                        albumItems = state.albumItems + uniqueNewItems,
                        albumPage = nextPage,
                        isAlbumLoadingMore = false,
                        hasMoreAlbum = hasMore,
                        albumLoadMoreError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAlbumLoadingMore = false,
                        albumLoadMoreError = error.message ?: "加载更多失败"
                    )
                }
            }
        }
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
                pageSize = 20
            )
            result.onSuccess { rawList ->
                val (pinned, unpinned) = partitionPosts(rawList)
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

    private fun partitionPosts(rawList: List<PostItem>): Pair<List<PostItem>, List<PostItem>> {
        return if (rawList.any { it.isPinned }) {
            val pinned = rawList.filter { it.isPinned }.take(5)
            val unpinned = rawList.filterNot { it.isPinned }
            pinned to unpinned
        } else if (rawList.size > 5) {
            val pinned = rawList.take(5)
            val unpinned = rawList.drop(5)
            pinned to unpinned
        } else if (rawList.size > 2) {
            val pinned = rawList.take(2)
            val unpinned = rawList.drop(2)
            pinned to unpinned
        } else {
            emptyList<PostItem>() to rawList
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
                pageSize = 20
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
