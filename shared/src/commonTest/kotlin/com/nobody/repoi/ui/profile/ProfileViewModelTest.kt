package com.nobody.repoi.ui.profile

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.UserFinanceRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        testDispatcher.cancelChildren()
        Dispatchers.resetMain()
    }

    private val profile = UserProfile(
        id = 88888L,
        nickname = "手办达人",
        userName = "master",
        level = 5,
    )

    private class Env(
        val mock: MockApi,
        val session: FakeUserSession,
        val viewModel: ProfileViewModel,
    )

    private fun env(initialState: SessionState): Env {
        val mock = MockApi()
        stubProfileRoutes(mock)
        val session = FakeUserSession(initialState)
        val api = mock.api(testDispatcher)
        val followRepo = FollowRepository(api, mock.json)
        val financeRepo = UserFinanceRepository(api)
        val userRepo = UserRepository(api, followRepo, financeRepo)
        val viewModel = ProfileViewModel(
            userRepo,
            MessageRepository(api),
            ThemeManager(TestDataStore.inMemory()),
            session,
            followRepo,
        )
        return Env(mock, session, viewModel)
    }

    private fun stubProfileRoutes(mock: MockApi) {
        mock.route(
            "api/user/fellow/count",
            """{"success":true,"code":200,"msg":"ok","data":{"data":{"fanCount":120,"friendCount":45,"praiseCount":666}}}"""
        )
        mock.route(
            "api/user/orderStatic",
            """{"success":true,"code":200,"msg":"ok","data":{"data":{"count":{"buy":10,"preorder":5,"care":15},"allOrderMoney":[{"key":"category_100","val":[{"currency":"rmb","money":2000.0}]}],"allPreorderMoney":[{"key":"category_100","val":[{"currency":"yen","money":10000.0}]}],"amazonMoney":{"initialPrice":1000,"price":1100}}}}"""
        )
        mock.routeWhen(
            predicate = { path, form -> path.contains("api/user/collect/top") && form["state"] == "preorder" },
            body = { _, _ -> """{"success":true,"code":200,"msg":"ok","data":{"list":[{"itemId":101,"nameCN":"预定手办A"}]}}""" }
        )
        mock.routeWhen(
            predicate = { path, form -> path.contains("api/user/collect/top") && form["state"] == "buy" },
            body = { _, _ -> """{"success":true,"code":200,"msg":"ok","data":{"list":[{"itemId":201,"nameCN":"已入手办B"}]}}""" }
        )
        mock.routeWhen(
            predicate = { path, form -> path.contains("api/user/collect/top") && form["state"] == "care" },
            body = { _, _ -> """{"success":true,"code":200,"msg":"ok","data":{"list":[{"itemId":301,"nameCN":"想要手办C"}]}}""" }
        )
        mock.route(
            "api/album/user-v2",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":501,"name":"相册A"}]}}"""
        )
        mock.route(
            "api/mp/task/checkIn/get",
            """{"success":true,"code":200,"msg":"ok","data":{"checkInLastTime":0,"checkInContinuityDays":3}}"""
        )
        mock.route("api/mp/get", """{"success":true,"code":200,"msg":"ok","data":{"mp":1234}}""")
        mock.route(
            "api/unread/push-message/count-v2",
            """{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{"system":1}}}}"""
        )
    }

    @Test
    fun authenticatedState_aggregatesAllProfileData() = runTest(testDispatcher) {
        val env = env(
            SessionState.Authenticated(userId = 88888L, token = "utoken-test", profile = profile)
        )
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertTrue(state.isLoggedIn)
        assertFalse(state.isLoading)
        assertEquals("手办达人", state.profile?.displayName)

        assertEquals(120, state.fellow?.fanCount)
        assertEquals(45, state.fellow?.friendCount)
        assertEquals(10, state.order?.buy)
        assertEquals(5, state.order?.preorder)
        assertEquals(15, state.order?.care)
        assertEquals(listOf(101L), state.preorderItems.map { it.itemId })
        assertEquals(listOf(201L), state.buyItems.map { it.itemId })
        assertEquals(listOf(301L), state.careItems.map { it.itemId })
        assertEquals(listOf(501L), state.userAlbums.map { it.id })
        assertEquals(3, state.checkInStatus?.checkInContinuityDays)
        assertEquals(1234, state.userMp)
        assertTrue(state.hasUnreadNotice)

        assertEquals(0, env.session.refreshProfileCalls)

        assertEquals(9, env.mock.requestCount)
    }

    @Test
    fun prewarmingThenAuthenticated_loadsProfileAndData() = runTest(testDispatcher) {
        val env = env(SessionState.Prewarming)
        env.session.refreshProfileResult = Result.success(profile)
        advanceUntilIdle()

        assertEquals(0, env.mock.requestCount)
        assertFalse(env.viewModel.uiState.value.isLoggedIn)
        assertFalse(env.viewModel.uiState.value.isLoading)

        env.session.setState(SessionState.Authenticated(userId = 0L, token = "utoken-test"))
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertTrue(state.isLoggedIn)
        assertEquals("手办达人", state.profile?.displayName)
        assertEquals(120, state.fellow?.fanCount)
        assertEquals(1234, state.userMp)

        assertEquals(1, env.session.refreshProfileCalls)
    }

    @Test
    fun pullToRefresh_refreshesSessionProfile() = runTest(testDispatcher) {
        val env = env(
            SessionState.Authenticated(userId = 88888L, token = "utoken-test", profile = profile)
        )
        advanceUntilIdle()
        assertEquals(0, env.session.refreshProfileCalls)

        env.session.refreshProfileResult = Result.success(profile.copy(nickname = "刷新后昵称"))
        env.viewModel.refresh()
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertEquals(1, env.session.refreshProfileCalls)
        assertEquals("刷新后昵称", state.profile?.nickname)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun tokenExpired_resetsToUnauthenticatedState() = runTest(testDispatcher) {
        val env = env(
            SessionState.Authenticated(userId = 88888L, token = "utoken-test", profile = profile)
        )
        advanceUntilIdle()
        assertTrue(env.viewModel.uiState.value.isLoggedIn)

        env.session.notifyTokenExpired()
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertEquals(1, env.session.tokenExpiredCalls)
        assertFalse(state.isLoggedIn)
        assertNull(state.profile)
        assertNull(state.fellow)
        assertEquals(0, state.userMp)
    }

    @Test
    fun logout_delegatesToSessionAndResetsState() = runTest(testDispatcher) {
        val env = env(
            SessionState.Authenticated(userId = 88888L, token = "utoken-test", profile = profile)
        )
        advanceUntilIdle()

        env.viewModel.logout()
        advanceUntilIdle()

        assertEquals(1, env.session.logoutCalls)
        assertFalse(env.viewModel.uiState.value.isLoggedIn)
        assertNull(env.viewModel.uiState.value.profile)
    }

    @Test
    fun pullToRefresh_刷新失败时展示错误且不重复刷新() = runTest(testDispatcher) {
        val env = env(
            SessionState.Authenticated(userId = 88888L, token = "utoken-test", profile = profile)
        )
        advanceUntilIdle()
        assertEquals(profile, env.viewModel.uiState.value.profile)

        env.session.refreshProfileResult = Result.failure(ApiException(500, "刷新失败"))
        env.viewModel.refresh()
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertEquals(1, env.session.refreshProfileCalls)
        assertEquals("刷新失败", state.errorMessage)
        assertFalse(state.isRefreshing)
        assertEquals(profile, state.profile)
    }

}
