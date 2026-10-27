package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateItem
import com.nobody.repoi.data.remote.model.UserProfileSummary
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class FeedActionDispatcherTest {

    @Test
    fun `resolveDestination - company or vendor resolves to Vendor destination`() {
        // 1. 通过 itemType = "company"
        val companyItem = ActionItem(
            id = 1L,
            relateItem = RelateItem(
                id = 100L,
                itemId = 200L,
                itemType = "company",
                name = "Good Smile Company",
                cover = "/cover/gsc.jpg"
            )
        )
        val dest1 = FeedActionDispatcher.resolveDestination(companyItem)
        assertTrue(dest1 is FeedTargetDestination.Vendor)
        val vendor1 = (dest1 as FeedTargetDestination.Vendor).param
        assertEquals(200L, vendor1.companyId)
        assertEquals(100L, vendor1.companyNodeId)
        assertEquals("Good Smile Company", vendor1.initialName)
        assertEquals("/cover/gsc.jpg", vendor1.initialCover)

        // 2. 通过 itemType = "vendor"
        val vendorItem = ActionItem(
            id = 2L,
            relateItem = RelateItem(
                id = 101L,
                itemId = 201L,
                itemType = "vendor",
                name = "ALTER"
            )
        )
        val dest2 = FeedActionDispatcher.resolveDestination(vendorItem)
        assertTrue(dest2 is FeedTargetDestination.Vendor)
        val vendor2 = (dest2 as FeedTargetDestination.Vendor).param
        assertEquals(201L, vendor2.companyId)
        assertEquals("ALTER", vendor2.initialName)

        // 3. 通过 categoryId in 40000..49999
        val categoryVendorItem = ActionItem(
            id = 3L,
            relateItem = RelateItem(
                id = 300L,
                itemId = 0L,
                categoryId = 40000,
                name = "Max Factory"
            )
        )
        val dest3 = FeedActionDispatcher.resolveDestination(categoryVendorItem)
        assertTrue(dest3 is FeedTargetDestination.Vendor)
        val vendor3 = (dest3 as FeedTargetDestination.Vendor).param
        assertEquals(300L, vendor3.companyId)
        assertEquals(300L, vendor3.companyNodeId)
    }

    @Test
    fun `resolveDestination - charactar with categoryId 40100 resolves to Character not Vendor`() {
        // 真机复现：角色条目 categoryId=40100 曾被厂商区间 40000..49999 误吞导致厂商详情加载失败
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
        val dest = FeedActionDispatcher.resolveDestination(elementItem)
        assertTrue(dest is FeedTargetDestination.Character)
        val charParam = (dest as FeedTargetDestination.Character).param
        assertEquals(89934L, charParam.characterId)
        assertEquals(34754957L, charParam.characterNodeId)
    }

    @Test
    fun `resolveDestination - character resolves to Character destination`() {
        val characterItem = ActionItem(
            id = 4L,
            relateItem = RelateItem(
                id = 501L,
                itemId = 601L,
                itemType = "charactar",
                nameCN = "初音未来"
            )
        )
        val dest = FeedActionDispatcher.resolveDestination(characterItem)
        assertTrue(dest is FeedTargetDestination.Character)
        val charParam = (dest as FeedTargetDestination.Character).param
        assertEquals(601L, charParam.characterId)
        assertEquals(501L, charParam.characterNodeId)
        assertEquals("初音未来", charParam.initialName)
    }

    @Test
    fun `resolveDestination - series resolves to Series destination`() {
        val seriesItem = ActionItem(
            id = 5L,
            relateItem = RelateItem(
                id = 701L,
                itemId = 801L,
                itemType = "series",
                name = "Figma"
            )
        )
        val dest = FeedActionDispatcher.resolveDestination(seriesItem)
        assertTrue(dest is FeedTargetDestination.Series)
        val seriesParam = (dest as FeedTargetDestination.Series).param
        assertEquals(801L, seriesParam.seriesId)
        assertEquals(701L, seriesParam.seriesNodeId)
        assertEquals("Figma", seriesParam.initialName)
    }

    @Test
    fun `resolveDestination - person resolves to Person destination`() {
        val personItem = ActionItem(
            id = 6L,
            relateItem = RelateItem(
                id = 901L,
                itemId = 1001L,
                itemType = "person",
                name = "花泽香菜"
            )
        )
        val dest = FeedActionDispatcher.resolveDestination(personItem)
        assertTrue(dest is FeedTargetDestination.Person)
        val personParam = (dest as FeedTargetDestination.Person).param
        assertEquals(1001L, personParam.personId)
        assertEquals(901L, personParam.personNodeId)
        assertEquals("花泽香菜", personParam.initialName)
    }

    @Test
    fun `resolveDestination - works resolves to Works destination`() {
        val worksItem = ActionItem(
            id = 7L,
            relateItem = RelateItem(
                id = 1101L,
                itemId = 1201L,
                itemType = "works",
                name = "命运石之门"
            )
        )
        val dest = FeedActionDispatcher.resolveDestination(worksItem)
        assertTrue(dest is FeedTargetDestination.Works)
        val worksParam = (dest as FeedTargetDestination.Works).param
        assertEquals(1201L, worksParam.worksId)
        assertEquals("命运石之门", worksParam.initialName)
    }

    @Test
    fun `resolveDestination - album resolves to Album destination`() {
        val albumItem = ActionItem(
            id = 8L,
            relateItem = RelateItem(
                id = 1301L,
                itemId = 0L,
                itemType = "album",
                name = "手办实物摄影相册"
            )
        )
        val dest = FeedActionDispatcher.resolveDestination(albumItem)
        assertTrue(dest is FeedTargetDestination.Album)
        val albumParam = (dest as FeedTargetDestination.Album).param
        assertEquals(1301L, albumParam.albumId)
        assertEquals("手办实物摄影相册", albumParam.initialTitle)
    }

    @Test
    fun `resolveDestination - hobby resolves to Hobby destination`() {
        val hobbyItem = ActionItem(
            id = 9L,
            relateItem = RelateItem(
                id = 1401L,
                itemId = 1501L,
                itemType = "hobby",
                nameCN = "初音未来 韶华手办",
                cover = "/cover/miku.jpg"
            )
        )
        val dest = FeedActionDispatcher.resolveDestination(hobbyItem)
        assertTrue(dest is FeedTargetDestination.Hobby)
        val hobbyParam = (dest as FeedTargetDestination.Hobby).param
        assertEquals(1501L, hobbyParam.itemId)
        assertEquals("初音未来 韶华手办", hobbyParam.initialName)
        assertEquals("/cover/miku.jpg", hobbyParam.initialCover)
    }

    @Test
    fun `resolveDestination - null relateItem with user resolves to User destination`() {
        val userItem = ActionItem(
            id = 10L,
            relateItem = null,
            user = UserProfileSummary(
                userId = 888L,
                userName = "PoiUser"
            )
        )
        val dest = FeedActionDispatcher.resolveDestination(userItem)
        assertTrue(dest is FeedTargetDestination.User)
        assertEquals(888L, (dest as FeedTargetDestination.User).userId)
    }

    @Test
    fun `resolveDestination - invalid relateItem with 0 id resolves to Invalid`() {
        val invalidItem = ActionItem(
            id = 11L,
            relateItem = RelateItem(
                id = 0L,
                itemId = 0L,
                itemType = "company",
                name = "未知厂商"
            )
        )
        val dest = FeedActionDispatcher.resolveDestination(invalidItem)
        assertTrue(dest is FeedTargetDestination.Invalid)
    }
}
