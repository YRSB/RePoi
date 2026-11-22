package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.search.SearchNavParam
import com.nobody.repoi.ui.search.SearchResultNavParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
        navigator.navigateToSearchResult(keyword = "ALTER", companyNodeId = 3303L)

        assertEquals(4, rootStack.size)
        val searchResultKey = assertIs<SearchResultNavKey>(rootStack.last())
        assertEquals("ALTER", searchResultKey.param.keyword)
        assertEquals(3303L, searchResultKey.param.companyNodeId)

        navigator.navigateToSearchResult(SearchResultNavParam(keyword = "GSC"))
        assertEquals(5, rootStack.size)
        val gscKey = assertIs<SearchResultNavKey>(rootStack.last())
        assertEquals("GSC", gscKey.param.keyword)

        navigator.navigateToImageViewer(listOf("https://example.com/2.jpg"), 0)
        assertEquals(6, rootStack.size)
        val imageViewerNavKey = assertIs<ImageViewerNavKey>(rootStack.last())
        assertEquals(listOf("https://example.com/2.jpg"), imageViewerNavKey.param.images)

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

        stack.navigateToSearchResult(keyword = "手办", category = 10000, companyNodeId = 2202L, companyName = "Max Factory")
        assertEquals(9, stack.size)
        val searchResultKey = assertIs<SearchResultNavKey>(stack.last())
        assertEquals("手办", searchResultKey.param.keyword)
        assertEquals(10000, searchResultKey.param.category)
        assertEquals(2202L, searchResultKey.param.companyNodeId)
        assertEquals("Max Factory", searchResultKey.param.companyName)

        stack.navigateToSearchResult(SearchResultNavParam(keyword = "粘土人"))
        assertEquals(10, stack.size)
        val searchResultParamKey = assertIs<SearchResultNavKey>(stack.last())
        assertEquals("粘土人", searchResultParamKey.param.keyword)

        stack.navigateToImageViewer(listOf("https://example.com/1.jpg"), 0)
        assertEquals(11, stack.size)
        val imageViewerKey = assertIs<ImageViewerNavKey>(stack.last())
        assertEquals(listOf("https://example.com/1.jpg"), imageViewerKey.param.images)
        assertEquals(0, imageViewerKey.param.initialIndex)
    }

    @Test
    fun testCommitSearchResultFromLandingInitialSearch() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigateToSearch(SearchNavParam(initialKeyword = "初音"))
        assertEquals(2, rootStack.size)
        assertIs<SearchNavKey>(rootStack.last())

        navigator.commitSearchResult(SearchResultNavParam(keyword = "初音"))
        assertEquals(3, rootStack.size)
        assertEquals(MainNavKey, rootStack[0])
        assertIs<SearchNavKey>(rootStack[1])
        val resultKey = assertIs<SearchResultNavKey>(rootStack[2])
        assertEquals("初音", resultKey.param.keyword)

        assertTrue(navigator.pop())
        assertEquals(2, rootStack.size)
        assertIs<SearchNavKey>(rootStack.last())
    }

    @Test
    fun testNavigateToSearchResultExternalDirect() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigateToDetail(1001L)
        assertEquals(2, rootStack.size)
        assertIs<HobbyDetailNavKey>(rootStack.last())

        navigator.navigateToSearchResult(SearchResultNavParam(keyword = "GSC"))
        assertEquals(3, rootStack.size)
        assertEquals(MainNavKey, rootStack[0])
        assertIs<HobbyDetailNavKey>(rootStack[1])
        val resultKey = assertIs<SearchResultNavKey>(rootStack[2])
        assertEquals("GSC", resultKey.param.keyword)

        assertTrue(navigator.pop())
        assertEquals(2, rootStack.size)
        assertIs<HobbyDetailNavKey>(rootStack.last())
    }

    @Test
    fun testSearchResultReSearchCancelAndPop() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigateToSearch(SearchNavParam(initialKeyword = "初音"))
        navigator.commitSearchResult(SearchResultNavParam(keyword = "初音"))
        assertEquals(3, rootStack.size)

        navigator.navigateToSearch(SearchNavParam(initialKeyword = "初音", initialCategory = 10000))
        assertEquals(4, rootStack.size)
        assertIs<SearchNavKey>(rootStack.last())

        assertTrue(navigator.pop())
        assertEquals(3, rootStack.size)
        val resultKey = assertIs<SearchResultNavKey>(rootStack.last())
        assertEquals("初音", resultKey.param.keyword)
    }

    @Test
    fun testCommitSearchResultReSearchConvergence() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigateToSearch(SearchNavParam(initialKeyword = "初音"))
        navigator.commitSearchResult(SearchResultNavParam(keyword = "初音"))
        assertEquals(3, rootStack.size)

        navigator.navigateToSearch(SearchNavParam(initialKeyword = "初音", initialCategory = 10000))
        assertEquals(4, rootStack.size)

        navigator.commitSearchResult(SearchResultNavParam(keyword = "初音未来"))
        assertEquals(3, rootStack.size)
        assertEquals(MainNavKey, rootStack[0])
        assertIs<SearchNavKey>(rootStack[1])
        val newResultKey = assertIs<SearchResultNavKey>(rootStack[2])
        assertEquals("初音未来", newResultKey.param.keyword)

        val searchResultCount = rootStack.count { it is SearchResultNavKey }
        assertEquals(1, searchResultCount)
        assertTrue(navigator.pop())
        assertEquals(2, rootStack.size)
        assertIs<SearchNavKey>(rootStack.last())
    }

    @Test
    fun testCommitSearchResultExternalDirectReSearchConvergence() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigateToDetail(2002L)
        navigator.navigateToSearchResult(SearchResultNavParam(keyword = "GSC"))
        assertEquals(3, rootStack.size)

        navigator.navigateToSearch(SearchNavParam(initialKeyword = "GSC"))
        assertEquals(4, rootStack.size)

        navigator.commitSearchResult(SearchResultNavParam(keyword = "MaxFactory"))
        assertEquals(3, rootStack.size)
        assertEquals(MainNavKey, rootStack[0])
        assertIs<HobbyDetailNavKey>(rootStack[1])
        val newResultKey = assertIs<SearchResultNavKey>(rootStack[2])
        assertEquals("MaxFactory", newResultKey.param.keyword)

        val searchResultCount = rootStack.count { it is SearchResultNavKey }
        assertEquals(1, searchResultCount)

        assertTrue(navigator.pop())
        assertEquals(2, rootStack.size)
        assertIs<HobbyDetailNavKey>(rootStack.last())
    }

    @Test
    fun testCommitSearchResultMultipleRoundsConvergence() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigateToSearch(SearchNavParam(initialKeyword = "round0"))
        navigator.commitSearchResult(SearchResultNavParam(keyword = "round0"))
        assertEquals(3, rootStack.size)

        for (i in 1..5) {
            navigator.navigateToSearch(SearchNavParam(initialKeyword = "round${i - 1}"))
            assertEquals(4, rootStack.size)
            assertIs<SearchNavKey>(rootStack.last())

            navigator.commitSearchResult(SearchResultNavParam(keyword = "round$i"))

            assertEquals(3, rootStack.size)
            val resultKey = assertIs<SearchResultNavKey>(rootStack.last())
            assertEquals("round$i", resultKey.param.keyword)
            val searchResultCount = rootStack.count { it is SearchResultNavKey }
            assertEquals(1, searchResultCount)
        }

        assertTrue(navigator.pop())
        assertEquals(2, rootStack.size)
        assertIs<SearchNavKey>(rootStack.last())

        assertTrue(navigator.pop())
        assertEquals(1, rootStack.size)
        assertEquals(MainNavKey, rootStack.last())
        assertFalse(navigator.pop())
    }

    @Test
    fun testNavBackStackCommitSearchResultDirectConvergence() {
        val stack = NavBackStack<NavKey>(MainNavKey)

        stack.navigateToSearch(keyword = "ALTER")
        stack.commitSearchResult(keyword = "ALTER", category = 10000)
        assertEquals(3, stack.size)
        val firstResult = assertIs<SearchResultNavKey>(stack.last())
        assertEquals("ALTER", firstResult.param.keyword)

        stack.navigateToSearch(keyword = "ALTER", category = 10000)
        assertEquals(4, stack.size)
        stack.commitSearchResult(keyword = "ALTER 1/7", category = 10000)
        assertEquals(3, stack.size)
        val secondResult = assertIs<SearchResultNavKey>(stack.last())
        assertEquals("ALTER 1/7", secondResult.param.keyword)
        assertEquals(1, stack.count { it is SearchResultNavKey })
    }
}
