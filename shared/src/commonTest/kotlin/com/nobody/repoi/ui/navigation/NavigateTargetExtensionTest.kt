package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.UriHandler
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateItem
import com.nobody.repoi.ui.detail.DetailNavParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 导航执行扩展测试：验证 [RouteTarget] 三分支副作用落点
 * （Internal 压栈 / External 调 UriHandler / Notice 走提示），均为纯 JVM。
 */
class NavigateTargetExtensionTest {

    private class RecordingUriHandler(private val failure: Exception? = null) : UriHandler {
        val opened = mutableListOf<String>()

        override fun openUri(uri: String) {
            failure?.let { throw it }
            opened += uri
        }
    }

    private fun testNavigator(): RePoiNavigator {
        val stack = mapOf(
            AppDestinations.HOME to NavBackStack<NavKey>(HomeNavKey),
            AppDestinations.ITEMS to NavBackStack<NavKey>(ItemsNavKey),
            AppDestinations.ALBUM to NavBackStack<NavKey>(AlbumNavKey),
            AppDestinations.FOLLOW to NavBackStack<NavKey>(FollowNavKey),
            AppDestinations.PROFILE to NavBackStack<NavKey>(ProfileNavKey),
        )
        val tabState = RePoiNavigationState(
            startDestination = AppDestinations.HOME,
            currentDestinationState = mutableStateOf(AppDestinations.HOME),
            backStacks = stack
        )
        return RePoiNavigator(NavBackStack<NavKey>(MainNavKey), tabState)
    }

    @Test
    fun `navigate - Internal pushes key onto root back stack`() {
        val navigator = testNavigator()
        val uriHandler = RecordingUriHandler()
        val messages = mutableListOf<String>()

        navigator.navigate(
            RouteTarget.Internal(HobbyDetailNavKey(param = DetailNavParam(itemId = 7L))),
            uriHandler,
            messages::add
        )

        assertEquals(2, navigator.rootBackStack.size)
        val key = assertIs<HobbyDetailNavKey>(navigator.rootBackStack.last())
        assertEquals(7L, key.param.itemId)
        assertTrue(uriHandler.opened.isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `navigate - External opens non-blank url through uri handler`() {
        val navigator = testNavigator()
        val uriHandler = RecordingUriHandler()
        val messages = mutableListOf<String>()

        navigator.navigate(RouteTarget.External("https://example.com/a"), uriHandler, messages::add)

        assertEquals(listOf("https://example.com/a"), uriHandler.opened)
        assertEquals(1, navigator.rootBackStack.size)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `navigate - External blank url is silent`() {
        val navigator = testNavigator()
        val uriHandler = RecordingUriHandler()
        val messages = mutableListOf<String>()

        navigator.navigate(RouteTarget.External(""), uriHandler, messages::add)
        navigator.navigate(RouteTarget.External("   "), uriHandler, messages::add)

        assertTrue(uriHandler.opened.isEmpty())
        assertTrue(messages.isEmpty())
        assertEquals(1, navigator.rootBackStack.size)
    }

    @Test
    fun `navigate - External failure reports message without crashing`() {
        val navigator = testNavigator()
        val uriHandler = RecordingUriHandler(IllegalArgumentException("no activity"))
        val messages = mutableListOf<String>()

        navigator.navigate(RouteTarget.External("https://example.com/a"), uriHandler, messages::add)

        assertEquals(listOf("无法打开链接：https://example.com/a"), messages)
        assertEquals(1, navigator.rootBackStack.size)
    }

    @Test
    fun `navigate - Notice reports message`() {
        val navigator = testNavigator()
        val uriHandler = RecordingUriHandler()
        val messages = mutableListOf<String>()

        navigator.navigate(RouteTarget.Notice("条目信息暂未就绪"), uriHandler, messages::add)

        assertEquals(listOf("条目信息暂未就绪"), messages)
        assertTrue(uriHandler.opened.isEmpty())
        assertEquals(1, navigator.rootBackStack.size)
    }

    @Test
    fun `navigateAction resolves ActionItem before navigating`() {
        val navigator = testNavigator()
        val uriHandler = RecordingUriHandler()
        val messages = mutableListOf<String>()

        navigator.navigateAction(
            ActionItem(
                id = 1L,
                relateItem = RelateItem(id = 100L, itemId = 200L, itemType = "character", nameCN = "知更鸟")
            ),
            uriHandler,
            messages::add
        )

        val key = assertIs<CharacterNavKey>(navigator.rootBackStack.last())
        assertEquals(200L, key.param.characterId)
        assertEquals(100L, key.param.characterNodeId)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `navigateUrl opens external links derived from url`() {
        val navigator = testNavigator()
        val uriHandler = RecordingUriHandler()
        val messages = mutableListOf<String>()

        navigator.navigateUrl("https://www.bilibili.com/video/BV1xx", uriHandler, messages::add)

        assertEquals(listOf("https://www.bilibili.com/video/BV1xx"), uriHandler.opened)
        assertEquals(1, navigator.rootBackStack.size)
        assertTrue(messages.isEmpty())
    }
}
