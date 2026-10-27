package com.nobody.repoi.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.AlbumOrder
import com.nobody.repoi.data.remote.model.AlbumZoneTab
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.pagedChunkLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
data class RankingSection(
    val title: String,
    val albums: List<AlbumListItem> = emptyList(),
    val moreParam: AlbumRankListNavParam
)

internal data class RankWindows(
    val month: Pair<String, String>,
    val week: Pair<String, String>,
    val year: Pair<String, String>
)

internal fun rankWindows(today: LocalDate): RankWindows {
    val monthFirst = LocalDate(today.year, today.month, 1)
    val monthLast = monthFirst.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val monday = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
    val sunday = monday.plus(6, DateTimeUnit.DAY)
    val yearFirst = LocalDate(today.year, 1, 1)
    val yearLast = LocalDate(today.year, 12, 31)
    return RankWindows(
        month = Pair(formatRankTime(monthFirst, false), formatRankTime(monthLast, true)),
        week = Pair(formatRankTime(monday, false), formatRankTime(sunday, true)),
        year = Pair(formatRankTime(yearFirst, false), formatRankTime(yearLast, true))
    )
}

private fun formatRankTime(date: LocalDate, endOfDay: Boolean): String {
    val day = "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    return if (endOfDay) "$day 23:59:59" else "$day 00:00:00"
}
data class AlbumUiState(
    val tabs: List<AlbumZoneTab> = listOf(AlbumZoneTab.ALL),
    val selectedTab: AlbumZoneTab = AlbumZoneTab.ALL,
    val order: AlbumOrder = AlbumOrder.Recommend,
    val showRanking: Boolean = false,
    val isTabsLoading: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val items: List<AlbumListItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val sections: List<RankingSection> = emptyList(),
    val isSectionsLoading: Boolean = false,
    val loginRequired: Boolean = false,
    val isFollowEmpty: Boolean = false
)

class AlbumViewModel constructor(
    private val repository: AlbumRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    private var tabsJob: Job? = null
    private var rankingJob: Job? = null
    var savedScrollIndex: Int = 0
        private set
    var savedScrollOffset: Int = 0
        private set

    private val loader: PagedListLoader<AlbumListItem> = pagedChunkLoader(
        scope = viewModelScope,
        pageSize = 24,
        keySelector = { it.targetAlbumId }
    ) { page, _ ->
        val snapshot = _uiState.value
        val requestTab = snapshot.selectedTab
        val requestOrder = snapshot.order
        val result = repository.getAlbums(
            page = page,
            order = requestOrder,
            zones = requestTab.zoneIds.ifBlank { null }
        )
        result.onSuccess {
            _uiState.update { it.copy(loginRequired = false) }
        }.onFailure { error ->
            if ((error as? ApiException)?.code == 4001) {
                _uiState.update { it.copy(loginRequired = true) }
            }
        }.map { PageChunk(items = it.items, hasMore = it.hasMore) }
    }

    init {
        viewModelScope.launch {
            loader.state.collect { listState ->
                _uiState.update { state ->
                    if (state.showRanking) return@update state
                    val nextLoginRequired = if (listState.items.isNotEmpty()) false else state.loginRequired
                    state.copy(
                        items = listState.items,
                        page = listState.page,
                        hasMore = listState.hasMore,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isAppending = listState.isLoadingMore,
                        errorMessage = listState.errorMessage,
                        loginRequired = nextLoginRequired
                    )
                }
            }
        }
    }

    fun saveScrollPosition(index: Int, offset: Int) {
        savedScrollIndex = index
        savedScrollOffset = offset
    }

    fun resetScrollPosition() {
        savedScrollIndex = 0
        savedScrollOffset = 0
    }

    fun loadInitial() {
        if (!uiState.value.isTabsLoading && uiState.value.items.isNotEmpty()) return
        tabsJob?.cancel()
        tabsJob = viewModelScope.launch {
            _uiState.update { it.copy(isTabsLoading = true) }
            val tabs = repository.getZoneTabs().getOrDefault(listOf(AlbumZoneTab.ALL))
            _uiState.update { it.copy(tabs = tabs, isTabsLoading = false) }
            if (!uiState.value.showRanking) loader.loadInitial()
        }
    }

    fun selectZone(tab: AlbumZoneTab) {
        if (tab == uiState.value.selectedTab && !uiState.value.showRanking) return
        tabsJob?.cancel()
        rankingJob?.cancel()
        if (isFollowGated(tab)) {
            loader.reset(clearItems = true)
            resetScrollPosition()
            _uiState.update {
                it.copy(
                    selectedTab = tab,
                    showRanking = false,
                    isSectionsLoading = false,
                    errorMessage = null,
                    loginRequired = true,
                    isFollowEmpty = false
                )
            }
            return
        }
        if (tab.key == "follow" && tab.zoneIds.isBlank()) {
            loader.reset(clearItems = true)
            resetScrollPosition()
            _uiState.update {
                it.copy(
                    selectedTab = tab,
                    showRanking = false,
                    isSectionsLoading = false,
                    errorMessage = null,
                    loginRequired = false,
                    isFollowEmpty = true
                )
            }
            // 关注分区为空时保持空态，不发起列表请求
            return
        }
        _uiState.update {
            it.copy(
                selectedTab = tab,
                showRanking = false,
                loginRequired = false,
                isFollowEmpty = false
            )
        }
        loader.loadInitial()
    }

    fun selectOrder(order: AlbumOrder) {
        if (order == uiState.value.order && !uiState.value.showRanking) return
        tabsJob?.cancel()
        rankingJob?.cancel()
        resetScrollPosition()
        _uiState.update {
            it.copy(
                order = order,
                showRanking = false,
                loginRequired = false,
                isFollowEmpty = false
            )
        }
        loader.loadInitial()
    }

    fun enterRanking() {
        if (uiState.value.showRanking) return
        tabsJob?.cancel()
        rankingJob?.cancel()
        resetScrollPosition()
        rankingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showRanking = true,
                    isSectionsLoading = true,
                    loginRequired = false,
                    isFollowEmpty = false,
                    errorMessage = null
                )
            }
            loadRankingSections()
        }
    }

    fun refresh() {
        resetScrollPosition()
        if (uiState.value.showRanking) {
            refreshRanking()
        } else {
            loader.refresh()
        }
    }

    fun loadMore() {
        val state = uiState.value
        if (state.showRanking) return
        loader.loadMore()
    }

    fun retry() {
        if (uiState.value.showRanking) {
            refreshRanking()
        } else {
            loader.retry()
        }
    }

    private fun isFollowGated(tab: AlbumZoneTab): Boolean =
        tab.key == "follow" && tokenManager.currentToken.isBlank()

    private fun refreshRanking() {
        rankingJob?.cancel()
        rankingJob = viewModelScope.launch {
            _uiState.update { it.copy(isSectionsLoading = true, errorMessage = null) }
            loadRankingSections()
        }
    }

    private suspend fun loadRankingSections() {
        val windows = rankWindows(currentDate())
        val sections = supervisorScope {
            val weekWindow = async {
                repository.getRankingWindow(
                    releaseStart = windows.week.first,
                    releaseEnd = windows.week.second,
                    pageSize = 10
                )
            }
            val monthWindow = async {
                repository.getRankingWindow(
                    releaseStart = windows.month.first,
                    releaseEnd = windows.month.second,
                    pageSize = 10
                )
            }
            val yearWindow = async {
                repository.getRankingWindow(
                    releaseStart = windows.year.first,
                    releaseEnd = windows.year.second,
                    pageSize = 10
                )
            }
            val datePraise = async { repository.getPraiseTop(type = "").map { it.items } }
            val weekPraise = async { repository.getPraiseTop(type = "week").map { it.items } }
            val monthPraise = async { repository.getPraiseTop(type = "month").map { it.items } }
            val results = listOf(
                RankingSection(
                    title = "本周人气相册",
                    moreParam = AlbumRankListNavParam(title = "本周人气相册", releaseStart = windows.week.first, releaseEnd = windows.week.second, period = RankPeriod.WEEK)
                ) to weekWindow.await(),
                RankingSection(
                    title = "本月人气相册",
                    moreParam = AlbumRankListNavParam(title = "本月人气相册", releaseStart = windows.month.first, releaseEnd = windows.month.second, period = RankPeriod.MONTH)
                ) to monthWindow.await(),
                RankingSection(
                    title = "本年人气相册",
                    moreParam = AlbumRankListNavParam(title = "本年人气相册", releaseStart = windows.year.first, releaseEnd = windows.year.second, period = RankPeriod.YEAR)
                ) to yearWindow.await(),
                RankingSection(
                    title = "今日点赞榜",
                    moreParam = AlbumRankListNavParam(title = "今日点赞榜", praiseType = "")
                ) to datePraise.await(),
                RankingSection(
                    title = "本周点赞榜",
                    moreParam = AlbumRankListNavParam(title = "本周点赞榜", praiseType = "week")
                ) to weekPraise.await(),
                RankingSection(
                    title = "本月点赞榜",
                    moreParam = AlbumRankListNavParam(title = "本月点赞榜", praiseType = "month")
                ) to monthPraise.await()
            )
            val failed = results.count { it.second.isFailure }
            val ready = results.mapNotNull { (section, result) ->
                result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { section.copy(albums = it) }
            }
            Pair(ready, failed)
        }
        _uiState.update {
            it.copy(
                sections = sections.first,
                isSectionsLoading = false,
                errorMessage = if (sections.first.isEmpty() && sections.second > 0) "加载失败" else null
            )
        }
    }
}
