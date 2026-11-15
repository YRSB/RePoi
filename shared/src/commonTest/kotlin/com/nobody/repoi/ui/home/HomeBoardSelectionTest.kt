package com.nobody.repoi.ui.home

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneQueryScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 首页聚合态二级板块列表的能力过滤（issue #159 验收标准）。
 *
 * 关注聚合的产品板块不可用（`zones` 逗号串是交集语义、`homeScope=follow` 待登录态复验），
 * 必须从 Tab 中隐藏且不发起请求；「全部」聚合不受影响。
 */
class HomeBoardSelectionTest {

    @Test
    fun allScope_usesHomeOrderIntersectedWithImplemented() {
        val boards = homeBoardsFor(ZoneQueryScope.All)
        assertEquals(ZoneBoard.HOME_ORDER.filter { it in ZoneBoard.HOME_IMPLEMENTED }, boards)
        assertTrue(ZoneBoard.Hobby in boards)
    }

    @Test
    fun followScope_hidesFollowUnavailableBoards() {
        val boards = homeBoardsFor(ZoneQueryScope.Follow(listOf(100L, 200L)))
        assertFalse(ZoneBoard.Hobby in boards)
        assertTrue(ZoneBoard.Hobby in FOLLOW_UNAVAILABLE_BOARDS)
        // 其余已实现板块保持 HOME_ORDER 顺序
        assertEquals(boards.sortedBy { ZoneBoard.HOME_ORDER.indexOf(it) }, boards)
    }

    @Test
    fun emptyFollowScope_stillHidesUnavailableBoards() {
        val boards = homeBoardsFor(ZoneQueryScope.Follow(emptyList()))
        assertFalse(ZoneBoard.Hobby in boards)
    }

    @Test
    fun singleScope_stillUsesActionFallback() {
        assertEquals(listOf(ZoneBoard.Action), homeBoardsFor(ZoneQueryScope.Single(100L)))
    }

    @Test
    fun immediateBoards_followExcludesHobby() {
        assertFalse(ZoneBoard.Hobby in immediateHomeBoards(ZoneQueryScope.Follow(listOf(1L))))
        assertTrue(ZoneBoard.Hobby in immediateHomeBoards(ZoneQueryScope.All))
    }
}
