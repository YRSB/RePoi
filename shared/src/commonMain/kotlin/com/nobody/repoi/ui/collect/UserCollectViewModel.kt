package com.nobody.repoi.ui.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 用户全量收藏中枢 ViewModel。
 * 驱动 8 大实体大类、手办双模视图、6 档状态胶囊、多套排序与多维筛选，以及批量取消收藏多选。
 *
 * 排序维度按大类隔离，互不串味：
 * - 周边：[UserCollectUiState.order] + [UserCollectUiState.sortType]（按收藏状态分别记忆）
 * - 相册：[UserCollectUiState.albumOrder]（3 档）
 * - 图片：[UserCollectUiState.picOrder]（3 档）
 * - 通用 4 页：[UserCollectUiState.commonOrder]（2 档白名单）
 * - 作品：[UserCollectUiState.worksState] + [UserCollectUiState.worksOrderAsc] + [UserCollectUiState.worksTag]
 */
class UserCollectViewModel(
    private val userRepository: UserRepository,
    private val collectRepository: CollectRepository,
    private val viewPreferencesManager: ViewPreferencesManager,
    private val tokenManager: TokenManager,
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
            isSelf = resolveIsSelf(userId)
        )
    )
    val uiState: StateFlow<UserCollectUiState> = _uiState.asStateFlow()

    private var currentParam: UserCollectNavParam? = null
    private val fetchJobs = mutableMapOf<UserCollectCategory, Job>()

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

    init {
        // 监听视图偏好变更
        viewModelScope.launch {
            viewPreferencesManager.isCollectListGridModeFlow.collect { isGrid ->
                _uiState.update { it.copy(isListView = !isGrid) }
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
        val targetCategory = UserCollectCategory.entries.find { it.key == param.initialCategory } ?: UserCollectCategory.HOBBY
        if (_uiState.value.selectedCategory != targetCategory) {
            _uiState.update { it.copy(selectedCategory = targetCategory) }
        }
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
        val targetSubCategory = param.initialSubCategoryId
        val targetTag = param.initialTag
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
                albumType = param.albumType,
                albumOrder = if (targetCategory == UserCollectCategory.ALBUM) targetOrder else it.albumOrder,
                picOrder = if (targetCategory == UserCollectCategory.PICTURE) targetOrder else it.picOrder,
                commonOrder = if (targetCategory in COMMON_CATEGORIES) targetOrder else it.commonOrder,
                hobbyData = SubPageData(),
                albumData = SubPageData(),
                picData = SubPageData(),
                vendorData = SubPageData(),
                seriesData = SubPageData(),
                worksData = SubPageData(),
                characterData = SubPageData(),
                personData = SubPageData()
            )
        }
        loadFirstPage(targetCategory)
    }

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
    fun switchCategory(category: UserCollectCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        val hasData = _uiState.value.subPageData(category).items.isNotEmpty()
        if (!hasData) {
            loadFirstPage(category)
        }
    }

    /**
     * 切换手办视图模式（网格/列表），并持久化偏好。
     */
    fun toggleViewMode() {
        val currentIsList = _uiState.value.isListView
        val newIsList = !currentIsList
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
    fun switchCollectState(newState: String) {
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
                sortType = restoredSortType,
                hobbyData = SubPageData()
            )
        }
        loadFirstPage(UserCollectCategory.HOBBY)
        refreshTagPoolIfSheetOpen()
        persistHobbySortToPrefs()
    }

    /**
     * 切换周边排序方式（收藏时间/发售时间/评分/我的评分/更新时间/入手时间）。
     * 入手时间仅「已入(buy)」档可选，其余档位自动回落收藏时间。
     */
    fun switchOrder(newOrder: String) {
        if (!HobbyOrderOption.isValid(newOrder, _uiState.value.collectState)) return
        val effectiveOrder = HobbyOrderOption.effective(_uiState.value.collectState, newOrder)
        if (_uiState.value.order == effectiveOrder) return
        stateSet("order", effectiveOrder)
        hobbyOrderMemory[_uiState.value.collectState] = effectiveOrder
        persistHobbySortMemory()
        _uiState.update {
            it.copy(
                order = effectiveOrder,
                hobbyData = SubPageData()
            )
        }
        loadFirstPage(UserCollectCategory.HOBBY)
        persistHobbySortToPrefs()
    }

    /**
     * 切换周边升降序（1 升序 / 2 降序）。
     */
    fun switchSortDirection(newSortType: Int) {
        if (newSortType != UserCollectUiState.SORT_ASC && newSortType != UserCollectUiState.SORT_DESC) return
        if (_uiState.value.sortType == newSortType) return
        stateSet("sortType", newSortType)
        hobbySortTypeMemory[_uiState.value.collectState] = newSortType
        persistHobbySortMemory()
        _uiState.update {
            it.copy(
                sortType = newSortType,
                hobbyData = SubPageData()
            )
        }
        loadFirstPage(UserCollectCategory.HOBBY)
        persistHobbySortToPrefs()
    }

    /**
     * 选择周边细分子分类。
     */
    fun selectSubCategory(subCategoryId: Int?) {
        if (_uiState.value.subCategoryId == subCategoryId) return
        _uiState.update {
            it.copy(
                subCategoryId = subCategoryId,
                hobbyData = SubPageData()
            )
        }
        loadFirstPage(UserCollectCategory.HOBBY)
        refreshTagPoolIfSheetOpen()
    }

    /**
     * 选择/反选标签过滤。
     */
    fun selectTag(tag: String?) {
        val newTag = if (_uiState.value.selectedTag == tag) null else tag
        _uiState.update {
            it.copy(
                selectedTag = newTag,
                hobbyData = SubPageData()
            )
        }
        loadFirstPage(UserCollectCategory.HOBBY)
    }

    /**
     * 重置所有筛选条件（子分类与标签）。
     */
    fun resetFilter() {
        if (_uiState.value.subCategoryId == null && _uiState.value.selectedTag == null) return
        _uiState.update {
            it.copy(
                subCategoryId = null,
                selectedTag = null,
                hobbyData = SubPageData()
            )
        }
        loadFirstPage(UserCollectCategory.HOBBY)
    }

    /**
     * 控制筛选抽屉显隐。**打开时按当前收藏状态与细分类懒加载标签候选池**
     * （原版 `UserCollectHobbyFragment.java:593-611` 每次开弹层带 `categoryId` + `collectState` 重拉）。
     */
    fun setFilterSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showFilterSheet = visible) }
        if (visible) {
            loadAvailableTags(force = true)
        }
    }

    /**
     * 按当前收藏状态与细分类懒加载周边标签候选池。
     *
     * @param force 为 true 时无视已有缓存强制重拉（开筛选弹层时使用）。
     */
    fun loadAvailableTags(force: Boolean = false) {
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
            loadAvailableTags(force = true)
        }
    }

    // ==================== 通用 4 页排序 ====================

    /**
     * 切换通用 4 页（厂商/系列/角色/人物）排序：收藏时间 / 人气。
     * 白名单外（含历史遗留的 `orderTime`）一律拒绝。
     */
    fun switchCommonOrder(newOrder: String) {
        if (!CommonOrderOption.isValid(newOrder)) return
        if (_uiState.value.commonOrder == newOrder) return
        val isCurrentCommon = _uiState.value.selectedCategory in COMMON_CATEGORIES
        _uiState.update {
            it.copy(
                commonOrder = newOrder,
                vendorData = SubPageData(),
                seriesData = SubPageData(),
                characterData = SubPageData(),
                personData = SubPageData()
            )
        }
        if (isCurrentCommon) {
            loadFirstPage(_uiState.value.selectedCategory)
        }
    }

    // ==================== 相册页排序 ====================

    /**
     * 切换相册排序：发布时间 / 收藏时间 / 点赞量。
     */
    fun switchAlbumOrder(newOrder: String) {
        if (!MediaOrderOption.isValid(newOrder)) return
        if (_uiState.value.albumOrder == newOrder) return
        _uiState.update { it.copy(albumOrder = newOrder, albumData = SubPageData()) }
        if (_uiState.value.selectedCategory == UserCollectCategory.ALBUM) {
            loadFirstPage(UserCollectCategory.ALBUM)
        }
    }

    // ==================== 图片页排序 ====================

    /**
     * 切换图片排序：发布时间 / 收藏时间 / 点赞量。
     */
    fun switchPicOrder(newOrder: String) {
        if (!MediaOrderOption.isValid(newOrder)) return
        if (_uiState.value.picOrder == newOrder) return
        _uiState.update { it.copy(picOrder = newOrder, picData = SubPageData()) }
        if (_uiState.value.selectedCategory == UserCollectCategory.PICTURE) {
            loadFirstPage(UserCollectCategory.PICTURE)
        }
    }

    // ==================== 作品页（独立实现）====================

    /**
     * 切换作品收藏状态（想看=1 / 在看=2 / 看过=3）。
     */
    fun switchWorksState(newState: Int) {
        if (!WorksStateOption.isValid(newState)) return
        if (_uiState.value.worksState == newState) return
        stateSet("worksState", newState)
        _uiState.update { it.copy(worksState = newState, worksData = SubPageData()) }
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadWorksFirstPage()
        }
    }

    /**
     * 切换作品页收藏时间升降序。
     */
    fun switchWorksOrderAsc(isAsc: Boolean) {
        if (_uiState.value.worksOrderAsc == isAsc) return
        stateSet("worksOrderAsc", isAsc)
        _uiState.update { it.copy(worksOrderAsc = isAsc, worksData = SubPageData()) }
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadWorksFirstPage()
        }
    }

    /**
     * 选择/反选作品页标签过滤。
     */
    fun selectWorksTag(tag: String?) {
        val newTag = if (_uiState.value.worksTag == tag) null else tag
        _uiState.update { it.copy(worksTag = newTag, worksData = SubPageData()) }
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadWorksFirstPage()
        }
    }

    /**
     * 重置作品页标签筛选。
     */
    fun resetWorksTag() {
        if (_uiState.value.worksTag == null) return
        _uiState.update { it.copy(worksTag = null, worksData = SubPageData()) }
        if (_uiState.value.selectedCategory == UserCollectCategory.WORKS) {
            loadWorksFirstPage()
        }
    }

    /**
     * 控制作品页筛选弹层显隐，打开时懒加载作品标签候选池
     * （原版池来自 `api/user/glean/textTag/get`，与手办池不是同一个）。
     */
    fun setWorksFilterSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showWorksFilterSheet = visible) }
        if (visible) {
            loadWorksAvailableTags(force = true)
        }
    }

    /**
     * 懒加载作品页标签候选池。
     */
    fun loadWorksAvailableTags(force: Boolean = false) {
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

    /**
     * 加载作品页第一页（独立于通用 4 页的请求形态：`type="works"` + `state` + `isAsc` + `tagValue`）。
     */
    fun loadWorksFirstPage() {
        fetchWorksFirstPageInternal(isRefresh = false)
    }

    /**
     * 加载作品页下一页。
     */
    fun loadWorksNextPage() {
        if (userId <= 0L) return
        val current = _uiState.value.worksData
        if (current.isLoading || current.isRefreshing || current.isPaging || !current.hasMore) return
        val nextPage = current.page + 1
        cancelFetchJob(UserCollectCategory.WORKS)
        fetchJobs[UserCollectCategory.WORKS] = viewModelScope.launch {
            setSubPagePaging(UserCollectCategory.WORKS, isPaging = true)
            fetchWorks(page = nextPage).fold(
                onSuccess = { newItems ->
                    updateCommonData(UserCollectCategory.WORKS) { currentData ->
                        currentData.appendPagedItems(newItems, nextPage) { it.id }
                    }
                },
                onFailure = { error ->
                    updateCommonData(UserCollectCategory.WORKS) { it.toPagingFailed(error) }
                }
            )
        }
    }

    private suspend fun fetchWorks(page: Int) = userRepository.getUserCollectCommonList(
        userId = userId,
        categoryId = UserCollectCategory.WORKS.key,
        type = UserCollectCategory.WORKS.apiType,
        order = CommonOrderOption.ACTION_DATE,
        page = page,
        pageSize = PAGE_SIZE,
        state = _uiState.value.worksState,
        isAsc = _uiState.value.worksOrderAsc,
        tagValue = _uiState.value.worksTag
    )

    // ==================== 批量取消收藏 ====================

    /**
     * 进入/退出多选模式。周边页与相册页各持一套独立选中集合，切换互不影响。
     */
    fun toggleSelectionMode() {
        when (_uiState.value.selectedCategory) {
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
    fun toggleSelect(id: Long) {
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
     * 全选当前大类已加载的全部条目。
     */
    fun selectAll() {
        when (_uiState.value.selectedCategory) {
            UserCollectCategory.HOBBY -> {
                val allIds = _uiState.value.hobbyData.items.map { it.id }.toSet()
                _uiState.update { it.copy(hobbySelectedIds = allIds, hobbySelectionMode = allIds.isNotEmpty()) }
            }
            UserCollectCategory.ALBUM -> {
                val allIds = _uiState.value.albumData.items.map { it.id }.toSet()
                _uiState.update { it.copy(albumSelectedIds = allIds, albumSelectionMode = allIds.isNotEmpty()) }
            }
            else -> Unit
        }
    }

    /**
     * 清空当前大类的选中集合并退出多选模式。
     */
    fun clearSelection() {
        when (_uiState.value.selectedCategory) {
            UserCollectCategory.HOBBY -> _uiState.update { it.copy(hobbySelectedIds = emptySet(), hobbySelectionMode = false) }
            UserCollectCategory.ALBUM -> _uiState.update { it.copy(albumSelectedIds = emptySet(), albumSelectionMode = false) }
            else -> Unit
        }
    }

    /**
     * 批量取消收藏（`api/collect/batch/cancel`）。仅本人可见可用。
     */
    fun cancelSelectedCollect() {
        val category = _uiState.value.selectedCategory
        // 仅周边页与相册页有批量取消收藏能力
        if (category != UserCollectCategory.HOBBY && category != UserCollectCategory.ALBUM) return
        if (!_uiState.value.isSelf) {
            _uiState.update { it.copy(batchCancelError = "仅可管理自己的收藏") }
            return
        }
        val isHobby = category == UserCollectCategory.HOBBY
        val ids = if (isHobby) _uiState.value.hobbySelectedIds else _uiState.value.albumSelectedIds
        if (ids.isEmpty() || _uiState.value.isCancellingBatch) return

        _uiState.update { it.copy(isCancellingBatch = true, batchCancelError = null) }
        viewModelScope.launch {
            collectRepository.cancelCollectBatch(ids.toList())
                .onSuccess {
                    val cancelled = ids
                    _uiState.update { state ->
                        if (isHobby) {
                            state.copy(
                                isCancellingBatch = false,
                                hobbySelectedIds = emptySet(),
                                hobbySelectionMode = false,
                                hobbyData = state.hobbyData.copy(items = state.hobbyData.items.filterNot { it.id in cancelled })
                            )
                        } else {
                            state.copy(
                                isCancellingBatch = false,
                                albumSelectedIds = emptySet(),
                                albumSelectionMode = false,
                                albumData = state.albumData.copy(items = state.albumData.items.filterNot { it.id in cancelled })
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
    fun dismissBatchCancelError() {
        _uiState.update { it.copy(batchCancelError = null) }
    }

    private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

    // ==================== 分页装载 ====================

    /**
     * 加载指定大类（默认当前）的第一页。
     */
    fun loadFirstPage(category: UserCollectCategory = _uiState.value.selectedCategory) {
        fetchFirstPage(category, isRefresh = false)
    }

    /**
     * 下拉刷新指定大类（默认当前）的数据。
     */
    fun refresh(category: UserCollectCategory = _uiState.value.selectedCategory) {
        fetchFirstPage(category, isRefresh = true)
    }

    /**
     * 加载指定大类（默认当前）的下一页。
     */
    fun loadNextPage(category: UserCollectCategory = _uiState.value.selectedCategory) {
        if (userId <= 0L) return
        // 作品页请求形态独立，走专用分支
        if (category == UserCollectCategory.WORKS) {
            loadWorksNextPage()
            return
        }
        val currentSubPage = getSubPageData(category)
        if (currentSubPage.isLoading || currentSubPage.isRefreshing || currentSubPage.isPaging || !currentSubPage.hasMore) {
            return
        }
        val nextPage = currentSubPage.page + 1
        cancelFetchJob(category)
        fetchJobs[category] = viewModelScope.launch {
            setSubPagePaging(category, isPaging = true)
            when (category) {
                UserCollectCategory.HOBBY -> {
                    val result = userRepository.getUserCollectHobbyList(
                        userId = userId,
                        collectState = _uiState.value.collectState,
                        category = _uiState.value.subCategoryId ?: DEFAULT_HOBBY_KEY,
                        tag = _uiState.value.selectedTag,
                        order = _uiState.value.order,
                        sortType = _uiState.value.sortType,
                        page = nextPage,
                        pageSize = PAGE_SIZE
                    )
                    result.fold(
                        onSuccess = { newItems ->
                            _uiState.update { current ->
                                current.copy(
                                    hobbyData = current.hobbyData.appendPagedItems(newItems, nextPage) { it.id to it.actualItemId }
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update { current ->
                                current.copy(hobbyData = current.hobbyData.toPagingFailed(error))
                            }
                        }
                    )
                }
                UserCollectCategory.ALBUM -> {
                    val result = userRepository.getUserCollectAlbumList(
                        userId = userId,
                        type = _uiState.value.albumType,
                        order = _uiState.value.albumOrder,
                        page = nextPage,
                        pageSize = PAGE_SIZE
                    )
                    result.fold(
                        onSuccess = { newItems ->
                            _uiState.update { current ->
                                current.copy(
                                    albumData = current.albumData.appendPagedItems(newItems, nextPage) { it.id }
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update { current ->
                                current.copy(albumData = current.albumData.toPagingFailed(error))
                            }
                        }
                    )
                }
                UserCollectCategory.PICTURE -> {
                    val result = userRepository.getUserCollectPicList(
                        userId = userId,
                        order = _uiState.value.picOrder,
                        page = nextPage,
                        pageSize = PAGE_SIZE
                    )
                    result.fold(
                        onSuccess = { newItems ->
                            _uiState.update { current ->
                                current.copy(
                                    picData = current.picData.appendPagedItems(newItems, nextPage) { it.id }
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update { current ->
                                current.copy(picData = current.picData.toPagingFailed(error))
                            }
                        }
                    )
                }
                UserCollectCategory.VENDOR,
                UserCollectCategory.SERIES,
                UserCollectCategory.CHARACTER,
                UserCollectCategory.PERSON -> {
                    val result = fetchCommon(category, page = nextPage)
                    result.fold(
                        onSuccess = { newItems ->
                            updateCommonData(category) { current ->
                                current.appendPagedItems(newItems, nextPage) { it.id to it.actualItemId }
                            }
                        },
                        onFailure = { error ->
                            updateCommonData(category) { current ->
                                current.toPagingFailed(error)
                            }
                        }
                    )
                }
                // 作品页走独立的 loadWorksNextPage
                UserCollectCategory.WORKS -> Unit
            }
        }
    }

    /**
     * 统一拉取指定分类的第一页（支持初次加载与下拉刷新）。
     */
    private fun fetchFirstPage(category: UserCollectCategory, isRefresh: Boolean) {
        if (userId <= 0L) return
        // 作品页请求形态独立，走专用分支
        if (category == UserCollectCategory.WORKS) {
            fetchWorksFirstPageInternal(isRefresh)
            return
        }
        cancelFetchJob(category)
        fetchJobs[category] = viewModelScope.launch {
            setSubPageLoading(category, isLoading = !isRefresh, isRefreshing = isRefresh)
            when (category) {
                UserCollectCategory.HOBBY -> {
                    val result = userRepository.getUserCollectHobbyList(
                        userId = userId,
                        collectState = _uiState.value.collectState,
                        category = _uiState.value.subCategoryId ?: DEFAULT_HOBBY_KEY,
                        tag = _uiState.value.selectedTag,
                        order = _uiState.value.order,
                        sortType = _uiState.value.sortType,
                        page = 1,
                        pageSize = PAGE_SIZE
                    )
                    result.fold(
                        onSuccess = { items ->
                            _uiState.update {
                                it.copy(
                                    hobbyData = SubPageData(
                                        items = items,
                                        isLoading = false,
                                        isRefreshing = false,
                                        hasMore = items.size >= PAGE_SIZE,
                                        page = 1
                                    )
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    hobbyData = it.hobbyData.copy(
                                        isLoading = false,
                                        isRefreshing = false,
                                        errorMessage = error.toDisplayMessage(isRefresh, "加载手办收藏失败"),
                                        isContentHidden = error.isContentHiddenError()
                                    )
                                )
                            }
                        }
                    )
                }
                UserCollectCategory.ALBUM -> {
                    val result = userRepository.getUserCollectAlbumList(
                        userId = userId,
                        type = _uiState.value.albumType,
                        order = _uiState.value.albumOrder,
                        page = 1,
                        pageSize = PAGE_SIZE
                    )
                    result.fold(
                        onSuccess = { items ->
                            _uiState.update {
                                it.copy(
                                    albumData = SubPageData(
                                        items = items,
                                        isLoading = false,
                                        isRefreshing = false,
                                        hasMore = items.size >= PAGE_SIZE,
                                        page = 1
                                    )
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    albumData = it.albumData.copy(
                                        isLoading = false,
                                        isRefreshing = false,
                                        errorMessage = error.toDisplayMessage(isRefresh, "加载相册收藏失败"),
                                        isContentHidden = error.isContentHiddenError()
                                    )
                                )
                            }
                        }
                    )
                }
                UserCollectCategory.PICTURE -> {
                    val result = userRepository.getUserCollectPicList(
                        userId = userId,
                        order = _uiState.value.picOrder,
                        page = 1,
                        pageSize = PAGE_SIZE
                    )
                    result.fold(
                        onSuccess = { items ->
                            _uiState.update {
                                it.copy(
                                    picData = SubPageData(
                                        items = items,
                                        isLoading = false,
                                        isRefreshing = false,
                                        hasMore = items.size >= PAGE_SIZE,
                                        page = 1
                                    )
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    picData = it.picData.copy(
                                        isLoading = false,
                                        isRefreshing = false,
                                        errorMessage = error.toDisplayMessage(isRefresh, "加载图片收藏失败"),
                                        isContentHidden = error.isContentHiddenError()
                                    )
                                )
                            }
                        }
                    )
                }
                UserCollectCategory.VENDOR,
                UserCollectCategory.SERIES,
                UserCollectCategory.CHARACTER,
                UserCollectCategory.PERSON -> {
                    val result = fetchCommon(category, page = 1)
                    result.fold(
                        onSuccess = { items ->
                            updateCommonData(category) {
                                SubPageData(
                                    items = items,
                                    isLoading = false,
                                    isRefreshing = false,
                                    hasMore = items.size >= PAGE_SIZE,
                                    page = 1
                                )
                            }
                        },
                        onFailure = { error ->
                            updateCommonData(category) { current ->
                                current.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    errorMessage = error.toDisplayMessage(isRefresh, "加载${category.title}收藏失败"),
                                    isContentHidden = error.isContentHiddenError()
                                )
                            }
                        }
                    )
                }
                // 作品页已在方法入口分流
                UserCollectCategory.WORKS -> Unit
            }
        }
    }

    /**
     * 作品页第一页装载（区分下拉刷新与首次加载的 loading 语义）。
     */
    private fun fetchWorksFirstPageInternal(isRefresh: Boolean) {
        if (userId <= 0L) return
        cancelFetchJob(UserCollectCategory.WORKS)
        fetchJobs[UserCollectCategory.WORKS] = viewModelScope.launch {
            setSubPageLoading(UserCollectCategory.WORKS, isLoading = !isRefresh, isRefreshing = isRefresh)
            fetchWorks(page = 1).fold(
                onSuccess = { items ->
                    updateCommonData(UserCollectCategory.WORKS) {
                        SubPageData(
                            items = items,
                            isLoading = false,
                            isRefreshing = false,
                            hasMore = items.size >= PAGE_SIZE,
                            page = 1
                        )
                    }
                },
                onFailure = { error ->
                    updateCommonData(UserCollectCategory.WORKS) { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.toDisplayMessage(isRefresh, "加载作品收藏失败"),
                            isContentHidden = error.isContentHiddenError()
                        )
                    }
                }
            )
        }
    }

    private suspend fun fetchCommon(category: UserCollectCategory, page: Int) =
        userRepository.getUserCollectCommonList(
            userId = userId,
            categoryId = category.key,
            type = category.apiType,
            order = _uiState.value.commonOrder,
            page = page,
            pageSize = PAGE_SIZE
        )

    /**
     * 清除指定大类（默认当前）的错误提示。
     */
    fun dismissError(category: UserCollectCategory = _uiState.value.selectedCategory) {
        when (category) {
            UserCollectCategory.HOBBY -> _uiState.update {
                it.copy(hobbyData = it.hobbyData.copy(errorMessage = null, isContentHidden = false))
            }
            UserCollectCategory.ALBUM -> _uiState.update {
                it.copy(albumData = it.albumData.copy(errorMessage = null, isContentHidden = false))
            }
            UserCollectCategory.PICTURE -> _uiState.update {
                it.copy(picData = it.picData.copy(errorMessage = null, isContentHidden = false))
            }
            else -> updateCommonData(category) { it.copy(errorMessage = null, isContentHidden = false) }
        }
    }

    // ==================== 内部工具 ====================

    private fun getSubPageData(category: UserCollectCategory): SubPageData<*> {
        return _uiState.value.subPageData(category)
    }

    private fun cancelFetchJob(category: UserCollectCategory) {
        fetchJobs.remove(category)?.cancel()
    }

    private fun setSubPageLoading(category: UserCollectCategory, isLoading: Boolean, isRefreshing: Boolean) {
        when (category) {
            UserCollectCategory.HOBBY -> _uiState.update {
                it.copy(hobbyData = it.hobbyData.copy(isLoading = isLoading, isRefreshing = isRefreshing, errorMessage = null))
            }
            UserCollectCategory.ALBUM -> _uiState.update {
                it.copy(albumData = it.albumData.copy(isLoading = isLoading, isRefreshing = isRefreshing, errorMessage = null))
            }
            UserCollectCategory.PICTURE -> _uiState.update {
                it.copy(picData = it.picData.copy(isLoading = isLoading, isRefreshing = isRefreshing, errorMessage = null))
            }
            else -> updateCommonData(category) {
                it.copy(isLoading = isLoading, isRefreshing = isRefreshing, errorMessage = null)
            }
        }
    }

    private fun setSubPagePaging(category: UserCollectCategory, isPaging: Boolean) {
        when (category) {
            UserCollectCategory.HOBBY -> _uiState.update {
                it.copy(hobbyData = it.hobbyData.copy(isPaging = isPaging, errorMessage = null))
            }
            UserCollectCategory.ALBUM -> _uiState.update {
                it.copy(albumData = it.albumData.copy(isPaging = isPaging, errorMessage = null))
            }
            UserCollectCategory.PICTURE -> _uiState.update {
                it.copy(picData = it.picData.copy(isPaging = isPaging, errorMessage = null))
            }
            else -> updateCommonData(category) {
                it.copy(isPaging = isPaging, errorMessage = null)
            }
        }
    }

    private fun updateCommonData(
        category: UserCollectCategory,
        transform: (SubPageData<UserCollectCommonItem>) -> SubPageData<UserCollectCommonItem>
    ) {
        _uiState.update { state ->
            when (category) {
                UserCollectCategory.VENDOR -> state.copy(vendorData = transform(state.vendorData))
                UserCollectCategory.SERIES -> state.copy(seriesData = transform(state.seriesData))
                UserCollectCategory.WORKS -> state.copy(worksData = transform(state.worksData))
                UserCollectCategory.CHARACTER -> state.copy(characterData = transform(state.characterData))
                UserCollectCategory.PERSON -> state.copy(personData = transform(state.personData))
                else -> state
            }
        }
    }

    /**
     * 登录用户 id 与当前浏览用户 id 相同即为「本人」，仅本人可见写操作入口。
     */
    private fun resolveIsSelf(targetUserId: Long): Boolean {
        val loginId = tokenManager.currentUserId
        return loginId > 0L && loginId == targetUserId
    }

    private fun Throwable.toDisplayMessage(isRefresh: Boolean, fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: if (isRefresh) "刷新失败" else fallback

    /**
     * 识别后端「用户已隐藏内容」语义（原版 `text_empty_user_hide` / `text_empty_user_hide_content`），
     * 供 UI 渲染专用空态而非通用错误态。
     */
    private fun Throwable.isContentHiddenError(): Boolean =
        message?.contains(CONTENT_HIDDEN_KEYWORD) == true

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

        /** 周边「全部」细分类（原版以 10000 表示，而非 null） */
        const val DEFAULT_HOBBY_KEY = 10000

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
    }

    /**
     * 合并分页新数据：执行主键去重、更新当前页码并根据批次容量判定是否还有更多数据。
     */
    private fun <T> SubPageData<T>.appendPagedItems(
        newItems: List<T>,
        nextPage: Int,
        pageSize: Int = PAGE_SIZE,
        keySelector: (T) -> Any
    ): SubPageData<T> {
        val existingKeys = items.map(keySelector).toSet()
        val unique = newItems.filterNot { keySelector(it) in existingKeys }
        return copy(
            items = items + unique,
            isPaging = false,
            page = nextPage,
            hasMore = newItems.size >= pageSize,
            errorMessage = null,
            isContentHidden = false
        )
    }

    /**
     * 分页失败状态流转。
     */
    private fun <T> SubPageData<T>.toPagingFailed(
        error: Throwable,
        fallback: String = "加载下一页失败"
    ): SubPageData<T> = copy(
        isPaging = false,
        errorMessage = error.toDisplayMessage(isRefresh = false, fallback = fallback)
    )
}
