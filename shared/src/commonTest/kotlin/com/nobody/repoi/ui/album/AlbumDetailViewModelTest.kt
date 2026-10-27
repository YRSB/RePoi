package com.nobody.repoi.ui.album

import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.HobbyAlbumUser
import com.nobody.repoi.data.repository.FriendState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AlbumDetailViewModel 互动分支的 KMP 移植版（真实仓储 + MockEngine + 真 TokenManager）。
 *
 * 翻译对照：
 * - `userRepo.isLoggedIn/currentUserId` mock → `env.login(userId)`；
 * - `Result.failure(RuntimeException(msg))` → 业务失败 envelope（HTTP 200 + `success:false`），
 *   仓储抛 `ApiException`，`err.message` 即服务端 msg，文案可精确断言；
 * - `verify(userRepo, never()).getFellowType` → 请求历史中无 `api/user/fellow/type`；
 * - tokenFlow 登录广播 → 真 `TokenManager.saveToken/saveCurrentUser`（FakeDataStore 驱动 flow）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    private val env = AlbumDetailTestEnv()
    private val albumId = 123L
    private val authorId = 888L

    @BeforeTest
    fun setUp() = env.setUp()

    @AfterTest
    fun tearDown() = env.tearDown()

    @Test
    fun loadDetail_whenLoggedIn_populatesInitialLikeCollectAndFollow() =
        runTest(env.dispatchers.testDispatcher) {
            env.login(userId = 999L)
            env.stubBasics(
                likeBody = env.likeInfoJson(true),
                collectBody = env.collectStateJson("care"),
                fellowBody = env.fellowJson(0)
            )

            val viewModel = env.viewModel()
            viewModel.loadDetail(AlbumNavParam(albumId = albumId))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isAuthor)
            assertTrue(state.isLiked)
            assertTrue(state.isCollected)
            assertEquals(FriendState.FOLLOWING, state.followState)
            assertTrue(state.isFollowed)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun loadDetail_whenAuthorIsSelf_marksIsAuthorAndSkipsFollowCheck() =
        runTest(env.dispatchers.testDispatcher) {
            env.login(userId = 999L)
            env.stubBasics(albumId = albumId, authorId = 999L)

            val viewModel = env.viewModel()
            viewModel.loadDetail(AlbumNavParam(albumId = albumId))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.isAuthor)
            assertFalse(state.isLiked)
            assertFalse(state.isCollected)
            assertFalse(state.isFollowed)
            assertTrue(
                env.mock.engine.requestHistory.none {
                    it.url.encodedPath.contains("api/user/fellow/type")
                }
            )
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun toggleLike_optimisticUpdate_success() = runTest(env.dispatchers.testDispatcher) {
        env.login()
        env.stubBasics()

        val viewModel = env.viewModel()
        viewModel.loadDetail(AlbumNavParam(albumId = albumId))
        advanceUntilIdle()

        viewModel.toggleLike()
        // 乐观更新即时断言
        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals(1L, viewModel.uiState.value.praiseCountDelta)

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLiked)
        assertEquals(1L, viewModel.uiState.value.praiseCountDelta)
        assertEquals("感谢你的点赞 ♥", viewModel.uiState.value.interactionActionMessage)
        assertFalse(viewModel.uiState.value.isPraisePending)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun toggleLike_failure_rollsBackStateAndCount() = runTest(env.dispatchers.testDispatcher) {
        env.login()
        env.stubBasics(
            praiseAddBody = """{"success":false,"code":500,"msg":"点赞网络异常","data":null}"""
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(AlbumNavParam(albumId = albumId))
        advanceUntilIdle()

        viewModel.toggleLike()
        advanceUntilIdle()

        // 失败回滚
        assertFalse(viewModel.uiState.value.isLiked)
        assertEquals(0L, viewModel.uiState.value.praiseCountDelta)
        assertEquals("点赞网络异常", viewModel.uiState.value.interactionActionMessage)
        assertFalse(viewModel.uiState.value.isPraisePending)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun toggleCollect_optimisticUpdate_successAndFailureRollback() =
        runTest(env.dispatchers.testDispatcher) {
            env.login()
            var collectDelShouldFail = false
            env.mock.dynamicResponder = { request ->
                if (request.url.encodedPath.contains("api/collect/del") && collectDelShouldFail) {
                    """{"success":false,"code":500,"msg":"取消收藏失败","data":null}""" to io.ktor.http.HttpStatusCode.OK
                } else {
                    null
                }
            }
            env.stubBasics()

            val viewModel = env.viewModel()
            viewModel.loadDetail(AlbumNavParam(albumId = albumId))
            advanceUntilIdle()

            // 1. 成功收藏
            viewModel.toggleCollect()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isCollected)
            assertEquals(1L, viewModel.uiState.value.collectCountDelta)
            assertEquals("已收藏该相册 ★", viewModel.uiState.value.interactionActionMessage)
            viewModel.consumeInteractionActionMessage()
            assertNull(viewModel.uiState.value.interactionActionMessage)

            // 2. 取消收藏失败并回滚
            collectDelShouldFail = true
            viewModel.toggleCollect()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isCollected)
            assertEquals(1L, viewModel.uiState.value.collectCountDelta)
            assertEquals("取消收藏失败", viewModel.uiState.value.interactionActionMessage)
            assertFalse(viewModel.uiState.value.isCollectPending)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun followAuthor_and_unfollowAuthor_workAsExpected() =
        runTest(env.dispatchers.testDispatcher) {
            env.login()
            env.stubBasics()

            val viewModel = env.viewModel()
            viewModel.loadDetail(AlbumNavParam(albumId = albumId))
            advanceUntilIdle()

            // 1. 关注作者成功
            viewModel.followAuthor()
            assertTrue(viewModel.uiState.value.isFollowed)

            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isFollowed)
            assertEquals(FriendState.FOLLOWING, viewModel.uiState.value.followState)
            assertEquals("已关注作者 (´▽`ʃ♡ƪ)", viewModel.uiState.value.interactionActionMessage)

            // 2. 取消关注作者成功
            viewModel.unfollowAuthor()
            assertFalse(viewModel.uiState.value.isFollowed)

            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isFollowed)
            assertEquals(FriendState.NOT_FOLLOWING, viewModel.uiState.value.followState)
            assertEquals("已取消关注", viewModel.uiState.value.interactionActionMessage)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun interactions_whenNotLoggedIn_promptLoginAndRejectCalls() =
        runTest(env.dispatchers.testDispatcher) {
            env.stubBasics()

            val viewModel = env.viewModel()
            viewModel.loadDetail(AlbumNavParam(albumId = albumId))
            advanceUntilIdle()

            viewModel.toggleLike()
            assertEquals("请先登录后再进行操作", viewModel.uiState.value.interactionActionMessage)
            assertFalse(viewModel.uiState.value.isLiked)

            viewModel.consumeInteractionActionMessage()
            viewModel.toggleCollect()
            assertEquals("请先登录后再进行操作", viewModel.uiState.value.interactionActionMessage)
            assertFalse(viewModel.uiState.value.isCollected)

            viewModel.consumeInteractionActionMessage()
            viewModel.followAuthor()
            assertEquals("请先登录后再进行操作", viewModel.uiState.value.interactionActionMessage)
            assertFalse(viewModel.uiState.value.isFollowed)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun loginStateChange_viaTokenFlow_backfillsInteractionStates() =
        runTest(env.dispatchers.testDispatcher) {
            env.mock.dynamicResponder = { request ->
                when {
                    request.url.encodedPath.contains("api/item/likeInfo") && env.tokenManager.currentToken.isNotBlank() ->
                        env.likeInfoJson(true) to io.ktor.http.HttpStatusCode.OK
                    request.url.encodedPath.contains("api/collect/state") && env.tokenManager.currentToken.isNotBlank() ->
                        env.collectStateJson("care") to io.ktor.http.HttpStatusCode.OK
                    request.url.encodedPath.contains("api/user/fellow/type") && env.tokenManager.currentToken.isNotBlank() ->
                        env.fellowJson(2) to io.ktor.http.HttpStatusCode.OK
                    else -> null
                }
            }
            env.stubBasics()

            val viewModel = env.viewModel()
            viewModel.loadDetail(AlbumNavParam(albumId = albumId))
            advanceUntilIdle()

            // 初始未登录，无互动状态
            assertFalse(viewModel.uiState.value.isLiked)
            assertFalse(viewModel.uiState.value.isCollected)
            assertEquals(FriendState.UNKNOWN, viewModel.uiState.value.followState)

            // 用户登录后，状态变为已登录并由 tokenFlow 广播
            env.tokenManager.saveToken("test_token_123")
            env.tokenManager.saveCurrentUser(999L)
            advanceUntilIdle()

            val updatedState = viewModel.uiState.value
            assertTrue(updatedState.isLiked)
            assertTrue(updatedState.isCollected)
            assertEquals(FriendState.MUTUAL, updatedState.followState)
            assertTrue(updatedState.isFollowed)
            viewModel.viewModelScope.cancel()
        }

    @Test
    fun albumData_authorUserId_correctlyFallsBackWhenUserIdIsZero() {
        val albumWithUserZero = AlbumData(
            id = 1L,
            userId = 777L,
            user = HobbyAlbumUser(id = 0L, userId = 0L)
        )
        assertEquals(777L, albumWithUserZero.authorUserId)

        val albumWithUserUserId = AlbumData(
            id = 1L,
            userId = 777L,
            user = HobbyAlbumUser(id = 0L, userId = 666L)
        )
        assertEquals(666L, albumWithUserUserId.authorUserId)

        val albumWithUserIdOnly = AlbumData(
            id = 1L,
            userId = 777L,
            user = null
        )
        assertEquals(777L, albumWithUserIdOnly.authorUserId)
    }
}
