package com.nobody.repoi.ui.user

import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.UserFinanceRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.session.FakeUserSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserInfoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        testDispatcher.cancelChildren()
        Dispatchers.resetMain()
    }

    private class Env(val testDispatcher: CoroutineDispatcher, val mock: MockApi = MockApi()) {
        val followRepo = FollowRepository(mock.api(testDispatcher), Json { ignoreUnknownKeys = true })
        val financeRepo = UserFinanceRepository(mock.api(testDispatcher), Json { ignoreUnknownKeys = true })
        val userRepo = UserRepository(mock.api(testDispatcher), followRepo, financeRepo)
        val session = FakeUserSession("utoken-test", 12345L)
        val viewModel = UserInfoViewModel(
            userRepository = userRepo,
            userSession = session,
            followRepository = followRepo,
            userFinanceRepository = financeRepo,
            userId = 0L
        )
    }

    @Test
    fun selectAlbumSubTab_switchesTabsAndLoadsDataSuccessfully() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        val targetUserId = 88888L

        env.mock.route("api/user/getinfo", """{"success":true,"code":200,"msg":"ok","data":{"user":{"id":88888,"nickname":"测试用户","userName":"test_user"}}}""")
        env.mock.route("api/user/fellow/count", """{"success":true,"code":200,"msg":"ok","data":{"data":{"fanCount":10,"friendCount":5,"praiseCount":20}}}""")
        env.mock.route("api/user/orderStatic", """{"success":true,"code":200,"msg":"ok","data":{"count":{"buy":0,"preorder":0,"care":0}}}}""")
        env.mock.route("api/user/collect/similarity", """{"success":true,"code":200,"msg":"ok","data":{"similarity":50}}""")
        env.mock.route("api/user/fellow/type", """{"success":true,"code":200,"msg":"ok","data":{"type":0}}""")
        env.mock.route("api/album/user-v2", """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":101,"name":"发布相册A","cover":"coverA.jpg"}]}}""")
        env.mock.route("api/user-action/user/list", """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        env.mock.route("api/collect/album-v2", """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":201,"itemId":201,"name":"收藏相册B","cover":"coverB.jpg"}]}}""")
        env.mock.route("api/album/get/gkdiy/list", """{"success":true,"code":200,"msg":"ok","data":{"albums":[{"id":301,"name":"作品相册C","cover":"coverC.jpg"}]}}""")

        env.viewModel.initUserId(targetUserId)
        advanceUntilIdle()

        val state0 = env.viewModel.uiState.value
        assertEquals(0, state0.selectedAlbumSubTab)
        assertEquals(1, state0.albums.size)
        assertEquals("发布相册A", state0.albums[0].displayName)
        assertEquals(1, state0.userAlbums.size)
        assertTrue(state0.favoriteAlbums.isEmpty())
        assertTrue(state0.creatorAlbums.isEmpty())

        env.viewModel.selectAlbumSubTab(1)
        advanceUntilIdle()

        val state1 = env.viewModel.uiState.value
        assertEquals(1, state1.selectedAlbumSubTab)
        assertEquals(1, state1.albums.size)
        assertEquals("收藏相册B", state1.albums[0].displayName)
        assertEquals(1, state1.favoriteAlbums.size)
        assertFalse(state1.isAlbumLoading)

        env.viewModel.selectAlbumSubTab(2)
        advanceUntilIdle()

        val state2 = env.viewModel.uiState.value
        assertEquals(2, state2.selectedAlbumSubTab)
        assertEquals(1, state2.albums.size)
        assertEquals("作品相册C", state2.albums[0].displayName)
        assertEquals(1, state2.creatorAlbums.size)
        assertFalse(state2.isAlbumLoading)

        env.viewModel.selectAlbumSubTab(0)

        val stateBack0 = env.viewModel.uiState.value
        assertEquals(0, stateBack0.selectedAlbumSubTab)
        assertEquals(1, stateBack0.albums.size)
        assertEquals("发布相册A", stateBack0.albums[0].displayName)

        env.viewModel.selectAlbumSubTab(1)

        val stateBack1 = env.viewModel.uiState.value
        assertEquals(1, stateBack1.selectedAlbumSubTab)
        assertEquals(1, stateBack1.albums.size)
        assertEquals("收藏相册B", stateBack1.albums[0].displayName)
    }

    @Test
    fun selectTab_whenSwitchingToAlbumTab_triggersLoadingIfEmpty() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        val targetUserId = 88888L

        env.mock.route("api/user/getinfo", """{"success":true,"code":200,"msg":"ok","data":{"user":{"id":88888,"nickname":"测试用户","userName":"test_user"}}}""")
        env.mock.route("api/user/fellow/count", """{"success":true,"code":200,"msg":"ok","data":{"data":{"fanCount":10,"friendCount":5,"praiseCount":20}}}""")
        env.mock.route("api/user/orderStatic", """{"success":true,"code":200,"msg":"ok","data":{"count":{"buy":0,"preorder":0,"care":0}}}}""")
        env.mock.route("api/user/collect/similarity", """{"success":true,"code":200,"msg":"ok","data":{"similarity":50}}""")
        env.mock.route("api/user/fellow/type", """{"success":true,"code":200,"msg":"ok","data":{"type":0}}""")
        env.mock.route("api/album/user-v2", """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        env.mock.route("api/user-action/user/list", """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        env.viewModel.initUserId(targetUserId)
        advanceUntilIdle()

        env.viewModel.selectTab(0)
        assertEquals(0, env.viewModel.uiState.value.selectedTab)
    }

    private fun setupUserEnv(env: Env, targetUserId: Long = 88888L) {
        env.mock.route("api/user/getinfo", """{"success":true,"code":200,"msg":"ok","data":{"user":{"id":$targetUserId,"nickname":"测试用户","userName":"test_user","header":"https://example.com/avatar_latest.jpg"}}}""")
        env.mock.route("api/user/fellow/count", """{"success":true,"code":200,"msg":"ok","data":{"data":{"fanCount":10,"friendCount":5,"praiseCount":20}}}""")
        env.mock.route("api/user/orderStatic", """{"success":true,"code":200,"msg":"ok","data":{"count":{"buy":0,"preorder":0,"care":0}}}}""")
        env.mock.route("api/user/collect/similarity", """{"success":true,"code":200,"msg":"ok","data":{"similarity":50}}""")
        env.mock.route("api/user/fellow/type", """{"success":true,"code":200,"msg":"ok","data":{"type":0}}""")
        env.mock.route("api/album/user-v2", """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":101,"name":"发布相册A","cover":"coverA.jpg"}]}}""")
        env.mock.route("api/user-action/user/list", """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":501,"actionComment":"初始动态A","user":{"id":$targetUserId,"header":"https://example.com/old_avatar.jpg"}}]}}""")
        env.mock.route("api/collect/album-v2", """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":201,"itemId":201,"name":"收藏相册B","cover":"coverB.jpg"}]}}""")
        env.mock.route("api/album/get/gkdiy/list", """{"success":true,"code":200,"msg":"ok","data":{"albums":[{"id":301,"name":"作品相册C","cover":"coverC.jpg"}]}}""")
    }

    private fun drainRequests(env: Env) {
        while (env.mock.requestCount > 0) {
            env.mock.takeRequest()
        }
    }

    @Test
    fun refreshActiveTab_whenAlbumTabAndSubTab0_refreshesOnlyUserAlbumListAndPreservesOtherData() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        val targetUserId = 88888L
        setupUserEnv(env, targetUserId)
        env.viewModel.initUserId(targetUserId)
        advanceUntilIdle()

        env.viewModel.selectTab(0)
        env.viewModel.selectAlbumSubTab(0)

        assertEquals("发布相册A", env.viewModel.uiState.value.albums.first().displayName)
        assertEquals(1, env.viewModel.uiState.value.actions.size)
        drainRequests(env)

        env.mock.replaceRoute("api/album/user-v2", """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":102,"name":"刷新后的发布相册A2","cover":"coverA2.jpg"}]}}""")

        env.viewModel.refreshActiveTab()
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertEquals("刷新后的发布相册A2", state.albums.first().displayName)
        assertEquals("刷新后的发布相册A2", state.userAlbums.first().displayName)
        assertEquals(1, state.actions.size)
        assertEquals("初始动态A", state.actions.first().actionComment)
        assertFalse(state.isAlbumLoading)
        assertFalse(state.isLoading)

        assertEquals(1, env.mock.requestCount)
        val req = env.mock.takeRequest()
        assertTrue(req.encodedPath?.contains("api/album/user-v2") == true, "仅请求相册接口: ${req.encodedPath}")
    }

    @Test
    fun refreshActiveTab_whenAlbumTabAndSubTab1_refreshesOnlyFavoriteAlbums() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        val targetUserId = 88888L
        setupUserEnv(env, targetUserId)
        env.viewModel.initUserId(targetUserId)
        advanceUntilIdle()

        env.viewModel.selectTab(0)
        env.viewModel.selectAlbumSubTab(1)
        advanceUntilIdle()
        assertEquals("收藏相册B", env.viewModel.uiState.value.albums.first().displayName)
        drainRequests(env)

        env.mock.replaceRoute("api/collect/album-v2", """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":202,"itemId":202,"name":"刷新后的收藏相册B2","cover":"coverB2.jpg"}]}}""")

        env.viewModel.refreshActiveTab()
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertEquals("刷新后的收藏相册B2", state.albums.first().displayName)
        assertEquals("刷新后的收藏相册B2", state.favoriteAlbums.first().displayName)
        assertEquals("发布相册A", state.userAlbums.first().displayName)
        assertFalse(state.isAlbumLoading)

        assertEquals(1, env.mock.requestCount)
        val req = env.mock.takeRequest()
        assertTrue(req.encodedPath?.contains("api/collect/album-v2") == true, "仅请求收藏相册接口: ${req.encodedPath}")
    }

    @Test
    fun refreshActiveTab_whenAlbumTabAndSubTab2_refreshesOnlyCreatorAlbums() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        val targetUserId = 88888L
        setupUserEnv(env, targetUserId)
        env.viewModel.initUserId(targetUserId)
        advanceUntilIdle()

        env.viewModel.selectTab(0)
        env.viewModel.selectAlbumSubTab(2)
        advanceUntilIdle()
        assertEquals("作品相册C", env.viewModel.uiState.value.albums.first().displayName)
        drainRequests(env)

        env.mock.replaceRoute("api/album/get/gkdiy/list", """{"success":true,"code":200,"msg":"ok","data":{"albums":[{"id":302,"name":"刷新后的作品相册C2","cover":"coverC2.jpg"}]}}""")

        env.viewModel.refreshActiveTab()
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertEquals("刷新后的作品相册C2", state.albums.first().displayName)
        assertEquals("刷新后的作品相册C2", state.creatorAlbums.first().displayName)
        assertEquals("发布相册A", state.userAlbums.first().displayName)
        assertFalse(state.isAlbumLoading)

        assertEquals(1, env.mock.requestCount)
        val req = env.mock.takeRequest()
        assertTrue(req.encodedPath?.contains("api/album/get/gkdiy/list") == true, "仅请求作品相册接口: ${req.encodedPath}")
    }

    @Test
    fun refreshActiveTab_whenActionsTab_refreshesOnlyUserActionListAndNormalizesAvatar() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        val targetUserId = 88888L
        setupUserEnv(env, targetUserId)
        env.viewModel.initUserId(targetUserId)
        advanceUntilIdle()

        assertEquals(1, env.viewModel.uiState.value.selectedTab)
        assertEquals(1, env.viewModel.uiState.value.actions.size)
        assertEquals("https://example.com/avatar_latest.jpg", env.viewModel.uiState.value.actions.first().user?.header)
        drainRequests(env)

        env.mock.replaceRoute("api/user-action/user/list", """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":502,"actionComment":"刷新后的动态B","user":{"id":$targetUserId,"header":"outdated_avatar.png"}}]}}""")

        env.viewModel.refreshActiveTab()
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertEquals(1, state.actions.size)
        val refreshedAction = state.actions.first()
        assertEquals("刷新后的动态B", refreshedAction.actionComment)
        assertEquals("https://example.com/avatar_latest.jpg", refreshedAction.user?.header)
        assertFalse(state.isTabLoading)
        assertFalse(state.isLoading)

        assertEquals("发布相册A", state.userAlbums.first().displayName)

        assertEquals(1, env.mock.requestCount)
        val req = env.mock.takeRequest()
        assertTrue(req.encodedPath?.contains("api/user-action/user/list") == true, "仅请求动态接口: ${req.encodedPath}")
    }

    @Test
    fun refreshActiveTab_whenResaleTab_doesNotTriggerNetworkAndResetsTabLoading() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        val targetUserId = 88888L
        setupUserEnv(env, targetUserId)
        env.viewModel.initUserId(targetUserId)
        advanceUntilIdle()

        env.viewModel.selectTab(2)
        assertEquals(2, env.viewModel.uiState.value.selectedTab)
        drainRequests(env)

        env.viewModel.refreshActiveTab()
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertFalse(state.isTabLoading)
        assertEquals(0, env.mock.requestCount)
        assertEquals("发布相册A", state.userAlbums.first().displayName)
        assertEquals(1, state.actions.size)
    }

    @Test
    fun tabStateIsolation_switchingTabsAndSubTabs_preservesAllCachesWithoutClearing() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        val targetUserId = 88888L
        setupUserEnv(env, targetUserId)
        env.viewModel.initUserId(targetUserId)
        advanceUntilIdle()

        env.viewModel.selectTab(0)
        env.viewModel.selectAlbumSubTab(1)
        advanceUntilIdle()
        env.viewModel.selectAlbumSubTab(2)
        advanceUntilIdle()

        drainRequests(env)

        env.viewModel.selectTab(2)
        assertEquals(2, env.viewModel.uiState.value.selectedTab)

        env.viewModel.selectTab(1)
        assertEquals(1, env.viewModel.uiState.value.selectedTab)
        assertEquals(1, env.viewModel.uiState.value.actions.size)

        env.viewModel.selectTab(0)
        assertEquals(0, env.viewModel.uiState.value.selectedTab)
        assertEquals("作品相册C", env.viewModel.uiState.value.albums.first().displayName)
        assertEquals(1, env.viewModel.uiState.value.userAlbums.size)
        assertEquals(1, env.viewModel.uiState.value.favoriteAlbums.size)
        assertEquals(1, env.viewModel.uiState.value.creatorAlbums.size)

        env.viewModel.selectAlbumSubTab(0)
        assertEquals("发布相册A", env.viewModel.uiState.value.albums.first().displayName)
        assertEquals(0, env.mock.requestCount, "切回已有缓存的子分类不应触发网络请求")
    }
}
