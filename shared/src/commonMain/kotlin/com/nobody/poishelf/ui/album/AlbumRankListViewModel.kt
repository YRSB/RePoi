package com.nobody.poishelf.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.AlbumOrder
import com.nobody.poishelf.data.repository.AlbumRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.nobody.poishelf.util.paging.PageChunk
import com.nobody.poishelf.util.paging.pagedChunkLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
enum class RankPeriod {
    WEEK,
    MONTH,
    YEAR
}

@Serializable
data class AlbumRankListNavParam(
    val title: String,
    val releaseStart: String? = null,
    val releaseEnd: String? = null,
    val praiseType: String? = null,
    val period: RankPeriod? = null
)

@OptIn(ExperimentalTime::class)
internal fun periodWindow(period: RankPeriod, diff: Int, now: LocalDateTime): Pair<String, String> {
    val date = now.date
    return when (period) {
        RankPeriod.WEEK -> {
            // 周一起始（与原 WeekFields MONDAY,1 一致）
            val monday = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY).plus(diff * 7, DateTimeUnit.DAY)
            Pair(formatRankListTime(startOfDay(monday)), formatRankListTime(endOfDay(monday.plus(6, DateTimeUnit.DAY))))
        }
        RankPeriod.MONTH -> {
            val shifted = date.plus(DatePeriod(months = diff))
            val first = LocalDate(shifted.year, shifted.month, 1)
            val lastDay = daysInMonth(shifted.year, shifted.month.number)
            val last = LocalDate(shifted.year, shifted.month, lastDay)
            Pair(
                formatRankListTime(startOfDay(first)),
                formatRankListTime(endOfDay(last))
            )
        }
        RankPeriod.YEAR -> {
            val shifted = date.plus(DatePeriod(years = diff))
            Pair(
                formatRankListTime(startOfDay(LocalDate(shifted.year, 1, 1))),
                formatRankListTime(endOfDay(LocalDate(shifted.year, 12, 31)))
            )
        }
    }
}

private fun startOfDay(date: LocalDate): LocalDateTime = LocalDateTime(date.year, date.month, date.day, 0, 0, 0)

private fun endOfDay(date: LocalDate): LocalDateTime = LocalDateTime(date.year, date.month, date.day, 23, 59, 59)

private fun daysInMonth(year: Int, month: Int): Int {
    val first = LocalDate(year, month, 1)
    val next = first.plus(DatePeriod(months = 1))
    return first.daysUntil(next)
}

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

private fun formatRankListTime(time: LocalDateTime): String =
    "${time.year}-${time.month.number.toString().padStart(2, '0')}-${
        time.day.toString().padStart(2, '0')
    } ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}:${
        time.second.toString().padStart(2, '0')
    }"
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

class AlbumRankListViewModel(
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
        val window = param.period?.let { periodWindow(it, 0, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) }
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
        val window = periodWindow(period, newDiff, Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
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
