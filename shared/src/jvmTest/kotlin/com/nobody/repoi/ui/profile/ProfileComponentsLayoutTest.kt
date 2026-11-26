package com.nobody.repoi.ui.profile

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.nobody.repoi.data.remote.model.CategoryMoneyGroup
import com.nobody.repoi.data.remote.model.CurrencyItem
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.ui.profile.components.CheckInSpringButton
import com.nobody.repoi.ui.profile.components.ExpressiveProfileHeader
import com.nobody.repoi.ui.profile.components.FigureAssetHubSection
import com.nobody.repoi.ui.profile.components.FinanceDetailContent
import com.nobody.repoi.ui.profile.components.ProfileErrorCard
import com.nobody.repoi.ui.profile.components.ProfileQuickActionBar
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
        onNodeWithText("手办、周边与各类维基条目").assertIsDisplayed()
        assertEquals(42L, collectUserId)

        onNodeWithText("我的发布").assertIsDisplayed().performClick()
        onNodeWithText("相册、评测与个人创作").assertIsDisplayed()
        assertEquals(42L, creationUserId)

        onNodeWithText("我的二手").assertIsDisplayed().performClick()
        onNodeWithText("闲置发布与交易管理").assertIsDisplayed()
        onNodeWithText("即将上线").assertIsDisplayed()
        assertTrue(shownMessage.isNotBlank())

        onNodeWithText("浏览历史").assertIsDisplayed().performClick()
        onNodeWithText("近期查看过的条目记录").assertIsDisplayed()
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
    fun figureAssetHubSection_switchesTabsAndDispatchesActions() = runComposeUiTest {
        var collectState = ""
        var moneyTab = -1

        setContent {
            FigureAssetHubSection(
                order = OrderCount(care = 2, preorder = 3, buy = 17),
                finance = OrderFinanceSummary(
                    supplementedTotalRmb = 3428.0,
                    historyTotalRmb = 15000.0,
                    amazonFluctuation = "-3%",
                    supplementedGroups = listOf(
                        CategoryMoneyGroup(
                            categoryKey = "category_100",
                            categoryName = "手办",
                            items = listOf(CurrencyItem("rmb", 2800.0)),
                            totalRmb = 2800.0
                        )
                    ),
                    historyGroups = listOf(
                        CategoryMoneyGroup(
                            categoryKey = "category_100",
                            categoryName = "手办",
                            items = listOf(CurrencyItem("rmb", 12000.0)),
                            totalRmb = 12000.0
                        ),
                        CategoryMoneyGroup(
                            categoryKey = "category_900",
                            categoryName = "动漫周边",
                            items = listOf(CurrencyItem("rmb", 3000.0)),
                            totalRmb = 3000.0
                        )
                    )
                ),
                isLoggedIn = true,
                onNavigateToCollect = { collectState = it },
                onMoneyClick = { moneyTab = it },
                onLoginClick = {}
            )
        }

        onNodeWithText("想买", substring = true).assertDoesNotExist()
        onNodeWithText("待补款总折合").assertIsDisplayed()
        onNodeWithText("¥3,428").assertIsDisplayed().performClick()
        assertEquals(0, moneyTab)

        onNodeWithText("手办 ¥2,800").assertDoesNotExist()
        onNodeWithText("已预定").assertIsDisplayed().performClick()
        assertEquals("preorder", collectState)

        onNodeWithText("已入 17").assertIsDisplayed().performClick()
        onNodeWithText("历史总投入").assertIsDisplayed()
        onNodeWithText("¥15,000").assertIsDisplayed().performClick()
        assertEquals(1, moneyTab)

        onNodeWithText("手办 ¥12,000").assertIsDisplayed()
        onNodeWithText("动漫周边 ¥3,000").assertIsDisplayed()
        onNodeWithText("已入").assertIsDisplayed().performClick()
        assertEquals("buy", collectState)
    }

    @Test
    fun figureAssetHubSection_redirectsToLoginWhenLoggedOut() = runComposeUiTest {
        var loginCount = 0

        setContent {
            FigureAssetHubSection(
                order = null,
                finance = null,
                isLoggedIn = false,
                onNavigateToCollect = {},
                onMoneyClick = {},
                onLoginClick = { loginCount++ }
            )
        }

        onNodeWithText("待补款总折合").assertIsDisplayed().performClick()
        onNodeWithText("已预定").assertIsDisplayed().performClick()
        assertEquals(2, loginCount)
    }

    @Test
    fun figureAssetHubSection_keepsConstantHeightWhenBothTabsLackBreakdown() = runComposeUiTest {
        var measuredHeight = 0

        setContent {
            FigureAssetHubSection(
                order = OrderCount(care = 2, preorder = 3, buy = 17),
                finance = OrderFinanceSummary(
                    supplementedTotalRmb = 0.0,
                    historyTotalRmb = 12000.0,
                    supplementedGroups = emptyList(),
                    historyGroups = listOf(
                        CategoryMoneyGroup("category_100", "手办", listOf(CurrencyItem("rmb", 12000.0)), 12000.0)
                    )
                ),
                isLoggedIn = true,
                onNavigateToCollect = {},
                onMoneyClick = {},
                onLoginClick = {},
                modifier = Modifier.onSizeChanged { measuredHeight = it.height }
            )
        }

        waitForIdle()
        val preorderHeight = measuredHeight
        assertTrue(preorderHeight > 0)

        onNodeWithText("已入 17").performClick()
        waitForIdle()
        assertEquals(preorderHeight, measuredHeight)
    }

    @Test
    fun financeDetailContent_switchesBetweenTabsAndDisplaysAllCategories() = runComposeUiTest {
        var dismissed = false
        setContent {
            FinanceDetailContent(
                finance = OrderFinanceSummary(
                    supplementedTotalRmb = 3428.0,
                    historyTotalRmb = 15000.0,
                    supplementedGroups = listOf(
                        CategoryMoneyGroup(
                            categoryKey = "category_100",
                            categoryName = "手办",
                            items = listOf(CurrencyItem("rmb", 3428.0)),
                            totalRmb = 3428.0
                        )
                    ),
                    historyGroups = listOf(
                        CategoryMoneyGroup(
                            categoryKey = "category_900",
                            categoryName = "动漫周边",
                            items = listOf(CurrencyItem("rmb", 500.0)),
                            totalRmb = 500.0
                        ),
                        CategoryMoneyGroup(
                            categoryKey = "category_1000",
                            categoryName = "GK/个人创作",
                            items = listOf(CurrencyItem("rmb", 1200.0)),
                            totalRmb = 1200.0
                        )
                    )
                ),
                initialTab = 0,
                onDismissRequest = { dismissed = true }
            )
        }

        onNodeWithText("待补款总折合").assertIsDisplayed()
        onNodeWithText("¥3,428").assertIsDisplayed()
        onNodeWithText("手办").assertIsDisplayed()

        onNodeWithText("已入总额明细").assertIsDisplayed().performClick()
        onNodeWithText("已入手办总折合").assertIsDisplayed()
        onNodeWithText("¥15,000").assertIsDisplayed()
        onNodeWithText("动漫周边").assertIsDisplayed()
        onNodeWithText("GK/个人创作").assertIsDisplayed()

        onNodeWithContentDescription("关闭").performClick()
        assertTrue(dismissed)
    }
}
