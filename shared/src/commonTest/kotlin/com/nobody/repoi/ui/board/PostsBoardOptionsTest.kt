package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.PostItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 讨论板块共享定义单测（issue #158）。
 *
 * 排序键值/文案与置顶切分都从专区详情现状原样收敛到 [PostsBoardOptions]，首页与详情共用；
 * 分类过滤已确认「原版无入口、接口无参数」（见 [PostsBoardOptions] KDoc），因此刻意不提供分类选项。
 */
class PostsBoardOptionsTest {

    @Test
    fun orders_areThreeWithOriginalKeysAndLabels() {
        assertEquals(
            listOf("upd" to "回复", "add" to "新贴", "top" to "推荐"),
            PostsBoardOptions.ORDERS
        )
        assertEquals(3, PostsBoardOptions.ORDERS.size)
        // 键值必须与原版 2.43 排序条一致（服务端只认这三个键）
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
        // 沿专区详情现状：显式置顶只取前 5 条；其余显式置顶条目不会进入普通列表（既有行为，不修正）
        val raw = (1L..8L).map { post(it, pinned = true) }
        val (pinned, unpinned) = PostsBoardOptions.partition(raw)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), pinned.map { it.id })
        assertTrue(unpinned.isEmpty())
    }

    @Test
    fun partition_withoutPinned_usesSizeHeuristic() {
        // >5：前 5 条作为置顶条（沿专区详情现状的启发式）
        val large = (1L..6L).map { post(it) }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), PostsBoardOptions.partition(large).first.map { it.id })
        // 恰 5 条仍在「3..5」档：前 2 条（>5 的分界点必须被钉住，否则 `>= 5` 也能全绿）
        val five = (1L..5L).map { post(it) }
        val (fivePinned, fiveUnpinned) = PostsBoardOptions.partition(five)
        assertEquals(listOf(1L, 2L), fivePinned.map { it.id })
        assertEquals(listOf(3L, 4L, 5L), fiveUnpinned.map { it.id })
        // 3..5 条：前 2 条
        val medium = (1L..3L).map { post(it) }
        assertEquals(listOf(1L, 2L), PostsBoardOptions.partition(medium).first.map { it.id })
        // <=2 条：不置顶
        val small = (1L..2L).map { post(it) }
        assertTrue(PostsBoardOptions.partition(small).first.isEmpty())
        assertTrue(PostsBoardOptions.partition(emptyList()).first.isEmpty())
    }

    @Test
    fun partition_mixedPinnedAndNormal_keepsPinnedFirstAndOrdinaryOrder() {
        // 混合样本钉住「显式置顶」分支的真实语义：置顶按原序取前 5，普通帖原序进普通列表，
        // 超出 5 条的显式置顶既不进置顶条也不进普通列表（既有行为，见 [PostsBoardOptions.partition] KDoc）
        val raw = (1L..6L).map { post(it, pinned = true) } + post(100) + post(101)
        val (pinned, unpinned) = PostsBoardOptions.partition(raw)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), pinned.map { it.id })
        assertEquals(listOf(100L, 101L), unpinned.map { it.id })
    }

    @Test
    fun partition_heuristicBranch_neverLosesOrDuplicatesItems() {
        // 全部样本走「无显式置顶」的启发式分支；显式置顶分支的丢帖语义由
        // partition_capsExplicitPinnedAtFive / partition_mixedPinnedAndNormal_keepsPinnedFirstAndOrdinaryOrder 钉住
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
