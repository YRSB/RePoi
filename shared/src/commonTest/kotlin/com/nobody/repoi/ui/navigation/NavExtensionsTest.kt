package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.person.PersonNavParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 验证 [NavExtensions] 核心栈管理原语与导航扩展。
 */
class NavExtensionsTest {

    @Test
    fun testSwitchTopLevelTab_FromHomeToItems() {
        val stack = NavBackStack<NavKey>(HomeNavKey)
        stack.switchTopLevelTab(AppDestinations.ITEMS)

        assertEquals(2, stack.size)
        assertEquals(HomeNavKey, stack[0])
        assertEquals(ItemsNavKey, stack[1])
    }

    @Test
    fun testSwitchTopLevelTab_FromItemsToHome() {
        val stack = NavBackStack<NavKey>(HomeNavKey, ItemsNavKey)
        stack.switchTopLevelTab(AppDestinations.HOME)

        assertEquals(1, stack.size)
        assertEquals(HomeNavKey, stack[0])
    }

    @Test
    fun testSwitchTopLevelTab_ClearsDeepSecondaryStack() {
        val stack = NavBackStack<NavKey>(
            HomeNavKey,
            ItemsNavKey,
            HobbyDetailNavKey(123L),
            ZoneBrowseNavKey
        )
        stack.switchTopLevelTab(AppDestinations.ALBUM)

        assertEquals(2, stack.size)
        assertEquals(HomeNavKey, stack[0])
        assertEquals(AlbumNavKey, stack[1])
    }

    @Test
    fun testPopToTopLevel_ClearsDeepStackToHome() {
        val stack = NavBackStack<NavKey>(
            HomeNavKey,
            HobbyDetailNavKey(123L),
            PersonDetailNavKey(PersonNavParam(personId = 456L))
        )
        stack.popToTopLevel()

        assertEquals(1, stack.size)
        assertEquals(HomeNavKey, stack.last())
    }

    @Test
    fun testPopToTopLevel_ClearsDeepStackToProfileTab() {
        val stack = NavBackStack<NavKey>(
            HomeNavKey,
            ProfileNavKey,
            SettingsNavKey
        )
        stack.popToTopLevel()

        assertEquals(2, stack.size)
        assertEquals(ProfileNavKey, stack.last())
    }

    @Test
    fun testSafePop_ProtectsRootAndTopLevel() {
        val rootStack = NavBackStack<NavKey>(HomeNavKey)
        val poppedRoot = rootStack.safePop()
        assertFalse(poppedRoot)
        assertEquals(1, rootStack.size)

        val topLevelStack = NavBackStack<NavKey>(HomeNavKey, ItemsNavKey)
        val poppedTopLevel = topLevelStack.safePop()
        assertFalse(poppedTopLevel)
        assertEquals(2, topLevelStack.size)

        val detailStack = NavBackStack<NavKey>(HomeNavKey, HobbyDetailNavKey(123L))
        val poppedDetail = detailStack.safePop()
        assertTrue(poppedDetail)
        assertEquals(1, detailStack.size)
        assertEquals(HomeNavKey, detailStack.last())
    }

    @Test
    fun testSemanticNavigationHelpers() {
        val stack = NavBackStack<NavKey>(HomeNavKey)

        stack.navigateToDetail(999L)
        assertEquals(2, stack.size)
        val detailKey = assertIs<HobbyDetailNavKey>(stack.last())
        assertEquals(999L, detailKey.param.itemId)

        stack.navigateToUser(888L)
        assertEquals(3, stack.size)
        val userKey = assertIs<UserInfoNavKey>(stack.last())
        assertEquals(888L, userKey.param.userId)

        stack.navigateToSearch(keyword = "初音", category = 10000, companyNodeId = 1101L, companyName = "GoodSmile")
        assertEquals(4, stack.size)
        val searchKey = assertIs<SearchNavKey>(stack.last())
        assertEquals("初音", searchKey.param.initialKeyword)
        assertEquals(10000, searchKey.param.initialCategory)
        assertEquals(1101L, searchKey.param.initialCompanyNodeId)
        assertEquals("GoodSmile", searchKey.param.initialCompanyName)

        stack.navigateToVendorActions(companyNodeId = 2202L, companyName = "Max Factory")
        assertEquals(5, stack.size)
        val vendorActionsKey = assertIs<VendorActionsNavKey>(stack.last())
        assertEquals(2202L, vendorActionsKey.param.companyNodeId)
        assertEquals("Max Factory", vendorActionsKey.param.companyName)

        stack.navigateToFriendList(userId = 3303L, initialType = 1)
        assertEquals(6, stack.size)
        val friendListKey = assertIs<FriendListNavKey>(stack.last())
        assertEquals(3303L, friendListKey.param.userId)
        assertEquals(1, friendListKey.param.initialType)

        stack.navigateToMessageChat(targetUserId = 4404L, targetUserName = "Alice")
        assertEquals(7, stack.size)
        val chatKey = assertIs<MessageChatNavKey>(stack.last())
        assertEquals(4404L, chatKey.param.targetUserId)
        assertEquals("Alice", chatKey.param.targetUserName)

        stack.navigateToCommentDetail(commentId = 5505L, relateNodeId = 6606L, isDiscuss = true)
        assertEquals(8, stack.size)
        val commentKey = assertIs<CommentDetailNavKey>(stack.last())
        assertEquals(5505L, commentKey.param.commentId)
        assertEquals(6606L, commentKey.param.relateNodeId)
        assertEquals(true, commentKey.param.isDiscuss)
    }
}
