package com.nobody.repoi.ui.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.domain.usecase.collect.BatchCancelCollectUseCase
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.util.paging.LoadStage
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.PagedListState
import com.nobody.repoi.util.paging.pagedListLoader
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserCollectViewModel(
    private val collectRepository: CollectRepository,
    private val batchCancelCollectUseCase: BatchCancelCollectUseCase,
    private val viewPreferencesManager: ViewPreferencesManager,
    private val userSession: UserSession,
    navUserId: Long = 0L,
    navInitialCollectState: String? = null,
    navInitialOrder: String? = null,
    navInitialCategory: Int? = null,
) : ViewModel() {

    private val savedState: MutableMap<String, Any?> = mutableMapOf<String, Any?>().also { map ->
        map["userId"] = navUserId
        navInitialCollectState?.let { map["initialCollectState"] = it }
        navInitialOrder?.let { map["initialOrder"] = it }
        navInitialCategory?.let { map["initialCategory"] = it }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stateGet(key: String): T? = savedState[key] as? T

    private fun stateSet(key: String, value: Any?) {
        if (value == null) savedState.remove(key) else savedState[key] = value
    }

    var userId: Long = (stateGet<Long>("userId")
        ?: (stateGet<String>("userId"))?.toLongOrNull()
        ?: 0L)
        private set

    private val initialCollectState: String = stateGet<String>("initialCollectState")
        ?: stateGet<String>("collectState")
        ?: CollectStateOption.DEFAULT

    private val initialOrder: String = stateGet<String>("initialOrder")
        ?: stateGet<String>("order")
        ?: HobbyOrderOption.DEFAULT

    private val hasExplicitInitialOrder: Boolean =
        savedState.containsKey("initialOrder") || savedState.containsKey("order")

    private val initialCategoryKey: Int = stateGet<Int>("initialCategory") ?: DEFAULT_HOBBY_KEY

    private val _uiState = MutableStateFlow(
        UserCollectUiState(
            selectedCategory = UserCollectCategory.entries.find { it.key == initialCategoryKey } ?: UserCollectCategory.HOBBY,
            collectState = initialCollectState,
            order = HobbyOrderOption.effective(initialCollectState, initialOrder),
            isListView = !viewPreferencesManager.isCollectListGridMode,
            isSelf = resolveIsSelf(userId),
            albumType = resolveAlbumType(stateGet<String>("albumType")),
            hobbyData = SubPageData(isLoading = true),
            albumData = SubPageData(isLoading = true),
            picData = SubPageData(isLoading = true),
            vendorData = SubPageData(isLoading = true),
            seriesData = SubPageData(isLoading = true),
            worksData = SubPageData(isLoading = true),
            characterData = SubPageData(isLoading = true),
            personData = SubPageData(isLoading = true)
        )
    )
    val uiState: StateFlow<UserCollectUiState> = _uiState.asStateFlow()

    private var appliedNavParam: UserCollectNavParam? = null
    private var currentParam: UserCollectNavParam? = null

    private val hobbyOrderMemory: MutableMap<String, String> = readStringMapMemory(KEY_HOBBY_ORDER_MEMORY)

    private val hobbySortTypeMemory: MutableMap<String, Int> =
        readStringMapMemory(KEY_HOBBY_SORT_TYPE_MEMORY).mapValues { it.value.toIntOrNull() ?: UserCollectUiState.SORT_DESC }.toMutableMap()

    private val persistedSortReady: Deferred<Unit> = viewModelScope.async {
        val state = initialCollectState
        val persisted = runCatching { viewPreferencesManager.getCollectHobbyOrder(state) }.getOrNull()
        if (hasExplicitInitialOrder) {
            hobbyOrderMemory[state] = HobbyOrderOption.effective(state, initialOrder)
            hobbySortTypeMemory.putIfAbsent(state, UserCollectUiState.SORT_DESC)
        } else {
            val restoredOrder = HobbyOrderOption.effective(state, persisted?.first ?: HobbyOrderOption.DEFAULT)
            val restoredSortType = persisted?.second ?: UserCollectUiState.SORT_DESC
            hobbyOrderMemory.putIfAbsent(state, restoredOrder)
            hobbySortTypeMemory.putIfAbsent(state, restoredSortType)

            if (restoredOrder != _uiState.value.order || restoredSortType != _uiState.value.sortType) {
                _uiState.update { it.copy(order = restoredOrder, sortType = restoredSortType) }
            }
        }
        persistHobbySortMemory()
    }

    private var consumedMutationGeneration: Long = 0L

    private val consumedGenerationReady: Deferred<Long> = viewModelScope.async {
        val restored = viewPreferencesManager.getConsumedCollectMutationGeneration()
        val clamped = restored.coerceAtMost(collectRepository.collectMutationGeneration.value)
        consumedMutationGeneration = clamped
        clamped
    }

    private var resumeAwaitingCursor = false

    private val dirtyCategories: MutableSet<UserCollectCategory> = mutableSetOf()

    private val hobbyLoader: PagedListLoader<UserCollectHobbyItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,

        keySelector = { item: UserCollectHobbyItem -> item.id to item.actualItemId }
    ) { page, size ->
        val state = _uiState.value
        collectRepository.getUserCollectHobbyList(
            userId = userId,
            collectState = state.collectState,
            category = state.subCategoryId ?: DEFAULT_HOBBY_KEY,
            tag = state.selectedTag,
            order = state.order,
            sortType = state.sortType,
            page = page,
            pageSize = size
        )
    }

    private val albumLoader: PagedListLoader<UserCollectAlbumItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,
        keySelector = { item: UserCollectAlbumItem -> item.id }
    ) { page, size ->
        fetchAlbumPage(page, size)
    }

    private val picLoader: PagedListLoader<UserCollectPicItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,
        keySelector = { item: UserCollectPicItem -> item.id }
    ) { page, size ->
        val state = _uiState.value
        collectRepository.getUserCollectPicList(
            userId = userId,
            order = state.picOrder,
            page = page,
            pageSize = size
        )
    }

    private val vendorLoader: PagedListLoader<UserCollectCommonItem> = commonCollectLoader(UserCollectCategory.VENDOR)
    private val seriesLoader: PagedListLoader<UserCollectCommonItem> = commonCollectLoader(UserCollectCategory.SERIES)
    private val characterLoader: PagedListLoader<UserCollectCommonItem> = commonCollectLoader(UserCollectCategory.CHARACTER)
    private val personLoader: PagedListLoader<UserCollectCommonItem> = commonCollectLoader(UserCollectCategory.PERSON)

    private val worksLoader: PagedListLoader<UserCollectCommonItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,
        keySelector = { item: UserCollectCommonItem -> item.id }
    ) { page, size ->
        val state = _uiState.value
        fetchWorksPage(
            page = page,
            pageSize = size,
            worksState = state.worksState,
            isAsc = state.worksOrderAsc,
            tagValue = state.worksTag
        )
    }

    private fun commonCollectLoader(category: UserCollectCategory): PagedListLoader<UserCollectCommonItem> =
        pagedListLoader(
            scope = viewModelScope,
            pageSize = PAGE_SIZE,
            keySelector = { item: UserCollectCommonItem -> item.id to item.actualItemId }
        ) { page, size ->
            val state = _uiState.value
            fetchCommonPage(
                category = category,
                page = page,
                pageSize = size,
                order = state.commonOrder
            )
        }

    private val loaders: Map<UserCollectCategory, PagedListLoader<*>> = mapOf(
        UserCollectCategory.HOBBY to hobbyLoader,
        UserCollectCategory.ALBUM to albumLoader,
        UserCollectCategory.PICTURE to picLoader,
        UserCollectCategory.VENDOR to vendorLoader,
        UserCollectCategory.SERIES to seriesLoader,
        UserCollectCategory.WORKS to worksLoader,
        UserCollectCategory.CHARACTER to characterLoader,
        UserCollectCategory.PERSON to personLoader
    )

    init {

        viewModelScope.launch {
            viewPreferencesManager.isCollectListGridModeFlow.collect { isGrid ->
                _uiState.update { it.copy(isListView = !isGrid) }
            }
        }

        loaders.forEach { (category, loader) ->
            viewModelScope.launch {
                loader.state.collect { syncCategory(category) }
            }
        }

        if (userId > 0L) {
            val initialCat = UserCollectCategory.entries.find { it.key == initialCategoryKey } ?: UserCollectCategory.HOBBY
            initParam(
                UserCollectNavParam(
                    userId = userId,
                    initialCollectState = initialCollectState,

                    initialOrder = initialOrder.takeIf { hasExplicitInitialOrder },
                    initialCategory = initialCat.key
                )
            )
        }
    }

    fun onIntent(intent: UserCollectIntent) {
        when (intent) {
            is UserCollectIntent.SwitchCategory -> applyCategorySwitch(intent.category)
            is UserCollectIntent.EnsureLoaded -> applyEnsureLoaded(intent.category)
            UserCollectIntent.ToggleViewMode -> applyToggleViewMode()
            is UserCollectIntent.SwitchCollectState -> applyCollectState(intent.state)
            is UserCollectIntent.SwitchOrder -> applyHobbyOrder(intent.order)
            is UserCollectIntent.SwitchSortDirection -> applyHobbySortType(intent.sortType)
            is UserCollectIntent.SelectSubCategory -> applySubCategory(intent.id)
            is UserCollectIntent.SelectTag -> applyTag(intent.tag)
            UserCollectIntent.ResetFilter -> resetFilterInternal()
            is UserCollectIntent.SetFilterSheetVisible -> applyFilterSheetVisible(intent.visible)
            is UserCollectIntent.LoadHobbyTags -> loadHobbyTags(intent.force)
            is UserCollectIntent.SwitchAlbumOrder -> applyAlbumOrder(intent.order)
            is UserCollectIntent.SwitchPicOrder -> applyPicOrder(intent.order)
            is UserCollectIntent.SwitchCommonOrder -> applyCommonOrder(intent.order)
            is UserCollectIntent.SwitchWorksState -> applyWorksState(intent.state)
            is UserCollectIntent.SwitchWorksOrderAsc -> applyWorksOrderAsc(intent.asc)
            is UserCollectIntent.SelectWorksTag -> applyWorksTag(intent.tag)
            UserCollectIntent.ResetWorksTag -> resetWorksTagInternal()
            is UserCollectIntent.SetWorksFilterSheetVisible -> applyWorksFilterSheetVisible(intent.visible)
            is UserCollectIntent.LoadWorksTags -> loadWorksTags(intent.force)
            is UserCollectIntent.LoadFirstPage -> loadFirstPage(intent.category)
            is UserCollectIntent.Refresh -> refresh(intent.category)
            is UserCollectIntent.LoadNextPage -> loadNextPage(intent.category)
            is UserCollectIntent.DismissError -> dismissError(intent.category)
            is UserCollectIntent.ToggleSelectionMode -> toggleSelectionMode(intent.category)
            is UserCollectIntent.ToggleSelect -> applyToggleSelect(intent.id)
            is UserCollectIntent.SelectAll -> selectAll(intent.category)
            is UserCollectIntent.ClearSelection -> clearSelection(intent.category)
            UserCollectIntent.ConfirmBatchCancel -> confirmBatchCancel()
            UserCollectIntent.DismissBatchCancelError -> applyDismissBatchCancelError()
            UserCollectIntent.OnResume -> handleResume()
        }
    }

    fun initParam(param: UserCollectNavParam) {

        if (appliedNavParam == param) return
        appliedNavParam = param

        reloadAlbumIfTypeChanged(resolveAlbumType(param.albumType))

        viewModelScope.launch {
            persistedSortReady.await()
            consumedGenerationReady.await()
            applyInitParam(param)
        }
    }

    private fun applyInitParam(param: UserCollectNavParam) {
        val targetCategory = UserCollectCategory.entries.find { it.key == param.initialCategory } ?: UserCollectCategory.HOBBY
        val targetState = resolveCollectState(param.initialCollectState)
        val targetWorksState = if (targetCategory == UserCollectCategory.WORKS) {
            resolveWorksState(param.initialCollectState)
        } else {
            _uiState.value.worksState
        }
        val targetOrder = resolveOrderFor(targetCategory, param.initialOrder, targetState)

        val targetSubCategory = HobbySubCategoryOption.normalize(param.initialSubCategoryId)
        val targetTag = param.initialTag?.takeUnless { it.isBlank() }
        val normalizedParam = param.copy(
            initialCollectState = if (targetCategory == UserCollectCategory.WORKS) targetWorksState.toString() else targetState,
            initialOrder = targetOrder,
            initialCategory = targetCategory.key,
            initialSubCategoryId = targetSubCategory,
            initialTag = targetTag
        )
        if (currentParam == normalizedParam && userId == param.userId) return

        val generation = collectRepository.collectMutationGeneration.value
        consumedMutationGeneration = generation
        viewModelScope.launch {
            viewPreferencesManager.setConsumedCollectMutationGeneration(generation)
        }
        currentParam = normalizedParam
        userId = param.userId
        stateSet("userId", param.userId)
        stateSet("initialCollectState", if (targetCategory == UserCollectCategory.WORKS) targetWorksState.toString() else targetState)
        stateSet("initialOrder", targetOrder)
        stateSet("initialCategory", targetCategory.key)

        stateSet("albumType", resolveAlbumType(param.albumType))
        if (targetCategory == UserCollectCategory.WORKS) {
            stateSet("worksState", targetWorksState)
        }

        if (targetCategory == UserCollectCategory.HOBBY && param.initialOrder != null) {
            hobbyOrderMemory[targetState] = targetOrder
            persistHobbySortMemory()
        }
        val restoredSortType = hobbySortTypeMemory[targetState] ?: UserCollectUiState.SORT_DESC

        _uiState.update {
            it.copy(
                selectedCategory = targetCategory,
                isInitialized = true,
                isSelf = resolveIsSelf(param.userId),
                collectState = targetState,
                worksState = targetWorksState,
                order = if (targetCategory == UserCollectCategory.HOBBY) targetOrder else it.order,
                sortType = if (targetCategory == UserCollectCategory.HOBBY) restoredSortType else it.sortType,
                subCategoryId = targetSubCategory,
                selectedTag = targetTag,
                albumType = resolveAlbumType(param.albumType),
                albumOrder = if (targetCategory == UserCollectCategory.ALBUM) targetOrder else it.albumOrder,
                picOrder = if (targetCategory == UserCollectCategory.PICTURE) targetOrder else it.picOrder,
                commonOrder = if (targetCategory in COMMON_CATEGORIES) targetOrder else it.commonOrder
            )
        }

        resetAllLoaders()
        loadFirstPage(targetCategory)
    }

    private fun reloadAlbumIfTypeChanged(targetType: String) {
        if (_uiState.value.albumType == targetType) return
        stateSet("albumType", targetType)
        _uiState.update { it.copy(albumType = targetType) }
        resetLoader(UserCollectCategory.ALBUM)
        if (_uiState.value.selectedCategory == UserCollectCategory.ALBUM) {
            loadFirstPage(UserCollectCategory.ALBUM)
        }
    }

    private fun resolveAlbumType(raw: String?): String =
        if (raw != null && AlbumTypeOption.isValid(raw)) raw else AlbumTypeOption.DEFAULT

    private fun resolveWorksState(raw: String?): Int {
        if (raw.isNullOrBlank()) return WorksStateOption.WANT
        return when (raw.trim().lowercase()) {
            "1", "want", "care", "想看", "想玩" -> WorksStateOption.WANT
            "2", "watching", "do", "在看", "在玩" -> WorksStateOption.WATCHING
            "3", "watched", "collect", "看过", "玩过" -> WorksStateOption.WATCHED
            else -> raw.trim().toIntOrNull()?.takeIf { WorksStateOption.isValid(it) } ?: WorksStateOption.WANT
        }
    }

    private fun resolveCollectState(raw: String?): String {
        if (raw == null) return CollectStateOption.DEFAULT
        if (raw.isEmpty()) return CollectStateOption.ALL
        return if (CollectStateOption.isValid(raw)) raw else CollectStateOption.DEFAULT
    }

    private fun resolveOrderFor(category: UserCollectCategory, raw: String?, targetState: String): String {
        if (raw == null) return defaultOrderFor(category, targetState)
        return when (category) {
            UserCollectCategory.HOBBY -> HobbyOrderOption.effective(
                targetState,
                if (HobbyOrderOption.isValid(raw, targetState)) raw else HobbyOrderOption.DEFAULT
            )
            UserCollectCategory.ALBUM, UserCollectCategory.PICTURE ->
                if (MediaOrderOption.isValid(raw)) raw else MediaOrderOption.DEFAULT

            UserCollectCategory.WORKS -> CommonOrderOption.ACTION_DATE
            else -> if (CommonOrderOption.isValid(raw)) raw else CommonOrderOption.DEFAULT
        }
    }

    private fun defaultOrderFor(category: UserCollectCategory, targetState: String): String = when (category) {
        UserCollectCategory.HOBBY -> HobbyOrderOption.effective(
            targetState,
            hobbyOrderMemory[targetState] ?: HobbyOrderOption.DEFAULT
        )
        UserCollectCategory.ALBUM, UserCollectCategory.PICTURE -> MediaOrderOption.DEFAULT
        UserCollectCategory.WORKS -> CommonOrderOption.ACTION_DATE
        else -> CommonOrderOption.DEFAULT
    }

    private fun applyCategorySwitch(category: UserCollectCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        ensureCategoryData(category)
    }

    private fun applyEnsureLoaded(category: UserCollectCategory) {
        if (!_uiState.value.isInitialized) return
        ensureCategoryData(category)
    }

    private fun ensureCategoryData(category: UserCollectCategory) {
        if (dirtyCategories.remove(category)) {
            dispatchToLoader(category) { it.refresh() }
            return
        }
        if (isLoaderNeverLoaded(category)) {
            loadFirstPage(category)
        }
    }

    private fun applyToggleViewMode() {
        val newIsList = !_uiState.value.isListView
        _uiState.update { it.copy(isListView = newIsList) }
        viewModelScope.launch {
            viewPreferencesManager.setCollectListGridMode(!newIsList)
        }
    }

    private fun applyCollectState(newState: String) {
        if (!CollectStateOption.isValid(newState)) return
        if (_uiState.value.collectState == newState) return
        stateSet("collectState", newState)

        val restoredOrder = HobbyOrderOption.effective(newState, hobbyOrderMemory[newState] ?: HobbyOrderOption.DEFAULT)
        val restoredSortType = hobbySortTypeMemory[newState] ?: UserCollectUiState.SORT_DESC
        _uiState.update {
            it.copy(
                collectState = newState,
                order = restoredOrder,
                sortType = restoredSortType
            )
        }
        resetLoader(UserCollectCategory.HOBBY)
        loadFirstPage(UserCollectCategory.HOBBY)
        refreshTagPoolIfSheetOpen()
        persistHobbySortToPrefs()
    }

    private fun applyHobbyOrder(newOrder: String) {
        if (!HobbyOrderOption.isValid(newOrder, _uiState.value.collectState)) return
        val effectiveOrder = HobbyOrderOption.effective(_uiState.value.collectState, newOrder)
        if (_uiState.value.order == effectiveOrder) return
        stateSet("order", effectiveOrder)
        hobbyOrderMemory[_uiState.value.collectState] = effectiveOrder
        persistHobbySortMemory()
        _uiState.update { it.copy(order = effectiveOrder) }
        resetLoader(UserCollectCategory.HOBBY)
        loadFirstPage(UserCollectCategory.HOBBY)
        persistHobbySortToPrefs()
    }

    private fun applyHobbySortType(newSortType: Int) {
        if (newSortType != UserCollectUiState.SORT_ASC && newSortType != UserCollectUiState.SORT_DESC) return
        if (_uiState.value.sortType == newSortType) return
        stateSet("sortType", newSortType)
        hobbySortTypeMemory[_uiState.value.collectState] = newSortType
        persistHobbySortMemory()
        _uiState.update { it.copy(sortType = newSortType) }
        resetLoader(UserCollectCategory.HOBBY)
        loadFirstPage(UserCollectCategory.HOBBY)
        persistHobbySortToPrefs()
    }

    private fun applySubCategory(subCategoryId: Int?) {

        val normalized = HobbySubCategoryOption.normalize(subCategoryId)
        if (_uiState.value.subCategoryId == normalized) return
        _uiState.update { it.copy(subCategoryId = normalized) }
        resetLoader(UserCollectCategory.HOBBY)
        loadFirstPage(UserCollectCategory.HOBBY)
        refreshTagPoolIfSheetOpen()
    }

    private fun applyTag(tag: String?) {

        val requested = tag?.takeUnless { it.isBlank() }
        val newTag = if (_uiState.value.selectedTag == requested) null else requested
        _uiState.update { it.copy(selectedTag = newTag) }
        resetLoader(UserCollectCategory.HOBBY)
        loadFirstPage(UserCollectCategory.HOBBY)
    }

    private fun resetFilterInternal() {

        if (_uiState.value.subCategoryId == null && _uiState.value.selectedTag == null) return
        _uiState.update {
            it.copy(
                subCategoryId = null,
                selectedTag = null
            )
        }
        resetLoader(UserCollectCategory.HOBBY)
        loadFirstPage(UserCollectCategory.HOBBY)
    }

    private fun applyFilterSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showFilterSheet = visible) }
        if (visible) {
            loadHobbyTags(force = true)
        }
    }

    private fun loadHobbyTags(force: Boolean) {
        if (_uiState.value.isTagPoolLoading) return
        if (!force && _uiState.value.availableTags.isNotEmpty()) return
        val categoryId = _uiState.value.subCategoryId ?: DEFAULT_HOBBY_KEY
        _uiState.update { it.copy(isTagPoolLoading = true) }
        viewModelScope.launch {
            collectRepository.getCollectTags(page = 1, pageSize = TAG_POOL_SIZE, categoryId = categoryId)
                .onSuccess { tags ->
                    _uiState.update { it.copy(availableTags = tags, isTagPoolLoading = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isTagPoolLoading = false) }
                }
        }
    }

    private fun refreshTagPoolIfSheetOpen() {
        if (_uiState.value.showFilterSheet) {
            loadHobbyTags(force = true)
        }
    }

    private fun applyCommonOrder(newOrder: String) {
        if (!CommonOrderOption.isValid(newOrder)) return
        if (_uiState.value.commonOrder == newOrder) return
        val current = _uiState.value.selectedCategory
        _uiState.update { it.copy(commonOrder = newOrder) }
        COMMON_CATEGORIES.forEach { category ->
            if (category != current) resetLoader(category)
        }
        if (current in COMMON_CATEGORIES) {
            resetLoader(current)
            loadFirstPage(current)
        }
    }

    private fun applyAlbumOrder(newOrder: String) {
        if (!MediaOrderOption.isValid(newOrder)) return
        if (_uiState.value.albumOrder == newOrder) return
        _uiState.update { it.copy(albumOrder = newOrder) }
        resetLoader(UserCollectCategory.ALBUM)
        if (_uiState.value.selectedCategory == UserCollectCategory.ALBUM) {
            loadFirstPage(UserCollectCategory.ALBUM)
        }
    }

    private suspend fun fetchAlbumPage(page: Int, pageSize: Int): Result<List<UserCollectAlbumItem>> {
        val state = _uiState.value
        return when (state.albumType) {
            AlbumTypeOption.UPLOAD ->
                collectRepository.getUserCreationAlbumList(
                    userId = userId,
                    page = page,
                    pageSize = pageSize,
                    order = state.albumOrder
                ).map { it.items }
            else ->
                collectRepository.getUserCollectAlbumList(
                    userId = userId,
                    type = state.albumType,
                    order = state.albumOrder,
                    page = page,
                    pageSize = pageSize
                )
        }
    }

    private fun applyPicOrder(newOrder: String) {
        if (!MediaOrderOption.isValid(newOrder)) return
        if (_uiState.value.picOrder == newOrder) return
        _uiState.update { it.copy(picOrder = newOrder) }
        resetLoader(UserCollectCategory.PICTURE)
        if (_uiState.value.selectedCategory == UserCollectCategory.PICTURE) {
            loadFirstPage(UserCollectCategory.PICTURE)
        }
    }

    private fun applyWorksState(newState: Int) {
        if (!WorksStateOption.isValid(newState)) return
        if (_uiState.value.worksState == newState) return
        stateSet("worksState", newState)
        _uiState.update { it.copy(worksState = newState) }
        resetLoader(UserCollectCategory.WORKS)
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadFirstPage(UserCollectCategory.WORKS)
        }
    }

    private fun applyWorksOrderAsc(isAsc: Boolean) {
        if (_uiState.value.worksOrderAsc == isAsc) return
        stateSet("worksOrderAsc", isAsc)
        _uiState.update { it.copy(worksOrderAsc = isAsc) }
        resetLoader(UserCollectCategory.WORKS)
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadFirstPage(UserCollectCategory.WORKS)
        }
    }

    private fun applyWorksTag(tag: String?) {
        val newTag = if (_uiState.value.worksTag == tag) null else tag
        _uiState.update { it.copy(worksTag = newTag) }
        resetLoader(UserCollectCategory.WORKS)
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadFirstPage(UserCollectCategory.WORKS)
        }
    }

    private fun resetWorksTagInternal() {
        if (_uiState.value.worksTag == null) return
        _uiState.update { it.copy(worksTag = null) }
        resetLoader(UserCollectCategory.WORKS)
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadFirstPage(UserCollectCategory.WORKS)
        }
    }

    private fun applyWorksFilterSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showWorksFilterSheet = visible) }
        if (visible) {
            loadWorksTags(force = true)
        }
    }

    private fun loadWorksTags(force: Boolean) {
        if (_uiState.value.isWorksTagPoolLoading) return
        if (!force && _uiState.value.worksAvailableTags.isNotEmpty()) return
        _uiState.update { it.copy(isWorksTagPoolLoading = true) }
        viewModelScope.launch {
            collectRepository.getWorksCollectTags(page = 1, pageSize = TAG_POOL_SIZE)
                .onSuccess { tags ->
                    _uiState.update { it.copy(worksAvailableTags = tags, isWorksTagPoolLoading = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isWorksTagPoolLoading = false) }
                }
        }
    }

    private fun toggleSelectionMode(category: UserCollectCategory) {
        when (category) {
            UserCollectCategory.HOBBY -> _uiState.update {
                if (it.hobbySelectionMode) it.copy(hobbySelectionMode = false, hobbySelectedIds = emptySet())
                else it.copy(hobbySelectionMode = true)
            }
            UserCollectCategory.ALBUM -> _uiState.update {
                if (it.albumSelectionMode) it.copy(albumSelectionMode = false, albumSelectedIds = emptySet())
                else it.copy(albumSelectionMode = true)
            }
            else -> Unit
        }
    }

    private fun applyToggleSelect(id: Long) {
        when (_uiState.value.selectedCategory) {
            UserCollectCategory.HOBBY -> _uiState.update {
                val newSet = it.hobbySelectedIds.toggle(id)
                it.copy(
                    hobbySelectedIds = newSet,

                    hobbySelectionMode = newSet.isNotEmpty()
                )
            }
            UserCollectCategory.ALBUM -> _uiState.update {
                val newSet = it.albumSelectedIds.toggle(id)
                it.copy(
                    albumSelectedIds = newSet,
                    albumSelectionMode = newSet.isNotEmpty()
                )
            }
            else -> Unit
        }
    }

    private fun selectAll(category: UserCollectCategory) {
        when (category) {
            UserCollectCategory.HOBBY -> {
                val allIds = hobbyLoader.state.value.items.map { it.id }.toSet()
                _uiState.update { it.copy(hobbySelectedIds = allIds, hobbySelectionMode = allIds.isNotEmpty()) }
            }
            UserCollectCategory.ALBUM -> {
                val allIds = albumLoader.state.value.items.map { it.id }.toSet()
                _uiState.update { it.copy(albumSelectedIds = allIds, albumSelectionMode = allIds.isNotEmpty()) }
            }
            else -> Unit
        }
    }

    private fun clearSelection(category: UserCollectCategory) {
        when (category) {
            UserCollectCategory.HOBBY -> _uiState.update { it.copy(hobbySelectedIds = emptySet(), hobbySelectionMode = false) }
            UserCollectCategory.ALBUM -> _uiState.update { it.copy(albumSelectedIds = emptySet(), albumSelectionMode = false) }
            else -> Unit
        }
    }

    private fun confirmBatchCancel() {
        val snapshot = _uiState.value
        val category = snapshot.selectedCategory

        if (category != UserCollectCategory.HOBBY && category != UserCollectCategory.ALBUM) return
        val isHobby = category == UserCollectCategory.HOBBY
        val ids = if (isHobby) snapshot.hobbySelectedIds else snapshot.albumSelectedIds
        if (ids.isEmpty() || snapshot.isCancellingBatch) return

        _uiState.update { it.copy(isCancellingBatch = true, batchCancelError = null) }
        viewModelScope.launch {
            batchCancelCollectUseCase(ids.toList(), ownerUserId = userId)
                .onSuccess {
                    val generation = collectRepository.collectMutationGeneration.value
                    consumedMutationGeneration = generation
                    viewModelScope.launch {
                        viewPreferencesManager.setConsumedCollectMutationGeneration(generation)
                    }
                    dirtyCategories.remove(category)
                    dispatchToLoader(category) { it.refresh() }
                    _uiState.update {
                        if (isHobby) {
                            it.copy(
                                isCancellingBatch = false,
                                hobbySelectedIds = emptySet(),
                                hobbySelectionMode = false
                            )
                        } else {
                            it.copy(
                                isCancellingBatch = false,
                                albumSelectedIds = emptySet(),
                                albumSelectionMode = false
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isCancellingBatch = false, batchCancelError = error.message ?: "批量取消收藏失败")
                    }
                }
        }
    }

    private fun applyDismissBatchCancelError() {
        _uiState.update { it.copy(batchCancelError = null) }
    }

    private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

    private fun loadFirstPage(category: UserCollectCategory) {
        if (userId <= 0L) return
        dispatchToLoader(category) { it.loadInitial() }
    }

    private fun refresh(category: UserCollectCategory) {
        if (userId <= 0L) return
        dispatchToLoader(category) { it.refresh() }
    }

    private fun loadNextPage(category: UserCollectCategory) {
        if (userId <= 0L) return
        dispatchToLoader(category) { it.loadMore() }
    }

    private fun dismissError(category: UserCollectCategory) {
        dispatchToLoader(category) { it.clearError() }
    }

    private fun handleResume() {
        if (!consumedGenerationReady.isCompleted) {
            if (resumeAwaitingCursor) return
            resumeAwaitingCursor = true
            viewModelScope.launch {
                consumedGenerationReady.await()
                resumeAwaitingCursor = false
                handleResume()
            }
            return
        }
        val generation = collectRepository.collectMutationGeneration.value
        if (generation <= consumedMutationGeneration) return
        consumedMutationGeneration = generation
        viewModelScope.launch {
            viewPreferencesManager.setConsumedCollectMutationGeneration(generation)
        }
        if (userId <= 0L) return
        val active = _uiState.value.selectedCategory
        val staged = mutableSetOf<UserCollectCategory>()
        UserCollectCategory.entries.forEach { category ->

            if (category != active && isLoaderNeverLoaded(category)) return@forEach
            if (category == active) {
                dispatchToLoader(category) { it.refresh() }
            } else {
                staged.add(category)
            }
        }
        dirtyCategories.clear()
        dirtyCategories.addAll(staged)
    }

    private fun syncCategory(category: UserCollectCategory) {
        when (category) {
            UserCollectCategory.HOBBY -> _uiState.update { it.copy(hobbyData = toSubPageData(category, hobbyLoader.state.value)) }
            UserCollectCategory.ALBUM -> _uiState.update { it.copy(albumData = toSubPageData(category, albumLoader.state.value)) }
            UserCollectCategory.PICTURE -> _uiState.update { it.copy(picData = toSubPageData(category, picLoader.state.value)) }
            UserCollectCategory.VENDOR -> _uiState.update { it.copy(vendorData = toSubPageData(category, vendorLoader.state.value)) }
            UserCollectCategory.SERIES -> _uiState.update { it.copy(seriesData = toSubPageData(category, seriesLoader.state.value)) }
            UserCollectCategory.WORKS -> _uiState.update { it.copy(worksData = toSubPageData(category, worksLoader.state.value)) }
            UserCollectCategory.CHARACTER -> _uiState.update { it.copy(characterData = toSubPageData(category, characterLoader.state.value)) }
            UserCollectCategory.PERSON -> _uiState.update { it.copy(personData = toSubPageData(category, personLoader.state.value)) }
        }
    }

    private fun <T> toSubPageData(category: UserCollectCategory, listState: PagedListState<T>): SubPageData<T> {
        val rawError = if (category in ERROR_SILENCED_CATEGORIES) {
            null
        } else {
            listState.initialError ?: listState.appendError
        }
        val isPendingInitial = isLoaderStateNeverLoaded(listState)
        return SubPageData(
            items = listState.items,
            isLoading = listState.isLoading || (isPendingInitial && canAutoLoadNow()),
            isRefreshing = listState.isRefreshing,
            isPaging = listState.isLoadingMore,
            hasMore = listState.hasMore,
            page = listState.page,
            errorMessage = rawError,
            isContentHidden = rawError?.contains(CONTENT_HIDDEN_KEYWORD) == true,
            isPendingInitialLoad = isPendingInitial
        )
    }

    private fun canAutoLoadNow(): Boolean = !_uiState.value.isInitialized || userId > 0L

    private fun dispatchToLoader(category: UserCollectCategory, action: (PagedListLoader<*>) -> Unit) {
        action(loaders.getValue(category))
        syncCategory(category)
    }

    private fun resetLoader(category: UserCollectCategory) {
        loaders.getValue(category).reset()
        syncCategory(category)
    }

    private fun resetAllLoaders() {
        loaders.forEach { (category, loader) ->
            loader.reset()
            syncCategory(category)
        }
    }

    private fun isLoaderNeverLoaded(category: UserCollectCategory): Boolean =
        isLoaderStateNeverLoaded(loaders.getValue(category).state.value)

    private fun isLoaderStateNeverLoaded(listState: PagedListState<*>): Boolean =
        listState.stage == LoadStage.Idle &&
            listState.items.isEmpty() &&
            listState.hasMore &&
            listState.initialError == null

    private suspend fun fetchCommonPage(
        category: UserCollectCategory,
        page: Int,
        pageSize: Int,
        order: String
    ) = collectRepository.getUserCollectCommonList(
        userId = userId,
        categoryId = category.key,
        type = category.apiType,
        order = order,
        page = page,
        pageSize = pageSize
    )

    private suspend fun fetchWorksPage(
        page: Int,
        pageSize: Int,
        worksState: Int,
        isAsc: Boolean,
        tagValue: String?
    ) = collectRepository.getUserCollectCommonList(
        userId = userId,
        categoryId = UserCollectCategory.WORKS.key,
        type = UserCollectCategory.WORKS.apiType,
        order = CommonOrderOption.ACTION_DATE,
        page = page,
        pageSize = pageSize,
        state = worksState,
        isAsc = isAsc,
        tagValue = tagValue
    )

    private fun resolveIsSelf(targetUserId: Long): Boolean {
        val loginId = userSession.currentUserId
        return loginId > 0L && loginId == targetUserId
    }

    private fun readStringMapMemory(key: String): MutableMap<String, String> =
        runCatching {
            @Suppress("UNCHECKED_CAST")
            (stateGet<Any>(key) as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
        }.getOrDefault(mutableMapOf())

    private fun persistHobbySortMemory() {
        runCatching {
            stateSet(KEY_HOBBY_ORDER_MEMORY, HashMap(hobbyOrderMemory))
            stateSet(KEY_HOBBY_SORT_TYPE_MEMORY, HashMap(hobbySortTypeMemory.mapValues { it.value.toString() }))
        }
    }

    private fun persistHobbySortToPrefs() {
        val state = _uiState.value.collectState
        val order = _uiState.value.order
        val sortType = _uiState.value.sortType
        viewModelScope.launch {
            runCatching { viewPreferencesManager.setCollectHobbyOrder(state, order, sortType) }
        }
    }

    companion object {
        const val PAGE_SIZE = 20

        const val DEFAULT_HOBBY_KEY = HobbySubCategoryOption.ALL_KEY

        const val TAG_POOL_SIZE = 50

        private const val CONTENT_HIDDEN_KEYWORD = "隐藏"
        private const val KEY_HOBBY_ORDER_MEMORY = "collect_hobby_order_memory"
        private const val KEY_HOBBY_SORT_TYPE_MEMORY = "collect_hobby_sort_type_memory"

        val COMMON_CATEGORIES = setOf(
            UserCollectCategory.VENDOR,
            UserCollectCategory.SERIES,
            UserCollectCategory.CHARACTER,
            UserCollectCategory.PERSON
        )

        private val ERROR_SILENCED_CATEGORIES = setOf(
            UserCollectCategory.ALBUM,
            UserCollectCategory.PICTURE
        )
    }
}
