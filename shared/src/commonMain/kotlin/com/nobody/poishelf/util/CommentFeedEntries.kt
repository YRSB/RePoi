package com.nobody.poishelf.util

import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.ui.common.comment.CommentFilterMode

sealed interface CommentFeedEntry {
    data object Header : CommentFeedEntry
    data object Skeleton : CommentFeedEntry
    data class HotComment(val key: String, val comment: CommentItem, val showDivider: Boolean = true) : CommentFeedEntry
    data object LatestDivider : CommentFeedEntry
    data class LatestComment(val key: String, val comment: CommentItem) : CommentFeedEntry
    data class Empty(val key: String, val text: String) : CommentFeedEntry
    data class PagedFooter(
        val key: String,
        val isLoadingMore: Boolean,
        val hasMore: Boolean,
        val hasAny: Boolean,
        val errorMessage: String?
    ) : CommentFeedEntry
    data class HotFooter(val hotCount: Int) : CommentFeedEntry
}

fun buildCommentFeedEntries(
    filterMode: CommentFilterMode,
    hotComments: List<CommentItem>,
    latestComments: List<CommentItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    commentsError: String?,
    showHeader: Boolean = true
): List<CommentFeedEntry> {
    val entries = mutableListOf<CommentFeedEntry>()
    if (showHeader) entries.add(CommentFeedEntry.Header)
    if (isLoading && hotComments.isEmpty() && latestComments.isEmpty()) {
        entries.add(CommentFeedEntry.Skeleton)
        return entries
    }
    when (filterMode) {
        CommentFilterMode.DEFAULT -> {
            val topHot = hotComments.take(COMMENT_HOT_PREVIEW_LIMIT)
            if (topHot.isNotEmpty()) {
                val hasLatest = latestComments.isNotEmpty()
                topHot.forEachIndexed { index, it ->
                    val isLast = index == topHot.lastIndex
                    val showDivider = !(isLast && hasLatest)
                    entries.add(CommentFeedEntry.HotComment("default_hot_${it.id}", it, showDivider))
                }
                if (hasLatest) entries.add(CommentFeedEntry.LatestDivider)
            }
            if (latestComments.isNotEmpty()) {
                latestComments.forEach { entries.add(CommentFeedEntry.LatestComment("default_latest_${it.id}", it)) }
            } else if (topHot.isEmpty()) {
                entries.add(CommentFeedEntry.Empty("empty_default_comments", "暂无评论，快来抢占第一条讨论吧 (・ω・)"))
            }
            entries.add(
                CommentFeedEntry.PagedFooter(
                    key = "default_comments_footer",
                    isLoadingMore = isLoadingMore,
                    hasMore = hasMore,
                    hasAny = topHot.isNotEmpty() || latestComments.isNotEmpty(),
                    errorMessage = commentsError
                )
            )
        }
        CommentFilterMode.HOT -> {
            if (hotComments.isNotEmpty()) {
                hotComments.forEachIndexed { index, it ->
                    val isLast = index == hotComments.lastIndex
                    entries.add(CommentFeedEntry.HotComment("hot_only_${it.id}", it, !isLast))
                }
                entries.add(CommentFeedEntry.HotFooter(hotComments.size))
            } else if (commentsError != null) {
                entries.add(
                    CommentFeedEntry.PagedFooter(
                        key = "hot_comments_footer",
                        isLoadingMore = isLoadingMore,
                        hasMore = hasMore,
                        hasAny = false,
                        errorMessage = commentsError
                    )
                )
            } else {
                entries.add(CommentFeedEntry.Empty("empty_hot_comments", "暂无热门神评，多点点赞产生第一条神评吧 (・ω・)"))
            }
        }
        CommentFilterMode.LATEST -> {
            if (latestComments.isNotEmpty()) {
                latestComments.forEach { entries.add(CommentFeedEntry.LatestComment("latest_only_${it.id}", it)) }
                entries.add(
                    CommentFeedEntry.PagedFooter(
                        key = "latest_comments_footer",
                        isLoadingMore = isLoadingMore,
                        hasMore = hasMore,
                        hasAny = true,
                        errorMessage = commentsError
                    )
                )
            } else if (commentsError != null) {
                entries.add(
                    CommentFeedEntry.PagedFooter(
                        key = "latest_comments_footer",
                        isLoadingMore = isLoadingMore,
                        hasMore = hasMore,
                        hasAny = false,
                        errorMessage = commentsError
                    )
                )
            } else {
                entries.add(CommentFeedEntry.Empty("empty_latest_comments", "暂无最新评论，快来发表讨论吧 (・ω・)"))
            }
        }
    }
    return entries
}

const val COMMENT_HOT_PREVIEW_LIMIT = 5
