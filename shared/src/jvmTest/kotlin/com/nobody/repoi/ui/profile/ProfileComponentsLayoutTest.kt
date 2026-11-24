package com.nobody.repoi.ui.profile

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.nobody.repoi.ui.profile.components.CheckInSpringButton
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
}
