package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumDetailNavKey
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 验证 [NavExtensions] 核心栈管理原语、导航扩展及 [RePoiNavigator] 控制器。
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

    @Test
    fun testNavigator_TabSwitchAndBackStepByStep() {
        val stack = NavBackStack<NavKey>(HomeNavKey)
        var exitCalled = false
        var exitMessage: String? = null
        val navigator = RePoiNavigator(
            backStack = stack,
            onExitApp = { exitCalled = true },
            onShowExitMessage = { exitMessage = it }
        )

        // 1. 连续在多个主目的地之间切换：Home -> Items -> Album -> Profile
        navigator.selectTab(AppDestinations.ITEMS)
        assertEquals(ItemsNavKey, stack.last())

        navigator.selectTab(AppDestinations.ALBUM)
        assertEquals(AlbumNavKey, stack.last())

        navigator.selectTab(AppDestinations.PROFILE)
        assertEquals(ProfileNavKey, stack.last())

        // 2. 连续按下返回键：应当沿着访问历史逐级倒退回上一个 Tab
        // Profile -> Album
        assertTrue(navigator.goBack())
        assertEquals(AlbumNavKey, stack.last())

        // Album -> Items
        assertTrue(navigator.goBack())
        assertEquals(ItemsNavKey, stack.last())

        // Items -> Home
        assertTrue(navigator.goBack())
        assertEquals(HomeNavKey, stack.last())

        // 3. 在首页根视图按下返回键：触发提示“再按一次退出应用”
        assertTrue(navigator.goBack())
        assertEquals("再按一次退出应用", exitMessage)
        assertFalse(exitCalled)
    }

    @Test
    fun testNavigator_DetailPopBeforeTabBack() {
        val stack = NavBackStack<NavKey>(HomeNavKey)
        val navigator = RePoiNavigator(backStack = stack)

        navigator.selectTab(AppDestinations.ITEMS)
        assertEquals(ItemsNavKey, stack.last())

        // 在条目页深入二级页面：手办详情 -> 用户详情
        stack.navigateToDetail(12345L)
        stack.navigateToUser(67890L)
        assertEquals(4, stack.size)
        assertIs<UserInfoNavKey>(stack.last())

        // 第 1 次返回：弹出用户详情，回到手办详情
        assertTrue(navigator.goBack())
        assertIs<HobbyDetailNavKey>(stack.last())

        // 第 2 次返回：弹出手办详情，回到条目页根视图
        assertTrue(navigator.goBack())
        assertEquals(ItemsNavKey, stack.last())

        // 第 3 次返回：从条目页根视图退回首页
        assertTrue(navigator.goBack())
        assertEquals(HomeNavKey, stack.last())
    }

    @Test
    fun testNavigator_ReselectCurrentTabPopsSecondary() {
        val stack = NavBackStack<NavKey>(HomeNavKey)
        val navigator = RePoiNavigator(backStack = stack)

        navigator.selectTab(AppDestinations.ITEMS)
        stack.navigateToDetail(12345L)
        assertEquals(3, stack.size)

        // 再次点击当前已选中的 Items Tab：应当清空二级页面退回条目根视图
        navigator.selectTab(AppDestinations.ITEMS)
        assertEquals(2, stack.size)
        assertEquals(ItemsNavKey, stack.last())
    }

    @Test
    fun testMultipleBackStacks_RetainsStateAcrossAllDestinations() {
        val homeStack = NavBackStack<NavKey>(HomeNavKey)
        val itemsStack = NavBackStack<NavKey>(ItemsNavKey)
        val albumStack = NavBackStack<NavKey>(AlbumNavKey)
        val followStack = NavBackStack<NavKey>(FollowNavKey)
        val profileStack = NavBackStack<NavKey>(ProfileNavKey)

        val stacks = mapOf(
            AppDestinations.HOME to homeStack,
            AppDestinations.ITEMS to itemsStack,
            AppDestinations.ALBUM to albumStack,
            AppDestinations.FOLLOW to followStack,
            AppDestinations.PROFILE to profileStack,
        )

        var exitCalled = false
        var exitMessage: String? = null
        val state = RePoiNavigationState(
            startDestination = AppDestinations.HOME,
            currentDestinationState = mutableStateOf(AppDestinations.HOME),
            backStacks = stacks,
            isSingleStackMode = false
        )
        val navigator = RePoiNavigator(
            state = state,
            onExitApp = { exitCalled = true },
            onShowExitMessage = { exitMessage = it }
        )

        // 1. 切换到条目页，并在条目页中进入手办详情
        navigator.selectTab(AppDestinations.ITEMS)
        assertEquals(AppDestinations.ITEMS, state.currentDestination)
        navigator.navigate(HobbyDetailNavKey(111L))
        assertEquals(2, itemsStack.size)
        assertEquals(HobbyDetailNavKey(111L), itemsStack.last())

        // 2. 切换到相册页，并在相册页进入某个相册详情
        navigator.selectTab(AppDestinations.ALBUM)
        assertEquals(AppDestinations.ALBUM, state.currentDestination)
        navigator.navigate(AlbumDetailNavKey(AlbumNavParam(albumId = 222L)))
        assertEquals(2, albumStack.size)

        // 3. 再次切回条目页：验证条目页的详情依然保留，状态未被清除！
        navigator.selectTab(AppDestinations.ITEMS)
        assertEquals(AppDestinations.ITEMS, state.currentDestination)
        assertEquals(2, itemsStack.size)
        assertEquals(HobbyDetailNavKey(111L), itemsStack.last())

        // 4. 在条目页详情执行返回：弹出详情，回到条目根页面
        assertTrue(navigator.goBack())
        assertEquals(1, itemsStack.size)
        assertEquals(ItemsNavKey, itemsStack.last())

        // 5. 再次切回相册页：验证相册页的详情依然完好保留！
        navigator.selectTab(AppDestinations.ALBUM)
        assertEquals(AppDestinations.ALBUM, state.currentDestination)
        assertEquals(2, albumStack.size)

        // 6. 切换到关注页和我的页
        navigator.selectTab(AppDestinations.FOLLOW)
        assertEquals(AppDestinations.FOLLOW, state.currentDestination)
        navigator.selectTab(AppDestinations.PROFILE)
        assertEquals(AppDestinations.PROFILE, state.currentDestination)

        // 7. 从我的页连续返回：按照历史逐级回退（Profile -> Follow -> Album -> Items -> Home）
        assertTrue(navigator.goBack())
        assertEquals(AppDestinations.FOLLOW, state.currentDestination)

        assertTrue(navigator.goBack())
        assertEquals(AppDestinations.ALBUM, state.currentDestination)
        // 回到相册页时，二级详情依然在
        assertEquals(2, albumStack.size)
        // 弹出相册二级详情
        assertTrue(navigator.goBack())
        assertEquals(1, albumStack.size)
        assertEquals(AlbumNavKey, albumStack.last())

        assertTrue(navigator.goBack())
        assertEquals(AppDestinations.ITEMS, state.currentDestination)

        assertTrue(navigator.goBack())
        assertEquals(AppDestinations.HOME, state.currentDestination)

        // 8. 在首页按返回：触发退出提示
        assertTrue(navigator.goBack())
        assertEquals("再按一次退出应用", exitMessage)
        assertFalse(exitCalled)
    }

    @Test
    fun testMultipleBackStacks_ReselectCurrentTabPopsSecondary() {
        val homeStack = NavBackStack<NavKey>(HomeNavKey)
        val itemsStack = NavBackStack<NavKey>(ItemsNavKey)
        val stacks = mapOf(
            AppDestinations.HOME to homeStack,
            AppDestinations.ITEMS to itemsStack,
            AppDestinations.ALBUM to NavBackStack(AlbumNavKey),
            AppDestinations.FOLLOW to NavBackStack(FollowNavKey),
            AppDestinations.PROFILE to NavBackStack(ProfileNavKey),
        )
        val state = RePoiNavigationState(
            startDestination = AppDestinations.HOME,
            currentDestinationState = mutableStateOf(AppDestinations.HOME),
            backStacks = stacks,
            isSingleStackMode = false
        )
        val navigator = RePoiNavigator(state = state)

        navigator.selectTab(AppDestinations.ITEMS)
        navigator.navigate(HobbyDetailNavKey(12345L))
        assertEquals(2, itemsStack.size)

        // 再次点击当前已激活的 Items Tab：应当清空其二级页面退回条目根视图
        navigator.selectTab(AppDestinations.ITEMS)
        assertEquals(1, itemsStack.size)
        assertEquals(ItemsNavKey, itemsStack.last())
    }
}
