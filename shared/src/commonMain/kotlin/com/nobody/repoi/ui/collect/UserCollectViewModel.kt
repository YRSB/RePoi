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

/**
 * 用户全量收藏中枢 ViewModel（纯 MVI 单向数据流）。
 *
 * 对外只暴露两个接缝：
 * - [uiState]：单一不可变状态流，由 8 个 [PagedListLoader] 的状态投影而来；
 * - [onIntent]：全部用户意图的唯一入口（[UserCollectIntent] 密封接口）。
 *
 * 分页、并发守卫、稳定键去重、在途取消、首尾错误恢复全部下沉到 [PagedListLoader]；
 * 本类不再持有手写 `Job` 映射、页码递增或分页状态机样板代码。
 *
 * 8 大类各持一个 loader 实例（`KeyedListLoader` 只有一个 `activeJob`，共用会互相取消），
 * loader 的 `fetcher` 闭包在每次请求时重读 [uiState]，因此收藏状态、排序、升降序、
 * 筛选条件、相册类型与作品三态都是**运行时可变条件**，不在构造时快照。
 *
 * 排序维度按大类隔离，互不串味：
 * - 周边：[UserCollectUiState.order] + [UserCollectUiState.sortType]（按收藏状态分别记忆）
 * - 相册：[UserCollectUiState.albumOrder]（3 档）
 * - 图片：[UserCollectUiState.picOrder]（3 档）
 * - 通用 4 页：[UserCollectUiState.commonOrder]（2 档白名单）
 * - 作品：[UserCollectUiState.worksState] + [UserCollectUiState.worksOrderAsc] + [UserCollectUiState.worksTag]
 */
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

    /**
     * 进程恢复/深链透传状态槽（原 `SavedStateHandle` 的 NavKey 等价实现）。
     * 构造入参即 NavKey 字段；运行时写入仅保活内存（附带 DataStore 持久化见 persist*）。
     * 构造参数以 nav 前缀命名，避免与下方同名成员属性产生遮蔽歧义。
     */
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

    /** 初始排序是调用方显式指定的（深链透传 / 进程恢复），还是仅仅走了默认值 */
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
            albumType = resolveAlbumType(stateGet<String>("albumType"))
        )
    )
    val uiState: StateFlow<UserCollectUiState> = _uiState.asStateFlow()

    /**
     * 已消费的**原始**导航参数（入口初始化去重依据）。
     *
     * 必须与归一化后的 [currentParam] 分开：归一化结果会随排序记忆等运行时状态漂移，
     * 不能稳定代表「这个 NavKey 是否已经初始化过」。
     */
    private var appliedNavParam: UserCollectNavParam? = null
    private var currentParam: UserCollectNavParam? = null

    /**
     * 周边页「排序 + 升降序」按收藏状态分别记忆：
     * 原版 `UserCollectHobbyFragment.java:803-816` 把 `orderObject`/`sortObject` 落 Prefs。
     */
    private val hobbyOrderMemory: MutableMap<String, String> = readStringMapMemory(KEY_HOBBY_ORDER_MEMORY)

    private val hobbySortTypeMemory: MutableMap<String, Int> =
        readStringMapMemory(KEY_HOBBY_SORT_TYPE_MEMORY).mapValues { it.value.toIntOrNull() ?: UserCollectUiState.SORT_DESC }.toMutableMap()

    /**
     * 读回「按收藏状态分别记忆」的排序持久化（`ViewPreferencesManager.setCollectHobbyOrder`）。
     *
     * 优先级：调用方显式传入的深链排序 > 持久化记忆 > 类默认值。
     * 所有首屏装载都必须先 `await()` 本任务，否则会用默认值白发一次请求。
     */
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
            // 同步回状态，避免「记忆中的排序生效前」出现一帧默认排序
            if (restoredOrder != _uiState.value.order || restoredSortType != _uiState.value.sortType) {
                _uiState.update { it.copy(order = restoredOrder, sortType = restoredSortType) }
            }
        }
        persistHobbySortMemory()
    }

    /**
     * 已消费的画廊写操作计数快照。
     *
     * [CollectRepository.galleryMutationTicks] 单调递增且无订阅也不丢事件，
     * 因此恢复时只需比对计数是否变大，即可判定「画廊里动过收藏」。
     */
    private var consumedGalleryTicks: Long = collectRepository.galleryMutationTicks.value

    // ==================== 8 大类分页装载器 ====================

    private val hobbyLoader: PagedListLoader<UserCollectHobbyItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,
        // 外层收藏节点 id 与真实条目 id 的组合键：跨页可能出现同一 actualItemId 的不同收藏节点
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

    /**
     * 作品页请求形态独立：`type="works"` + 收藏三态 `state` + 升降序 `isAsc` + 标签 `tagValue`，
     * 且没有 `order` 维度（强制收藏时间）。
     */
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

    /** 通用 4 页 loader：`type` 与 `categoryId` 随大类固定，排序取运行时 [UserCollectUiState.commonOrder] */
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

    /** 大类 → loader 映射（首屏重置、状态投影与错误清理共用） */
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
        // 监听视图偏好变更
        viewModelScope.launch {
            viewPreferencesManager.isCollectListGridModeFlow.collect { isGrid ->
                _uiState.update { it.copy(isListView = !isGrid) }
            }
        }

        // 8 个 loader 的状态投影到 UI 状态（列表数据的唯一来源）
        loaders.forEach { (category, loader) ->
            viewModelScope.launch {
                loader.state.collect { syncCategory(category) }
            }
        }

        // 排序记忆槽由 persistedSortReady 统一灌注（显式深链 > 持久化 > 默认值）

        if (userId > 0L) {
            val initialCat = UserCollectCategory.entries.find { it.key == initialCategoryKey } ?: UserCollectCategory.HOBBY
            initParam(
                UserCollectNavParam(
                    userId = userId,
                    initialCollectState = initialCollectState,
                    // 关键：调用方没有显式指定排序时必须传 null。
                    // 若把类默认值当成"显式传入"下发，会覆盖 persistedSortReady 刚恢复出来的排序记忆，
                    // 导致每次冷启动都被重置回 actionDate。
                    initialOrder = initialOrder.takeIf { hasExplicitInitialOrder },
                    initialCategory = initialCat.key
                )
            )
        }
    }

    // ==================== 意图入口 ====================

    /**
     * 全部用户意图的唯一入口。
     */
    fun onIntent(intent: UserCollectIntent) {
        when (intent) {
            is UserCollectIntent.SwitchCategory -> applyCategorySwitch(intent.category)
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
            is UserCollectIntent.SwitchAlbumType -> applyAlbumType(intent.type)
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

    // ==================== 深链 ====================

    /**
     * 外部通过路由参数初始化或更新状态。
     *
     * 深链三态语义（勿用 `ifBlank` 折叠）：
     * - `initialCollectState == null` → 调用方未指定，回落到原版默认档（`CollectStateOption.DEFAULT`，即「全部」）；
     * - `initialCollectState == ""`   → 调用方显式指定「全部」档，必须原样保留；
     * - 其余值原样透传，**`care`（关注）不得被改写成 `want`（想买）**。
     *
     * `initialOrder` 为 null 时回落到该收藏状态下**已记忆**的排序（持久化优先，最后才用类默认值），
     * 按目标大类归位到对应的排序槽位，并对该大类的白名单做校验。
     */
    fun initParam(param: UserCollectNavParam) {
        // 入口初始化必须幂等：Nav3 从二级页返回时会重建 Entry 组合，
        // UserCollectScreen 的 LaunchedEffect(param) 会再次触发本方法。
        // 若重复下发 param.initialCategory（通常为 null → 周边页），
        // 会把用户已切换的大类 Tab 与浏览位置重置回默认页。
        // 注意：applyInitParam 内的归一化守卫会被排序记忆漂移误判，故门禁必须用原始 param。
        if (appliedNavParam == param) return
        appliedNavParam = param
        // 相册语料档位只在档位确实变化时重载：NavKey 默认 fav 不能覆盖用户在页内切过的「我创建的」
        reloadAlbumIfTypeChanged(resolveAlbumType(param.albumType))
        // 归一化必须排在持久化读回之后，否则首屏会用默认排序白发一次请求
        viewModelScope.launch {
            persistedSortReady.await()
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
        // 深链 / 进程恢复注入的筛选条件同样要归一化，否则同一份内容会落成两个保存键
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
        currentParam = normalizedParam
        userId = param.userId
        stateSet("userId", param.userId)
        stateSet("initialCollectState", if (targetCategory == UserCollectCategory.WORKS) targetWorksState.toString() else targetState)
        stateSet("initialOrder", targetOrder)
        stateSet("initialCategory", targetCategory.key)
        // 相册语料档位同样进恢复槽：Nav3 重建 Entry 后仍停在用户选过的那一档
        stateSet("albumType", resolveAlbumType(param.albumType))
        if (targetCategory == UserCollectCategory.WORKS) {
            stateSet("worksState", targetWorksState)
        }

        // 排序按收藏状态记忆：只有深链**显式**指定了排序才覆写记忆槽，否则保留持久化记忆
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
        // 8 类缓存全部失效：清空 loader 状态（含页码与 hasMore），只装载目标大类首屏
        resetAllLoaders()
        loadFirstPage(targetCategory)
    }

    /**
     * 相册语料档位变化时重载相册列表（保留 AppRoute 已有的 albumType 入口语义）。
     *
     * 仅在档位确实不同时动作：NavKey 默认 `fav` 不得把页内切到的「我创建的」顶回去。
     */
    private fun reloadAlbumIfTypeChanged(targetType: String) {
        if (_uiState.value.albumType == targetType) return
        stateSet("albumType", targetType)
        _uiState.update { it.copy(albumType = targetType) }
        resetLoader(UserCollectCategory.ALBUM)
        if (_uiState.value.selectedCategory == UserCollectCategory.ALBUM) {
            loadFirstPage(UserCollectCategory.ALBUM)
        }
    }

    /**
     * 解析相册语料档位：NavKey 透传值 / 进程恢复槽值，白名单外一律回落 [AlbumTypeOption.DEFAULT]。
     */
    private fun resolveAlbumType(raw: String?): String =
        if (raw != null && AlbumTypeOption.isValid(raw)) raw else AlbumTypeOption.DEFAULT

    /**
     * 解析作品页深链收藏状态：支持 "1"/"2"/"3" 或语义别名（want/watching/watched 等），默认想看 (1)。
     */
    private fun resolveWorksState(raw: String?): Int {
        if (raw.isNullOrBlank()) return WorksStateOption.WANT
        return when (raw.trim().lowercase()) {
            "1", "want", "care", "想看", "想玩" -> WorksStateOption.WANT
            "2", "watching", "do", "在看", "在玩" -> WorksStateOption.WATCHING
            "3", "watched", "collect", "看过", "玩过" -> WorksStateOption.WATCHED
            else -> raw.trim().toIntOrNull()?.takeIf { WorksStateOption.isValid(it) } ?: WorksStateOption.WANT
        }
    }

    /**
     * 解析深链收藏状态：`null` = 未指定走默认档；`""` = 显式「全部」档原样保留；其余按白名单校验。
     */
    private fun resolveCollectState(raw: String?): String {
        if (raw == null) return CollectStateOption.DEFAULT
        if (raw.isEmpty()) return CollectStateOption.ALL
        return if (CollectStateOption.isValid(raw)) raw else CollectStateOption.DEFAULT
    }

    /**
     * 解析深链排序：按目标大类选择白名单，越界值回落该大类的默认排序。
     *
     * @param targetState 本次深链解析出的收藏状态，周边排序值域校验须以它为准（而非旧状态）。
     */
    private fun resolveOrderFor(category: UserCollectCategory, raw: String?, targetState: String): String {
        if (raw == null) return defaultOrderFor(category, targetState)
        return when (category) {
            UserCollectCategory.HOBBY -> HobbyOrderOption.effective(
                targetState,
                if (HobbyOrderOption.isValid(raw, targetState)) raw else HobbyOrderOption.DEFAULT
            )
            UserCollectCategory.ALBUM, UserCollectCategory.PICTURE ->
                if (MediaOrderOption.isValid(raw)) raw else MediaOrderOption.DEFAULT
            // 作品页没有 order 维度（原版只有 state + isAsc + tagValue）
            UserCollectCategory.WORKS -> CommonOrderOption.ACTION_DATE
            else -> if (CommonOrderOption.isValid(raw)) raw else CommonOrderOption.DEFAULT
        }
    }

    /**
     * 未指定排序时的回落：周边页先取该收藏状态下**已记忆**的排序（持久化），再退到类默认值。
     */
    private fun defaultOrderFor(category: UserCollectCategory, targetState: String): String = when (category) {
        UserCollectCategory.HOBBY -> HobbyOrderOption.effective(
            targetState,
            hobbyOrderMemory[targetState] ?: HobbyOrderOption.DEFAULT
        )
        UserCollectCategory.ALBUM, UserCollectCategory.PICTURE -> MediaOrderOption.DEFAULT
        UserCollectCategory.WORKS -> CommonOrderOption.ACTION_DATE
        else -> CommonOrderOption.DEFAULT
    }

    // ==================== 大类 / 视图 ====================

    /**
     * 切换大类（若目标大类尚未加载数据，自动触发首屏加载）。
     */
    private fun applyCategorySwitch(category: UserCollectCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        if (isLoaderIdleAndEmpty(category)) {
            loadFirstPage(category)
        }
    }

    /**
     * 切换手办视图模式（网格/列表），并持久化偏好。
     */
    private fun applyToggleViewMode() {
        val newIsList = !_uiState.value.isListView
        _uiState.update { it.copy(isListView = newIsList) }
        viewModelScope.launch {
            viewPreferencesManager.setCollectListGridMode(!newIsList)
        }
    }

    // ==================== 周边页：状态 / 排序 / 筛选 ====================

    /**
     * 切换周边收藏状态（全部/关注/想买/预定/已入/有过）。
     * 排序与升降序按状态分别记忆：切回旧状态时恢复该状态上次的排序选择。
     */
    private fun applyCollectState(newState: String) {
        if (!CollectStateOption.isValid(newState)) return
        if (_uiState.value.collectState == newState) return
        stateSet("collectState", newState)
        // 入手时间在非 buy 档非法，回落到收藏时间
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

    /**
     * 切换周边排序方式（收藏时间/发售时间/评分/我的评分/更新时间/入手时间）。
     * 入手时间仅「已入(buy)」档可选，其余档位自动回落收藏时间。
     */
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

    /**
     * 切换周边升降序（1 升序 / 2 降序）。
     */
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

    /**
     * 选择周边细分子分类。
     */
    private fun applySubCategory(subCategoryId: Int?) {
        // 筛选抽屉回传「全部」可能是候选首项 10000，统一收敛到 null，
        // 保证保存键与请求参数一一对应（否则同内容会同时存在 null / 10000 两个键）。
        val normalized = HobbySubCategoryOption.normalize(subCategoryId)
        if (_uiState.value.subCategoryId == normalized) return
        _uiState.update { it.copy(subCategoryId = normalized) }
        resetLoader(UserCollectCategory.HOBBY)
        loadFirstPage(UserCollectCategory.HOBBY)
        refreshTagPoolIfSheetOpen()
    }

    /**
     * 选择/反选标签过滤。
     */
    private fun applyTag(tag: String?) {
        // 空白标签等价于「未筛选」，避免 null 与 "" 两种表示制造两个保存键
        val requested = tag?.takeUnless { it.isBlank() }
        val newTag = if (_uiState.value.selectedTag == requested) null else requested
        _uiState.update { it.copy(selectedTag = newTag) }
        resetLoader(UserCollectCategory.HOBBY)
        loadFirstPage(UserCollectCategory.HOBBY)
    }

    /**
     * 重置所有筛选条件（子分类与标签）。
     */
    private fun resetFilterInternal() {
        // subCategoryId 已归一化（「全部」恒为 null），此守卫即「本来就没筛选」的精确判断
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

    /**
     * 控制筛选抽屉显隐。**打开时按当前收藏状态与细分类懒加载标签候选池**
     * （原版 `UserCollectHobbyFragment.java:593-611` 每次开弹层带 `categoryId` + `collectState` 重拉）。
     */
    private fun applyFilterSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showFilterSheet = visible) }
        if (visible) {
            loadHobbyTags(force = true)
        }
    }

    /**
     * 按当前收藏状态与细分类懒加载周边标签候选池。
     *
     * @param force 为 true 时无视已有缓存强制重拉（开筛选弹层时使用）。
     */
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

    /**
     * 筛选弹层打开时上下文（收藏状态 / 细分类）发生变化后刷新标签池。
     */
    private fun refreshTagPoolIfSheetOpen() {
        if (_uiState.value.showFilterSheet) {
            loadHobbyTags(force = true)
        }
    }

    // ==================== 通用 4 页排序 ====================

    /**
     * 切换通用 4 页（厂商/系列/角色/人物）排序：收藏时间 / 人气。
     * 白名单外（含历史遗留的 `orderTime`）一律拒绝。
     *
     * 排序维度被 4 页共享，故其余 3 页的缓存随之失效（重置为空），
     * 下次切到该 Tab 时由 [UserCollectIntent.SwitchCategory] 触发首屏装载。
     */
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

    // ==================== 相册页排序 ====================

    /**
     * 切换相册排序：发布时间 / 收藏时间 / 点赞量。
     */
    private fun applyAlbumOrder(newOrder: String) {
        if (!MediaOrderOption.isValid(newOrder)) return
        if (_uiState.value.albumOrder == newOrder) return
        _uiState.update { it.copy(albumOrder = newOrder) }
        resetLoader(UserCollectCategory.ALBUM)
        if (_uiState.value.selectedCategory == UserCollectCategory.ALBUM) {
            loadFirstPage(UserCollectCategory.ALBUM)
        }
    }

    // ==================== 相册页语料（我收藏的 / 我创建的）====================

    /**
     * 切换相册语料档位（[AlbumTypeOption.FAV] / [AlbumTypeOption.UPLOAD]）。
     *
     * 两档走两套接口，切换即等于换了一份列表：必须重置 loader（清掉旧页码与 hasMore）
     * 再装载首屏，否则「我创建的」会沿用「我收藏的」的页码继续翻页。
     * 滚动位置由相册子页按 `albumType + order` 绑定新键自动回顶。
     */
    private fun applyAlbumType(newType: String) {
        if (!AlbumTypeOption.isValid(newType)) return
        if (_uiState.value.albumType == newType) return
        stateSet("albumType", newType)
        _uiState.update { it.copy(albumType = newType) }
        resetLoader(UserCollectCategory.ALBUM)
        if (_uiState.value.selectedCategory == UserCollectCategory.ALBUM) {
            loadFirstPage(UserCollectCategory.ALBUM)
        }
    }

    /**
     * 相册页分页请求：按运行时 [UserCollectUiState.albumType] 分流到两套接口。
     *
     * - `fav` → [CollectRepository.getUserCollectAlbumList]（`api/collect/album-v2`）；
     * - `upload` → [CollectRepository.getUserCreationAlbumList]（`api/album/user-v2`，点击需改用 `actualAlbumId`）。
     */
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

    // ==================== 图片页排序 ====================

    /**
     * 切换图片排序：发布时间 / 收藏时间 / 点赞量。
     */
    private fun applyPicOrder(newOrder: String) {
        if (!MediaOrderOption.isValid(newOrder)) return
        if (_uiState.value.picOrder == newOrder) return
        _uiState.update { it.copy(picOrder = newOrder) }
        resetLoader(UserCollectCategory.PICTURE)
        if (_uiState.value.selectedCategory == UserCollectCategory.PICTURE) {
            loadFirstPage(UserCollectCategory.PICTURE)
        }
    }

    // ==================== 作品页（独立请求形态）====================

    /**
     * 切换作品收藏状态（想看=1 / 在看=2 / 看过=3）。
     */
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

    /**
     * 切换作品页收藏时间升降序。
     */
    private fun applyWorksOrderAsc(isAsc: Boolean) {
        if (_uiState.value.worksOrderAsc == isAsc) return
        stateSet("worksOrderAsc", isAsc)
        _uiState.update { it.copy(worksOrderAsc = isAsc) }
        resetLoader(UserCollectCategory.WORKS)
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadFirstPage(UserCollectCategory.WORKS)
        }
    }

    /**
     * 选择/反选作品页标签过滤。
     */
    private fun applyWorksTag(tag: String?) {
        val newTag = if (_uiState.value.worksTag == tag) null else tag
        _uiState.update { it.copy(worksTag = newTag) }
        resetLoader(UserCollectCategory.WORKS)
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadFirstPage(UserCollectCategory.WORKS)
        }
    }

    /**
     * 重置作品页标签筛选。
     */
    private fun resetWorksTagInternal() {
        if (_uiState.value.worksTag == null) return
        _uiState.update { it.copy(worksTag = null) }
        resetLoader(UserCollectCategory.WORKS)
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadFirstPage(UserCollectCategory.WORKS)
        }
    }

    /**
     * 控制作品页筛选弹层显隐，打开时懒加载作品标签候选池
     * （原版池来自 `api/user/glean/textTag/get`，与手办池不是同一个）。
     */
    private fun applyWorksFilterSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showWorksFilterSheet = visible) }
        if (visible) {
            loadWorksTags(force = true)
        }
    }

    /**
     * 懒加载作品页标签候选池。
     */
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

    // ==================== 批量取消收藏 ====================

    /**
     * 进入/退出多选模式。周边页与相册页各持一套独立选中集合，切换互不影响。
     */
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

    /**
     * 选中/反选单条（长按卡片进入多选后使用）。
     *
     * @param id **外层收藏节点 id**（不是 `actualItemId`/`actualAlbumId`）。
     */
    private fun applyToggleSelect(id: Long) {
        when (_uiState.value.selectedCategory) {
            UserCollectCategory.HOBBY -> _uiState.update {
                val newSet = it.hobbySelectedIds.toggle(id)
                it.copy(
                    hobbySelectedIds = newSet,
                    // 取消到 0 条时自动退出多选模式
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

    /**
     * 全选指定大类已加载的全部条目。
     */
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

    /**
     * 清空指定大类的选中集合并退出多选模式。
     */
    private fun clearSelection(category: UserCollectCategory) {
        when (category) {
            UserCollectCategory.HOBBY -> _uiState.update { it.copy(hobbySelectedIds = emptySet(), hobbySelectionMode = false) }
            UserCollectCategory.ALBUM -> _uiState.update { it.copy(albumSelectedIds = emptySet(), albumSelectionMode = false) }
            else -> Unit
        }
    }

    /**
     * 批量取消收藏（`api/collect/batch/cancel`）。仅周边页与相册页具备该能力。
     *
     * 验权（非本人零请求）已上移到 [BatchCancelCollectUseCase]；
     * 成功后就地 `removeItem` 剔除已取消条目，使后续 `loadMore` 不会重复拉取已删页。
     */
    private fun confirmBatchCancel() {
        val snapshot = _uiState.value
        val category = snapshot.selectedCategory
        // 仅周边页与相册页有批量取消收藏能力
        if (category != UserCollectCategory.HOBBY && category != UserCollectCategory.ALBUM) return
        val isHobby = category == UserCollectCategory.HOBBY
        val ids = if (isHobby) snapshot.hobbySelectedIds else snapshot.albumSelectedIds
        if (ids.isEmpty() || snapshot.isCancellingBatch) return

        _uiState.update { it.copy(isCancellingBatch = true, batchCancelError = null) }
        viewModelScope.launch {
            batchCancelCollectUseCase(ids.toList(), ownerUserId = userId)
                .onSuccess {
                    if (isHobby) {
                        hobbyLoader.removeItem { it.id in ids }
                        syncCategory(UserCollectCategory.HOBBY)
                        _uiState.update {
                            it.copy(
                                isCancellingBatch = false,
                                hobbySelectedIds = emptySet(),
                                hobbySelectionMode = false
                            )
                        }
                    } else {
                        albumLoader.removeItem { it.id in ids }
                        syncCategory(UserCollectCategory.ALBUM)
                        _uiState.update {
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

    /**
     * 清除批量取消收藏的错误提示。
     */
    private fun applyDismissBatchCancelError() {
        _uiState.update { it.copy(batchCancelError = null) }
    }

    private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

    // ==================== 分页装载 ====================

    /** 加载指定大类的第一页（首次进入 / 重试），用户态入口见 [UserCollectIntent.LoadFirstPage] */
    private fun loadFirstPage(category: UserCollectCategory) {
        if (userId <= 0L) return
        dispatchToLoader(category) { it.loadInitial() }
    }

    /** 下拉刷新指定大类，用户态入口见 [UserCollectIntent.Refresh] */
    private fun refresh(category: UserCollectCategory) {
        if (userId <= 0L) return
        dispatchToLoader(category) { it.refresh() }
    }

    /** 加载指定大类下一页，用户态入口见 [UserCollectIntent.LoadNextPage] */
    private fun loadNextPage(category: UserCollectCategory) {
        if (userId <= 0L) return
        dispatchToLoader(category) { it.loadMore() }
    }

    /** 清除指定大类错误提示，用户态入口见 [UserCollectIntent.DismissError] */
    private fun dismissError(category: UserCollectCategory) {
        dispatchToLoader(category) { it.clearError() }
    }

    // ==================== 恢复感知 ====================

    /**
     * 恢复感知：读画廊脏位，有变化则对**图片页**静默刷新。
     *
     * 脏位方案来自 ADR-0011：`CollectRepository` 的写操作计数是状态而非事件，
     * 出栈返回、进程内任意时刻都可比对，且无订阅者也不丢。
     * 仅当当前正停在图片大类、或图片列表已开始装载过时才刷新，避免白发一次首屏请求。
     */
    private fun handleResume() {
        val ticks = collectRepository.galleryMutationTicks.value
        if (ticks <= consumedGalleryTicks) return
        consumedGalleryTicks = ticks
        if (userId <= 0L) return
        val picState = picLoader.state.value
        val shouldRefreshPic = _uiState.value.selectedCategory == UserCollectCategory.PICTURE ||
            picState.items.isNotEmpty() ||
            picState.stage != LoadStage.Idle
        if (shouldRefreshPic) {
            dispatchToLoader(UserCollectCategory.PICTURE) { it.refresh() }
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 单个 loader 状态 → [UserCollectUiState] 对应子页的投影。
     *
     * `SubPageData` 完全由 loader 的 [PagedListState] 派生，不额外保存副本，
     * 因此不存在"列表与页码各改一半"的中间态。
     */
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

    /**
     * loader 状态 → UI 子页状态。
     *
     * 相册页与图片页沿用重构前的错误静默行为：这两类不发错误文案（失败时展示通用空态），
     * 其余大类透传 `initialError` / `appendError`。「用户已隐藏内容」专用空态由错误文案关键词派生
     * ——[PagedListState] 没有该字段，故在此处旁路判定。
     */
    private fun <T> toSubPageData(category: UserCollectCategory, listState: PagedListState<T>): SubPageData<T> {
        val rawError = if (category in ERROR_SILENCED_CATEGORIES) {
            null
        } else {
            listState.initialError ?: listState.appendError
        }
        return SubPageData(
            items = listState.items,
            isLoading = listState.isLoading,
            isRefreshing = listState.isRefreshing,
            isPaging = listState.isLoadingMore,
            hasMore = listState.hasMore,
            page = listState.page,
            errorMessage = rawError,
            isContentHidden = rawError?.contains(CONTENT_HIDDEN_KEYWORD) == true
        )
    }

    /**
     * 对指定大类的 loader 执行一次动作，并**同步**把结果投影进 [uiState]。
     *
     * loader 自身的状态流由后台协程转发；若不做同步投影，UI 会先看到一帧旧状态
     * （典型症状：清除错误后 snackbar 又按旧值弹一次、点重试后没有立即出现 loading）。
     */
    private fun dispatchToLoader(category: UserCollectCategory, action: (PagedListLoader<*>) -> Unit) {
        action(loaders.getValue(category))
        syncCategory(category)
    }

    /**
     * 取消在途请求并清空该大类列表（条件变化后必须重置，避免旧页码与 hasMore 污染新条件）。
     *
     * 重置后立刻同步投影一次：否则 UI 会残留一帧旧条件的数据（重构前是在 `_uiState.update` 里同步清空的）。
     */
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

    /** 目标大类没装载过（闲置且空列表）：切 Tab 时才补首屏请求，已有缓存则原地复用 */
    private fun isLoaderIdleAndEmpty(category: UserCollectCategory): Boolean {
        val listState = loaders.getValue(category).state.value
        return listState.stage == LoadStage.Idle && listState.items.isEmpty()
    }

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

    /**
     * 登录用户 id 与当前浏览用户 id 相同即为「本人」，仅本人可见写操作入口。
     */
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

    /**
     * 把当前收藏状态的排序 + 升降序落 DataStore（对标原版 Prefs 的 `collect_order` / `collect_sort`）。
     *
     * DataStore 键名里收藏状态是空串时（如「全部」档）后缀为空，属预期行为。
     */
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

        /** 周边「全部」细分类的请求取值（原版以 10000 表示；UI 态见 [HobbySubCategoryOption]） */
        const val DEFAULT_HOBBY_KEY = HobbySubCategoryOption.ALL_KEY

        /** 标签候选池拉取条数 */
        const val TAG_POOL_SIZE = 50

        private const val CONTENT_HIDDEN_KEYWORD = "隐藏"
        private const val KEY_HOBBY_ORDER_MEMORY = "collect_hobby_order_memory"
        private const val KEY_HOBBY_SORT_TYPE_MEMORY = "collect_hobby_sort_type_memory"

        /** 通用 4 页：厂商/系列/角色/人物，共用 2 档排序白名单 */
        val COMMON_CATEGORIES = setOf(
            UserCollectCategory.VENDOR,
            UserCollectCategory.SERIES,
            UserCollectCategory.CHARACTER,
            UserCollectCategory.PERSON
        )

        /** 相册页与图片页沿用重构前的错误静默行为（失败时不弹错误文案，走通用空态） */
        private val ERROR_SILENCED_CATEGORIES = setOf(
            UserCollectCategory.ALBUM,
            UserCollectCategory.PICTURE
        )
    }
}
