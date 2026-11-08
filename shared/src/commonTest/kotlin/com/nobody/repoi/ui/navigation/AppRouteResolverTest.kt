package com.nobody.repoi.ui.navigation

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateItem
import com.nobody.repoi.data.remote.model.UserProfileSummary
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 统一路由解析器测试（纯 JVM，无 Compose/平台副作用）。
 *
 * 第 1 段为旧版动态分流器与链接调度器用例的无损平移（断言对象统一为 [RouteTarget]）；
 * 第 2 段补全 9 类站内 URL、站外伪装路径、空白与非法 ID 边界；
 * 第 3 段覆盖 resolveAction 全部分类区间、ID 回退与用户回退。
 */
class AppRouteResolverTest {

    // ── 1. 旧版分流器用例平移 ────────────────────────────────────────────

    @Test
    fun `resolveAction - company or vendor resolves to Vendor destination`() {
        // 1. 通过 itemType = "company"
        val vendor1 = vendorParam(
            ActionItem(
                id = 1L,
                relateItem = RelateItem(
                    id = 100L,
                    itemId = 200L,
                    itemType = "company",
                    name = "Good Smile Company",
                    cover = "/cover/gsc.jpg"
                )
            )
        )
        assertEquals(200L, vendor1.companyId)
        assertEquals(100L, vendor1.companyNodeId)
        assertEquals("Good Smile Company", vendor1.initialName)
        assertEquals("/cover/gsc.jpg", vendor1.initialCover)

        // 2. 通过 itemType = "vendor"
        val vendor2 = vendorParam(
            ActionItem(
                id = 2L,
                relateItem = RelateItem(
                    id = 101L,
                    itemId = 201L,
                    itemType = "vendor",
                    name = "ALTER"
                )
            )
        )
        assertEquals(201L, vendor2.companyId)
        assertEquals("ALTER", vendor2.initialName)

        // 3. 通过 categoryId = 40000
        val vendor3 = vendorParam(
            ActionItem(
                id = 3L,
                relateItem = RelateItem(
                    id = 300L,
                    itemId = 0L,
                    categoryId = 40000,
                    name = "Max Factory"
                )
            )
        )
        assertEquals(300L, vendor3.companyId)
        assertEquals(300L, vendor3.companyNodeId)
    }

    @Test
    fun `resolveAction - charactar with categoryId 40100 resolves to Character not Vendor`() {
        // 真机复现：角色条目 categoryId=40100 曾被厂商区间误吞导致厂商详情加载失败
        val elementItem = ActionItem(
            id = 40922987L,
            relateItem = RelateItem(
                id = 34754957L,
                itemId = 89934L,
                itemType = "charactar",
                categoryId = 40100,
                nameCN = "知更鸟"
            )
        )
        val charParam = characterParam(elementItem)
        assertEquals(89934L, charParam.characterId)
        assertEquals(34754957L, charParam.characterNodeId)
        assertEquals("知更鸟", charParam.initialName)
    }

    @Test
    fun `resolveAction - character resolves to Character destination`() {
        val charParam = characterParam(
            ActionItem(
                id = 4L,
                relateItem = RelateItem(
                    id = 501L,
                    itemId = 601L,
                    itemType = "charactar",
                    nameCN = "初音未来"
                )
            )
        )
        assertEquals(601L, charParam.characterId)
        assertEquals(501L, charParam.characterNodeId)
        assertEquals("初音未来", charParam.initialName)
    }

    @Test
    fun `resolveAction - series resolves to Series destination`() {
        val seriesParam = seriesParam(
            ActionItem(
                id = 5L,
                relateItem = RelateItem(
                    id = 701L,
                    itemId = 801L,
                    itemType = "series",
                    name = "Figma"
                )
            )
        )
        assertEquals(801L, seriesParam.seriesId)
        assertEquals(701L, seriesParam.seriesNodeId)
        assertEquals("Figma", seriesParam.initialName)
    }

    @Test
    fun `resolveAction - person resolves to Person destination`() {
        val personParam = personParam(
            ActionItem(
                id = 6L,
                relateItem = RelateItem(
                    id = 901L,
                    itemId = 1001L,
                    itemType = "person",
                    name = "花泽香菜"
                )
            )
        )
        assertEquals(1001L, personParam.personId)
        assertEquals(901L, personParam.personNodeId)
        assertEquals("花泽香菜", personParam.initialName)
    }

    @Test
    fun `resolveAction - works resolves to Works destination`() {
        val worksParam = worksParam(
            ActionItem(
                id = 7L,
                relateItem = RelateItem(
                    id = 1101L,
                    itemId = 1201L,
                    itemType = "works",
                    name = "命运石之门"
                )
            )
        )
        assertEquals(1201L, worksParam.worksId)
        assertEquals("命运石之门", worksParam.initialName)
    }

    @Test
    fun `resolveAction - album resolves to Album destination`() {
        val albumParam = albumParam(
            ActionItem(
                id = 8L,
                relateItem = RelateItem(
                    id = 1301L,
                    itemId = 0L,
                    itemType = "album",
                    name = "手办实物摄影相册"
                )
            )
        )
        assertEquals(1301L, albumParam.albumId)
        assertEquals("手办实物摄影相册", albumParam.initialTitle)
    }

    @Test
    fun `resolveAction - hobby resolves to Hobby destination`() {
        val key = hobbyKey(
            ActionItem(
                id = 9L,
                relateItem = RelateItem(
                    id = 1401L,
                    itemId = 1501L,
                    itemType = "hobby",
                    nameCN = "初音未来 韶华手办",
                    cover = "/cover/miku.jpg"
                )
            )
        )
        assertEquals(1501L, key.param.itemId)
        assertEquals("hobby", key.param.itemType)
        assertEquals("初音未来 韶华手办", key.param.initialName)
        assertEquals("/cover/miku.jpg", key.param.initialCover)
    }

    @Test
    fun `resolveAction - null relateItem with user resolves to User destination`() {
        val key = userKey(
            ActionItem(
                id = 10L,
                relateItem = null,
                user = UserProfileSummary(userId = 888L, userName = "PoiUser")
            )
        )
        assertEquals(888L, key.param.userId)
    }

    @Test
    fun `resolveAction - invalid relateItem with 0 id resolves to Notice`() {
        val notice = assertIs<RouteTarget.Notice>(
            AppRouteResolver.resolveAction(
                ActionItem(
                    id = 11L,
                    relateItem = RelateItem(
                        id = 0L,
                        itemId = 0L,
                        itemType = "company",
                        name = "未知厂商"
                    )
                )
            )
        )
        assertEquals("条目信息暂未就绪：未知厂商", notice.message)
    }

    // ── 2. URL 解析：9 类站内实体 ────────────────────────────────────────

    @Test
    fun `resolveUrl - hobby and item urls resolve to HobbyDetailNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/hobby/59570",
            "https://www.hpoi.net.cn/hobby/59570/",
            "http://hpoi.net/item/59570?page=1#comments",
            "/hobby/59570",
            "hobby/59570",
            "/item/59570"
        )
        for (url in urls) {
            val key = hobbyUrlKey(url, "Expected Hobby route for $url")
            assertEquals(59570L, key.param.itemId, "itemId for $url")
        }
    }

    @Test
    fun `resolveUrl - album urls resolve to AlbumDetailNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/album/8888",
            "https://www.hpoi.net.cn/album/8888/",
            "http://hpoi.net/album/8888?sort=new",
            "/album/8888",
            "album/8888",
            "https://www.hpoi.net/album/8888#top"
        )
        for (url in urls) {
            val key = assertIs<AlbumDetailNavKey>(urlKey(url), "Expected Album route for $url")
            assertEquals(8888L, key.param.albumId, "albumId for $url")
        }
    }

    @Test
    fun `resolveUrl - character and charactar urls resolve to CharacterNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/charactar/8110",
            "https://www.hpoi.net.cn/character/8110/",
            "http://hpoi.net/charactar/8110?page=1",
            "/charactar/8110",
            "character/8110"
        )
        for (url in urls) {
            val key = assertIs<CharacterNavKey>(urlKey(url), "Expected Character route for $url")
            assertEquals(8110L, key.param.characterId, "characterId for $url")
        }
    }

    @Test
    fun `resolveUrl - post and posts urls resolve to PostDetailNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/posts/12345",
            "https://www.hpoi.net.cn/post/12345/",
            "http://hpoi.net/posts/12345?page=1",
            "/posts/12345",
            "/post/12345",
            "posts/12345"
        )
        for (url in urls) {
            val key = assertIs<PostDetailNavKey>(urlKey(url), "Expected Post route for $url")
            assertEquals(12345L, key.postId, "postId for $url")
        }
    }

    @Test
    fun `resolveUrl - person urls resolve to PersonDetailNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/person/222",
            "https://www.hpoi.net.cn/person/222/",
            "http://hpoi.net/person/222?tab=works#top",
            "/person/222",
            "person/222"
        )
        for (url in urls) {
            val key = assertIs<PersonDetailNavKey>(urlKey(url), "Expected Person route for $url")
            assertEquals(222L, key.param.personId, "personId for $url")
        }
    }

    @Test
    fun `resolveUrl - company and vendor urls resolve to VendorDetailNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/company/333",
            "https://www.hpoi.net.cn/vendor/333/",
            "http://hpoi.net/company/333?page=2",
            "/company/333",
            "vendor/333",
            "/vendor/333#items"
        )
        for (url in urls) {
            val key = assertIs<VendorDetailNavKey>(urlKey(url), "Expected Vendor route for $url")
            assertEquals(333L, key.param.companyId, "companyId for $url")
        }
    }

    @Test
    fun `resolveUrl - works urls resolve to WorksDetailNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/works/444",
            "https://www.hpoi.net.cn/works/444/",
            "http://hpoi.net/works/444?tab=hobby#top",
            "/works/444",
            "works/444"
        )
        for (url in urls) {
            val key = assertIs<WorksDetailNavKey>(urlKey(url), "Expected Works route for $url")
            assertEquals(444L, key.param.worksId, "worksId for $url")
        }
    }

    @Test
    fun `resolveUrl - series urls resolve to SeriesDetailNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/series/555",
            "https://www.hpoi.net.cn/series/555/",
            "http://hpoi.net/series/555?page=1#list",
            "/series/555",
            "series/555"
        )
        for (url in urls) {
            val key = assertIs<SeriesDetailNavKey>(urlKey(url), "Expected Series route for $url")
            assertEquals(555L, key.param.seriesId, "seriesId for $url")
        }
    }

    @Test
    fun `resolveUrl - user urls resolve to UserInfoNavKey`() {
        val urls = listOf(
            "https://www.hpoi.net/user/666",
            "https://www.hpoi.net.cn/user/666/",
            "http://hpoi.net/user/666?tab=collect#top",
            "/user/666",
            "user/666"
        )
        for (url in urls) {
            val key = assertIs<UserInfoNavKey>(urlKey(url), "Expected User route for $url")
            assertEquals(666L, key.param.userId, "userId for $url")
        }
    }

    @Test
    fun `resolveUrl - case insensitive host and path variants resolve internally`() {
        val hobby = hobbyUrlKey("HTTPS://WWW.HPOI.NET/HOBBY/42")
        assertEquals(42L, hobby.param.itemId)

        val relative = hobbyUrlKey("Hobby/42")
        assertEquals(42L, relative.param.itemId)

        val character = assertIs<CharacterNavKey>(urlKey("CHARACTER/42"))
        assertEquals(42L, character.param.characterId)
    }

    // ── 2b. 站外 / 伪装 / 空白 / 非法 ID ────────────────────────────────

    @Test
    fun `resolveUrl - external urls fall back to External preserving trimmed input`() {
        val external = "https://www.bilibili.com/video/BV1xx"
        assertEquals(RouteTarget.External(external), AppRouteResolver.resolveUrl(external))

        val trimmed = "  https://example.com/a?b=1  "
        assertEquals(RouteTarget.External("https://example.com/a?b=1"), AppRouteResolver.resolveUrl(trimmed))
    }

    @Test
    fun `resolveUrl - other site with hobby path is not treated as internal`() {
        val otherSite = "https://example.com/hobby/59570"
        assertEquals(RouteTarget.External(otherSite), AppRouteResolver.resolveUrl(otherSite))
    }

    @Test
    fun `resolveUrl - blank url returns empty External`() {
        assertEquals(RouteTarget.External(""), AppRouteResolver.resolveUrl("   "))
        assertEquals(RouteTarget.External(""), AppRouteResolver.resolveUrl(""))
    }

    @Test
    fun `resolveUrl - zero or malformed id falls back to External`() {
        assertEquals(RouteTarget.External("/hobby/0"), AppRouteResolver.resolveUrl("/hobby/0"))
        assertEquals(RouteTarget.External("/hobby/abc"), AppRouteResolver.resolveUrl("/hobby/abc"))
        assertEquals(RouteTarget.External("/hobby/12abc"), AppRouteResolver.resolveUrl("/hobby/12abc"))
    }

    // ── 3. resolveAction：全分类区间 / ID 回退 / 用户回退 ────────────────

    @Test
    fun `resolveAction - album classification by category range and actionType`() {
        for (categoryId in listOf(60000, 70000, 79999)) {
            val param = albumParam(actionOf(RelateItem(id = 5L, itemId = 6L, categoryId = categoryId)))
            assertEquals(5L, param.albumId, "albumId for categoryId=$categoryId")
        }

        val byActionType = albumParam(
            ActionItem(id = 1L, actionType = 3, relateItem = RelateItem(id = 9L, itemId = 10L))
        )
        assertEquals(9L, byActionType.albumId)

        val byIsAlbum = albumParam(actionOf(RelateItem(id = 11L, itemId = 12L, itemType = "ALBUM")))
        assertEquals(11L, byIsAlbum.albumId)
    }

    @Test
    fun `resolveAction - vendor only matches categoryId 40000`() {
        val param = vendorParam(actionOf(RelateItem(id = 1L, itemId = 2L, categoryId = 40000)))
        assertEquals(2L, param.companyId)
        assertEquals(1L, param.companyNodeId)

        // 40100 属于角色区间，绝不能落入厂商
        val character = characterParam(actionOf(RelateItem(id = 1L, itemId = 2L, categoryId = 40100)))
        assertEquals(2L, character.characterId)
    }

    @Test
    fun `resolveAction - character classification covers 40100-40599 and 50000-59999`() {
        for (categoryId in listOf(40100, 40599, 50000, 59999)) {
            val param = characterParam(actionOf(RelateItem(id = 7L, itemId = 8L, categoryId = categoryId)))
            assertEquals(8L, param.characterId, "characterId for categoryId=$categoryId")
            assertEquals(7L, param.characterNodeId, "characterNodeId for categoryId=$categoryId")
        }
    }

    @Test
    fun `resolveAction - series classification covers 80000-89999`() {
        for (categoryId in listOf(80000, 89999)) {
            val param = seriesParam(actionOf(RelateItem(id = 3L, itemId = 4L, categoryId = categoryId)))
            assertEquals(4L, param.seriesId, "seriesId for categoryId=$categoryId")
            assertEquals(3L, param.seriesNodeId, "seriesNodeId for categoryId=$categoryId")
        }
    }

    @Test
    fun `resolveAction - person classification covers 30000-39999`() {
        for (categoryId in listOf(30000, 39999)) {
            val param = personParam(actionOf(RelateItem(id = 3L, itemId = 4L, categoryId = categoryId)))
            assertEquals(4L, param.personId, "personId for categoryId=$categoryId")
            assertEquals(3L, param.personNodeId, "personNodeId for categoryId=$categoryId")
        }
    }

    @Test
    fun `resolveAction - works classification covers 20000-29999`() {
        for (categoryId in listOf(20000, 29999)) {
            val param = worksParam(actionOf(RelateItem(id = 3L, itemId = 4L, categoryId = categoryId)))
            assertEquals(4L, param.worksId, "worksId for categoryId=$categoryId")
        }
    }

    @Test
    fun `resolveAction - hobby is the default classification`() {
        val typed = hobbyKey(actionOf(RelateItem(id = 3L, itemId = 4L, categoryId = 10000, itemType = "Figure")))
        assertEquals(4L, typed.param.itemId)
        assertEquals("figure", typed.param.itemType)

        val noType = hobbyKey(actionOf(RelateItem(id = 3L, itemId = 4L)))
        assertEquals("hobby", noType.param.itemType)
    }

    @Test
    fun `resolveAction - node id falls back to the other id when itemId is zero`() {
        val character = characterParam(actionOf(RelateItem(id = 55L, itemId = 0L, itemType = "character")))
        assertEquals(55L, character.characterId)
        assertEquals(55L, character.characterNodeId)

        val album = albumParam(actionOf(RelateItem(id = 0L, itemId = 77L, itemType = "album")))
        assertEquals(77L, album.albumId)

        val vendor = vendorParam(actionOf(RelateItem(id = 0L, itemId = 88L, categoryId = 40000)))
        assertEquals(88L, vendor.companyId)
        assertEquals(88L, vendor.companyNodeId)
    }

    @Test
    fun `resolveAction - display name prefers non-blank nameCN then name and cover falls back to path`() {
        val key = hobbyKey(
            actionOf(
                RelateItem(
                    id = 1L,
                    itemId = 2L,
                    itemType = "hobby",
                    nameCN = "   ",
                    name = "Fallback Name",
                    path = "/path/cover.jpg"
                )
            )
        )
        assertEquals("Fallback Name", key.param.initialName)
        assertEquals("/path/cover.jpg", key.param.initialCover)
    }

    @Test
    fun `resolveAction - null relate without usable user yields plain notice`() {
        val noUser = AppRouteResolver.resolveAction(ActionItem(id = 1L, relateItem = null))
        assertEquals(RouteTarget.Notice("条目信息暂未就绪"), noUser)

        val zeroUser = AppRouteResolver.resolveAction(
            ActionItem(
                id = 1L,
                relateItem = null,
                user = UserProfileSummary(id = 0L, userId = 0L)
            )
        )
        assertEquals(RouteTarget.Notice("条目信息暂未就绪"), zeroUser)
    }

    @Test
    fun `resolveAction - user fallback prefers id over userId`() {
        val key = userKey(
            ActionItem(
                id = 1L,
                relateItem = null,
                user = UserProfileSummary(id = 12L, userId = 34L)
            )
        )
        assertEquals(12L, key.param.userId)
    }

    @Test
    fun `resolveAction - invalid id yields entity-specific notice`() {
        val anonymous = AppRouteResolver.resolveAction(
            actionOf(RelateItem(id = 0L, itemId = 0L, itemType = "character"))
        )
        assertEquals(RouteTarget.Notice("条目信息暂未就绪：角色详情"), anonymous)

        val hobby = AppRouteResolver.resolveAction(actionOf(RelateItem(id = 0L, itemId = 0L)))
        assertEquals(RouteTarget.Notice("条目信息暂未就绪：手办详情"), hobby)
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun actionOf(relate: RelateItem): ActionItem = ActionItem(id = 1L, relateItem = relate)

    private fun keyOf(actionItem: ActionItem): AppNavKey {
        val target = AppRouteResolver.resolveAction(actionItem)
        val internal = assertIs<RouteTarget.Internal>(target, "Expected Internal but was $target")
        return internal.key
    }

    private fun urlKey(url: String): AppNavKey {
        val target = AppRouteResolver.resolveUrl(url)
        val internal = assertIs<RouteTarget.Internal>(target, "Expected Internal for $url but was $target")
        return internal.key
    }

    private fun hobbyKey(actionItem: ActionItem): HobbyDetailNavKey =
        assertIs<HobbyDetailNavKey>(keyOf(actionItem))

    private fun hobbyUrlKey(url: String, hint: String = "url=$url"): HobbyDetailNavKey =
        assertIs<HobbyDetailNavKey>(urlKey(url), hint)

    private fun albumParam(actionItem: ActionItem): AlbumNavParam =
        assertIs<AlbumDetailNavKey>(keyOf(actionItem)).param

    private fun vendorParam(actionItem: ActionItem): VendorNavParam =
        assertIs<VendorDetailNavKey>(keyOf(actionItem)).param

    private fun characterParam(actionItem: ActionItem): CharacterNavParam =
        assertIs<CharacterNavKey>(keyOf(actionItem)).param

    private fun seriesParam(actionItem: ActionItem): SeriesNavParam =
        assertIs<SeriesDetailNavKey>(keyOf(actionItem)).param

    private fun personParam(actionItem: ActionItem): PersonNavParam =
        assertIs<PersonDetailNavKey>(keyOf(actionItem)).param

    private fun worksParam(actionItem: ActionItem): WorksNavParam =
        assertIs<WorksDetailNavKey>(keyOf(actionItem)).param

    private fun userKey(actionItem: ActionItem): UserInfoNavKey =
        assertIs<UserInfoNavKey>(keyOf(actionItem))
}
