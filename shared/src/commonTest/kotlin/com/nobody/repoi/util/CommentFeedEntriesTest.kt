package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.ui.common.comment.CommentFilterMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class CommentFeedEntriesTest {

    private fun comments(range: IntRange): List<CommentItem> {
        return range.map { CommentItem(id = it.toLong(), content = "主楼$it") }
    }

    @Test
    fun defaultFull_sequencesHeaderHotDividerLatestFooter() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.DEFAULT,
            hotComments = comments(1..8),
            latestComments = comments(11..20),
            isLoading = false,
            isLoadingMore = false,
            hasMore = true,
            commentsError = null
        )
        assertTrue(entries.first() is CommentFeedEntry.Header)
        val hot = entries.filterIsInstance<CommentFeedEntry.HotComment>()
        assertEquals(5, hot.size)
        assertTrue(hot.all { it.key.startsWith("default_hot_") })
        assertTrue(entries.any { it is CommentFeedEntry.LatestDivider })
        assertTrue(hot.dropLast(1).all { it.showDivider })
        assertFalse(hot.last().showDivider)
        val latest = entries.filterIsInstance<CommentFeedEntry.LatestComment>()
        assertEquals(10, latest.size)
        assertTrue(latest.all { it.key.startsWith("default_latest_") })
        val footer = entries.filterIsInstance<CommentFeedEntry.PagedFooter>().single()
        assertEquals("default_comments_footer", footer.key)
        assertTrue(footer.hasAny)
        assertTrue(footer.hasMore)
    }

    @Test
    fun defaultEmpty_showsEmptyAndBareFooter() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.DEFAULT,
            hotComments = emptyList(),
            latestComments = emptyList(),
            isLoading = false,
            isLoadingMore = false,
            hasMore = true,
            commentsError = null
        )
        val empty = entries.filterIsInstance<CommentFeedEntry.Empty>().single()
        assertEquals("empty_default_comments", empty.key)
        val footer = entries.filterIsInstance<CommentFeedEntry.PagedFooter>().single()
        assertFalse(footer.hasAny)
    }

    @Test
    fun loadingEmpty_returnsHeaderPlusSkeletonOnly() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.DEFAULT,
            hotComments = emptyList(),
            latestComments = emptyList(),
            isLoading = true,
            isLoadingMore = false,
            hasMore = true,
            commentsError = null
        )
        assertEquals(
            listOf(CommentFeedEntry.Header, CommentFeedEntry.Skeleton),
            entries
        )
    }

    @Test
    fun loadingWithContent_rendersNormally() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.LATEST,
            hotComments = emptyList(),
            latestComments = comments(1..3),
            isLoading = true,
            isLoadingMore = false,
            hasMore = false,
            commentsError = null
        )
        assertFalse(entries.any { it is CommentFeedEntry.Skeleton })
        assertEquals(3, entries.filterIsInstance<CommentFeedEntry.LatestComment>().size)
    }

    @Test
    fun hiddenHeader_omitsHeaderEntry() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.DEFAULT,
            hotComments = emptyList(),
            latestComments = comments(1..2),
            isLoading = false,
            isLoadingMore = false,
            hasMore = false,
            commentsError = null,
            showHeader = false
        )
        assertFalse(entries.any { it is CommentFeedEntry.Header })
        assertTrue(entries.first() is CommentFeedEntry.LatestComment)
    }

    @Test
    fun hotFull_showsAllHotPlusCountFooter() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.HOT,
            hotComments = comments(1..7),
            latestComments = comments(11..20),
            isLoading = false,
            isLoadingMore = false,
            hasMore = true,
            commentsError = null
        )
        val hot = entries.filterIsInstance<CommentFeedEntry.HotComment>()
        assertEquals(7, hot.size)
        assertTrue(hot.all { it.key.startsWith("hot_only_") })
        assertTrue(hot.dropLast(1).all { it.showDivider })
        assertFalse(hot.last().showDivider)
        assertEquals(7, entries.filterIsInstance<CommentFeedEntry.HotFooter>().single().hotCount)
        assertFalse(entries.any { it is CommentFeedEntry.LatestComment })
    }

    @Test
    fun hotEmpty_showsHotEmpty() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.HOT,
            hotComments = emptyList(),
            latestComments = comments(1..2),
            isLoading = false,
            isLoadingMore = false,
            hasMore = true,
            commentsError = null
        )
        val empty = entries.filterIsInstance<CommentFeedEntry.Empty>().single()
        assertEquals("empty_hot_comments", empty.key)
    }

    @Test
    fun latestFull_carriesFooterWithError() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.LATEST,
            hotComments = comments(1..2),
            latestComments = comments(1..10),
            isLoading = false,
            isLoadingMore = false,
            hasMore = true,
            commentsError = "boom"
        )
        val latest = entries.filterIsInstance<CommentFeedEntry.LatestComment>()
        assertEquals(10, latest.size)
        assertTrue(latest.all { it.key.startsWith("latest_only_") })
        val footer = entries.filterIsInstance<CommentFeedEntry.PagedFooter>().single()
        assertEquals("latest_comments_footer", footer.key)
        assertTrue(footer.hasAny)
        assertEquals("boom", footer.errorMessage)
    }

    @Test
    fun latestEmpty_showsLatestEmpty() {
        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.LATEST,
            hotComments = comments(1..2),
            latestComments = emptyList(),
            isLoading = false,
            isLoadingMore = false,
            hasMore = true,
            commentsError = null
        )
        val empty = entries.filterIsInstance<CommentFeedEntry.Empty>().single()
        assertEquals("empty_latest_comments", empty.key)
    }
}
