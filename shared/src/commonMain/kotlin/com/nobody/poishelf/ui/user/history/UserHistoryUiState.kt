package com.nobody.poishelf.ui.user.history

import com.nobody.poishelf.data.remote.model.UserHistoryItem
import com.nobody.poishelf.platform.nowMillis
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 自然日分组历史项。
 */
data class HistoryDateGroup(
    val title: String,
    val dayStart: Long,
    val items: List<UserHistoryItem>
)

/**
 * 单个分类 Tab 的分页数据与加载状态。
 */
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

/**
 * 浏览历史界面整体 UI 状态。
 */
data class UserHistoryUiState(
    val selectedCategory: UserHistoryCategory = UserHistoryCategory.HOBBY,
    val hobbyState: HistoryTabState = HistoryTabState(),
    val albumState: HistoryTabState = HistoryTabState(),
    val isManageMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val isDeleting: Boolean = false,
    val userMessage: String? = null
) {
    /** 当前选中分类的 Tab 状态 */
    val currentTabState: HistoryTabState
        get() = when (selectedCategory) {
            UserHistoryCategory.HOBBY -> hobbyState
            UserHistoryCategory.ALBUM -> albumState
        }

    /** 当前分类所有项目是否已全选 */
    val isAllSelected: Boolean
        get() {
            val allIds = currentTabState.items.map { it.id }.toSet()
            return allIds.isNotEmpty() && selectedIds.containsAll(allIds)
        }
}

/**
 * 对齐原版 Hpoi (DateUtils.p) 的相对时间计算逻辑：
 * - < 60秒: 刚刚
 * - < 3600秒: X分钟前
 * - < 86400秒 (24小时): X小时前 (如 19小时前)
 * - < 2592000秒 (30天): X天前 (如 1天前)
 * - < 31536000秒 (365天): X个月前
 * - 其它: X年前
 */
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

/**
 * 将浏览历史项按自然日聚合（对齐原版 Hpoi 零点时间归一化逻辑）。
 *
 * KMP 移植：`TimeZone.getDefault().rawOffset`（固定标准偏移，无视 DST）→
 * `offsetAt`（当前时刻实际偏移，DST 正确；行为差异见平台文档）。
 */
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
