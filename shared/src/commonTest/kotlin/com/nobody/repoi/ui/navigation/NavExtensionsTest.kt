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
        navigator.navigateToSearchResult(keyword = "ALTER", companyNodeId = 3303L)

        assertEquals(4, rootStack.size)
        val searchResultKey = assertIs<SearchResultNavKey>(rootStack.last())
        assertEquals("ALTER", searchResultKey.param.keyword)
        assertEquals(3303L, searchResultKey.param.companyNodeId)

        navigator.navigateToSearchResult(SearchResultNavParam(keyword = "GSC"))
        assertEquals(5, rootStack.size)
        val gscKey = assertIs<SearchResultNavKey>(rootStack.last())
        assertEquals("GSC", gscKey.param.keyword)

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
    }

    @Test
    fun testCommitSearchResultFromLandingInitialSearch() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        navigator.navigateToSearch(SearchNavParam(initialKeyword = "初音"))
        assertEquals(2, rootStack.size)
        assertIs<SearchNavKey>(rootStack.last())

        // 普通首搜：[MainNavKey, SearchNavKey] -> commitSearchResult -> [MainNavKey, SearchNavKey, SearchResultNavKey]
        navigator.commitSearchResult(SearchResultNavParam(keyword = "初音"))
        assertEquals(3, rootStack.size)
        assertEquals(MainNavKey, rootStack[0])
        assertIs<SearchNavKey>(rootStack[1])
        val resultKey = assertIs<SearchResultNavKey>(rootStack[2])
        assertEquals("初音", resultKey.param.keyword)

        // 验证返回手势能退回原输入现场落地页
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

        // 外部直达首搜：[MainNavKey, HobbyDetailNavKey] -> navigateToSearchResult -> [MainNavKey, HobbyDetailNavKey, SearchResultNavKey]
        navigator.navigateToSearchResult(SearchResultNavParam(keyword = "GSC"))
        assertEquals(3, rootStack.size)
        assertEquals(MainNavKey, rootStack[0])
        assertIs<HobbyDetailNavKey>(rootStack[1])
        val resultKey = assertIs<SearchResultNavKey>(rootStack[2])
        assertEquals("GSC", resultKey.param.keyword)

        // 验证返回手势直接退回原详情页，不经过落地页
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

        // 用户在结果页点击搜索栏发起二次重搜，压入带有当前词与分类的 SearchNavKey
        navigator.navigateToSearch(SearchNavParam(initialKeyword = "初音", initialCategory = 10000))
        assertEquals(4, rootStack.size)
        assertIs<SearchNavKey>(rootStack.last())

        // 用户未做修改直接返回，验证精准回退到原 SearchResultNavKey，保留原结果页现场
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

        // 二次重搜：[MainNavKey, SearchNavKey, SearchResultNavKey, SearchNavKey]
        navigator.navigateToSearch(SearchNavParam(initialKeyword = "初音", initialCategory = 10000))
        assertEquals(4, rootStack.size)

        // 提交修改后的新词，触发收敛
        navigator.commitSearchResult(SearchResultNavParam(keyword = "初音未来"))
        assertEquals(3, rootStack.size)
        assertEquals(MainNavKey, rootStack[0])
        assertIs<SearchNavKey>(rootStack[1])
        val newResultKey = assertIs<SearchResultNavKey>(rootStack[2])
        assertEquals("初音未来", newResultKey.param.keyword)

        // 栈中仅包含一个 SearchResultNavKey，返回直达落地页现场
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

        // 外部直达：[MainNavKey, HobbyDetailNavKey]
        navigator.navigateToDetail(2002L)
        navigator.navigateToSearchResult(SearchResultNavParam(keyword = "GSC"))
        assertEquals(3, rootStack.size)

        // 结果页发起二次重搜：[MainNavKey, HobbyDetailNavKey, SearchResultNavKey, SearchNavKey]
        navigator.navigateToSearch(SearchNavParam(initialKeyword = "GSC"))
        assertEquals(4, rootStack.size)

        // 提交二次重搜收敛：[MainNavKey, HobbyDetailNavKey, SearchResultNavKey(new)]
        navigator.commitSearchResult(SearchResultNavParam(keyword = "MaxFactory"))
        assertEquals(3, rootStack.size)
        assertEquals(MainNavKey, rootStack[0])
        assertIs<HobbyDetailNavKey>(rootStack[1])
        val newResultKey = assertIs<SearchResultNavKey>(rootStack[2])
        assertEquals("MaxFactory", newResultKey.param.keyword)

        val searchResultCount = rootStack.count { it is SearchResultNavKey }
        assertEquals(1, searchResultCount)

        // 验证返回直接回到来源详情页
        assertTrue(navigator.pop())
        assertEquals(2, rootStack.size)
        assertIs<HobbyDetailNavKey>(rootStack.last())
    }

    @Test
    fun testCommitSearchResultMultipleRoundsConvergence() {
        val (navigator, _) = testNavigator()
        val rootStack = navigator.rootBackStack

        // 首搜
        navigator.navigateToSearch(SearchNavParam(initialKeyword = "round0"))
        navigator.commitSearchResult(SearchResultNavParam(keyword = "round0"))
        assertEquals(3, rootStack.size)

        // 连续 5 轮修改再提交
        for (i in 1..5) {
            navigator.navigateToSearch(SearchNavParam(initialKeyword = "round${i - 1}"))
            assertEquals(4, rootStack.size)
            assertIs<SearchNavKey>(rootStack.last())

            navigator.commitSearchResult(SearchResultNavParam(keyword = "round$i"))
            // 断言栈深严格收敛为 3，且栈中仅包含 1 个 SearchResultNavKey
            assertEquals(3, rootStack.size)
            val resultKey = assertIs<SearchResultNavKey>(rootStack.last())
            assertEquals("round$i", resultKey.param.keyword)
            val searchResultCount = rootStack.count { it is SearchResultNavKey }
            assertEquals(1, searchResultCount)
        }

        // 5 轮重搜后返回链路依然清晰：一退落地页，二退主页
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

        // 首搜：[MainNavKey, SearchNavKey] -> commitSearchResult -> [MainNavKey, SearchNavKey, SearchResultNavKey]
        stack.navigateToSearch(keyword = "ALTER")
        stack.commitSearchResult(keyword = "ALTER", category = 10000)
        assertEquals(3, stack.size)
        val firstResult = assertIs<SearchResultNavKey>(stack.last())
        assertEquals("ALTER", firstResult.param.keyword)

        // 二次重搜：压入 SearchNavKey，然后 commitSearchResult
        stack.navigateToSearch(keyword = "ALTER", category = 10000)
        assertEquals(4, stack.size)
        stack.commitSearchResult(keyword = "ALTER 1/7", category = 10000)
        assertEquals(3, stack.size)
        val secondResult = assertIs<SearchResultNavKey>(stack.last())
        assertEquals("ALTER 1/7", secondResult.param.keyword)
        assertEquals(1, stack.count { it is SearchResultNavKey })
    }
}
