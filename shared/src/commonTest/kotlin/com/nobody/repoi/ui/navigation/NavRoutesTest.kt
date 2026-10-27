package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.search.SearchNavParam
import com.nobody.repoi.ui.search.SearchTab
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavRoutesTest {

    @Test
    fun searchNavKey_defaultParamRoundTrip() {
        val key = SearchNavKey()
        assertEquals(SearchNavParam(), key.param)
        val custom = SearchNavKey(param = SearchNavParam(initialKeyword = "初音"))
        assertEquals("初音", custom.param.initialKeyword)
    }

    @Test
    fun hobbyDetailNavKey_carriesParam() {
        val param = DetailNavParam(itemId = 59570L, itemType = "hobby")
        val key = HobbyDetailNavKey(param = param)
        assertEquals(59570L, key.param.itemId)
        assertEquals("hobby", key.param.itemType)
    }

    @Test
    fun navKeyIds_defaultDistinct() {
        val hobbyA = HobbyListNavKey(param = HobbyListNavParam())
        val hobbyB = HobbyListNavKey(param = HobbyListNavParam())
        assertTrue(hobbyA.id != hobbyB.id)
        val searchA = SearchNavKey()
        val searchB = SearchNavKey()
        assertTrue(searchA.id != searchB.id)
        val seriesA = SeriesDetailNavKey(param = SeriesNavParam(seriesId = 1L))
        val seriesB = SeriesDetailNavKey(param = SeriesNavParam(seriesId = 1L))
        assertTrue(seriesA.id != seriesB.id)
        val personA = PersonDetailNavKey(param = PersonNavParam(personId = 1589L))
        val personB = PersonDetailNavKey(param = PersonNavParam(personId = 1589L))
        assertTrue(personA.id != personB.id)
        val albumA = AlbumDetailNavKey(param = AlbumNavParam(albumId = 123L))
        val albumB = AlbumDetailNavKey(param = AlbumNavParam(albumId = 123L))
        assertTrue(albumA.id != albumB.id)
        val vendorA = VendorDetailNavKey(param = VendorNavParam(companyId = 456L))
        val vendorB = VendorDetailNavKey(param = VendorNavParam(companyId = 456L))
        assertTrue(vendorA.id != vendorB.id)
    }

    @Test
    fun fromCategoryId_unknownFallsBackToHobby() {
        assertEquals(SearchTab.HOBBY, SearchTab.fromCategoryId(99999))
    }

    @Test
    fun userCollectNavKey_carriesParamAndDistinctIds() {
        val param = UserCollectNavParam(userId = 12345L, initialCollectState = "care", initialOrder = "release")
        val keyA = UserCollectNavKey(param = param)
        val keyB = UserCollectNavKey(param = param)
        assertEquals(12345L, keyA.param.userId)
        assertEquals("care", keyA.param.initialCollectState)
        assertEquals("release", keyA.param.initialOrder)
        assertTrue(keyA.id != keyB.id)
    }

    @Test
    fun userCollectNavParam_defaultArguments() {
        val param = UserCollectNavParam(userId = 888L)
        assertEquals(888L, param.userId)
        // 深链三态语义：null = 调用方未指定，默认值必须保持 null，
        // 绝不能在参数层就写死成「预定/release」——否则 ViewModel 无从区分
        // 「未指定（走默认档）」与「调用方显式指定」。
        // 真正的兜底发生在 UserCollectViewModel.resolveCollectState / resolveOrderFor。
        assertNull(param.initialCollectState)
        assertNull(param.initialOrder)
        assertNull(param.initialCategory)
        assertNull(param.initialSubCategoryId)
        assertNull(param.initialTag)
        assertEquals("fav", param.albumType)
    }

    @Test
    fun isTopLevelNavKey_detectsCorrectly() {
        assertTrue(isTopLevelNavKey(HomeNavKey))
        assertTrue(isTopLevelNavKey(ItemsNavKey))
        assertTrue(isTopLevelNavKey(AlbumNavKey))
        assertTrue(isTopLevelNavKey(FollowNavKey))
        assertTrue(isTopLevelNavKey(ProfileNavKey))

        assertFalse(isTopLevelNavKey(SearchNavKey()))
        assertFalse(isTopLevelNavKey(HobbyDetailNavKey(DetailNavParam(itemId = 1L))))
        assertFalse(isTopLevelNavKey(AlbumDetailNavKey(AlbumNavParam(albumId = 1L))))
        assertFalse(isTopLevelNavKey(null))
        assertFalse(isTopLevelNavKey("other"))
    }

    @Test
    fun safePop_singleEntryDoesNotPop() {
        val stack = NavBackStack<NavKey>(HomeNavKey)
        val popped = stack.safePop()
        assertFalse(popped)
        assertEquals(1, stack.size)
        assertEquals(HomeNavKey, stack.last())
    }

    @Test
    fun safePop_topIsTopLevelDoesNotPop() {
        val stack = NavBackStack<NavKey>(HomeNavKey, ItemsNavKey)
        val popped = stack.safePop()
        assertFalse(popped)
        assertEquals(2, stack.size)
        assertEquals(ItemsNavKey, stack.last())
    }

    @Test
    fun safePop_multiLevelDetailPopsUntilRootTopLevel() {
        val hobbyKey = HobbyDetailNavKey(DetailNavParam(itemId = 100L))
        val albumKey = AlbumDetailNavKey(AlbumNavParam(albumId = 200L))
        val stack = NavBackStack<NavKey>(HomeNavKey, hobbyKey, albumKey)

        assertEquals(3, stack.size)

        // 1. 从相册详情安全返回到手办详情
        assertTrue(stack.safePop())
        assertEquals(2, stack.size)
        assertEquals(hobbyKey, stack.last())

        // 2. 从手办详情安全返回到首页根视图
        assertTrue(stack.safePop())
        assertEquals(1, stack.size)
        assertEquals(HomeNavKey, stack.last())

        // 3. 已到达根视图，继续出栈被拦截，绝不击穿栈底
        assertFalse(stack.safePop())
        assertEquals(1, stack.size)
        assertEquals(HomeNavKey, stack.last())
    }

    @Test
    fun zoneNavKeys_instantiationAndEquivalence() {
        assertEquals(ZoneBrowseNavKey, ZoneBrowseNavKey)
        assertEquals(ZoneSettingsNavKey, ZoneSettingsNavKey)

        val detailA = ZoneDetailNavKey(nodeId = 100L, initialName = "手办", id = "fixed-id")
        val detailB = ZoneDetailNavKey(nodeId = 100L, initialName = "手办", id = "fixed-id")
        assertEquals(detailA, detailB)
        assertEquals(100L, detailA.nodeId)
        assertEquals("手办", detailA.initialName)

        val detailRandomA = ZoneDetailNavKey(nodeId = 100L)
        val detailRandomB = ZoneDetailNavKey(nodeId = 100L)
        assertTrue(detailRandomA.id != detailRandomB.id)
    }
}
