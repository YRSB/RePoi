package com.nobody.repoi.ui.board.session

import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.ENTITY_BOARD_PAGE_SIZE
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.data.remote.model.ZoneHobbyFilter
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.applyTo
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
import com.nobody.repoi.util.paging.KeyedListLoader
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.hasNewKeysAfterDedup
import com.nobody.repoi.util.paging.pagedChunkLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 专区子板块会话控制器实现类（深模块）。
 *
 * 封装专区 10 类子板块的生命周期调度、首屏懒加载、独立分页状态机、筛选不变性与滚动记忆。
 * 宿主只需通过 [activeSnapshot] 观察当前活跃板块的快照，或通过 [snapshotFor] 提取任意板块投影。
 */
class ZoneBoardSessionImpl(
    private val coroutineScope: CoroutineScope,
    initialScope: ZoneQueryScope = ZoneQueryScope.All,
    initialBoard: ZoneBoard = ZoneBoard.Action,
    homeRepository: HomeRepository,
    albumRepository: AlbumRepository,
    zoneRepository: ZoneRepository,
    itemRepository: ItemRepository,
    resaleRepository: ResaleRepository,
    viewPreferencesManager: ViewPreferencesManager
) : ZoneBoardSession {

    private var currentScope: ZoneQueryScope = initialScope
    private var activeBoard: ZoneBoard = initialBoard

    private val scrollPositions = mutableMapOf<ZoneBoard, BoardScrollPosition>()

    private val actionPager = ActionBoardPager(
        homeRepository = homeRepository,
        coroutineScope = coroutineScope,
        onChanged = { handleBoardChanged(ZoneBoard.Action) }
    )

    private val albumPager = AlbumBoardPager(
        albumRepository = albumRepository,
        coroutineScope = coroutineScope,
        onChanged = { handleBoardChanged(ZoneBoard.Album) }
    )

    private val postsPager = PostsBoardPager(
        zoneRepository = zoneRepository,
        coroutineScope = coroutineScope,
        onChanged = { handleBoardChanged(ZoneBoard.Posts) }
    )

    private val hobbyPager = HobbyBoardPager(
        itemRepository = itemRepository,
        viewPreferencesManager = viewPreferencesManager,
        coroutineScope = coroutineScope,
        onChanged = { handleBoardChanged(ZoneBoard.Hobby) }
    )

    private val resalePager = ResaleBoardPager(
        resaleRepository = resaleRepository,
        coroutineScope = coroutineScope,
        onChanged = { handleBoardChanged(ZoneBoard.Resale) }
    )

    private val entityPagers: Map<ZoneBoard, EntityBoardPager> = ZoneBoard.ENTITY.associateWith { board ->
        EntityBoardPager(
            board = board,
            zoneRepository = zoneRepository,
            coroutineScope = coroutineScope,
            onChanged = { handleBoardChanged(board) }
        )
    }

    private val allPagers: List<BoardPager> by lazy {
        listOf(actionPager, albumPager, postsPager, hobbyPager, resalePager) + entityPagers.values
    }

    private fun pagerFor(board: ZoneBoard): BoardPager = when (board) {
        ZoneBoard.Action -> actionPager
        ZoneBoard.Album -> albumPager
        ZoneBoard.Posts -> postsPager
        ZoneBoard.Hobby -> hobbyPager
        ZoneBoard.Resale -> resalePager
        else -> entityPagers.getValue(board)
    }

    private val _activeSnapshot: MutableStateFlow<ZoneBoardSnapshot>
    override val activeSnapshot: StateFlow<ZoneBoardSnapshot>

    init {
        // 初始活跃板块触发首屏懒加载
        pagerFor(activeBoard).ensureLoaded(currentScope)
        _activeSnapshot = MutableStateFlow(snapshotFor(activeBoard))
        activeSnapshot = _activeSnapshot.asStateFlow()
    }

    override fun snapshotFor(board: ZoneBoard): ZoneBoardSnapshot {
        val pager = pagerFor(board)
        if (!pager.isLoaded) {
            pager.ensureLoaded(currentScope)
        }
        val scroll = scrollPositions[board] ?: BoardScrollPosition()
        return pager.currentSnapshot(scroll)
    }

    override fun dispatch(intent: ZoneBoardIntent) {
        when (intent) {
            // ── 核心通用生命周期意图 ──
            is ZoneBoardIntent.SelectBoard -> {
                val previousBoard = activeBoard
                activeBoard = intent.board
                val snapshot = snapshotFor(intent.board)
                if (previousBoard != intent.board) {
                    _activeSnapshot.value = snapshot
                }
            }
            is ZoneBoardIntent.LoadMore -> {
                val target = intent.board ?: activeBoard
                pagerFor(target).loadMore(currentScope)
            }
            is ZoneBoardIntent.RetryInitial -> {
                val target = intent.board ?: activeBoard
                pagerFor(target).loadInitial(currentScope)
            }
            is ZoneBoardIntent.Refresh -> {
                val target = intent.board ?: activeBoard
                pagerFor(target).refresh(currentScope)
            }

            // ── 筛选意图：Action ──
            is ZoneBoardIntent.SelectSubType -> {
                scrollPositions[ZoneBoard.Action] = BoardScrollPosition()
                actionPager.setSubType(intent.subType, currentScope)
            }

            // ── 筛选意图：Album ──
            is ZoneBoardIntent.SelectAlbumOrder -> {
                scrollPositions[ZoneBoard.Album] = BoardScrollPosition()
                albumPager.setOrder(intent.order, currentScope)
            }
            is ZoneBoardIntent.SelectAlbumCategory -> {
                scrollPositions[ZoneBoard.Album] = BoardScrollPosition()
                albumPager.setCategory(intent.categoryId, currentScope)
            }

            // ── 筛选意图：Posts ──
            is ZoneBoardIntent.SelectPostsOrder -> {
                scrollPositions[ZoneBoard.Posts] = BoardScrollPosition()
                postsPager.setOrder(intent.order, currentScope)
            }

            // ── 筛选意图：Resale ──
            is ZoneBoardIntent.SelectResaleType -> {
                scrollPositions[ZoneBoard.Resale] = BoardScrollPosition()
                resalePager.setType(intent.type, currentScope)
            }
            is ZoneBoardIntent.SelectResalePurity -> {
                scrollPositions[ZoneBoard.Resale] = BoardScrollPosition()
                resalePager.setPurity(intent.purity, currentScope)
            }
            is ZoneBoardIntent.UpdateResaleKeyword -> {
                resalePager.updateKeyword(intent.keyword)
            }
            is ZoneBoardIntent.SubmitResaleSearch -> {
                scrollPositions[ZoneBoard.Resale] = BoardScrollPosition()
                resalePager.submitSearch(currentScope)
            }

            // ── 筛选意图：Hobby ──
            is ZoneBoardIntent.SelectHobbyOrder -> {
                scrollPositions[ZoneBoard.Hobby] = BoardScrollPosition()
                hobbyPager.setOrder(intent.order, currentScope)
            }
            is ZoneBoardIntent.SelectHobbyYear -> {
                scrollPositions[ZoneBoard.Hobby] = BoardScrollPosition()
                hobbyPager.setYear(intent.year, currentScope)
            }
            is ZoneBoardIntent.SelectHobbyMonth -> {
                scrollPositions[ZoneBoard.Hobby] = BoardScrollPosition()
                hobbyPager.setMonth(intent.month, currentScope)
            }
            is ZoneBoardIntent.SelectHobbyCategory -> {
                scrollPositions[ZoneBoard.Hobby] = BoardScrollPosition()
                hobbyPager.setCategory(intent.categoryId, intent.subCategoryId, currentScope)
            }
            is ZoneBoardIntent.ApplyHobbyFilter -> {
                scrollPositions[ZoneBoard.Hobby] = BoardScrollPosition()
                hobbyPager.applyAdvanced(intent.filterParam, currentScope)
            }
            is ZoneBoardIntent.ResetHobbyFilter -> {
                scrollPositions[ZoneBoard.Hobby] = BoardScrollPosition()
                hobbyPager.resetAdvanced(currentScope)
            }
            is ZoneBoardIntent.ToggleHobbyViewMode -> {
                hobbyPager.toggleViewMode()
            }
            is ZoneBoardIntent.SetHobbyFilterSheetVisible -> {
                hobbyPager.setFilterSheetVisible(intent.visible)
            }
            is ZoneBoardIntent.SetHobbyCategoryPickerVisible -> {
                hobbyPager.setCategoryPickerVisible(intent.visible)
            }
            is ZoneBoardIntent.UpdateHobbyKeyword -> {
                hobbyPager.updateKeyword(intent.keyword)
            }
            is ZoneBoardIntent.SubmitHobbySearch -> {
                scrollPositions[ZoneBoard.Hobby] = BoardScrollPosition()
                hobbyPager.submitSearch(currentScope)
            }

            // ── 滚动记忆意图 ──
            is ZoneBoardIntent.SaveScrollPosition -> {
                val current = scrollPositions[intent.board] ?: BoardScrollPosition()
                val next = current.copy(index = intent.index, offset = intent.offset)
                scrollPositions[intent.board] = next
                if (intent.board == activeBoard) {
                    _activeSnapshot.value = _activeSnapshot.value.copy(scrollPosition = next)
                }
            }
            is ZoneBoardIntent.SaveFilterCollapseFraction -> {
                val current = scrollPositions[intent.board] ?: BoardScrollPosition()
                val next = current.copy(filterCollapseFraction = intent.fraction)
                scrollPositions[intent.board] = next
                if (intent.board == activeBoard) {
                    _activeSnapshot.value = _activeSnapshot.value.copy(scrollPosition = next)
                }
            }
            is ZoneBoardIntent.SaveFilterRowScroll -> {
                val current = scrollPositions[intent.board] ?: BoardScrollPosition()
                val next = current.copy(filterRowScrollX = intent.scrollX)
                scrollPositions[intent.board] = next
                if (intent.board == activeBoard) {
                    _activeSnapshot.value = _activeSnapshot.value.copy(scrollPosition = next)
                }
            }
            is ZoneBoardIntent.ResetScrollMemory -> {
                scrollPositions[intent.board] = BoardScrollPosition()
                if (intent.board == activeBoard) {
                    _activeSnapshot.value = _activeSnapshot.value.copy(scrollPosition = BoardScrollPosition())
                }
            }
        }
    }

    override fun updateScope(scope: ZoneQueryScope) {
        if (scope == currentScope) return
        currentScope = scope
        scrollPositions.clear()
        for (pager in allPagers) {
            pager.onScopeChanged(scope)
        }
        val activePager = pagerFor(activeBoard)
        activePager.ensureLoaded(scope)
        _activeSnapshot.value = snapshotFor(activeBoard)
    }

    private fun handleBoardChanged(board: ZoneBoard) {
        if (board == activeBoard) {
            val scroll = scrollPositions[board] ?: BoardScrollPosition()
            _activeSnapshot.value = pagerFor(board).currentSnapshot(scroll)
        }
    }

    /**
     * 会话控制器创建工厂。
     */
    class Factory(
        private val homeRepository: HomeRepository,
        private val albumRepository: AlbumRepository,
        private val zoneRepository: ZoneRepository,
        private val itemRepository: ItemRepository,
        private val resaleRepository: ResaleRepository,
        private val viewPreferencesManager: ViewPreferencesManager
    ) : ZoneBoardSession.Factory {
        override fun create(
            coroutineScope: CoroutineScope,
            initialScope: ZoneQueryScope,
            initialBoard: ZoneBoard
        ): ZoneBoardSession {
            return ZoneBoardSessionImpl(
                coroutineScope = coroutineScope,
                initialScope = initialScope,
                initialBoard = initialBoard,
                homeRepository = homeRepository,
                albumRepository = albumRepository,
                zoneRepository = zoneRepository,
                itemRepository = itemRepository,
                resaleRepository = resaleRepository,
                viewPreferencesManager = viewPreferencesManager
            )
        }
    }
}

/** 内部子板块调度器通用接缝。 */
private interface BoardPager {
    val board: ZoneBoard
    val isLoaded: Boolean
    fun ensureLoaded(scope: ZoneQueryScope)
    fun loadInitial(scope: ZoneQueryScope)
    fun loadMore(scope: ZoneQueryScope)
    fun refresh(scope: ZoneQueryScope)
    fun onScopeChanged(newScope: ZoneQueryScope)
    fun currentSnapshot(scrollPosition: BoardScrollPosition): ZoneBoardSnapshot
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. ActionBoardPager（动态板块调度器）
// ─────────────────────────────────────────────────────────────────────────────

private class ActionBoardPager(
    private val homeRepository: HomeRepository,
    private val coroutineScope: CoroutineScope,
    private val onChanged: () -> Unit
) : BoardPager {

    override val board: ZoneBoard = ZoneBoard.Action
    override var isLoaded: Boolean = false
        private set

    private var subType: String = ActionSubType.DEFAULT.apiValue
    private var items: List<ActionItem> = emptyList()
    private var lastTime: Long = 0L
    private var hasMore: Boolean = true
    private var pageView: ZoneBoardPageView = ZoneBoardPageView()
    private var job: Job? = null

    private fun parseCursorTimestamp(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return DateTimeFormats.parseServerDateTime(dateStr) ?: 0L
    }

    override fun ensureLoaded(scope: ZoneQueryScope) {
        if (isLoaded || pageView.isLoading) return
        loadInitial(scope)
    }

    override fun loadInitial(scope: ZoneQueryScope) {
        job?.cancel()
        isLoaded = true
        if (scope.isFollowEmpty) {
            items = emptyList()
            lastTime = 0L
            hasMore = false
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        pageView = ZoneBoardPageView(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val result = homeRepository.getHomeFeed(
                categoryId = 0,
                subType = subType,
                lastTime = 0L,
                zoneNodeId = scope.zoneNodeId(),
                zoneIds = scope.zonesParam()
            )
            result.onSuccess { newList ->
                val newLastTime = parseCursorTimestamp(newList.lastOrNull()?.actionDate)
                val distinctList = newList.distinctBy { "${it.id}_${it.actionDate}" }
                items = distinctList
                lastTime = newLastTime
                hasMore = distinctList.isNotEmpty() && newLastTime > 0L
                pageView = ZoneBoardPageView(isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                pageView = ZoneBoardPageView(isLoading = false, errorMessage = error.message ?: "加载失败")
                onChanged()
            }
        }
    }

    override fun loadMore(scope: ZoneQueryScope) {
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        pageView = pageView.copy(isLoadingMore = true, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val result = homeRepository.getHomeFeed(
                categoryId = 0,
                subType = subType,
                lastTime = lastTime,
                zoneNodeId = scope.zoneNodeId(),
                zoneIds = scope.zonesParam()
            )
            result.onSuccess { nextPageList ->
                val newLastTime = parseCursorTimestamp(nextPageList.lastOrNull()?.actionDate)
                val existingKeys = items.map { "${it.id}_${it.actionDate}" }.toSet()
                val uniqueNewItems = nextPageList.filterNot { "${it.id}_${it.actionDate}" in existingKeys }
                val nextCursor = if (newLastTime > 0L) newLastTime else lastTime
                val hasNewPageItems = nextPageList.hasNewKeysAfterDedup(items) { "${it.id}_${it.actionDate}" }
                val more = nextPageList.isNotEmpty() && hasNewPageItems && newLastTime > 0L && newLastTime != lastTime
                items = items + uniqueNewItems
                lastTime = nextCursor
                hasMore = more
                pageView = pageView.copy(isLoadingMore = false, isEndReached = !more)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException) {
                    pageView = pageView.copy(isLoadingMore = false)
                    onChanged()
                    return@onFailure
                }
                pageView = pageView.copy(isLoadingMore = false, loadMoreError = error.message ?: "加载更多失败")
                onChanged()
            }
        }
    }

    override fun refresh(scope: ZoneQueryScope) {
        job?.cancel()
        if (scope.isFollowEmpty) {
            items = emptyList()
            lastTime = 0L
            hasMore = false
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        pageView = pageView.copy(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val result = homeRepository.getHomeFeed(
                categoryId = 0,
                subType = subType,
                lastTime = 0L,
                zoneNodeId = scope.zoneNodeId(),
                zoneIds = scope.zonesParam()
            )
            result.onSuccess { newList ->
                val newLastTime = parseCursorTimestamp(newList.lastOrNull()?.actionDate)
                val distinctList = newList.distinctBy { "${it.id}_${it.actionDate}" }
                items = distinctList
                lastTime = newLastTime
                hasMore = distinctList.isNotEmpty() && newLastTime > 0L
                pageView = ZoneBoardPageView(isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                pageView = pageView.copy(isLoading = false, errorMessage = error.message ?: "刷新失败")
                onChanged()
            }
        }
    }

    fun setSubType(newSubType: String, scope: ZoneQueryScope) {
        if (newSubType == subType) return
        subType = newSubType
        loadInitial(scope)
    }

    override fun onScopeChanged(newScope: ZoneQueryScope) {
        job?.cancel()
        items = emptyList()
        lastTime = 0L
        hasMore = true
        isLoaded = false
        pageView = ZoneBoardPageView()
        onChanged()
    }

    override fun currentSnapshot(scrollPosition: BoardScrollPosition): ZoneBoardSnapshot {
        return ZoneBoardSnapshot(
            board = ZoneBoard.Action,
            content = ZoneBoardContent.Action(items = items, page = pageView),
            filters = ZoneBoardFilters(subType = subType),
            pageView = pageView,
            scrollPosition = scrollPosition
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. AlbumBoardPager（分享板块调度器）
// ─────────────────────────────────────────────────────────────────────────────

private class AlbumBoardPager(
    private val albumRepository: AlbumRepository,
    private val coroutineScope: CoroutineScope,
    private val onChanged: () -> Unit
) : BoardPager {

    override val board: ZoneBoard = ZoneBoard.Album
    override var isLoaded: Boolean = false
        private set

    private var order: String = AlbumBoardOptions.DEFAULT_ORDER
    private var category: Int? = null
    private var items: List<AlbumListItem> = emptyList()
    private var pageView: ZoneBoardPageView = ZoneBoardPageView()
    private var existingAlbums: List<AlbumListItem> = emptyList()
    private var currentScope: ZoneQueryScope = ZoneQueryScope.All

    private val loader = PagedListLoader<AlbumListItem>(
        scope = coroutineScope,
        initialPage = 1,
        pageSize = AlbumBoardOptions.PAGE_SIZE,
        keySelector = { it.targetAlbumId }
    ) { page, pageSize ->
        albumRepository.getAlbums(
            page = page,
            pageSize = pageSize,
            orderString = order,
            zones = currentScope.zonesParam(),
            category = category
        ).map { payload ->
            val hasMore = payload.hasMore &&
                (page == 1 || payload.items.hasNewKeysAfterDedup(existingAlbums) { it.targetAlbumId })
            PageChunk(items = payload.items, hasMore = hasMore)
        }
    }

    init {
        coroutineScope.launch {
            loader.state.collect { state ->
                items = state.items
                pageView = ZoneBoardPageView(
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    errorMessage = state.initialError,
                    loadMoreError = state.appendError,
                    isEndReached = !state.hasMore
                )
                onChanged()
            }
        }
    }

    override fun ensureLoaded(scope: ZoneQueryScope) {
        if (isLoaded || pageView.isLoading) return
        loadInitial(scope)
    }

    override fun loadInitial(scope: ZoneQueryScope) {
        currentScope = scope
        isLoaded = true
        if (scope.isFollowEmpty) {
            loader.reset(clearItems = true)
            items = emptyList()
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        loader.loadInitial()
    }

    override fun loadMore(scope: ZoneQueryScope) {
        currentScope = scope
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        existingAlbums = loader.state.value.items
        loader.loadMore()
    }

    override fun refresh(scope: ZoneQueryScope) {
        currentScope = scope
        if (scope.isFollowEmpty) {
            loader.reset(clearItems = true)
            items = emptyList()
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        loader.refresh()
    }

    fun setOrder(newOrder: String, scope: ZoneQueryScope) {
        if (newOrder !in AlbumBoardOptions.ORDER_KEYS || newOrder == order) return
        order = newOrder
        loadInitial(scope)
    }

    fun setCategory(newCategoryId: Int?, scope: ZoneQueryScope) {
        if (newCategoryId == category) return
        category = newCategoryId
        loadInitial(scope)
    }

    override fun onScopeChanged(newScope: ZoneQueryScope) {
        currentScope = newScope
        loader.reset(clearItems = true)
        items = emptyList()
        isLoaded = false
        category = null
        pageView = ZoneBoardPageView()
        onChanged()
    }

    override fun currentSnapshot(scrollPosition: BoardScrollPosition): ZoneBoardSnapshot {
        return ZoneBoardSnapshot(
            board = ZoneBoard.Album,
            content = ZoneBoardContent.Album(items = items, page = pageView),
            filters = ZoneBoardFilters(albumOrder = order, albumCategory = category),
            pageView = pageView,
            scrollPosition = scrollPosition
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. PostsBoardPager（讨论板块调度器）
// ─────────────────────────────────────────────────────────────────────────────

private class PostsBoardPager(
    private val zoneRepository: ZoneRepository,
    private val coroutineScope: CoroutineScope,
    private val onChanged: () -> Unit
) : BoardPager {

    override val board: ZoneBoard = ZoneBoard.Posts
    override var isLoaded: Boolean = false
        private set

    private var order: String = PostsBoardOptions.DEFAULT_ORDER
    private var pinnedPosts: List<PostItem> = emptyList()
    private var items: List<PostItem> = emptyList()
    private var page: Int = 1
    private var hasMore: Boolean = true
    private var pageView: ZoneBoardPageView = ZoneBoardPageView()
    private var job: Job? = null

    override fun ensureLoaded(scope: ZoneQueryScope) {
        if (isLoaded || pageView.isLoading) return
        loadInitial(scope)
    }

    override fun loadInitial(scope: ZoneQueryScope) {
        job?.cancel()
        isLoaded = true
        if (scope.isFollowEmpty) {
            pinnedPosts = emptyList()
            items = emptyList()
            hasMore = false
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        pageView = ZoneBoardPageView(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqOrder = order
            val result = zoneRepository.getZonePosts(
                zoneNodeId = scope.zoneNodeId(),
                order = reqOrder,
                page = 1,
                pageSize = PostsBoardOptions.PAGE_SIZE
            )
            result.onSuccess { rawList ->
                if (order != reqOrder) return@onSuccess
                if (scope is ZoneQueryScope.Single) {
                    val (pinned, unpinned) = PostsBoardOptions.partition(rawList)
                    pinnedPosts = pinned
                    items = unpinned.distinctBy { it.id }
                } else {
                    pinnedPosts = emptyList()
                    items = rawList.distinctBy { it.id }
                }
                page = 1
                hasMore = rawList.size >= PostsBoardOptions.PAGE_SIZE
                pageView = ZoneBoardPageView(isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || order != reqOrder) return@onFailure
                pageView = ZoneBoardPageView(isLoading = false, errorMessage = error.message ?: "加载失败")
                onChanged()
            }
        }
    }

    override fun loadMore(scope: ZoneQueryScope) {
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        pageView = pageView.copy(isLoadingMore = true, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqOrder = order
            val nextPage = page + 1
            val result = zoneRepository.getZonePosts(
                zoneNodeId = scope.zoneNodeId(),
                order = reqOrder,
                page = nextPage,
                pageSize = PostsBoardOptions.PAGE_SIZE
            )
            result.onSuccess { rawList ->
                if (order != reqOrder) return@onSuccess
                val existingPosts = pinnedPosts + items
                val existingIds = existingPosts.map { it.id }.toSet()
                val uniqueNewItems = rawList.filterNot { it.id in existingIds }
                val hasNewItems = rawList.hasNewKeysAfterDedup(existingPosts) { it.id }
                val more = rawList.size >= PostsBoardOptions.PAGE_SIZE && hasNewItems
                items = items + uniqueNewItems
                page = nextPage
                hasMore = more
                pageView = pageView.copy(isLoadingMore = false, isEndReached = !more)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || order != reqOrder) {
                    pageView = pageView.copy(isLoadingMore = false)
                    onChanged()
                    return@onFailure
                }
                pageView = pageView.copy(isLoadingMore = false, loadMoreError = error.message ?: "加载更多失败")
                onChanged()
            }
        }
    }

    override fun refresh(scope: ZoneQueryScope) {
        job?.cancel()
        if (scope.isFollowEmpty) {
            pinnedPosts = emptyList()
            items = emptyList()
            hasMore = false
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        pageView = pageView.copy(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqOrder = order
            val result = zoneRepository.getZonePosts(
                zoneNodeId = scope.zoneNodeId(),
                order = reqOrder,
                page = 1,
                pageSize = PostsBoardOptions.PAGE_SIZE
            )
            result.onSuccess { rawList ->
                if (order != reqOrder) return@onSuccess
                if (scope is ZoneQueryScope.Single) {
                    val (pinned, unpinned) = PostsBoardOptions.partition(rawList)
                    pinnedPosts = pinned
                    items = unpinned.distinctBy { it.id }
                } else {
                    pinnedPosts = emptyList()
                    items = rawList.distinctBy { it.id }
                }
                page = 1
                hasMore = rawList.size >= PostsBoardOptions.PAGE_SIZE
                pageView = ZoneBoardPageView(isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || order != reqOrder) return@onFailure
                pageView = pageView.copy(isLoading = false, errorMessage = error.message ?: "刷新失败")
                onChanged()
            }
        }
    }

    fun setOrder(newOrder: String, scope: ZoneQueryScope) {
        if (newOrder == order) return
        order = newOrder
        loadInitial(scope)
    }

    override fun onScopeChanged(newScope: ZoneQueryScope) {
        job?.cancel()
        pinnedPosts = emptyList()
        items = emptyList()
        page = 1
        hasMore = true
        isLoaded = false
        pageView = ZoneBoardPageView()
        onChanged()
    }

    override fun currentSnapshot(scrollPosition: BoardScrollPosition): ZoneBoardSnapshot {
        return ZoneBoardSnapshot(
            board = ZoneBoard.Posts,
            content = ZoneBoardContent.Posts(pinnedPosts = pinnedPosts, items = items, page = pageView),
            filters = ZoneBoardFilters(postsOrder = order),
            pageView = pageView,
            scrollPosition = scrollPosition
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. HobbyBoardPager（产品板块调度器）
// ─────────────────────────────────────────────────────────────────────────────

private class HobbyBoardPager(
    private val itemRepository: ItemRepository,
    private val viewPreferencesManager: ViewPreferencesManager,
    private val coroutineScope: CoroutineScope,
    private val onChanged: () -> Unit
) : BoardPager {

    override val board: ZoneBoard = ZoneBoard.Hobby
    override var isLoaded: Boolean = false
        private set

    private var filter: ZoneHobbyFilter = ZoneHobbyFilter()
    private var isGridMode: Boolean = true
    private var showFilterSheet: Boolean = false
    private var showCategoryPickerSheet: Boolean = false
    private var items: List<HobbyItemSummary> = emptyList()
    private var pageView: ZoneBoardPageView = ZoneBoardPageView()
    private var existingHobbies: List<HobbyItemSummary> = emptyList()
    private var currentScope: ZoneQueryScope = ZoneQueryScope.All

    private val loader = PagedListLoader<HobbyItemSummary>(
        scope = coroutineScope,
        initialPage = 1,
        pageSize = HobbyBoardOptions.PAGE_SIZE,
        keySelector = { it.actualItemId }
    ) { reqPage, reqPageSize ->
        itemRepository.queryHobbyBoard(
            scope = currentScope,
            page = reqPage,
            pageSize = reqPageSize,
            filters = filter
        ).map { list ->
            val byPage = PageChunk.of(list, reqPageSize)
            val hasMore = byPage.hasMore &&
                (reqPage == 1 || list.hasNewKeysAfterDedup(existingHobbies) { it.actualItemId })
            byPage.copy(hasMore = hasMore)
        }
    }

    init {
        coroutineScope.launch {
            viewPreferencesManager.isHobbyListGridModeFlow.collect { grid ->
                if (isGridMode != grid) {
                    isGridMode = grid
                    onChanged()
                }
            }
        }
        coroutineScope.launch {
            loader.state.collect { state ->
                items = state.items
                pageView = ZoneBoardPageView(
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    errorMessage = state.initialError,
                    loadMoreError = state.appendError,
                    isEndReached = !state.hasMore
                )
                onChanged()
            }
        }
    }

    fun toggleViewMode() {
        coroutineScope.launch {
            viewPreferencesManager.setHobbyListGridMode(!isGridMode)
        }
    }

    override fun ensureLoaded(scope: ZoneQueryScope) {
        if (isLoaded || pageView.isLoading) return
        loadInitial(scope)
    }

    override fun loadInitial(scope: ZoneQueryScope) {
        currentScope = scope
        isLoaded = true
        if (scope.isFollowEmpty) {
            loader.reset(clearItems = true)
            items = emptyList()
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        loader.loadInitial()
    }

    override fun loadMore(scope: ZoneQueryScope) {
        currentScope = scope
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        existingHobbies = loader.state.value.items
        loader.loadMore()
    }

    override fun refresh(scope: ZoneQueryScope) {
        currentScope = scope
        if (scope.isFollowEmpty) {
            loader.reset(clearItems = true)
            items = emptyList()
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        loader.refresh()
    }

    fun setOrder(order: String, scope: ZoneQueryScope) {
        if (order !in HobbyBoardOptions.ORDER_KEYS || order == filter.order) return
        filter = filter.copy(order = order)
        loadInitial(scope)
    }

    fun setYear(year: Int?, scope: ZoneQueryScope) {
        if (year == filter.releaseYear) return
        filter = filter.copy(releaseYear = year)
        loadInitial(scope)
    }

    fun setMonth(month: Int?, scope: ZoneQueryScope) {
        if (month == filter.releaseMonth) return
        filter = filter.copy(releaseMonth = month)
        loadInitial(scope)
    }

    fun setCategory(categoryId: Int, subCategoryId: Int?, scope: ZoneQueryScope) {
        if (categoryId == filter.categoryId && subCategoryId == filter.subCategoryId) return
        filter = filter.copy(categoryId = categoryId, subCategoryId = subCategoryId)
        loadInitial(scope)
    }

    fun applyAdvanced(filterParam: HobbyAdvancedFilterParam, scope: ZoneQueryScope) {
        filter = filterParam.applyTo(filter)
        loadInitial(scope)
    }

    fun resetAdvanced(scope: ZoneQueryScope) {
        filter = filter.copy(sex = null, specs = null, scale = null, r18 = null)
        loadInitial(scope)
    }

    fun setFilterSheetVisible(visible: Boolean) {
        if (showFilterSheet == visible) return
        showFilterSheet = visible
        onChanged()
    }

    fun setCategoryPickerVisible(visible: Boolean) {
        if (showCategoryPickerSheet == visible) return
        showCategoryPickerSheet = visible
        onChanged()
    }

    fun updateKeyword(keyword: String) {
        if (filter.keyword == keyword) return
        filter = filter.copy(keyword = keyword)
        onChanged()
    }

    fun submitSearch(scope: ZoneQueryScope) {
        loadInitial(scope)
    }

    override fun onScopeChanged(newScope: ZoneQueryScope) {
        currentScope = newScope
        loader.reset(clearItems = true)
        items = emptyList()
        isLoaded = false
        showFilterSheet = false
        showCategoryPickerSheet = false
        pageView = ZoneBoardPageView()
        onChanged()
    }

    override fun currentSnapshot(scrollPosition: BoardScrollPosition): ZoneBoardSnapshot {
        return ZoneBoardSnapshot(
            board = ZoneBoard.Hobby,
            content = ZoneBoardContent.Hobby(items = items, isGridMode = isGridMode, page = pageView),
            filters = ZoneBoardFilters(
                hobby = filter,
                showHobbyFilterSheet = showFilterSheet,
                showHobbyCategoryPickerSheet = showCategoryPickerSheet
            ),
            pageView = pageView,
            scrollPosition = scrollPosition
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. ResaleBoardPager（二手板块调度器）
// ─────────────────────────────────────────────────────────────────────────────

private class ResaleBoardPager(
    private val resaleRepository: ResaleRepository,
    private val coroutineScope: CoroutineScope,
    private val onChanged: () -> Unit
) : BoardPager {

    override val board: ZoneBoard = ZoneBoard.Resale
    override var isLoaded: Boolean = false
        private set

    private var type: Int = ResaleBoardOptions.DEFAULT_TYPE
    private var purity: String? = null
    private var keyword: String = ""
    private var items: List<ResaleSearchItem> = emptyList()
    private var pageView: ZoneBoardPageView = ZoneBoardPageView()
    private var existingItems: List<ResaleSearchItem> = emptyList()
    private var currentScope: ZoneQueryScope = ZoneQueryScope.All

    private val loader = KeyedListLoader<ResaleSearchItem, String?>(
        scope = coroutineScope,
        initialKey = null,
        keySelector = { it.id },
        nextKeyProvider = { _, state -> state.items.lastOrNull()?.addDate },
        fetcher = { cursor ->
            resaleRepository.searchResale(
                scope = currentScope,
                type = type,
                keyword = keyword.takeIf { it.isNotBlank() },
                puritys = purity,
                lastTime = cursor,
                pageSize = ResaleBoardOptions.PAGE_SIZE
            ).map { payload ->
                val list = payload.list
                val hasMore = list.size >= ResaleBoardOptions.PAGE_SIZE &&
                    !list.lastOrNull()?.addDate.isNullOrBlank() &&
                    (cursor == null || list.hasNewKeysAfterDedup(existingItems) { it.id })
                PageChunk(
                    items = list,
                    hasMore = hasMore
                )
            }
        }
    )

    init {
        coroutineScope.launch {
            loader.state.collect { state ->
                items = state.items
                pageView = ZoneBoardPageView(
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    errorMessage = state.initialError,
                    loadMoreError = state.appendError,
                    isEndReached = !state.hasMore
                )
                onChanged()
            }
        }
    }

    override fun ensureLoaded(scope: ZoneQueryScope) {
        if (isLoaded || pageView.isLoading) return
        loadInitial(scope)
    }

    override fun loadInitial(scope: ZoneQueryScope) {
        currentScope = scope
        isLoaded = true
        if (scope.isFollowEmpty) {
            loader.reset(clearItems = true)
            items = emptyList()
            existingItems = emptyList()
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        existingItems = emptyList()
        loader.loadInitial()
    }

    override fun loadMore(scope: ZoneQueryScope) {
        currentScope = scope
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        existingItems = loader.state.value.items
        loader.loadMore()
    }

    override fun refresh(scope: ZoneQueryScope) {
        currentScope = scope
        if (scope.isFollowEmpty) {
            loader.reset(clearItems = true)
            items = emptyList()
            existingItems = emptyList()
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        existingItems = emptyList()
        loader.refresh()
    }

    fun setType(newType: Int, scope: ZoneQueryScope) {
        if (!ResaleBoardOptions.isValidType(newType) || newType == type) return
        type = newType
        loadInitial(scope)
    }

    fun setPurity(newPurity: String?, scope: ZoneQueryScope) {
        if (!ResaleBoardOptions.isValidPurity(newPurity) || newPurity == purity) return
        purity = newPurity
        loadInitial(scope)
    }

    fun updateKeyword(newKeyword: String) {
        if (keyword == newKeyword) return
        keyword = newKeyword
        onChanged()
    }

    fun submitSearch(scope: ZoneQueryScope) {
        loadInitial(scope)
    }

    override fun onScopeChanged(newScope: ZoneQueryScope) {
        currentScope = newScope
        loader.reset(clearItems = true)
        items = emptyList()
        existingItems = emptyList()
        isLoaded = false
        pageView = ZoneBoardPageView()
        onChanged()
    }

    override fun currentSnapshot(scrollPosition: BoardScrollPosition): ZoneBoardSnapshot {
        return ZoneBoardSnapshot(
            board = ZoneBoard.Resale,
            content = ZoneBoardContent.Resale(items = items, page = pageView),
            filters = ZoneBoardFilters(
                resaleType = type,
                resalePurity = purity,
                resaleKeyword = keyword
            ),
            pageView = pageView,
            scrollPosition = scrollPosition
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. EntityBoardPager（实体 5 类板块调度器：厂商/系列/作品/角色/人物）
// ─────────────────────────────────────────────────────────────────────────────

private class EntityBoardPager(
    override val board: ZoneBoard,
    private val zoneRepository: ZoneRepository,
    private val coroutineScope: CoroutineScope,
    private val onChanged: () -> Unit
) : BoardPager {

    override var isLoaded: Boolean = false
        private set

    private var items: List<ZoneEntityItem> = emptyList()
    private var pageView: ZoneBoardPageView = ZoneBoardPageView()
    private var existingItems: List<ZoneEntityItem> = emptyList()
    private var currentScope: ZoneQueryScope = ZoneQueryScope.All

    private val loader = pagedChunkLoader<ZoneEntityItem>(
        scope = coroutineScope,
        initialPage = 1,
        pageSize = ENTITY_BOARD_PAGE_SIZE,
        keySelector = { it.targetId }
    ) { page, size ->
        zoneRepository.getZoneItems(
            scope = currentScope,
            itemType = board.type,
            page = page,
            pageSize = size
        ).map { payload ->
            val hasMore = payload.hasMore &&
                (page == 1 || payload.list.hasNewKeysAfterDedup(existingItems) { it.targetId })
            PageChunk(items = payload.list, hasMore = hasMore)
        }
    }

    init {
        coroutineScope.launch {
            loader.state.collect { state ->
                items = state.items
                pageView = ZoneBoardPageView(
                    isLoading = state.isLoading,
                    isLoadingMore = state.isLoadingMore,
                    errorMessage = state.initialError,
                    loadMoreError = state.appendError,
                    isEndReached = !state.hasMore
                )
                onChanged()
            }
        }
    }

    override fun ensureLoaded(scope: ZoneQueryScope) {
        if (isLoaded || pageView.isLoading) return
        loadInitial(scope)
    }

    override fun loadInitial(scope: ZoneQueryScope) {
        currentScope = scope
        isLoaded = true
        if (scope.isFollowEmpty) {
            loader.reset(clearItems = true)
            items = emptyList()
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        loader.loadInitial()
    }

    override fun loadMore(scope: ZoneQueryScope) {
        currentScope = scope
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        existingItems = loader.state.value.items
        loader.loadMore()
    }

    override fun refresh(scope: ZoneQueryScope) {
        currentScope = scope
        if (scope.isFollowEmpty) {
            loader.reset(clearItems = true)
            items = emptyList()
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        loader.refresh()
    }

    override fun onScopeChanged(newScope: ZoneQueryScope) {
        currentScope = newScope
        loader.reset(clearItems = true)
        items = emptyList()
        isLoaded = false
        pageView = ZoneBoardPageView()
        onChanged()
    }

    override fun currentSnapshot(scrollPosition: BoardScrollPosition): ZoneBoardSnapshot {
        return ZoneBoardSnapshot(
            board = board,
            content = ZoneBoardContent.Entity(items = items, page = pageView),
            filters = ZoneBoardFilters(),
            pageView = pageView,
            scrollPosition = scrollPosition
        )
    }
}
