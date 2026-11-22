package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.PostItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostsBoardOptionsTest {

    @Test
    fun orders_areThreeWithOriginalKeysAndLabels() {
        assertEquals(
            listOf("upd" to "回复", "add" to "新贴", "top" to "推荐"),
            PostsBoardOptions.ORDERS
        )
        assertEquals(3, PostsBoardOptions.ORDERS.size)

        assertEquals(listOf("upd", "add", "top"), PostsBoardOptions.ORDERS.map { it.first })
        assertEquals(listOf("回复", "新贴", "推荐"), PostsBoardOptions.ORDERS.map { it.second })
    }

    @Test
    fun defaultOrder_isUpdAndAmongOrders() {
        assertEquals("upd", PostsBoardOptions.DEFAULT_ORDER)
        assertTrue(PostsBoardOptions.DEFAULT_ORDER in PostsBoardOptions.ORDERS.map { it.first })
        assertEquals(20, PostsBoardOptions.PAGE_SIZE)
    }

    @Test
    fun partition_prefersExplicitPinnedPosts() {
        val raw = listOf(post(1), post(2, pinned = true), post(3), post(4), post(5), post(6), post(7))
        val (pinned, unpinned) = PostsBoardOptions.partition(raw)
        assertEquals(listOf(2L), pinned.map { it.id })
        assertEquals(listOf(1L, 3L, 4L, 5L, 6L, 7L), unpinned.map { it.id })
    }

    @Test
    fun partition_capsExplicitPinnedAtFive() {

        val raw = (1L..8L).map { post(it, pinned = true) }
        val (pinned, unpinned) = PostsBoardOptions.partition(raw)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), pinned.map { it.id })
        assertTrue(unpinned.isEmpty())
    }

    @Test
    fun partition_withoutPinned_usesSizeHeuristic() {

        val large = (1L..6L).map { post(it) }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), PostsBoardOptions.partition(large).first.map { it.id })

        val five = (1L..5L).map { post(it) }
        val (fivePinned, fiveUnpinned) = PostsBoardOptions.partition(five)
        assertEquals(listOf(1L, 2L), fivePinned.map { it.id })
        assertEquals(listOf(3L, 4L, 5L), fiveUnpinned.map { it.id })

        val medium = (1L..3L).map { post(it) }
        assertEquals(listOf(1L, 2L), PostsBoardOptions.partition(medium).first.map { it.id })

        val small = (1L..2L).map { post(it) }
        assertTrue(PostsBoardOptions.partition(small).first.isEmpty())
        assertTrue(PostsBoardOptions.partition(emptyList()).first.isEmpty())
    }

    @Test
    fun partition_mixedPinnedAndNormal_keepsPinnedFirstAndOrdinaryOrder() {

        val raw = (1L..6L).map { post(it, pinned = true) } + post(100) + post(101)
        val (pinned, unpinned) = PostsBoardOptions.partition(raw)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), pinned.map { it.id })
        assertEquals(listOf(100L, 101L), unpinned.map { it.id })
    }

    @Test
    fun partition_heuristicBranch_neverLosesOrDuplicatesItems() {

        listOf(0, 1, 2, 3, 5, 6, 20).forEach { size ->
            val raw = (1L..size).map { post(it) }
            val (pinned, unpinned) = PostsBoardOptions.partition(raw)
            val merged = (pinned + unpinned).map { it.id }
            assertEquals(raw.map { it.id }, merged, "size=$size 时启发式置顶切分不得丢条目或改序")
            assertFalse(pinned.any { it.id in unpinned.map { item -> item.id } })
        }
    }

    private fun post(id: Long, pinned: Boolean = false): PostItem = PostItem(
        id = id,
        title = "帖子$id",
        topTime = if (pinned) 1794000000000L else 0L
    )
}
