package com.nobody.repoi.ui.profile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.ui.profile.components.AssetShowcaseSection
import com.nobody.repoi.ui.profile.components.CheckInSpringButton
import com.nobody.repoi.ui.profile.components.ExpressiveProfileHeader
import com.nobody.repoi.ui.profile.components.PreorderShowcaseSection
import com.nobody.repoi.ui.profile.components.ProfileErrorCard
import com.nobody.repoi.ui.profile.components.ProfileQuickActionBar
import com.nobody.repoi.ui.profile.components.ShowcaseStatItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProfileComponentsLayoutTest {

    @Test
    fun checkInButton_rendersUnsignedStateAndHandlesClick() = runComposeUiTest {
        var clicked = false
        setContent {
            CheckInSpringButton(
                isSignedInToday = false,
                isCheckingIn = false,
                onClick = { clicked = true }
            )
        }

        onNodeWithText("打卡签到").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun checkInButton_rendersSignedState() = runComposeUiTest {
        setContent {
            CheckInSpringButton(
                isSignedInToday = true,
                isCheckingIn = false,
                onClick = {}
            )
        }

        onNodeWithText("已签到 ✓").assertIsDisplayed()
    }

    @Test
    fun showcaseStatItem_rendersLabelAndValue() = runComposeUiTest {
        var clicked = false
        setContent {
            ShowcaseStatItem(
                title = "待补款",
                value = "¥1,234",
                onClick = { clicked = true }
            )
        }

        onNodeWithText("待补款").assertIsDisplayed()
        onNodeWithText("¥1,234").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun errorCard_rendersErrorAndRetryAction() = runComposeUiTest {
        var retried = false
        setContent {
            ProfileErrorCard(
                errorMessage = "网络连接超时",
                onRetry = { retried = true }
            )
        }

        onNodeWithText("数据加载失败").assertIsDisplayed()
        onNodeWithText("网络连接超时").assertIsDisplayed()
        onNodeWithText("重新加载").assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun expressiveProfileHeader_rendersLoggedOutState() = runComposeUiTest {
        var loginClicked = false
        setContent {
            ExpressiveProfileHeader(
                profile = null,
                fellow = null,
                userMp = 0,
                isSignedInToday = false,
                isCheckingIn = false,
                hasUnreadNotice = false,
                onCheckInClick = {},
                onMailClick = {},
                onSettingsClick = {},
                onNavigateToFriendList = { _, _ -> },
                onNavigateToUserInfo = {},
                onLoginClick = { loginClicked = true }
            )
        }

        onNodeWithText("点击登录").assertIsDisplayed()
        onNodeWithText("登录后同步心愿与手账").assertIsDisplayed()
        onNodeWithText("登录").assertIsDisplayed().performClick()
        assertTrue(loginClicked)
        onNodeWithContentDescription("消息中心").assertIsDisplayed()
        onNodeWithContentDescription("设置").assertIsDisplayed()
    }

    @Test
    fun expressiveProfileHeader_rendersLoggedInStateWithStats() = runComposeUiTest {
        var friendListType = -1
        setContent {
            ExpressiveProfileHeader(
                profile = UserProfile(
                    id = 12345L,
                    nickname = "天天才十二",
                    level = 3
                ),
                fellow = FellowCountData(
                    friendCount = 10,
                    fanCount = 3,
                    praiseCount = 2
                ),
                userMp = 7980,
                isSignedInToday = true,
                isCheckingIn = false,
                hasUnreadNotice = true,
                onCheckInClick = {},
                onMailClick = {},
                onSettingsClick = {},
                onNavigateToFriendList = { _, type -> friendListType = type },
                onNavigateToUserInfo = {},
                onLoginClick = {}
            )
        }

        onNodeWithText("天天才十二").assertIsDisplayed()
        onNodeWithText("Lv.3   H币 7980").assertIsDisplayed()
        onNodeWithText("已签到 ✓").assertIsDisplayed()
        onNodeWithText("关注").assertIsDisplayed().performClick()
        assertTrue(friendListType == 0)
        onNodeWithText("粉丝").assertIsDisplayed()
        onNodeWithText("获赞").assertIsDisplayed()
    }

    @Test
    fun quickActionBar_rendersAllEntriesAndDispatchesWhenLoggedIn() = runComposeUiTest {
        var collectUserId = -1L
        var creationUserId = -1L
        var historyClicked = false
        var shownMessage = ""

        setContent {
            ProfileQuickActionBar(
                profileId = 42L,
                isLoggedIn = true,
                isCompact = true,
                onNavigateToCollect = { collectUserId = it.userId },
                onNavigateToCreation = { creationUserId = it.userId },
                onNavigateToHistory = { historyClicked = true },
                onShowMessage = { shownMessage = it },
                onLoginClick = {}
            )
        }

        onNodeWithText("我的收藏").assertIsDisplayed().performClick()
        assertEquals(42L, collectUserId)

        onNodeWithText("我的发布").assertIsDisplayed().performClick()
        assertEquals(42L, creationUserId)

        onNodeWithText("我的二手").assertIsDisplayed().performClick()
        assertTrue(shownMessage.isNotBlank())

        onNodeWithText("浏览历史").assertIsDisplayed().performClick()
        assertTrue(historyClicked)
    }

    @Test
    fun quickActionBar_redirectsToLoginWhenLoggedOut() = runComposeUiTest {
        var loginCount = 0

        setContent {
            ProfileQuickActionBar(
                profileId = 0L,
                isLoggedIn = false,
                isCompact = false,
                onNavigateToCollect = {},
                onNavigateToCreation = {},
                onNavigateToHistory = {},
                onShowMessage = {},
                onLoginClick = { loginCount++ }
            )
        }

        onNodeWithText("我的收藏").assertIsDisplayed().performClick()
        onNodeWithText("我的发布").assertIsDisplayed().performClick()
        onNodeWithText("我的二手").assertIsDisplayed().performClick()
        onNodeWithText("浏览历史").assertIsDisplayed().performClick()
        assertEquals(4, loginCount)
    }

    @Test
    fun preorderShowcaseSection_rendersStatsAndCarouselItems() = runComposeUiTest {
        var collectState = ""
        var moneyClicked = false
        var clickedItemId = -1L

        setContent {
            PreorderShowcaseSection(
                order = OrderCount(care = 2, preorder = 3),
                finance = OrderFinanceSummary(supplementedTotalRmb = 3428.0),
                preorderItems = listOf(
                    CollectTopItem(itemId = 101L, nameCN = "初音未来手办")
                ),
                isLoggedIn = true,
                onNavigateToCollect = { collectState = it },
                onMoneyClick = { moneyClicked = true },
                onItemClick = { clickedItemId = it.itemId },
                onLoginClick = {}
            )
        }

        onNodeWithText("想买").assertIsDisplayed().performClick()
        assertEquals("want", collectState)

        onNodeWithText("预定").assertIsDisplayed().performClick()
        assertEquals("preorder", collectState)

        onNodeWithText("¥3,428").assertIsDisplayed().performClick()
        assertTrue(moneyClicked)

        onNodeWithContentDescription("初音未来手办").assertIsDisplayed().performClick()
        assertEquals(101L, clickedItemId)
    }

    @Test
    fun assetShowcaseSection_rendersStatsAndCarouselItems() = runComposeUiTest {
        var collectState = ""
        var moneyClicked = false
        var clickedItemId = -1L

        setContent {
            AssetShowcaseSection(
                order = OrderCount(buy = 17),
                finance = OrderFinanceSummary(
                    historyTotalRmb = 15000.0,
                    amazonFluctuation = "-3%"
                ),
                buyItems = listOf(
                    CollectTopItem(itemId = 202L, nameCN = "樱花初音")
                ),
                isLoggedIn = true,
                onNavigateToCollect = { collectState = it },
                onMoneyClick = { moneyClicked = true },
                onItemClick = { clickedItemId = it.itemId },
                onLoginClick = {}
            )
        }

        onNodeWithText("已入").assertIsDisplayed().performClick()
        assertEquals("buy", collectState)

        onNodeWithText("¥1.5万").assertIsDisplayed().performClick()
        assertTrue(moneyClicked)

        onNodeWithText("日亚浮动").assertIsDisplayed()
        onNodeWithText("-3%").assertIsDisplayed()

        onNodeWithContentDescription("樱花初音").assertIsDisplayed().performClick()
        assertEquals(202L, clickedItemId)
    }
}
