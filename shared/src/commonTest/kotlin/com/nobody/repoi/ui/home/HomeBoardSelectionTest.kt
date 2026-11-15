package com.nobody.repoi.ui.home

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneQueryScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 首页作用域感知的板块可用性（issue #158 安全闸门 / #160 二手 / #162 会在此基础上补齐完整矩阵）。
 *
 * 核心规则：无服务端多专区通道的板块在**关注聚合**必须隐藏——
 * `api/posts/search` 的专区过滤只认单值 `zoneNodeId`，逗号串被静默忽略并退化为全站；
 * `api/resale/search` 的 `zones` 逗号串是交集语义（多专区 0 条）。
 * 展示出来就是「全站/空结果假关注数据」。依据
 * `docs/research/aggregate-api-verification.md` §4.2/§4.3/§5。
 */
class HomeBoardSelectionTest {

    @Test
    fun homeBoardsFor_all_includesPostsAndAlbumAndKeepsHomeOrder() {
        val boards = homeBoardsFor(ZoneQueryScope.All)
        assertEquals(
            ZoneBoard.HOME_ORDER.filter { it in ZoneBoard.HOME_IMPLEMENTED },
            boards
        )
        assertTrue(ZoneBoard.Posts in boards, "全部聚合：讨论板块可上线（省略 zone 过滤即全站）")
        assertTrue(ZoneBoard.Album in boards, "全部聚合：分享板块可上线（zones 省略即全站）")
        assertTrue(ZoneBoard.Resale in boards, "全部聚合：二手板块可上线（zones 省略即全站）")
        assertEquals(HomeBoardSelectionBoards.expectedAll, boards)
    }

    @Test
    fun homeBoardsFor_follow_excludesPostsWithoutMultiZoneChannel() {
        val boards = homeBoardsFor(ZoneQueryScope.Follow(listOf(100L, 200L)))
        assertFalse(ZoneBoard.Posts in boards, "关注聚合无多专区通道，讨论板块必须隐藏")
        assertFalse(ZoneBoard.Resale in boards, "关注聚合 zones 逗号串是交集语义，二手板块必须隐藏")
        // 分享板块的 `zones` 支持逗号串并集，关注聚合可保留
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Album), boards)
    }

    @Test
    fun homeBoardsFor_emptyFollow_alsoExcludesPosts() {
        // 未关注任何专区：同样是关注作用域，规则不变（请求由上层短路，不依赖本列表）
        val boards = homeBoardsFor(ZoneQueryScope.Follow(emptyList()))
        assertFalse(ZoneBoard.Posts in boards)
        assertFalse(ZoneBoard.Resale in boards)
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Album), boards)
    }

    @Test
    fun homeBoardsFor_singleZone_returnsFallbackUntilServerTabsArrive() {
        // 单专区最终 Tab 由 ViewModel 用服务端 zone/info.tabs 决定；同步兜底只保留动态板块
        assertEquals(HOME_BOARD_FALLBACK, homeBoardsFor(ZoneQueryScope.Single(100L)))
        assertEquals(listOf(ZoneBoard.Action), homeBoardsFor(ZoneQueryScope.Single(100L)))
    }

    @Test
    fun homeBoardsFor_neverLeaksUnimplementedBoards() {
        (listOf(ZoneQueryScope.All, ZoneQueryScope.Follow(listOf(1L))) as List<ZoneQueryScope>).forEach { scope ->
            homeBoardsFor(scope).forEach { board ->
                assertTrue(
                    board in ZoneBoard.HOME_IMPLEMENTED,
                    "作用域 $scope 泄漏了未接入板块 $board"
                )
            }
        }
    }

    @Test
    fun followUnavailableBoards_areSubsetOfAggregateBoards() {
        // 关注态隐藏集合必须是全部聚合可展示集合的子集，避免隐藏一个本来就不会出现的板块
        val all = homeBoardsFor(ZoneQueryScope.All).toSet()
        assertTrue(all.containsAll(FOLLOW_UNAVAILABLE_BOARDS))
        assertTrue(ZoneBoard.Posts in FOLLOW_UNAVAILABLE_BOARDS)
        assertTrue(ZoneBoard.Resale in FOLLOW_UNAVAILABLE_BOARDS)
    }

    @Test
    fun homeAggregateBoards_equalsAllScopeResult() {
        assertEquals(homeBoardsFor(ZoneQueryScope.All), HOME_AGGREGATE_BOARDS)
        assertEquals(HOME_AGGREGATE_BOARDS, immediateHomeBoards(ZoneQueryScope.All))
        assertEquals(
            listOf(ZoneBoard.Action, ZoneBoard.Album),
            immediateHomeBoards(ZoneQueryScope.Follow(listOf(1L)))
        )
    }

    /** 显式写死「全部聚合」的期望顺序，避免测试只做自指断言。 */
    private object HomeBoardSelectionBoards {
        val expectedAll: List<ZoneBoard> =
            listOf(ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Posts, ZoneBoard.Resale)
    }
}
