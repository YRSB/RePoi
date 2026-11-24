package com.nobody.repoi.ui.profile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.ui.profile.components.CheckInSpringButton
import com.nobody.repoi.ui.profile.components.ExpressiveProfileHeader
import com.nobody.repoi.ui.profile.components.ProfileErrorCard
import com.nobody.repoi.ui.profile.components.ShowcaseStatItem
import kotlin.test.Test
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
}
