package com.nobody.repoi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.data.remote.model.ZoneTabsSettings
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.util.DateTimeFormats
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeFeedUiState(
    val categories: List<FeedCategory> = listOf(FeedCategory("all", "全部")),
    val selectedCategory: FeedCategory = FeedCategory("all", "全部"),
    val selectedSubType: String = "recommend",
    val items: List<ActionItem> = emptyList(),
    val lastTime: Long = 0L,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

class HomeFeedViewModel constructor(
    private val homeRepository: HomeRepository,
    private val zoneRepository: ZoneRepository,
    private val viewPreferencesManager: ViewPreferencesManager
) : ViewModel() {

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

    private val initialSubTypeKey = viewPreferencesManager.getInitialFeedSubType()
    private val initialSubType = subTypes.find { it.first == initialSubTypeKey }?.first ?: "recommend"
    private val initialTabKey = viewPreferencesManager.getInitialFeedZoneTabKey()

    private val _uiState = MutableStateFlow(
        HomeFeedUiState(
            selectedCategory = FeedCategory(
                tabKey = initialTabKey,
                name = if (initialTabKey == "follow") "关注" else "全部",
                zoneIds = if (initialTabKey == "follow") "-1" else ""
            ),
            selectedSubType = initialSubType
        )
    )
    val uiState: StateFlow<HomeFeedUiState> = _uiState.asStateFlow()

    private var initialLoadJob: Job? = null

    // 记忆滚动位置（用于 Tab 切换保活）
    var savedScrollIndex: Int = 0
        private set
    var savedScrollOffset: Int = 0
        private set

    fun saveScrollPosition(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    fun resetScrollPosition() {
        savedScrollIndex = 0
        savedScrollOffset = 0
    }

    init {
        // 监听专区与设置变化，构建动态分类列表
        viewModelScope.launch {
            combine(
                zoneRepository.focusZonesFlow,
                zoneRepository.settingsFlow
            ) { focusZones, settings ->
                buildCategories(focusZones, settings)
            }.collect { dynamicCategories ->
                _uiState.update { current ->
                    val matchedCategory = dynamicCategories.find { it.tabKey == current.selectedCategory.tabKey }
                        ?: dynamicCategories.find { it.tabKey == initialTabKey }
                        ?: dynamicCategories.firstOrNull()
                        ?: FeedCategory("all", "全部")

                    val categoryChanged = matchedCategory.tabKey != current.selectedCategory.tabKey ||
                        matchedCategory.zoneIds != current.selectedCategory.zoneIds

                    current.copy(
                        categories = dynamicCategories,
                        selectedCategory = matchedCategory
                    ).also {
                        if (categoryChanged && !current.isInitialLoading) {
                            loadInitialFeed()
                        }
                    }
                }
            }
        }

        // 启动时在后台静默同步一次服务端最新的专区与设置（若本地缓存为空也能即时拉取）
        viewModelScope.launch {
            zoneRepository.getZoneHomeTabs()
        }

        loadInitialFeed()
    }

    private fun buildCategories(
        focusZones: List<ZoneItem>,
        settings: ZoneTabsSettings
    ): List<FeedCategory> {
        val list = mutableListOf<FeedCategory>()
        if (settings.showAll) {
            list.add(FeedCategory(tabKey = "all", name = "全部", nodeId = 0L, zoneIds = ""))
        }

        // 过滤掉用户设置隐藏的专区
        val hiddenSet = settings.hiddenZoneIds.toSet()
        val visibleFocusZones = focusZones.filter { it.nodeId > 0L && it.nodeId !in hiddenSet }

        // 依据设置的排序方式进行重排
        val sortedFocusZones = when (settings.sort) {
            "custom" -> {
                val orderMap = settings.customOrder.mapIndexed { index, id -> id to index }.toMap()
                visibleFocusZones.sortedBy { orderMap[it.nodeId] ?: Int.MAX_VALUE }
            }
            "recent" -> {
                val recentMap = settings.recentZoneIds.mapIndexed { index, id -> id to index }.toMap()
                visibleFocusZones.sortedBy { recentMap[it.nodeId] ?: Int.MAX_VALUE }
            }
            else -> visibleFocusZones
        }

        val allFocusZoneIds = sortedFocusZones.map { it.nodeId }.joinToString(",")
        // 关注分类：若关注列表为空，传 "-1" 作为未关注标记
        list.add(
            FeedCategory(
                tabKey = "follow",
                name = "关注",
                nodeId = 0L,
                zoneIds = allFocusZoneIds.ifEmpty { "-1" }
            )
        )

        sortedFocusZones.forEach { zone ->
            list.add(
                FeedCategory(
                    tabKey = "z${zone.nodeId}",
                    name = zone.displayName,
                    nodeId = zone.nodeId,
                    zoneIds = zone.nodeId.toString()
                )
            )
        }
        return list
    }

    fun selectCategory(category: FeedCategory) {
        if (_uiState.value.selectedCategory.tabKey == category.tabKey) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                selectedCategory = category,
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(category.tabKey)
            viewPreferencesManager.setFeedCategory(category.id)
        }
        loadInitialFeed()
    }

    fun selectSubType(subType: String) {
        if (_uiState.value.selectedSubType == subType) return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                selectedSubType = subType,
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedSubType(subType)
        }
        loadInitialFeed()
    }

    fun resetFilters() {
        val defaultCategory = _uiState.value.categories.firstOrNull() ?: FeedCategory("all", "全部")
        if (_uiState.value.selectedCategory.tabKey == defaultCategory.tabKey && _uiState.value.selectedSubType == "recommend") return
        resetScrollPosition()
        _uiState.update {
            it.copy(
                selectedCategory = defaultCategory,
                selectedSubType = "recommend",
                items = emptyList(),
                lastTime = 0L,
                hasMore = true,
                isInitialLoading = true,
                errorMessage = null,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            viewPreferencesManager.setFeedZoneTabKey(defaultCategory.tabKey)
            viewPreferencesManager.setFeedCategory(defaultCategory.id)
            viewPreferencesManager.setFeedSubType("recommend")
        }
        loadInitialFeed()
    }

    fun refreshFeed() = refresh()

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        resetScrollPosition()
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null, loadMoreError = null) }

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

    private fun parseCursorTimestamp(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        dateStr.toLongOrNull()?.let { return it }
        return DateTimeFormats.parseDateTimeToEpochMillis(dateStr) ?: 0L
    }
}
