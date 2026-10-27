package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.CommentItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 列表分页通用规则的接口级测试：评论区与图片瀑布流共用同一套去重与「还有下一页」规则。
 */
class ListPagingTest {

    private fun comments(from: Int, count: Int) =
        List(count) { CommentItem(id = (from + it).toLong(), content = "Comment ${from + it}") }

    @Test
    fun hasMoreByFullPage_onlyFullPageCounts() {
        assertTrue(ListPaging.hasMoreByFullPage(10, 10))
        assertTrue(ListPaging.hasMoreByFullPage(20, 20))
        assertFalse(ListPaging.hasMoreByFullPage(9, 10))
        assertFalse(ListPaging.hasMoreByFullPage(19, 20))
        assertFalse(ListPaging.hasMoreByFullPage(0, 10))
    }

    @Test
    fun hasMoreAfterPage_blocksPagingWhenFullPageAddsNothing() {
        val existing = comments(1, 18)
        val fetched = comments(9, 10)
        val merged = ListPaging.appendPage(existing, fetched) { it.id }
        val addedCount = merged.size - existing.size

        // 整页皆为重复项：护栏必须停止翻页，否则会无限拉取同一页
        assertFalse(ListPaging.hasMoreAfterPage(fetchedCount = fetched.size, addedCount = addedCount, pageSize = 10))
        assertTrue(ListPaging.hasMoreAfterPage(fetchedCount = 10, addedCount = 8, pageSize = 10))
        assertFalse(ListPaging.hasMoreAfterPage(fetchedCount = 3, addedCount = 3, pageSize = 10))
        assertFalse(ListPaging.hasMoreAfterPage(fetchedCount = 10, addedCount = 0, pageSize = 10))
    }

    @Test
    fun appendPage_keepsExistingThenAppendsOnlyNewIds() {
        val existing = comments(1, 10)
        val merged = ListPaging.appendPage(existing, comments(9, 10)) { it.id }

        assertEquals(18, merged.size)
        assertEquals(existing, merged.subList(0, 10))
        assertEquals(listOf(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L), merged.drop(10).map { it.id })
    }

    @Test
    fun appendPage_allDuplicatesLeavesListUnchanged() {
        val existing = comments(1, 18)
        val merged = ListPaging.appendPage(existing, comments(9, 10)) { it.id }

        assertEquals(18, merged.size)
        assertEquals(existing, merged)
    }

    @Test
    fun appendPage_keylessItemsAreAlwaysKept() {
        val existing = listOf(CommentItem(id = 1L, content = "已有"))
        val fetched = listOf(
            CommentItem(id = 0L, content = "无 ID 一"),
            CommentItem(id = 1L, content = "与已有重复"),
            CommentItem(id = 0L, content = "无 ID 二")
        )

        val merged = ListPaging.appendPage(existing, fetched) { if (it.id == 0L) null else it.id }

        assertEquals(listOf(1L, 0L, 0L), merged.map { it.id })
        assertEquals(listOf("已有", "无 ID 一", "无 ID 二"), merged.map { it.content })
    }

    @Test
    fun appendPage_emptyPageReturnsSameList() {
        val existing = comments(1, 3)
        assertSame(existing, ListPaging.appendPage(existing, emptyList<CommentItem>()) { it.id })
    }
}