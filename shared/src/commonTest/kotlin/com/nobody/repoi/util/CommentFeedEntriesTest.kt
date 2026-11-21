package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.ui.common.comment.CommentFilterMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 评论流条目构建测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `CommentFeedEntriesTest`（逐用例，JUnit4 → kotlin.test）。
 */
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
    fun loadingEmpty_returnsHeaderPlusLoadingOnly() {
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
            listOf(CommentFeedEntry.Header, CommentFeedEntry.Loading),
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
        assertFalse(entries.any { it is CommentFeedEntry.Loading })
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

    @Test
    fun feedEntries_filtersShieldedHotAndLatestComments() {
        val hotList = listOf(
            CommentItem(id = 1L, content = "正常热门1"),
            CommentItem(id = 2L, content = "(已屏蔽)"),
            CommentItem(id = 3L, content = "正常文字但被标记", isMask = 1),
            CommentItem(id = 4L, content = "正常热门4")
        )
        val latestList = listOf(
            CommentItem(id = 11L, content = "正常最新11"),
            CommentItem(id = 12L, content = "(已屏蔽)"),
            CommentItem(id = 13L, content = "正常最新13", isMask = 1),
            CommentItem(id = 14L, content = "正常最新14")
        )

        val entries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.DEFAULT,
            hotComments = hotList,
            latestComments = latestList,
            isLoading = false,
            isLoadingMore = false,
            hasMore = true,
            commentsError = null
        )

        val hot = entries.filterIsInstance<CommentFeedEntry.HotComment>()
        assertEquals(listOf(1L, 4L), hot.map { it.comment.id })

        val latest = entries.filterIsInstance<CommentFeedEntry.LatestComment>()
        assertEquals(listOf(11L, 14L), latest.map { it.comment.id })
    }

    @Test
    fun feedEntries_allCommentsShielded_fallsBackToEmptyState() {
        val shieldedHot = listOf(
            CommentItem(id = 1L, content = "(已屏蔽)"),
            CommentItem(id = 2L, content = "违规", isMask = 1)
        )
        val shieldedLatest = listOf(
            CommentItem(id = 11L, content = "(已屏蔽)"),
            CommentItem(id = 12L, content = "违规最新", isMask = 1)
        )

        // 1. DEFAULT 模式全屏蔽 -> 降级为 Empty，hasAny = false
        val defaultEntries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.DEFAULT,
            hotComments = shieldedHot,
            latestComments = shieldedLatest,
            isLoading = false,
            isLoadingMore = false,
            hasMore = false,
            commentsError = null
        )
        val defaultEmpty = defaultEntries.filterIsInstance<CommentFeedEntry.Empty>().single()
        assertEquals("empty_default_comments", defaultEmpty.key)
        val defaultFooter = defaultEntries.filterIsInstance<CommentFeedEntry.PagedFooter>().single()
        assertFalse(defaultFooter.hasAny)

        // 2. HOT 模式全屏蔽 -> 降级为 Empty
        val hotEntries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.HOT,
            hotComments = shieldedHot,
            latestComments = shieldedLatest,
            isLoading = false,
            isLoadingMore = false,
            hasMore = false,
            commentsError = null
        )
        val hotEmpty = hotEntries.filterIsInstance<CommentFeedEntry.Empty>().single()
        assertEquals("empty_hot_comments", hotEmpty.key)

        // 3. LATEST 模式全屏蔽 -> 降级为 Empty
        val latestEntries = buildCommentFeedEntries(
            filterMode = CommentFilterMode.LATEST,
            hotComments = shieldedHot,
            latestComments = shieldedLatest,
            isLoading = false,
            isLoadingMore = false,
            hasMore = false,
            commentsError = null
        )
        val latestEmpty = latestEntries.filterIsInstance<CommentFeedEntry.Empty>().single()
        assertEquals("empty_latest_comments", latestEmpty.key)
    }
}
