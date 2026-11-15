package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ZoneTabItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 板块能力矩阵单测：服务端 `zone/info.tabs` × 客户端白名单（issue #155 / #162 验收标准）。
 *
 * 真实样本与顺序来自 `docs/research/aggregate-api-verification.md` §2.4（10 个专区实测）。
 */
class ZoneBoardCapabilityTest {

    private fun tabs(vararg types: String): List<ZoneTabItem> =
        types.map { ZoneTabItem(type = it) }

    private fun tabsWithTitles(vararg pairs: Pair<String, String>): List<ZoneTabItem> =
        pairs.map { (type, title) -> ZoneTabItem(type = type, title = title) }

    /** 10 个真实专区的服务端下发顺序（§2.4 表格原样抄录，勿按客户端顺序重排）。 */
    private val serverSamples: Map<Long, List<String>> = mapOf(
        39989602L to listOf("action", "album", "works", "charactar", "posts", "shop_item"),
        33476094L to listOf(
            "action", "album", "posts", "hobby", "shop_item", "resale_item",
            "company", "series", "works", "charactar", "person", "community"
        ),
        39228575L to listOf(
            "action", "album", "posts", "hobby", "resale_item",
            "company", "series", "works", "charactar", "shop_item", "community"
        ),
        25619129L to listOf(
            "action", "album", "hobby", "works", "series",
            "charactar", "resale_item", "shop_item", "community"
        ),
        25734924L to listOf(
            "action", "album", "hobby", "works", "charactar",
            "series", "resale_item", "shop_item", "community"
        ),
        25194324L to listOf(
            "action", "album", "charactar", "works", "hobby",
            "resale_item", "shop_item", "community"
        ),
        25998424L to listOf(
            "action", "album", "works", "hobby", "posts",
            "resale_item", "shop_item", "community"
        ),
        39274231L to listOf("action", "works", "album", "posts", "charactar", "person", "shop_item"),
        39274091L to listOf("action", "works", "charactar", "album", "posts", "person", "shop_item"),
        36138460L to listOf(
            "action", "album", "hobby", "posts", "resale_item", "shop_item", "community"
        )
    )

    /** 与上面样本一一对应的期望结果：服务端顺序 ∩ 白名单，shop_item / community 丢弃。 */
    private val expectedBoards: Map<Long, List<ZoneBoard>> = mapOf(
        39989602L to listOf(
            ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Works, ZoneBoard.Character, ZoneBoard.Posts
        ),
        33476094L to ZoneBoard.HOME_ORDER,
        39228575L to listOf(
            ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale,
            ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works, ZoneBoard.Character
        ),
        25619129L to listOf(
            ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Hobby, ZoneBoard.Works,
            ZoneBoard.Series, ZoneBoard.Character, ZoneBoard.Resale
        ),
        25734924L to listOf(
            ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Hobby, ZoneBoard.Works,
            ZoneBoard.Character, ZoneBoard.Series, ZoneBoard.Resale
        ),
        25194324L to listOf(
            ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Character, ZoneBoard.Works,
            ZoneBoard.Hobby, ZoneBoard.Resale
        ),
        25998424L to listOf(
            ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Works, ZoneBoard.Hobby,
            ZoneBoard.Posts, ZoneBoard.Resale
        ),
        39274231L to listOf(
            ZoneBoard.Action, ZoneBoard.Works, ZoneBoard.Album, ZoneBoard.Posts,
            ZoneBoard.Character, ZoneBoard.Person
        ),
        39274091L to listOf(
            ZoneBoard.Action, ZoneBoard.Works, ZoneBoard.Character, ZoneBoard.Album,
            ZoneBoard.Posts, ZoneBoard.Person
        ),
        36138460L to listOf(
            ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Hobby, ZoneBoard.Posts, ZoneBoard.Resale
        )
    )

    @Test
    fun tabsFor_realServerSamples_keepServerOrderAndDropUnimplemented() {
        serverSamples.forEach { (nodeId, types) ->
            assertEquals(
                expectedBoards.getValue(nodeId),
                ZoneBoard.tabsFor(tabs(*types.toTypedArray())),
                "专区 nodeId=$nodeId 的能力矩阵结果应与服务端顺序一致"
            )
        }
    }

    @Test
    fun tabsFor_dropsShopItemAndCommunity() {
        val result = ZoneBoard.tabsFor(tabs("action", "shop_item", "album", "community"))
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Album), result)
    }

    @Test
    fun tabsFor_dropsUnknownType() {
        val result = ZoneBoard.tabsFor(tabs("action", "works_series", "album", "unknown_shop"))
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Album), result)
    }

    @Test
    fun tabsFor_putsActionFirstWhenServerOmitsIt() {
        assertEquals(
            listOf(ZoneBoard.Action, ZoneBoard.Album),
            ZoneBoard.tabsFor(tabs("album"))
        )
    }

    @Test
    fun tabsFor_movesActionToFrontWhenServerDoesNotListItFirst() {
        // 真实样本中 action 恒为第一位；此处只验证防御性置顶（ADR-0022 决策 2「动作板块恒为入口」）
        assertEquals(
            listOf(ZoneBoard.Action, ZoneBoard.Works, ZoneBoard.Album),
            ZoneBoard.tabsFor(tabs("works", "action", "album"))
        )
    }

    @Test
    fun tabsFor_emptyServerTabs_returnsEmpty() {
        assertTrue(ZoneBoard.tabsFor(emptyList()).isEmpty())
    }

    @Test
    fun tabsFor_respectsAvailableSubset() {
        // 聚合/关注等场景可传入更小的可用集合：交集之外一律隐藏
        val result = ZoneBoard.tabsFor(
            tabs("action", "album", "posts", "works"),
            available = setOf(ZoneBoard.Action, ZoneBoard.Works)
        )
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Works), result)
    }

    @Test
    fun homeOrder_isFixedTenBoardsAndEqualsWhitelist() {
        assertEquals(
            listOf(
                "action", "album", "posts", "hobby", "resale_item",
                "company", "series", "works", "charactar", "person"
            ),
            ZoneBoard.HOME_ORDER.map { it.type }
        )
        assertEquals(ZoneBoard.HOME_ORDER.toSet(), ZoneBoard.CLIENT_WHITELIST)
        assertEquals(10, ZoneBoard.CLIENT_WHITELIST.size)
    }

    @Test
    fun entity_isFiveEntityBoards() {
        assertEquals(
            setOf(
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),
            ZoneBoard.ENTITY
        )
        assertEquals(
            ZoneBoard.ENTITY,
            ZoneBoard.HOME_ORDER.filter { it.isEntity }.toSet()
        )
        // 服务端历史拼写 charactar 必须原样保留
        assertEquals("charactar", ZoneBoard.Character.type)
    }

    @Test
    fun fromType_mapsShareAliasToAlbumAndRejectsUnimplemented() {
        assertEquals(ZoneBoard.Album, ZoneBoard.fromType("share"))
        assertEquals(ZoneBoard.Album, ZoneBoard.fromType("album"))
        assertNull(ZoneBoard.fromType("shop_item"))
        assertNull(ZoneBoard.fromType("community"))
        assertNull(ZoneBoard.fromType(""))
    }

    @Test
    fun tabTitle_prefersServerTitleAndFallsBackToEnumLabel() {
        assertEquals("最新动态", ZoneBoard.Action.tabTitle("最新动态"))
        assertEquals("动态", ZoneBoard.Action.tabTitle(""))
        assertEquals("动态", ZoneBoard.Action.tabTitle(null))
        // 产品板块文案恒为「产品」（历史 parseTabs 行为）
        assertEquals("产品", ZoneBoard.Hobby.tabTitle("手办周边"))
        assertEquals("角色", ZoneBoard.Character.tabTitle("  "))
    }

    @Test
    fun tabsFor_shouldNotDeduplicateShareAliasIntoTwoTabs() {
        // share 别名与 album 同时下发时只保留一个「分享」板块，且保持首次出现位置
        val result = ZoneBoard.tabsFor(tabsWithTitles("action" to "动态", "share" to "分享", "album" to "相册"))
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Album), result)
    }

    // ── 首页「作用域 × 板块」能力矩阵（issue #162 验收标准）──────────────────

    @Test
    fun homeBoardsFor_all_returnsTenBoardsInHomeOrder() {
        val boards = ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.All)
        assertEquals(
            listOf(
                ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),
            boards
        )
        assertEquals(ZoneBoard.HOME_ORDER, boards)
        assertEquals(ZoneBoardCapability.ALL_BOARDS, boards)
    }

    @Test
    fun homeBoardsFor_follow_returnsSevenBoardsWithoutPostsHobbyResale() {
        val boards = ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Follow(listOf(100L, 200L)))
        assertEquals(
            listOf(
                ZoneBoard.Action, ZoneBoard.Album,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),
            boards
        )
        assertEquals(7, boards.size)
        assertEquals(ZoneBoardCapability.FOLLOW_BOARDS, boards)
        // 关注顺序 = HOME_ORDER 去掉隐藏三项，不能重排
        assertEquals(ZoneBoard.HOME_ORDER.filterNot { it in ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS }, boards)
    }

    @Test
    fun homeBoardsFor_single_keepsServerOrderAndDropsNonWhitelistTypes() {
        // 10 个真实专区样本：保留服务端顺序 ∩ 白名单，shop_item / community 丢弃
        serverSamples.forEach { (nodeId, types) ->
            val boards = ZoneBoardCapability.homeBoardsFor(
                ZoneQueryScope.Single(nodeId),
                tabs(*types.toTypedArray())
            )
            assertEquals(expectedBoards.getValue(nodeId), boards, "专区 nodeId=$nodeId 的 Tab 映射应与服务端顺序一致")
        }
    }

    @Test
    fun homeBoardsFor_aggregateScopesIgnoreServerTabs() {
        // 聚合态二级板块是客户端固定列表，一级 zone/home-tabs 不下发 tabs；即使误传也不得影响结果
        val serverTabs = tabs("action", "album", "posts", "hobby")
        assertEquals(
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.All),
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.All, serverTabs)
        )
        assertEquals(
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Follow(emptyList())),
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Follow(emptyList()), serverTabs)
        )
    }

    @Test
    fun homeBoardsFor_singlePendingOrFailed_usesFallback() {
        // null = 尚未拿到服务端 tabs（请求中 / 失败）：只保留动态板块兜底
        assertEquals(
            listOf(ZoneBoard.Action),
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Single(100L))
        )
        assertEquals(
            listOf(ZoneBoard.Action),
            ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Single(100L), null)
        )
        assertEquals(ZoneBoardCapability.SINGLE_FALLBACK_BOARDS, listOf(ZoneBoard.Action))
    }

    @Test
    fun homeBoardsFor_singleEmptyServerTabs_returnsEmptyNotFallback() {
        // 服务端明确没有可用板块：返回空列表，由宿主隐藏 TabRow 并展示空态（不伪造动态入口）
        assertTrue(ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Single(100L), emptyList()).isEmpty())
    }

    @Test
    fun isAvailableOnHome_matchesFullThreeScopeByTenBoardMatrix() {
        val expected: Map<ZoneQueryScope, Set<ZoneBoard>> = mapOf<ZoneQueryScope, Set<ZoneBoard>>(
            ZoneQueryScope.All to ZoneBoard.HOME_ORDER.toSet(),
            ZoneQueryScope.Follow(listOf(100L)) to setOf(
                ZoneBoard.Action, ZoneBoard.Album,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),
            // 单专区：白名单内都可能由服务端下发；实际可见性再看 zone/info.tabs
            ZoneQueryScope.Single(100L) to ZoneBoard.CLIENT_WHITELIST
        )
        expected.forEach { (scope, allowed) ->
            ZoneBoard.entries.forEach { board ->
                assertEquals(
                    board in allowed,
                    ZoneBoardCapability.isAvailableOnHome(scope, board),
                    "作用域 $scope 的板块 $board 可用性判定错误"
                )
            }
        }
    }

    @Test
    fun capability_neverExposesBoardsOutsideWhitelist() {
        // 枚举全集恰为白名单 10 类：shop_item / community / 未知 type 不在枚举内，矩阵永远无法产出
        assertEquals(10, ZoneBoard.entries.size)
        assertEquals(ZoneBoard.CLIENT_WHITELIST, ZoneBoard.entries.toSet())
        assertNull(ZoneBoard.fromType("shop_item"))
        assertNull(ZoneBoard.fromType("community"))
        assertNull(ZoneBoard.fromType("unknown_board"))

        val scopes = listOf(
            ZoneQueryScope.All,
            ZoneQueryScope.Follow(listOf(100L, 200L)),
            ZoneQueryScope.Follow(emptyList()),
            ZoneQueryScope.Single(100L)
        )
        scopes.forEach { scope ->
            ZoneBoardCapability.homeBoardsFor(scope).forEach { board ->
                assertTrue(
                    board in ZoneBoard.CLIENT_WHITELIST,
                    "作用域 $scope 泄漏了白名单外板块 $board"
                )
            }
        }
    }

    @Test
    fun followUnavailable_isExactSetDifferenceBetweenAggregates() {
        // 隐藏事实只有一处：关注可见 = 全部可见 - 关注不可用
        assertEquals(
            ZoneBoardCapability.ALL_BOARDS.toSet() - ZoneBoardCapability.FOLLOW_BOARDS.toSet(),
            ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS
        )
        assertEquals(
            setOf(ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale),
            ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS
        )
        assertEquals(7, ZoneBoardCapability.FOLLOW_BOARDS.size)
        assertEquals(3, ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS.size)
        // 隐藏集合与关注可见集合必须互斥且合起来恰为全部聚合 10 项
        assertTrue(
            ZoneBoardCapability.FOLLOW_BOARDS.toSet()
                .intersect(ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS)
                .isEmpty()
        )
        assertEquals(
            ZoneBoardCapability.ALL_BOARDS.toSet(),
            ZoneBoardCapability.FOLLOW_BOARDS.toSet() + ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS
        )
    }
}
