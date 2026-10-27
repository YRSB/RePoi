package com.nobody.repoi.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.AlbumOrder
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.pagedChunkLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 榜单周期窗口计算（kotlinx-datetime 等价重写，与老 java.time 版对齐：
 * 周一起算（ISO，周一为首）、月末/年末钳制、起止为 00:00:00/23:59:59）。
 */
@OptIn(ExperimentalTime::class)
internal fun periodWindow(period: RankPeriod, diff: Int, today: LocalDate = currentDate()): Pair<String, String> {
    return when (period) {
        RankPeriod.WEEK -> {
            val monday = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY).plus(diff * 7, DateTimeUnit.DAY)
            Pair(formatRankListTime(monday, false), formatRankListTime(monday.plus(6, DateTimeUnit.DAY), true))
        }
        RankPeriod.MONTH -> {
            val shifted = today.plus(diff, DateTimeUnit.MONTH)
            val first = LocalDate(shifted.year, shifted.month, 1)
            val last = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            Pair(formatRankListTime(first, false), formatRankListTime(last, true))
        }
        RankPeriod.YEAR -> {
            val shifted = today.plus(diff * 12, DateTimeUnit.MONTH)
            val first = LocalDate(shifted.year, 1, 1)
            val last = LocalDate(shifted.year, 12, 31)
            Pair(formatRankListTime(first, false), formatRankListTime(last, true))
        }
    }
}

@OptIn(ExperimentalTime::class)
internal fun currentDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

internal fun periodLabel(period: RankPeriod, diff: Int): String {
    val unit = when (period) {
        RankPeriod.WEEK -> "周"
        RankPeriod.MONTH -> "月"
        RankPeriod.YEAR -> "年"
    }
    val head = "本"
    return when {
        diff == 0 -> "$head$unit"
        diff == -1 -> "上$unit"
        diff == 1 -> "下$unit"
        diff < -1 -> "上${-diff}$unit"
        else -> "下${diff}$unit"
    }
}

private fun formatRankListTime(date: LocalDate, endOfDay: Boolean): String =
    "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')} " +
        if (endOfDay) "23:59:59" else "00:00:00"
data class AlbumRankListUiState(
    val title: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val items: List<AlbumListItem> = emptyList(),
    val ranks: Map<Long, Int> = emptyMap(),
    val page: Int = 1,
    val pageSize: Int = AlbumRankListViewModel.PAGE_SIZE,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val period: RankPeriod? = null,
    val diff: Int = 0,
    val periodLabel: String? = null,
    val windowStart: String? = null,
    val windowEnd: String? = null
)

class AlbumRankListViewModel constructor(
    private val repository: AlbumRepository
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 10
    }

    private val _uiState = MutableStateFlow(AlbumRankListUiState())
    val uiState: StateFlow<AlbumRankListUiState> = _uiState.asStateFlow()

    private var loadedFor: AlbumRankListNavParam? = null
    private var currentParam: AlbumRankListNavParam? = null
    private var currentRanks: Map<Long, Int> = emptyMap()

    private val loader = pagedChunkLoader<AlbumListItem>(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,
        keySelector = { it.targetAlbumId }
    ) { page, size ->
        val snapshot = _uiState.value
        val param = currentParam ?: return@pagedChunkLoader Result.success(PageChunk.empty())
        val result = if (param.praiseType != null) {
            repository.getPraiseTop(type = param.praiseType, page = page, pageSize = size)
        } else {
            repository.getAlbums(
                page = page,
                pageSize = size,
                order = AlbumOrder.Praise,
                releaseStart = snapshot.windowStart,
                releaseEnd = snapshot.windowEnd
            )
        }
        result.map { albumPage ->
            val baseRanks = if (page == 1) emptyMap() else currentRanks
            val newRanks = baseRanks.toMutableMap().apply {
                albumPage.items.forEachIndexed { index, item ->
                    putIfAbsent(item.targetAlbumId, (page - 1) * PAGE_SIZE + index + 1)
                }
            }
            currentRanks = newRanks
            PageChunk(items = albumPage.items, hasMore = albumPage.hasMore)
        }
    }

    init {
        viewModelScope.launch {
            loader.state.collect { listState ->
                _uiState.update { state ->
                    state.copy(
                        items = listState.items,
                        ranks = if (listState.items.isEmpty()) emptyMap() else currentRanks,
                        page = listState.page,
                        hasMore = listState.hasMore,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isAppending = listState.isLoadingMore,
                        errorMessage = listState.errorMessage,
                        loadMoreError = listState.loadMoreError
                    )
                }
            }
        }
    }

    fun loadInitial(param: AlbumRankListNavParam) {
        if (loadedFor == param && uiState.value.items.isNotEmpty()) return
        loadedFor = param
        currentParam = param
        val window = param.period?.let { periodWindow(it, 0) }
        _uiState.update {
            AlbumRankListUiState(
                title = param.title,
                period = param.period,
                periodLabel = param.period?.let { periodLabel(it, 0) },
                windowStart = window?.first ?: param.releaseStart,
                windowEnd = window?.second ?: param.releaseEnd,
                isLoading = true
            )
        }
        loader.loadInitial()
    }

    fun shiftPeriod(param: AlbumRankListNavParam, delta: Int) {
        val state = uiState.value
        val period = state.period ?: return
        if (param.praiseType != null) return
        val newDiff = (state.diff + delta).coerceAtMost(0)
        if (newDiff == state.diff) return
        currentParam = param
        val window = periodWindow(period, newDiff)
        _uiState.update {
            it.copy(
                diff = newDiff,
                periodLabel = periodLabel(period, newDiff),
                windowStart = window.first,
                windowEnd = window.second
            )
        }
        loader.loadInitial(clearItems = false)
    }

    fun refresh(param: AlbumRankListNavParam = currentParam ?: AlbumRankListNavParam(title = uiState.value.title)) {
        currentParam = param
        loader.refresh()
    }

    fun loadMore(param: AlbumRankListNavParam = currentParam ?: AlbumRankListNavParam(title = uiState.value.title)) {
        currentParam = param
        loader.loadMore()
    }

    fun retry(param: AlbumRankListNavParam = currentParam ?: AlbumRankListNavParam(title = uiState.value.title)) {
        currentParam = param
        loader.retry()
    }
}