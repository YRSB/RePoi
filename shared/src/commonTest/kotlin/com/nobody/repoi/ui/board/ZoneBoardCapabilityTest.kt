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
}
