package com.nobody.repoi.ui.user.history

import com.nobody.repoi.data.remote.model.UserHistoryItem
import com.nobody.repoi.platform.nowMillis
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class HistoryDateGroup(
    val title: String,
    val dayStart: Long,
    val items: List<UserHistoryItem>
)

data class HistoryTabState(
    val items: List<UserHistoryItem> = emptyList(),
    val groups: List<HistoryDateGroup> = emptyList(),
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

data class UserHistoryUiState(
    val selectedCategory: UserHistoryCategory = UserHistoryCategory.HOBBY,
    val hobbyState: HistoryTabState = HistoryTabState(),
    val albumState: HistoryTabState = HistoryTabState(),
    val isManageMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val isDeleting: Boolean = false,
    val userMessage: String? = null
) {

    val currentTabState: HistoryTabState
        get() = when (selectedCategory) {
            UserHistoryCategory.HOBBY -> hobbyState
            UserHistoryCategory.ALBUM -> albumState
        }

    val isAllSelected: Boolean
        get() {
            val allIds = currentTabState.items.map { it.id }.toSet()
            return allIds.isNotEmpty() && selectedIds.containsAll(allIds)
        }
}

fun formatRelativeHistoryDate(dayStart: Long, now: Long = nowMillis()): String {
    if (dayStart <= 0L) return "更早"
    val diffSeconds = (now - dayStart).coerceAtLeast(0L) / 1000L
    return when {
        diffSeconds < 60L -> "刚刚"
        diffSeconds < 3600L -> "${diffSeconds / 60L}分钟前"
        diffSeconds < 86400L -> "${maxOf(1L, diffSeconds / 3600L)}小时前"
        diffSeconds < 2592000L -> "${diffSeconds / 86400L}天前"
        diffSeconds < 31536000L -> "${diffSeconds / 2592000L}个月前"
        else -> "${diffSeconds / 31536000L}年前"
    }
}

@OptIn(ExperimentalTime::class)
fun groupHistoryItemsByDay(
    items: List<UserHistoryItem>,
    now: Long = nowMillis()
): List<HistoryDateGroup> {
    if (items.isEmpty()) return emptyList()
    val timeZone = TimeZone.currentSystemDefault()
    val timeZoneOffset = timeZone.offsetAt(Instant.fromEpochMilliseconds(now)).totalSeconds * 1000L

    val grouped = linkedMapOf<Long, MutableList<UserHistoryItem>>()
    for (item in items) {
        val t = item.actualViewTime
        val dayStart = if (t > 0L) {
            t - ((t + timeZoneOffset) % 86400000L)
        } else {
            0L
        }
        grouped.getOrPut(dayStart) { mutableListOf() }.add(item)
    }

    return grouped.map { (dayStart, dayItems) ->
        HistoryDateGroup(
            title = formatRelativeHistoryDate(dayStart, now),
            dayStart = dayStart,
            items = dayItems
        )
    }
}
