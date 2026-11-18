package com.nobody.repoi.ui.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.ZoneDetailData
import com.nobody.repoi.data.remote.model.ZoneInfoPayload
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.remote.model.ZoneTabItem
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.ui.board.AlbumCategoryOption
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.session.ZoneBoardIntent
import com.nobody.repoi.ui.board.session.ZoneBoardSession
import com.nobody.repoi.ui.board.session.ZoneBoardSnapshot
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 专区详情页宿主 UI 状态。
 *
 * 板块内容、分页状态机、筛选条件与滚动记忆已全部收敛至 [ZoneBoardSession]，
 * 宿主 UI 状态仅保留专区头图/关注等固有元数据、Tab 列表、相册单专区分类候选与发布弹层显隐。
 */
data class ZoneDetailUiState(
    val nodeId: Long = 0L,
    val zoneInfo: ZoneInfoPayload? = null,
    val isInfoLoading: Boolean = true,
    val isFocusToggling: Boolean = false,
    val focusState: Boolean = false,
    val followCount: Long = 0L,
    val availableTabs: List<Pair<String, String>> = listOf("action" to "动态"),
    val selectedTab: String = "action",

    // 相册单专区分类候选（GET api/zone/categories?nodeId=&itemType=album）
    val albumCategoryOptions: List<AlbumCategoryOption> = emptyList(),

    // 悬浮发布弹窗显隐
    val showPublishSheet: Boolean = false
) {
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

    companion object {
        val ENTITY_TAB_TYPES = setOf("company", "series", "works", "charactar", "person")
    }
}

/**
 * 专区详情页 ViewModel。
 *
 * 构造注入 [sessionFactory]，由深模块 [ZoneBoardSession] 接管专区 10 类子板块的
 * 分页加载、首屏懒加载、筛选不变性与滚动记忆。宿主只管理专区基础信息、关注状态、
 * 专区专属分类与发布弹层等页面级元数据。
 */
class ZoneDetailViewModel(
    private val zoneRepository: ZoneRepository,
    private val collectRepository: CollectRepository,
    private val categoryManager: CategoryManager,
    private val albumRepository: AlbumRepository,
    sessionFactory: ZoneBoardSession.Factory
) : ViewModel() {

    var nodeId: Long = 0L
        private set

    /** 当前专区查询作用域：专区详情固定单专区。 */
    val zoneScope: ZoneQueryScope
        get() = ZoneQueryScope.Single(nodeId)

    /** 子板块会话控制器（深模块）。 */
    val session: ZoneBoardSession = sessionFactory.create(
        coroutineScope = viewModelScope,
        initialScope = ZoneQueryScope.Single(nodeId),
        initialBoard = ZoneBoard.Action
    )

    /** 当前活跃板块的响应式状态快照流。 */
    val activeSnapshot: StateFlow<ZoneBoardSnapshot> = session.activeSnapshot

    /** 动态筛选项（key = 协议键值 subType，value = 2.43 中文文案）。 */
    val subTypes: List<Pair<String, String>> =
        ActionSubType.ordered.map { it.apiValue to it.label }

    val categoryTree: StateFlow<Map<Int, List<CategoryItem>>> = categoryManager.categoryMap

    private val _uiState = MutableStateFlow(ZoneDetailUiState())
    val uiState: StateFlow<ZoneDetailUiState> = _uiState.asStateFlow()

    private var albumCategoriesJob: Job? = null

    fun setNodeId(
        id: Long,
        initialName: String? = null,
        initialIcon: String? = null,
        initialCover: String? = null
    ) {
        if (id <= 0L || (nodeId == id && _uiState.value.zoneInfo != null)) return
        nodeId = id
        albumCategoriesJob?.cancel()
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
                albumCategoryOptions = emptyList(),
                showPublishSheet = false
            )
        }
        session.updateScope(ZoneQueryScope.Single(id))
        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Action))
        loadZoneInfo()
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

        if (rawTabs.isEmpty()) {
            return ZoneBoard.HOME_ORDER.map { it.type to it.label }
        }

        val boards = ZoneBoard.tabsFor(rawTabs, ZoneBoard.CLIENT_WHITELIST)
        if (boards.isEmpty()) {
            return ZoneBoard.HOME_ORDER.map { it.type to it.label }
        }

        return boards.map { board ->
            val serverTitle = rawTabs.firstOrNull { ZoneBoard.fromType(it.type) == board }?.title
            board.type to board.tabTitle(serverTitle)
        }
    }

    fun selectTab(tabType: String) {
        val board = ZoneBoard.fromType(tabType) ?: ZoneBoard.Action
        if (_uiState.value.selectedTab == board.type && session.activeSnapshot.value.board == board) return
        _uiState.update { it.copy(selectedTab = board.type) }
        session.dispatch(ZoneBoardIntent.SelectBoard(board))
        if (board == ZoneBoard.Album) {
            syncAlbumCategories()
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

    fun refresh() {
        loadZoneInfo()
        if (_uiState.value.isAlbumTab && _uiState.value.albumCategoryOptions.isEmpty()) {
            syncAlbumCategories()
        }
        session.dispatch(ZoneBoardIntent.Refresh())
    }

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
                    albumCategoryOptions = options
                )
            }
        }
    }

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

    fun showPublishSheet(show: Boolean) {
        _uiState.update { it.copy(showPublishSheet = show) }
    }

    // ── 滚动记忆委托 ──
    fun saveTabScrollPosition(tab: String, index: Int, offset: Int) {
        val board = ZoneBoard.fromType(tab) ?: return
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(board, index, offset))
    }

    fun getTabScrollPosition(tab: String): Pair<Int, Int> {
        val board = ZoneBoard.fromType(tab) ?: return Pair(0, 0)
        val pos = session.snapshotFor(board).scrollPosition
        return Pair(pos.index, pos.offset)
    }

    // ── 板块意图转发 ──
    fun selectSubType(subType: String) = session.dispatch(ZoneBoardIntent.SelectSubType(subType))
    fun selectAlbumOrder(order: String) = session.dispatch(ZoneBoardIntent.SelectAlbumOrder(order))
    fun selectAlbumCategory(categoryId: Int?) = session.dispatch(ZoneBoardIntent.SelectAlbumCategory(categoryId))
    fun selectPostsOrder(order: String) = session.dispatch(ZoneBoardIntent.SelectPostsOrder(order))
    fun setResaleType(type: Int) = session.dispatch(ZoneBoardIntent.SelectResaleType(type))
    fun setResalePurity(purity: String?) = session.dispatch(ZoneBoardIntent.SelectResalePurity(purity))
    fun updateResaleKeyword(keyword: String) = session.dispatch(ZoneBoardIntent.UpdateResaleKeyword(keyword))
    fun submitResaleSearch() = session.dispatch(ZoneBoardIntent.SubmitResaleSearch)
    fun setHobbyOrder(order: String) = session.dispatch(ZoneBoardIntent.SelectHobbyOrder(order))
    fun setHobbyYear(year: Int?) = session.dispatch(ZoneBoardIntent.SelectHobbyYear(year))
    fun setHobbyMonth(month: Int?) = session.dispatch(ZoneBoardIntent.SelectHobbyMonth(month))
    fun selectHobbyCategory(categoryId: Int, subCategoryId: Int?) =
        session.dispatch(ZoneBoardIntent.SelectHobbyCategory(categoryId, subCategoryId))
    fun applyHobbyAdvancedFilters(sex: Int?, specs: String?, scale: String?, r18: Int?) =
        session.dispatch(ZoneBoardIntent.ApplyHobbyFilter(HobbyAdvancedFilterParam(sex = sex, specs = specs, scale = scale, r18 = r18)))
    fun resetHobbyAdvancedFilters() = session.dispatch(ZoneBoardIntent.ResetHobbyFilter)
    fun toggleHobbyViewMode() = session.dispatch(ZoneBoardIntent.ToggleHobbyViewMode)
    fun openHobbyFilterSheet(open: Boolean) = session.dispatch(ZoneBoardIntent.SetHobbyFilterSheetVisible(open))
    fun openHobbyCategoryPicker(open: Boolean) = session.dispatch(ZoneBoardIntent.SetHobbyCategoryPickerVisible(open))
    fun updateHobbyKeyword(keyword: String) = session.dispatch(ZoneBoardIntent.UpdateHobbyKeyword(keyword))
    fun submitHobbySearch() = session.dispatch(ZoneBoardIntent.SubmitHobbySearch)
    fun loadNextPage() = session.dispatch(ZoneBoardIntent.LoadMore())
}
