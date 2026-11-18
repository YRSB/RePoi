package com.nobody.repoi.ui.album

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserHistoryRepository
import com.nobody.repoi.data.repository.testCoverFreshnessRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 相册详情收藏切换一致性测试：断言 UIState 与 [CollectRepository] 状态池/变更代数同步。
 *
 * `collectCountDelta` 是相对详情接口返回人数的增量，成功往返必须按服务端原始收藏态回零：
 * 原始未收藏时 0 → +1 → 0，原始已收藏时 0 → -1 → 0，避免重复切换积累虚假计数。
 *
 * 调度器三件套：`Dispatchers.setMain` + `runTest(testDispatcher)` + `MockApi.api(testDispatcher)`。
 * 类名刻意不含 `ViewModel`，避免受既有 ViewModel 测试过滤规则影响。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailCollectToggleTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleCollect_whenInitiallyUncollected_addsAndAdvancesGeneration() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubAlbum(collect = 40L)
        env.stubCollectState(collected = false)
        env.collectMock.route(
            "collect/item/add",
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )
        val viewModel = env.viewModel()

        viewModel.loadDetail(AlbumNavParam(albumId = ALBUM_ID))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(0L, viewModel.uiState.value.collectCountDelta)
        assertEquals(40L, viewModel.uiState.value.album?.collect)
        assertNull(env.collectRepository.observeCollectState(ALBUM_ID).value)
        assertEquals(0L, env.collectRepository.collectMutationGeneration.value)
        env.collectMock.drainRequests()

        viewModel.toggleCollect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCollected)
        assertFalse(state.isCollectPending)
        assertEquals(1L, state.collectCountDelta)
        assertEquals("已收藏该相册 ★", state.interactionActionMessage)
        // 界面人数 = 详情接口基数 40 + 增量 1
        assertEquals(41L, (state.album?.collect ?: 0L) + state.collectCountDelta)
        assertEquals(CollectConstants.STATE_CARE, env.collectRepository.observeCollectState(ALBUM_ID).value?.state)
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)
        val recorded = env.collectMock.takeRequest()
        assertEquals("/api/collect/item/add", recorded.encodedPath)
        assertEquals("$ALBUM_ID", recorded.formEntries()["id"])
    }

    @Test
    fun toggleCollect_whenInitiallyCollected_clearsStateAndAppliesNegativeDelta() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubAlbum(collect = 40L)
        env.stubCollectState(collected = true)
        env.collectMock.route(
            "collect/del",
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )
        val viewModel = env.viewModel()

        viewModel.loadDetail(AlbumNavParam(albumId = ALBUM_ID))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0L, viewModel.uiState.value.collectCountDelta)
        assertTrue(env.collectRepository.observeCollectState(ALBUM_ID).value?.isCollected == true)
        assertEquals(0L, env.collectRepository.collectMutationGeneration.value)
        env.collectMock.drainRequests()

        viewModel.toggleCollect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCollected)
        assertFalse(state.isCollectPending)
        assertEquals(-1L, state.collectCountDelta)
        assertEquals("已取消收藏", state.interactionActionMessage)
        assertEquals(39L, (state.album?.collect ?: 0L) + state.collectCountDelta)
        assertNull(env.collectRepository.observeCollectState(ALBUM_ID).value)
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)
        val recorded = env.collectMock.takeRequest()
        assertEquals("/api/collect/del", recorded.encodedPath)
        assertEquals("$ALBUM_ID", recorded.formEntries()["id"])
    }

    @Test
    fun toggleCollect_whenFailure_rollsBackStateAndKeepsGeneration() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubAlbum(collect = 40L)
        env.stubCollectState(collected = false)
        env.collectMock.route("collect/item/add", env.collectMock.failure(500, "收藏失败"))
        val viewModel = env.viewModel()

        viewModel.loadDetail(AlbumNavParam(albumId = ALBUM_ID))
        advanceUntilIdle()
        env.collectMock.drainRequests()

        viewModel.toggleCollect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCollected)
        assertFalse(state.isCollectPending)
        assertEquals(0L, state.collectCountDelta)
        assertEquals(40L, (state.album?.collect ?: 0L) + state.collectCountDelta)
        assertEquals("收藏失败", state.interactionActionMessage)
        assertNull(env.collectRepository.observeCollectState(ALBUM_ID).value)
        assertEquals(0L, env.collectRepository.collectMutationGeneration.value)
        val recorded = env.collectMock.takeRequest()
        assertEquals("/api/collect/item/add", recorded.encodedPath)
        assertEquals("$ALBUM_ID", recorded.formEntries()["id"])
    }

    @Test
    fun toggleCollect_repeatedFromUncollected_returnsDeltaToZero() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubAlbum(collect = 40L)
        env.stubCollectState(collected = false)
        env.collectMock.route(
            "collect/item/add",
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )
        env.collectMock.route(
            "collect/del",
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )
        val viewModel = env.viewModel()

        viewModel.loadDetail(AlbumNavParam(albumId = ALBUM_ID))
        advanceUntilIdle()
        env.collectMock.drainRequests()

        viewModel.toggleCollect()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(1L, viewModel.uiState.value.collectCountDelta)
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)

        viewModel.toggleCollect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCollected)
        assertFalse(state.isCollectPending)
        assertEquals(0L, state.collectCountDelta, "未收藏基线往返后增量必须回零，不能留下虚假计数")
        assertEquals(40L, (state.album?.collect ?: 0L) + state.collectCountDelta)
        assertNull(env.collectRepository.observeCollectState(ALBUM_ID).value)
        assertEquals(2L, env.collectRepository.collectMutationGeneration.value)
        assertEquals("/api/collect/item/add", env.collectMock.takeRequest().encodedPath)
        assertEquals("/api/collect/del", env.collectMock.takeRequest().encodedPath)
    }

    @Test
    fun toggleCollect_repeatedFromCollected_returnsDeltaToZero() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubAlbum(collect = 40L)
        env.stubCollectState(collected = true)
        env.collectMock.route(
            "collect/item/add",
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )
        env.collectMock.route(
            "collect/del",
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )
        val viewModel = env.viewModel()

        viewModel.loadDetail(AlbumNavParam(albumId = ALBUM_ID))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0L, viewModel.uiState.value.collectCountDelta)
        env.collectMock.drainRequests()

        viewModel.toggleCollect()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(-1L, viewModel.uiState.value.collectCountDelta)
        assertEquals(39L, (viewModel.uiState.value.album?.collect ?: 0L) + viewModel.uiState.value.collectCountDelta)

        viewModel.toggleCollect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCollected)
        assertFalse(state.isCollectPending)
        assertEquals(0L, state.collectCountDelta, "已收藏基线往返后增量必须回零，不能留下虚假计数")
        assertEquals(40L, (state.album?.collect ?: 0L) + state.collectCountDelta)
        assertTrue(env.collectRepository.observeCollectState(ALBUM_ID).value?.isCollected == true)
        assertEquals(2L, env.collectRepository.collectMutationGeneration.value)
        assertEquals("/api/collect/del", env.collectMock.takeRequest().encodedPath)
        assertEquals("/api/collect/item/add", env.collectMock.takeRequest().encodedPath)
    }

    @Test
    fun toggleCollect_whilePending_ignoresRapidSecondTap() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubAlbum(collect = 40L)
        env.stubCollectState(collected = false)
        env.collectMock.route(
            "collect/item/add",
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )
        val viewModel = env.viewModel()

        viewModel.loadDetail(AlbumNavParam(albumId = ALBUM_ID))
        advanceUntilIdle()
        env.collectMock.drainRequests()

        viewModel.toggleCollect()
        viewModel.toggleCollect()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCollected)
        assertEquals(1L, state.collectCountDelta)
        assertFalse(state.isCollectPending)
        assertEquals(1, env.collectMock.requestCount, "防抖锁期间第二次切换不应发请求")
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)
        assertEquals("/api/collect/item/add", env.collectMock.takeRequest().encodedPath)
    }

    /** 消费并丢弃首屏阶段的请求记录，保证后续断言只针对切换收藏。 */
    private fun MockApi.drainRequests() {
        repeat(requestCount) { takeRequest() }
    }

    private class Env(
        private val dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
    ) {
        val albumMock = MockApi()
        val collectMock = MockApi()
        private val commentMock = MockApi()
        private val followMock = MockApi()
        private val userMock = MockApi()
        private val session = FakeUserSession("utoken-test")
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        private val exhobbyEngine = MockEngine {
            respond(
                content = "[]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val albumRepository = AlbumDetailRepository(albumMock.api(dispatcher))
        val collectRepository = CollectRepository(collectMock.api(dispatcher), collectMock.json)
        private val coverFreshnessRepository = testCoverFreshnessRepository(
            scope = scope,
            hobbyDetailRepository = HobbyDetailRepository(MockApi().api(dispatcher)),
            albumDetailRepository = albumRepository,
            preferences = TestDataStore.inMemory(),
        )

        fun viewModel(): AlbumDetailViewModel {
            val delegate = CommentInteractionDelegate(
                CommentRepository(commentMock.api(dispatcher)),
                StickerRepository(albumMock.api(dispatcher)),
                session,
            )
            return AlbumDetailViewModel(
                albumRepository,
                collectRepository,
                session,
                FollowRepository(followMock.api(dispatcher), followMock.json),
                UserHistoryRepository(userMock.api(dispatcher), coverFreshnessRepository),
                delegate,
                ExHobbyRepository(ExHobbyApi(HttpClient(exhobbyEngine), json)),
                ViewPreferencesManager(TestDataStore.inMemory()),
                coverFreshnessRepository,
            )
        }

        fun stubAlbum(collect: Long) {
            albumMock.route(
                "api/album/detail",
                albumMock.ok(
                    """{"album":{"id":$ALBUM_ID,"itemId":12,"itemType":"album","cover":"cover/n/album.jpg","sortType":"new","collect":$collect}}"""
                )
            )
        }

        fun stubCollectState(collected: Boolean) {
            val body = if (collected) {
                """{"collect":{"id":$ALBUM_ID,"state":"care"}}"""
            } else {
                "{}"
            }
            collectMock.route("collect/state", collectMock.ok(body))
        }
    }

    private companion object {
        const val ALBUM_ID = 8888L
    }
}
