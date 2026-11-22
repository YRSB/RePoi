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
}
