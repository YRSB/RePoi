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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class FeedCategory(
    val tabKey: String = "all",
    val name: String = "全部",
    val nodeId: Long = 0L,
    val zoneIds: String = "",
    val id: Int = nodeId.toInt()
)

private data class ColdStartInputs(
    val focusZones: List<ZoneItem>,
    val settings: ZoneTabsSettings,
    val focusZonesLoaded: Boolean,
    val savedSelectionReady: Boolean
)

data class HomeFeedUiState(

    val isColdStartReady: Boolean = false,

    val coldStartError: String? = null,
    val categories: List<FeedCategory> = listOf(FeedCategory("all", "全部")),
    val selectedCategory: FeedCategory = FeedCategory("all", "全部"),

    val switcherGroups: List<ZoneSwitcherGroup> = defaultZoneSwitcherGroups(),

    val boards: List<ZoneBoard> = ZoneBoardCapability.ALL_BOARDS,

    val boardTitles: Map<ZoneBoard, String> = emptyMap(),

    val selectedBoard: ZoneBoard = ZoneBoardCapability.ALL_BOARDS.firstOrNull() ?: ZoneBoard.Action,

    val albumCategoryOptions: List<AlbumCategoryOption> = emptyList(),
    val userAvatar: String? = null,
    val unreadMessageCount: Int = 0,
    val actionItems: List<ActionItem> = emptyList(),

    val currentBoardSnapshot: ZoneBoardSnapshot = ZoneBoardSnapshot(
        board = ZoneBoard.Action,
        content = ZoneBoardContent.Action(emptyList(), ZoneBoardPageView(isLoading = true)),
        filters = ZoneBoardFilters(),
        pageView = ZoneBoardPageView(isLoading = true)
    )
) {

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

    private val _isColdStartReady = MutableStateFlow(false)

    val isColdStartReady: StateFlow<Boolean> = _isColdStartReady.asStateFlow()

    private val _coldStartError = MutableStateFlow<String?>(null)

    val coldStartError: StateFlow<String?> = _coldStartError.asStateFlow()

    private var lastSyncedToken: String? = null

    private var coldStartJob: Job? = null

    val session: ZoneBoardSession = sessionFactory.create(
        coroutineScope = viewModelScope,
        initialScope = _uiState.value.zoneScope,
        initialBoard = _uiState.value.selectedBoard,

        deferInitialLoad = true
    )

    val subTypes: List<Pair<String, String>> =
        ActionSubType.ordered.map { it.apiValue to it.label }

    val hobbyCategoryTree: StateFlow<Map<Int, List<CategoryItem>>> =
        categoryManager?.categoryMap ?: MutableStateFlow<Map<Int, List<CategoryItem>>>(emptyMap())

    private var savedTabKey: String? = null
    private var savedBoardKey: String? = null

    private val savedSelectionReady = MutableStateFlow(false)

    private val focusZonesLoaded = MutableStateFlow(false)

    private var coldStartTabResolved: Boolean = false

    private var userSelectedCategory: Boolean = false

    private var currentBoardSelectionKey: String = ""

    private var pendingSingleZoneFirstBoard: Boolean = false

    private var boardsJob: Job? = null

    private var albumCategoriesJob: Job? = null

    private val boardScrollMemories = mutableMapOf<String, HomeBoardScrollMemory>()

    val currentBoardMemoryKey: String
        get() = homeBoardMemoryKey(_uiState.value.zoneScope, _uiState.value.selectedBoard)

    private fun boardFromKey(boardKey: String): ZoneBoard {
        val boardType = boardKey.substringAfterLast('|', "")
        return ZoneBoard.fromType(boardType) ?: _uiState.value.selectedBoard
    }

    fun scrollMemoryFor(boardKey: String): HomeBoardScrollMemory {
        return boardScrollMemories[boardKey] ?: session.snapshotFor(boardFromKey(boardKey)).scrollPosition.toHomeScrollMemory()
    }

    fun scrollMemoryOrNull(boardKey: String): HomeBoardScrollMemory? = boardScrollMemories[boardKey]

    fun saveBoardFilterRowScroll(boardKey: String, scrollX: Int) {
        if (scrollX < 0) return
        val board = boardFromKey(boardKey)
        val updated = (boardScrollMemories[boardKey] ?: HomeBoardScrollMemory()).copy(filterRowScrollX = scrollX)
        boardScrollMemories[boardKey] = updated
        session.dispatch(ZoneBoardIntent.SaveFilterRowScroll(board, scrollX))
    }

    fun saveBoardScrollPosition(boardKey: String, index: Int, offset: Int) {
        if (index < 0) return
        val board = boardFromKey(boardKey)
        val updated = (boardScrollMemories[boardKey] ?: HomeBoardScrollMemory()).copy(index = index, offset = offset)
        boardScrollMemories[boardKey] = updated
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(board, index, offset))
    }

    fun saveBoardFilterCollapseFraction(boardKey: String, fraction: Float) {
        val board = boardFromKey(boardKey)
        val clamped = fraction.coerceIn(0f, 1f)
        val updated = (boardScrollMemories[boardKey] ?: HomeBoardScrollMemory()).copy(filterCollapseFraction = clamped)
        boardScrollMemories[boardKey] = updated
        session.dispatch(ZoneBoardIntent.SaveFilterCollapseFraction(board, clamped))
    }

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

        viewModelScope.launch {
            session.activeSnapshot.collect { snapshot ->
                _uiState.update { it.copy(currentBoardSnapshot = snapshot) }
            }
        }

        viewModelScope.launch {
            session.actionSnapshot.collect { snapshot ->
                val items = (snapshot.content as? ZoneBoardContent.Action)?.items.orEmpty()
                _uiState.update { it.copy(actionItems = items) }
            }
        }

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
                    if (categoryChanged && scope is ZoneQueryScope.Single) {
                        val job = syncBoards(scope)
                        job?.join()
                    } else if (categoryChanged) {
                        syncBoards(scope)
                    }
                    markColdStartReady()
                } else if (categoryChanged) {
                    syncBoards(scope)
                }
            }
        }

        setupAccountState()
        initColdStart()
    }

    fun retryColdStart() {
        initColdStart()
    }

    private fun markColdStartReady() {
        _coldStartError.value = null
        _isColdStartReady.value = true
        _uiState.update { it.copy(isColdStartReady = true, coldStartError = null) }
        session.resumeInitialLoad()
    }

    private fun initColdStart() {
        coldStartJob?.cancel()
        coldStartJob = viewModelScope.launch {
            _coldStartError.value = null
            _uiState.update { it.copy(coldStartError = null) }

            val initialSessionState = userSession.state.first { it !is SessionState.Prewarming }
            val currentToken = (initialSessionState as? SessionState.Authenticated)?.token
            lastSyncedToken = currentToken

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

            val result = runCatching {
                withTimeout(5000L) {
                    zoneRepository.getZoneHomeTabs()
                }
            }

            val isSuccess = result.isSuccess && result.getOrNull()?.isSuccess == true
            if (isSuccess) {
                focusZonesLoaded.value = true
            } else {
                val errorMsg = result.exceptionOrNull()?.message
                    ?: result.getOrNull()?.exceptionOrNull()?.message
                    ?: "网络连接异常，无法加载专区"
                _coldStartError.value = errorMsg
                _uiState.update { it.copy(coldStartError = errorMsg) }
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

        markColdStartReady()
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

        markColdStartReady()
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

    fun selectAlbumOrder(order: String) {
        if (order !in AlbumBoardOptions.ORDER_KEYS) return
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectAlbumOrder(order))
    }

    fun selectAlbumCategory(categoryId: Int?) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectAlbumCategory(categoryId))
    }

    fun selectPostsOrder(order: String) {
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectPostsOrder(order))
    }

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

    fun selectSubType(subType: String) {
        val normalized = ActionSubType.fromPersisted(subType).apiValue
        resetScrollPosition()
        session.dispatch(ZoneBoardIntent.SelectSubType(normalized))
        viewModelScope.launch {
            viewPreferencesManager.setFeedSubType(normalized)
        }
    }

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
        viewModelScope.launch {
            zoneRepository.getZoneHomeTabs()
        }
        session.dispatch(ZoneBoardIntent.Refresh())
    }

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
        markColdStartReady()
        syncBoards(scope)
    }

    private suspend fun persistBoardSelection() {
        currentBoardSelectionKey
            .takeIf { it.isNotBlank() }
            ?.let { viewPreferencesManager.setFeedZoneBoard(it) }
    }

    private fun setupAccountState() {
        viewModelScope.launch {
            var profileRefreshRequested = false
            var initialTokenConsumed = false
            userSession.state.collect { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        val tokenChanged = state.token != lastSyncedToken
                        if (tokenChanged) {
                            val isInitial = !initialTokenConsumed
                            initialTokenConsumed = true
                            lastSyncedToken = state.token
                            profileRefreshRequested = false
                            if (!isInitial) {
                                launch { zoneRepository.getZoneHomeTabs() }
                            }
                            fetchUnreadMessages()
                        }
                        _uiState.update { it.copy(userAvatar = state.profile?.header) }
                        if (state.profile == null && !profileRefreshRequested) {
                            profileRefreshRequested = true
                            launch { userSession.refreshProfile() }
                        }
                    }
                    SessionState.Unauthenticated -> {
                        initialTokenConsumed = true
                        lastSyncedToken = null
                        profileRefreshRequested = false
                        _uiState.update { it.copy(userAvatar = null, unreadMessageCount = 0) }
                    }
                    SessionState.Prewarming -> {
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

    private fun syncBoards(scope: ZoneQueryScope): Job? {
        boardsJob?.cancel()
        if (scope !is ZoneQueryScope.Single) return null
        val job = viewModelScope.launch {
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
        boardsJob = job
        return job
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
