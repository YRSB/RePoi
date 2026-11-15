package com.nobody.repoi.ui.home

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneQueryScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 首页聚合态板块列表与「关注态能力矩阵隐藏」纯函数测试（issue #160）。
 *
 * 事实依据：二手 `POST api/resale/search` 的 `zones` 逗号串实测为**交集**语义
 * （`docs/research/aggregate-api-verification.md` §5），无法表达关注聚合的并集，
 * 因此在关注态直接从二级 Tab 隐藏，而不是展示「暂不支持」占位。
 */
class HomeBoardSelectionTest {

    @Test
    fun aggregateBoards_followHomeOrderAndImplemented() {
        val boards = homeBoardsFor(ZoneQueryScope.All)
        assertEquals(ZoneBoard.HOME_ORDER.filter { it in ZoneBoard.HOME_IMPLEMENTED }, boards)
        assertTrue(ZoneBoard.Resale in boards, "全部聚合下二手可用（省略 zones 等价全站）")
    }

    @Test
    fun followBoards_hideResaleBecauseZonesCommaMeansIntersection() {
        val boards = homeBoardsFor(ZoneQueryScope.Follow(listOf(100L, 200L)))
        assertTrue(ZoneBoard.Resale !in boards)
        assertTrue(ZoneBoard.Resale in FOLLOW_UNAVAILABLE_BOARDS)
        // 其余可用板块保持 HOME_ORDER 顺序
        assertEquals(
            ZoneBoard.HOME_ORDER.filter {
                it in ZoneBoard.HOME_IMPLEMENTED && it !in FOLLOW_UNAVAILABLE_BOARDS
            },
            boards
        )
    }

    @Test
    fun followBoards_emptyFocusStillReturnsBoardListButCallerMustShortCircuit() {
        // 空关注集合不改变板块列表；「不发起请求」由上层 isFollowEmpty 短路保证
        val boards = homeBoardsFor(ZoneQueryScope.Follow(emptyList()))
        assertTrue(boards.isNotEmpty())
        assertTrue(ZoneBoard.Resale !in boards)
    }

    @Test
    fun singleZoneBoards_fallBackToActionUntilServerTabsArrive() {
        assertEquals(
            HOME_BOARD_FALLBACK,
            homeBoardsFor(ZoneQueryScope.Single(100L))
        )
        assertEquals(ZoneBoard.Action, immediateHomeSelectedBoard(ZoneQueryScope.Single(100L)))
    }
}
