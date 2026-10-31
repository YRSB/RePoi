package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 验证全局单根栈导航语义：[RePoiNavigator] 根栈出入栈、[RePoiNavigationState] Tab 状态，
 * 以及 [NavExtensions] 强类型跳转扩展。
 */
class NavExtensionsTest {

    private fun testTabState(start: AppDestinations = AppDestinations.HOME): RePoiNavigationState {
        val stacks = mapOf(
            AppDestinations.HOME to NavBackStack<NavKey>(HomeNavKey),
            AppDestinations.ITEMS to NavBackStack<NavKey>(ItemsNavKey),
            AppDestinations.ALBUM to NavBackStack<NavKey>(AlbumNavKey),
            AppDestinations.FOLLOW to NavBackStack<NavKey>(FollowNavKey),
            AppDestinations.PROFILE to NavBackStack<NavKey>(ProfileNavKey),
        )
        return RePoiNavigationState(
            startDestination = start,
            currentDestinationState = mutableStateOf(start),
            backStacks = stacks
        )
    }

    private fun testNavigator(): Pair<RePoiNavigator, RePoiNavigationState> {
        val tabState = testTabState()
        val rootBackStack = NavBackStack<NavKey>(MainNavKey)
        return RePoiNavigator(rootBackStack = rootBackStack, tabState = tabState) to tabState
    }

    @Test
    fun testNavigatePushesOntoRootBackStack() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigate(HobbyDetailNavKey(1L))
        assertEquals(2, rootStack.size)
        val detailKey = assertIs<HobbyDetailNavKey>(rootStack.last())
        assertEquals(1L, detailKey.param.itemId)

        navigator.navigate(UserInfoNavKey(UserInfoNavParam(userId = 2L)))
        assertEquals(3, rootStack.size)
        val userKey = assertIs<UserInfoNavKey>(rootStack.last())
        assertEquals(2L, userKey.param.userId)

        assertEquals(MainNavKey, rootStack.first())
    }

    @Test
    fun testPopAtRootReturnsFalse() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        assertFalse(navigator.pop())
        assertEquals(1, rootStack.size)
        assertEquals(MainNavKey, rootStack.last())
    }

    @Test
    fun testPopUnwindsToMainKey() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigate(HobbyDetailNavKey(1L))
        navigator.navigate(UserInfoNavKey(UserInfoNavParam(userId = 2L)))
        assertEquals(3, rootStack.size)

        assertTrue(navigator.pop())
        assertIs<HobbyDetailNavKey>(rootStack.last())
        assertTrue(navigator.pop())
        assertEquals(1, rootStack.size)
        assertEquals(MainNavKey, rootStack.last())
        assertFalse(navigator.pop())
    }

    @Test
    fun testSelectTabOnlyChangesDestination() {
        val (navigator, tabState) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.selectTab(AppDestinations.ITEMS)
        assertEquals(AppDestinations.ITEMS, tabState.currentDestination)
        assertEquals(1, rootStack.size)
        assertEquals(MainNavKey, rootStack.last())

        val detailKey = HobbyDetailNavKey(7L)
        navigator.navigate(detailKey)

        navigator.selectTab(AppDestinations.ALBUM)
        assertEquals(AppDestinations.ALBUM, tabState.currentDestination)
        assertEquals(2, rootStack.size)
        assertEquals(detailKey, rootStack.last())
    }

    @Test
    fun testTabStacksStayAtTheirRootKey() {
        val (navigator, tabState) = testNavigator()

        val expectedRoots = mapOf(
            AppDestinations.HOME to HomeNavKey,
            AppDestinations.ITEMS to ItemsNavKey,
            AppDestinations.ALBUM to AlbumNavKey,
            AppDestinations.FOLLOW to FollowNavKey,
            AppDestinations.PROFILE to ProfileNavKey,
        )
        assertEquals(expectedRoots.keys, tabState.backStacks.keys)
        expectedRoots.forEach { (destination, rootKey) ->
            val stack = tabState.backStacks.getValue(destination)
            assertEquals(1, stack.size)
            assertEquals(rootKey, stack.single())
        }

        navigator.selectTab(AppDestinations.PROFILE)
        navigator.navigate(HobbyDetailNavKey(3L))

        expectedRoots.forEach { (destination, rootKey) ->
            val stack = tabState.backStacks.getValue(destination)
            assertEquals(1, stack.size)
            assertEquals(rootKey, stack.single())
        }
    }

    @Test
    fun testSemanticExtensionsPushToRootBackStack() {
        val (navigator, tabState) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.selectTab(AppDestinations.ITEMS)
        navigator.navigateToDetail(1001L)
        navigator.navigateToAlbum(2002L)

        assertEquals(3, rootStack.size)
        val albumKey = assertIs<AlbumDetailNavKey>(rootStack.last())
        assertEquals(2002L, albumKey.param.albumId)

        tabState.backStacks.forEach { (_, stack) -> assertEquals(1, stack.size) }
    }

    @Test
    fun testSemanticNavigationHelpers() {
        val stack = NavBackStack<NavKey>(MainNavKey)

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
