package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneTabItem
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardCapability
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 首页作用域感知的板块可用性（issue #162：能力矩阵单一真相的消费侧）。
 *
 * 矩阵事实本身断言在 `ui/board/ZoneBoardCapabilityTest`；本类只验证首页 `ui.home` 的薄封装
 * 与矩阵完全一致（不得出现第二处隐藏规则）：
 * - 关注聚合隐藏讨论 / 产品 / 二手（无服务端多专区通道）；
 * - 全部聚合 10 项全显示；单专区服务端顺序 ∩ 白名单；
 * - `shop_item` / `community` / 未知 type 永不出现。
 */
class HomeBoardSelectionTest {

    @Test
    fun homeBoardsFor_all_returnsAllTenBoardsInHomeOrder() {
        val boards = homeBoardsFor(ZoneQueryScope.All)
        assertEquals(
            listOf(
                ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),
            boards
        )
        assertEquals(ZoneBoard.HOME_ORDER, boards)
        assertTrue(ZoneBoard.Posts in boards, "全部聚合：讨论板块可上线（省略 zone 过滤即全站）")
        assertTrue(ZoneBoard.Album in boards, "全部聚合：分享板块可上线（zones 省略即全站）")
        assertTrue(ZoneBoard.Resale in boards, "全部聚合：二手板块可上线（zones 省略即全站）")
        assertTrue(ZoneBoard.Hobby in boards, "全部聚合：产品板块可上线（zones 省略即全站）")
        assertTrue(ZoneBoard.ENTITY.all { it in boards }, "全部聚合：实体 5 类均可上线")
    }

    @Test
    fun homeBoardsFor_follow_excludesPostsHobbyResale() {
        val boards = homeBoardsFor(ZoneQueryScope.Follow(listOf(100L, 200L)))
        assertFalse(ZoneBoard.Posts in boards, "关注聚合无多专区通道，讨论板块必须隐藏")
        assertFalse(ZoneBoard.Resale in boards, "关注聚合 zones 逗号串是交集语义，二手板块必须隐藏")
        assertFalse(ZoneBoard.Hobby in boards, "产品关注态需登录态复验，当前必须先隐藏")
        // 关注顺序 = HOME_ORDER 去掉隐藏三项，分享与实体 5 类保留
        assertEquals(
            ZoneBoard.HOME_ORDER.filterNot { it in ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS },
            boards
        )
        assertTrue(ZoneBoard.ENTITY.all { it in boards })
    }

    @Test
    fun homeBoardsFor_emptyFollow_alsoExcludesPostsHobbyResale() {
        // 未关注任何专区：同样是关注作用域，规则不变（请求由上层短路，不依赖本列表）
        val boards = homeBoardsFor(ZoneQueryScope.Follow(emptyList()))
        assertFalse(ZoneBoard.Posts in boards)
        assertFalse(ZoneBoard.Resale in boards)
        assertFalse(ZoneBoard.Hobby in boards)
        assertEquals(
            listOf(
                ZoneBoard.Action, ZoneBoard.Album,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),
            boards
        )
    }

    @Test
    fun homeBoardsFor_singleZone_returnsFallbackUntilServerTabsArrive() {
        // 单专区最终 Tab 由 ViewModel 用服务端 zone/info.tabs 决定；同步兜底只保留动态板块
        assertEquals(HOME_BOARD_FALLBACK, homeBoardsFor(ZoneQueryScope.Single(100L)))
        assertEquals(listOf(ZoneBoard.Action), homeBoardsFor(ZoneQueryScope.Single(100L)))
    }

    @Test
    fun homeBoardsFor_singleZone_usesServerOrderAndDropsNonWhitelist() {
        // 服务端下发顺序（含白名单外 shop_item / community）→ 过滤后保持原相对顺序
        val serverTabs = listOf("action", "shop_item", "album", "community", "posts", "works")
            .map { ZoneTabItem(type = it) }
        assertEquals(
            listOf(ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Posts, ZoneBoard.Works),
            homeBoardsFor(ZoneQueryScope.Single(100L), serverTabs)
        )
    }

    @Test
    fun homeBoardsFor_neverLeaksUnimplementedBoards() {
        (listOf(
            ZoneQueryScope.All,
            ZoneQueryScope.Follow(listOf(1L)),
            ZoneQueryScope.Single(100L)
        ) as List<ZoneQueryScope>).forEach { scope ->
            homeBoardsFor(scope).forEach { board ->
                assertTrue(
                    board in ZoneBoard.CLIENT_WHITELIST,
                    "作用域 $scope 泄漏了白名单外板块 $board"
                )
            }
        }
    }

    @Test
    fun followUnavailableBoards_areSubsetOfAggregateBoards() {
        // 关注态隐藏集合必须是全部聚合可展示集合的子集，避免隐藏一个本来就不会出现的板块
        val all = homeBoardsFor(ZoneQueryScope.All).toSet()
        assertTrue(all.containsAll(ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS))
        assertTrue(ZoneBoard.Posts in ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS)
        assertTrue(ZoneBoard.Resale in ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS)
        assertTrue(ZoneBoard.Hobby in ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS)
    }

    @Test
    fun homeAggregateBoards_equalsAllScopeResult() {
        assertEquals(homeBoardsFor(ZoneQueryScope.All), HOME_AGGREGATE_BOARDS)
        assertEquals(HOME_AGGREGATE_BOARDS, immediateHomeBoards(ZoneQueryScope.All))
        assertEquals(
            HOME_AGGREGATE_BOARDS.filterNot { it in ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS },
            immediateHomeBoards(ZoneQueryScope.Follow(listOf(1L)))
        )
    }

    @Test
    fun wrappers_delegateToCapabilityMatrix_onlyOnce() {
        // ui.home 薄封装 == ui.board 矩阵结果（三作用域逐一比对，防止出现第二份隐藏规则）
        assertEquals(
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.All),
            homeBoardsFor(ZoneQueryScope.All)
        )
        assertEquals(
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Follow(listOf(1L, 2L))),
            homeBoardsFor(ZoneQueryScope.Follow(listOf(1L, 2L)))
        )
        val tabs = listOf(ZoneTabItem(type = "action"), ZoneTabItem(type = "album"))
        assertEquals(
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Single(9L), tabs),
            homeBoardsFor(ZoneQueryScope.Single(9L), tabs)
        )
        assertEquals(ZoneBoard.Action, immediateHomeSelectedBoard(ZoneQueryScope.Follow(emptyList())))
    }
}
