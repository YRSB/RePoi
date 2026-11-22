package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.remote.model.ZoneTabItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZoneBoardCapabilityTest {

    private fun tabs(vararg types: String): List<ZoneTabItem> =
        types.map { ZoneTabItem(type = it) }

    private fun tabsWithTitles(vararg pairs: Pair<String, String>): List<ZoneTabItem> =
        pairs.map { (type, title) -> ZoneTabItem(type = type, title = title) }

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
    fun tabsFor_keepsServerListWhenActionIsOmitted() {

        assertEquals(
            listOf(ZoneBoard.Album),
            ZoneBoard.tabsFor(tabs("album"))
        )
    }

    @Test
    fun tabsFor_keepsServerOrderWhenActionIsNotFirst() {

        assertEquals(
            listOf(ZoneBoard.Works, ZoneBoard.Action, ZoneBoard.Album),
            ZoneBoard.tabsFor(tabs("works", "action", "album"))
        )
    }

    @Test
    fun tabsFor_emptyServerTabs_returnsEmpty() {
        assertTrue(ZoneBoard.tabsFor(emptyList()).isEmpty())
    }

    @Test
    fun tabsFor_respectsAvailableSubset() {

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

        assertEquals("产品", ZoneBoard.Hobby.tabTitle("手办周边"))
        assertEquals("角色", ZoneBoard.Character.tabTitle("  "))
    }

    @Test
    fun tabsFor_shouldNotDeduplicateShareAliasIntoTwoTabs() {

        val result = ZoneBoard.tabsFor(tabsWithTitles("action" to "动态", "share" to "分享", "album" to "相册"))
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Album), result)
    }

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

        assertEquals(ZoneBoard.HOME_ORDER.filterNot { it in ZoneBoardCapability.FOLLOW_UNAVAILABLE_BOARDS }, boards)
    }

    @Test
    fun homeBoardsFor_single_keepsServerOrderAndDropsNonWhitelistTypes() {

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

        assertTrue(ZoneBoardCapability.homeBoardsFor(ZoneQueryScope.Single(100L), emptyList()).isEmpty())
    }

    @Test
    fun isAvailableOnHome_matchesFullThreeScopeByTenBoardMatrix() {

        val expected: Map<ZoneQueryScope, Set<ZoneBoard>> = mapOf<ZoneQueryScope, Set<ZoneBoard>>(
            ZoneQueryScope.All to setOf(
                ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works, ZoneBoard.Character, ZoneBoard.Person
            ),
            ZoneQueryScope.Follow(listOf(100L)) to setOf(
                ZoneBoard.Action, ZoneBoard.Album,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),

            ZoneQueryScope.Single(100L) to setOf(
                ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works, ZoneBoard.Character, ZoneBoard.Person
            )
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
