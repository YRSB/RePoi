package com.nobody.repoi.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileListDetailHostTest {

    private fun loggedInState(userId: Long = 42L, nickname: String = "测试玩家"): ProfileUiState =
        ProfileUiState(
            isLoggedIn = true,
            profile = UserProfile(id = userId, nickname = nickname, level = 4),
            fellow = FellowCountData(friendCount = 12, fanCount = 8, praiseCount = 99),
            order = OrderCount(care = 1, preorder = 3, buy = 17),
            finance = OrderFinanceSummary(supplementedTotalRmb = 1200.0, historyTotalRmb = 8800.0),
            userMp = 300
        )

    @Test
    fun wideScreen_loggedOut_displaysGuestPlaceholderAndLoginAction() = runComposeUiTest {
        var loginCount = 0

        setContent {
            Box(modifier = Modifier.size(1000.dp, 800.dp)) {
                ProfileListDetailContent(
                    uiState = ProfileUiState(isLoggedIn = false),
                    isWide = true,
                    onRefresh = {},
                    onRetryLoad = {},
                    onCheckInClick = {},
                    onShowMessage = {},
                    onNavigateToSettings = {},
                    onNavigateToLogin = { loginCount++ },
                    onNavigateToFriendList = { _, _ -> },
                    onNavigateToUserInfo = {},
                    onNavigateToMessage = {},
                    collectPaneContent = { _, _, _ -> },
                    creationPaneContent = { _, _, _ -> },
                    historyPaneContent = { _, _ -> }
                )
            }
        }

        waitForIdle()
        onNodeWithText("登录后查看你的收藏与发布").assertIsDisplayed()
        onNodeWithText("可同步预定清单、已入收藏、个人发布与浏览历史").assertIsDisplayed()
        onNodeWithText("去登录").assertIsDisplayed().performClick()
        assertEquals(1, loginCount)
    }

    @Test
    fun wideScreen_loggedIn_switchesDetailPanesUpdatesCollectParamAndHighlightsLeftRow() = runComposeUiTest {
        var shownMessage = ""

        setContent {
            Box(modifier = Modifier.size(1000.dp, 800.dp)) {
                ProfileListDetailContent(
                    uiState = loggedInState(),
                    isWide = true,
                    onRefresh = {},
                    onRetryLoad = {},
                    onCheckInClick = {},
                    onShowMessage = { shownMessage = it },
                    onNavigateToSettings = {},
                    onNavigateToLogin = {},
                    onNavigateToFriendList = { _, _ -> },
                    onNavigateToUserInfo = {},
                    onNavigateToMessage = {},
                    collectPaneContent = { param, showBackButton, _ ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("DETAIL_COLLECT_${param.userId}_${param.initialCollectState}_${param.initialCategory}_back=$showBackButton")
                        }
                    },
                    creationPaneContent = { param, showBackButton, _ ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("DETAIL_CREATION_${param.userId}_${param.initialCategory}_back=$showBackButton")
                        }
                    },
                    historyPaneContent = { showBackButton, _ ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("DETAIL_HISTORY_back=$showBackButton")
                        }
                    }
                )
            }
        }

        waitForIdle()
        onNodeWithText("收藏、发布与浏览历史").assertIsDisplayed()
        onNodeWithText("点击左侧对应入口，即可在这里展开查看").assertIsDisplayed()

        onNodeWithText("已预定").performClick()
        waitForIdle()
        onNodeWithText("DETAIL_COLLECT_42_preorder_null_back=false").assertIsDisplayed()
        onNodeWithText("我的收藏").assertIsSelected()
        onNodeWithText("我的发布").assertIsNotSelected()

        onNodeWithText("已入 17").performClick()
        waitForIdle()
        onNodeWithText("已入").performClick()
        waitForIdle()
        onNodeWithText("DETAIL_COLLECT_42_buy_null_back=false").assertIsDisplayed()
        onNodeWithText("我的收藏").assertIsSelected()

        onNodeWithText("我的收藏").performClick()
        waitForIdle()
        onNodeWithText("DETAIL_COLLECT_42_null_10000_back=false").assertIsDisplayed()
        onNodeWithText("我的收藏").assertIsSelected()

        onNodeWithText("我的发布").performClick()
        waitForIdle()
        onNodeWithText("DETAIL_CREATION_42_70000_back=false").assertIsDisplayed()
        onNodeWithText("我的发布").assertIsSelected()
        onNodeWithText("我的收藏").assertIsNotSelected()

        onNodeWithText("浏览历史").performClick()
        waitForIdle()
        onNodeWithText("DETAIL_HISTORY_back=false").assertIsDisplayed()
        onNodeWithText("浏览历史").assertIsSelected()
        onNodeWithText("我的发布").assertIsNotSelected()

        onNodeWithText("我的二手").performClick()
        waitForIdle()
        assertEquals("二手闲置市场功能即将上线", shownMessage)
        onNodeWithText("DETAIL_HISTORY_back=false").assertIsDisplayed()
    }

    @Test
    fun wideScreen_fullscreenActions_triggerExternalCallbacksWithoutReplacingPlaceholder() = runComposeUiTest {
        var settingsClicks = 0
        var mailClicks = 0
        var userInfoId = -1L
        var friendListPair: Pair<Long, Int>? = null

        setContent {
            Box(modifier = Modifier.size(1000.dp, 800.dp)) {
                ProfileListDetailContent(
                    uiState = loggedInState(userId = 77L),
                    isWide = true,
                    onRefresh = {},
                    onRetryLoad = {},
                    onCheckInClick = {},
                    onShowMessage = {},
                    onNavigateToSettings = { settingsClicks++ },
                    onNavigateToLogin = {},
                    onNavigateToFriendList = { uid, type -> friendListPair = uid to type },
                    onNavigateToUserInfo = { userInfoId = it },
                    onNavigateToMessage = { mailClicks++ },
                    collectPaneContent = { _, _, _ -> },
                    creationPaneContent = { _, _, _ -> },
                    historyPaneContent = { _, _ -> }
                )
            }
        }

        waitForIdle()
        onNodeWithContentDescription("设置").performClick()
        onNodeWithContentDescription("消息中心").performClick()
        onNodeWithText("关注").performClick()
        onNodeWithTag("profile_header_identity_row").performClick()

        assertEquals(1, settingsClicks)
        assertEquals(1, mailClicks)
        assertEquals(77L to 0, friendListPair)
        assertEquals(77L, userInfoId)
        onNodeWithText("收藏、发布与浏览历史").assertIsDisplayed()
    }

    @Test
    fun narrowScreen_pushesDetailWithBackButtonAndPopsBackToList() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(412.dp, 800.dp)) {
                ProfileListDetailContent(
                    uiState = loggedInState(),
                    isWide = false,
                    onRefresh = {},
                    onRetryLoad = {},
                    onCheckInClick = {},
                    onShowMessage = {},
                    onNavigateToSettings = {},
                    onNavigateToLogin = {},
                    onNavigateToFriendList = { _, _ -> },
                    onNavigateToUserInfo = {},
                    onNavigateToMessage = {},
                    collectPaneContent = { param, showBackButton, onBack ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text("NARROW_COLLECT_${param.userId}_back=$showBackButton")
                            if (showBackButton) {
                                Button(onClick = onBack) {
                                    Text("返回个人中心")
                                }
                            }
                        }
                    },
                    creationPaneContent = { _, _, _ -> },
                    historyPaneContent = { _, _ -> }
                )
            }
        }

        waitForIdle()
        onNodeWithText("收藏、发布与浏览历史").assertDoesNotExist()
        onNodeWithText("我的收藏").performClick()
        waitForIdle()

        onNodeWithText("NARROW_COLLECT_42_back=true").assertIsDisplayed()
        onNodeWithText("返回个人中心").performClick()
        waitForIdle()

        onNodeWithText("我的收藏").assertIsDisplayed()
        onNodeWithText("NARROW_COLLECT_42_back=true").assertDoesNotExist()
    }

    @Test
    fun breakpointChangePreservesDetail_andLogoutOrAccountSwitchResetsToRoot() = runComposeUiTest {
        var isWide by mutableStateOf(true)
        var state by mutableStateOf(loggedInState(userId = 100L, nickname = "账号A"))

        setContent {
            Box(modifier = Modifier.size(if (isWide) 1000.dp else 412.dp, 800.dp)) {
                ProfileListDetailContent(
                    uiState = state,
                    isWide = isWide,
                    onRefresh = {},
                    onRetryLoad = {},
                    onCheckInClick = {},
                    onShowMessage = {},
                    onNavigateToSettings = {},
                    onNavigateToLogin = {},
                    onNavigateToFriendList = { _, _ -> },
                    onNavigateToUserInfo = {},
                    onNavigateToMessage = {},
                    collectPaneContent = { _, _, _ -> },
                    creationPaneContent = { param, showBackButton, _ ->
                        Text("DETAIL_CREATION_${param.userId}_back=$showBackButton")
                    },
                    historyPaneContent = { showBackButton, _ ->
                        Text("DETAIL_HISTORY_back=$showBackButton")
                    }
                )
            }
        }

        waitForIdle()
        onNodeWithText("浏览历史").performClick()
        waitForIdle()
        onNodeWithText("DETAIL_HISTORY_back=false").assertIsDisplayed()

        isWide = false
        waitForIdle()
        onNodeWithText("DETAIL_HISTORY_back=true").assertIsDisplayed()

        isWide = true
        waitForIdle()
        onNodeWithText("DETAIL_HISTORY_back=false").assertIsDisplayed()

        state = ProfileUiState(isLoggedIn = false)
        waitForIdle()
        onNodeWithText("DETAIL_HISTORY_back=false").assertDoesNotExist()
        onNodeWithText("登录后查看你的收藏与发布").assertIsDisplayed()

        state = loggedInState(userId = 100L, nickname = "账号A")
        waitForIdle()
        onNodeWithText("我的发布").performClick()
        waitForIdle()
        onNodeWithText("DETAIL_CREATION_100_back=false").assertIsDisplayed()

        state = loggedInState(userId = 200L, nickname = "账号B")
        waitForIdle()
        onNodeWithText("DETAIL_CREATION_100_back=false").assertDoesNotExist()
        onNodeWithText("收藏、发布与浏览历史").assertIsDisplayed()

        state = ProfileUiState(isLoggedIn = true, isLoading = true, profile = null)
        waitForIdle()
        onNodeWithText("收藏、发布与浏览历史").assertIsDisplayed()
        onNodeWithText("登录后查看你的收藏与发布").assertDoesNotExist()
    }
}
