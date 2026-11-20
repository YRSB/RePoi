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
import com.nobody.repoi.util.paging.hasNewKeysAfterDedup
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
    /** 首屏加载延迟门闩：见 [ZoneBoardSession.resumeInitialLoad]（issue #198）。 */
    deferInitialLoad: Boolean = false,
    homeRepository: HomeRepository,
    albumRepository: AlbumRepository,
    zoneRepository: ZoneRepository,
    itemRepository: ItemRepository,
    resaleRepository: ResaleRepository,
    viewPreferencesManager: ViewPreferencesManager
) : ZoneBoardSession {

    /** 门闩状态：解除前所有首屏懒加载 / 重试 / 刷新与筛选重载挂起。 */
    private var initialLoadDeferred = deferInitialLoad
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

    private val _actionSnapshot: MutableStateFlow<ZoneBoardSnapshot>
    override val actionSnapshot: StateFlow<ZoneBoardSnapshot>

    init {
        val initialScroll = scrollPositions[activeBoard] ?: BoardScrollPosition()
        _activeSnapshot = MutableStateFlow(pendingAwareSnapshot(pagerFor(activeBoard), initialScroll))
        activeSnapshot = _activeSnapshot.asStateFlow()

        val actionScroll = scrollPositions[ZoneBoard.Action] ?: BoardScrollPosition()
        _actionSnapshot = MutableStateFlow(pendingAwareSnapshot(actionPager, actionScroll))
        actionSnapshot = _actionSnapshot.asStateFlow()

        if (!initialLoadDeferred) {
            // 初始活跃板块触发首屏懒加载（门闩延迟场景由 resumeInitialLoad 统一触发）
            pagerFor(activeBoard).ensureLoaded(currentScope)
            if (activeBoard != ZoneBoard.Action) {
                actionPager.ensureLoaded(currentScope)
            }
        }
    }

    /** 门闩期间把快照投影为「首屏加载中」，避免宿主四态判定闪空态（issue #198）。 */
    private fun pendingAwareSnapshot(
        pager: BoardPager,
        scroll: BoardScrollPosition
    ): ZoneBoardSnapshot {
        val snapshot = pager.currentSnapshot(scroll)
        return if (initialLoadDeferred) snapshot.withPendingInitialLoad() else snapshot
    }

    override fun snapshotFor(board: ZoneBoard): ZoneBoardSnapshot {
        val pager = pagerFor(board)
        if (!initialLoadDeferred && !pager.isLoaded) {
            pager.ensureLoaded(currentScope)
        }
        val scroll = scrollPositions[board] ?: BoardScrollPosition()
        return pendingAwareSnapshot(pager, scroll)
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
                if (initialLoadDeferred) return
                val target = intent.board ?: activeBoard
                pagerFor(target).loadInitial(currentScope)
            }
            is ZoneBoardIntent.Refresh -> {
                if (initialLoadDeferred) return
                val target = intent.board ?: activeBoard
                pagerFor(target).refresh(currentScope)
            }

            // ── 筛选意图：Action ──
            is ZoneBoardIntent.SelectSubType -> {
                scrollPositions[ZoneBoard.Action] = BoardScrollPosition()
                if (initialLoadDeferred) {
                    actionPager.stageSubType(intent.subType)
                } else {
                    actionPager.setSubType(intent.subType, currentScope)
                }
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
        if (!initialLoadDeferred) {
            val activePager = pagerFor(activeBoard)
            activePager.ensureLoaded(scope)
            if (activeBoard != ZoneBoard.Action) {
                actionPager.ensureLoaded(scope)
            }
        }
        _actionSnapshot.value = actionPager.currentSnapshot(BoardScrollPosition())
            .let { if (initialLoadDeferred) it.withPendingInitialLoad() else it }
        _activeSnapshot.value = snapshotFor(activeBoard)
    }

    override fun resumeInitialLoad() {
        if (!initialLoadDeferred) return
        initialLoadDeferred = false
        val activePager = pagerFor(activeBoard)
        activePager.ensureLoaded(currentScope)
        if (activeBoard != ZoneBoard.Action) {
            actionPager.ensureLoaded(currentScope)
        }
        // ensureLoaded 同步置「首屏加载中」并回调 handleBoardChanged 刷新快照流；
        // 幂等重入（门闩已解除）直接短路。
    }

    private fun handleBoardChanged(board: ZoneBoard) {
        // 门闩期间的任何回调（如 stageSubType）必须维持「首屏加载中」投影，
        // 否则会把 pager 的「零条目 + 未加载」真实态推给宿主闪空态（issue #198）。
        if (board == ZoneBoard.Action) {
            val scroll = scrollPositions[ZoneBoard.Action] ?: BoardScrollPosition()
            _actionSnapshot.value = pendingAwareSnapshot(actionPager, scroll)
        }
        if (board == activeBoard) {
            val scroll = scrollPositions[board] ?: BoardScrollPosition()
            _activeSnapshot.value = pendingAwareSnapshot(pagerFor(board), scroll)
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
            initialBoard: ZoneBoard,
            deferInitialLoad: Boolean
        ): ZoneBoardSession {
            return ZoneBoardSessionImpl(
                coroutineScope = coroutineScope,
                initialScope = initialScope,
                initialBoard = initialBoard,
                deferInitialLoad = deferInitialLoad,
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

    /**
     * 首屏延迟门闩期间的筛选落位：只登记 [subType] 不触发请求。
     *
     * 防坑契约：冷启动落盘 subType 与默认值不同时，若走 [setSubType] 会在门闩解除前
     * 以「当前作用域 + 落盘子类」乐观发出一次首屏请求，落定切换作用域后作废重发（issue #198）；
     * 登记后由 [ZoneBoardSessionImpl.resumeInitialLoad] 用最终参数一次性加载。
     */
    fun stageSubType(newSubType: String) {
        if (newSubType == subType) return
        subType = newSubType
        onChanged()
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
    private var page: Int = 1
    private var hasMore: Boolean = true
    private var items: List<AlbumListItem> = emptyList()
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
            items = emptyList()
            page = 1
            hasMore = false
            pageView = ZoneBoardPageView(page = 1, isEndReached = true)
            onChanged()
            return
        }
        pageView = ZoneBoardPageView(page = 1, isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqOrder = order
            val reqCategory = category
            val result = albumRepository.getAlbums(
                page = 1,
                pageSize = AlbumBoardOptions.PAGE_SIZE,
                orderString = reqOrder,
                zones = scope.zonesParam(),
                category = reqCategory
            )
            result.onSuccess { payload ->
                if (order != reqOrder || category != reqCategory) return@onSuccess
                val distinctList = payload.items.distinctBy { it.targetAlbumId }
                items = distinctList
                page = 1
                hasMore = payload.hasMore
                pageView = ZoneBoardPageView(page = 1, isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || order != reqOrder || category != reqCategory) return@onFailure
                pageView = ZoneBoardPageView(page = 1, isLoading = false, errorMessage = error.message ?: "加载失败")
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
            val reqCategory = category
            val nextPage = page + 1
            val result = albumRepository.getAlbums(
                page = nextPage,
                pageSize = AlbumBoardOptions.PAGE_SIZE,
                orderString = reqOrder,
                zones = scope.zonesParam(),
                category = reqCategory
            )
            result.onSuccess { payload ->
                if (order != reqOrder || category != reqCategory) return@onSuccess
                val existingKeys = items.map { it.targetAlbumId }.toSet()
                val uniqueNewItems = payload.items.filterNot { it.targetAlbumId in existingKeys }
                val hasNewItems = payload.items.hasNewKeysAfterDedup(items) { it.targetAlbumId }
                val more = payload.hasMore && hasNewItems
                items = items + uniqueNewItems
                page = nextPage
                hasMore = more
                pageView = pageView.copy(page = nextPage, isLoadingMore = false, isEndReached = !more)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || order != reqOrder || category != reqCategory) {
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
            page = 1
            hasMore = false
            pageView = ZoneBoardPageView(page = 1, isEndReached = true)
            onChanged()
            return
        }
        pageView = pageView.copy(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqOrder = order
            val reqCategory = category
            val result = albumRepository.getAlbums(
                page = 1,
                pageSize = AlbumBoardOptions.PAGE_SIZE,
                orderString = reqOrder,
                zones = scope.zonesParam(),
                category = reqCategory
            )
            result.onSuccess { payload ->
                if (order != reqOrder || category != reqCategory) return@onSuccess
                val distinctList = payload.items.distinctBy { it.targetAlbumId }
                items = distinctList
                page = 1
                hasMore = payload.hasMore
                pageView = ZoneBoardPageView(page = 1, isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || order != reqOrder || category != reqCategory) return@onFailure
                pageView = pageView.copy(isLoading = false, errorMessage = error.message ?: "刷新失败")
                onChanged()
            }
        }
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
        job?.cancel()
        items = emptyList()
        page = 1
        hasMore = true
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
            page = 1
            hasMore = false
            pageView = ZoneBoardPageView(page = 1, isEndReached = true)
            onChanged()
            return
        }
        pageView = ZoneBoardPageView(page = 1, isLoading = true, errorMessage = null, loadMoreError = null)
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
                pageView = ZoneBoardPageView(page = 1, isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || order != reqOrder) return@onFailure
                pageView = ZoneBoardPageView(page = 1, isLoading = false, errorMessage = error.message ?: "加载失败")
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
                pageView = pageView.copy(page = nextPage, isLoadingMore = false, isEndReached = !more)
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
            page = 1
            hasMore = false
            pageView = ZoneBoardPageView(page = 1, isEndReached = true)
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
                pageView = ZoneBoardPageView(page = 1, isLoading = false, isEndReached = !hasMore)
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
    private var isGridMode: Boolean = viewPreferencesManager.isHobbyListGridMode
    private var showFilterSheet: Boolean = false
    private var showCategoryPickerSheet: Boolean = false
    private var page: Int = 1
    private var hasMore: Boolean = true
    private var items: List<HobbyItemSummary> = emptyList()
    private var pageView: ZoneBoardPageView = ZoneBoardPageView()
    private var job: Job? = null

    fun toggleViewMode() {
        isGridMode = !isGridMode
        onChanged()
        coroutineScope.launch {
            viewPreferencesManager.setHobbyListGridMode(isGridMode)
        }
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
            page = 1
            hasMore = false
            pageView = ZoneBoardPageView(page = 1, isEndReached = true)
            onChanged()
            return
        }
        pageView = ZoneBoardPageView(page = 1, isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqFilter = filter
            val result = itemRepository.queryHobbyBoard(
                scope = scope,
                page = 1,
                pageSize = HobbyBoardOptions.PAGE_SIZE,
                filters = reqFilter
            )
            result.onSuccess { list ->
                if (filter != reqFilter) return@onSuccess
                val distinctList = list.distinctBy { it.actualItemId }
                items = distinctList
                page = 1
                hasMore = list.size >= HobbyBoardOptions.PAGE_SIZE
                pageView = ZoneBoardPageView(page = 1, isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || filter != reqFilter) return@onFailure
                pageView = ZoneBoardPageView(page = 1, isLoading = false, errorMessage = error.message ?: "加载失败")
                onChanged()
            }
        }
    }

    override fun loadMore(scope: ZoneQueryScope) {
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        pageView = pageView.copy(isLoadingMore = true, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqFilter = filter
            val nextPage = page + 1
            val result = itemRepository.queryHobbyBoard(
                scope = scope,
                page = nextPage,
                pageSize = HobbyBoardOptions.PAGE_SIZE,
                filters = reqFilter
            )
            result.onSuccess { list ->
                if (filter != reqFilter) return@onSuccess
                val existingKeys = items.map { it.actualItemId }.toSet()
                val uniqueNewItems = list.filterNot { it.actualItemId in existingKeys }
                val hasNewItems = list.hasNewKeysAfterDedup(items) { it.actualItemId }
                val more = list.size >= HobbyBoardOptions.PAGE_SIZE && hasNewItems
                items = items + uniqueNewItems
                page = nextPage
                hasMore = more
                pageView = pageView.copy(page = nextPage, isLoadingMore = false, isEndReached = !more)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || filter != reqFilter) {
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
            page = 1
            hasMore = false
            pageView = ZoneBoardPageView(page = 1, isEndReached = true)
            onChanged()
            return
        }
        pageView = pageView.copy(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqFilter = filter
            val result = itemRepository.queryHobbyBoard(
                scope = scope,
                page = 1,
                pageSize = HobbyBoardOptions.PAGE_SIZE,
                filters = reqFilter
            )
            result.onSuccess { list ->
                if (filter != reqFilter) return@onSuccess
                val distinctList = list.distinctBy { it.actualItemId }
                items = distinctList
                page = 1
                hasMore = list.size >= HobbyBoardOptions.PAGE_SIZE
                pageView = ZoneBoardPageView(page = 1, isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || filter != reqFilter) return@onFailure
                pageView = pageView.copy(isLoading = false, errorMessage = error.message ?: "刷新失败")
                onChanged()
            }
        }
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
        job?.cancel()
        items = emptyList()
        page = 1
        hasMore = true
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
            items = emptyList()
            hasMore = false
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        pageView = ZoneBoardPageView(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqType = type
            val reqPurity = purity
            val reqKeyword = keyword
            val result = resaleRepository.searchResale(
                scope = scope,
                type = reqType,
                keyword = reqKeyword.takeIf { it.isNotBlank() },
                puritys = reqPurity,
                lastTime = null,
                pageSize = ResaleBoardOptions.PAGE_SIZE
            )
            result.onSuccess { payload ->
                if (type != reqType || purity != reqPurity || keyword != reqKeyword) return@onSuccess
                val list = payload.list
                val distinctList = list.distinctBy { it.id }
                val lastAddDate = distinctList.lastOrNull()?.addDate
                val more = distinctList.size >= ResaleBoardOptions.PAGE_SIZE && !lastAddDate.isNullOrBlank()
                items = distinctList
                hasMore = more
                pageView = ZoneBoardPageView(isLoading = false, isEndReached = !more)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || type != reqType || purity != reqPurity || keyword != reqKeyword) return@onFailure
                pageView = ZoneBoardPageView(isLoading = false, errorMessage = error.message ?: "加载失败")
                onChanged()
            }
        }
    }

    override fun loadMore(scope: ZoneQueryScope) {
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        val cursor = items.lastOrNull()?.addDate
        if (cursor.isNullOrBlank()) {
            pageView = pageView.copy(isEndReached = true)
            onChanged()
            return
        }
        pageView = pageView.copy(isLoadingMore = true, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqType = type
            val reqPurity = purity
            val reqKeyword = keyword
            val result = resaleRepository.searchResale(
                scope = scope,
                type = reqType,
                keyword = reqKeyword.takeIf { it.isNotBlank() },
                puritys = reqPurity,
                lastTime = cursor,
                pageSize = ResaleBoardOptions.PAGE_SIZE
            )
            result.onSuccess { payload ->
                if (type != reqType || purity != reqPurity || keyword != reqKeyword) return@onSuccess
                val list = payload.list
                val existingKeys = items.map { it.id }.toSet()
                val uniqueNewItems = list.filterNot { it.id in existingKeys }
                val hasNewItems = list.hasNewKeysAfterDedup(items) { it.id }
                val lastAddDate = list.lastOrNull()?.addDate
                val more = list.size >= ResaleBoardOptions.PAGE_SIZE && !lastAddDate.isNullOrBlank() && hasNewItems
                items = items + uniqueNewItems
                hasMore = more
                pageView = pageView.copy(isLoadingMore = false, isEndReached = !more)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || type != reqType || purity != reqPurity || keyword != reqKeyword) {
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
            hasMore = false
            pageView = ZoneBoardPageView(isEndReached = true)
            onChanged()
            return
        }
        pageView = pageView.copy(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val reqType = type
            val reqPurity = purity
            val reqKeyword = keyword
            val result = resaleRepository.searchResale(
                scope = scope,
                type = reqType,
                keyword = reqKeyword.takeIf { it.isNotBlank() },
                puritys = reqPurity,
                lastTime = null,
                pageSize = ResaleBoardOptions.PAGE_SIZE
            )
            result.onSuccess { payload ->
                if (type != reqType || purity != reqPurity || keyword != reqKeyword) return@onSuccess
                val list = payload.list
                val distinctList = list.distinctBy { it.id }
                val lastAddDate = distinctList.lastOrNull()?.addDate
                val more = distinctList.size >= ResaleBoardOptions.PAGE_SIZE && !lastAddDate.isNullOrBlank()
                items = distinctList
                hasMore = more
                pageView = ZoneBoardPageView(isLoading = false, isEndReached = !more)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException || type != reqType || purity != reqPurity || keyword != reqKeyword) return@onFailure
                pageView = pageView.copy(isLoading = false, errorMessage = error.message ?: "刷新失败")
                onChanged()
            }
        }
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
        job?.cancel()
        items = emptyList()
        hasMore = true
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

    private var page: Int = 1
    private var hasMore: Boolean = true
    private var items: List<ZoneEntityItem> = emptyList()
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
            items = emptyList()
            page = 1
            hasMore = false
            pageView = ZoneBoardPageView(page = 1, isEndReached = true)
            onChanged()
            return
        }
        pageView = ZoneBoardPageView(page = 1, isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val result = zoneRepository.getZoneItems(
                scope = scope,
                itemType = board.type,
                page = 1,
                pageSize = ENTITY_BOARD_PAGE_SIZE
            )
            result.onSuccess { payload ->
                val distinctList = payload.list.distinctBy { it.targetId }
                items = distinctList
                page = 1
                hasMore = payload.hasMore
                pageView = ZoneBoardPageView(page = 1, isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                pageView = ZoneBoardPageView(page = 1, isLoading = false, errorMessage = error.message ?: "加载失败")
                onChanged()
            }
        }
    }

    override fun loadMore(scope: ZoneQueryScope) {
        if (!isLoaded || pageView.isLoading || pageView.isLoadingMore || pageView.isEndReached || scope.isFollowEmpty) return
        pageView = pageView.copy(isLoadingMore = true, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val nextPage = page + 1
            val result = zoneRepository.getZoneItems(
                scope = scope,
                itemType = board.type,
                page = nextPage,
                pageSize = ENTITY_BOARD_PAGE_SIZE
            )
            result.onSuccess { payload ->
                val existingKeys = items.map { it.targetId }.toSet()
                val uniqueNewItems = payload.list.filterNot { it.targetId in existingKeys }
                val hasNewItems = payload.list.hasNewKeysAfterDedup(items) { it.targetId }
                val more = payload.hasMore && hasNewItems
                items = items + uniqueNewItems
                page = nextPage
                hasMore = more
                pageView = pageView.copy(page = nextPage, isLoadingMore = false, isEndReached = !more)
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
            page = 1
            hasMore = false
            pageView = ZoneBoardPageView(page = 1, isEndReached = true)
            onChanged()
            return
        }
        pageView = pageView.copy(isLoading = true, errorMessage = null, loadMoreError = null)
        onChanged()

        job = coroutineScope.launch {
            val result = zoneRepository.getZoneItems(
                scope = scope,
                itemType = board.type,
                page = 1,
                pageSize = ENTITY_BOARD_PAGE_SIZE
            )
            result.onSuccess { payload ->
                val distinctList = payload.list.distinctBy { it.targetId }
                items = distinctList
                page = 1
                hasMore = payload.hasMore
                pageView = ZoneBoardPageView(page = 1, isLoading = false, isEndReached = !hasMore)
                onChanged()
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                pageView = ZoneBoardPageView(page = 1, isLoading = false, errorMessage = error.message ?: "刷新失败")
                onChanged()
            }
        }
    }

    override fun onScopeChanged(newScope: ZoneQueryScope) {
        job?.cancel()
        items = emptyList()
        page = 1
        hasMore = true
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
