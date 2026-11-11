package com.nobody.repoi.ui.album

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.CoverFreshnessRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserHistoryRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.domain.usecase.collect.ToggleItemCollectUseCase
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 相册详情页封面沉淀测试。
 *
 * 契约：详情接口返回的 `cover` 必须写入 [CoverFreshnessRepository]，并以
 * `actualAlbumId` 为键，使历史页的相册按条回源直接命中、零额外请求。
 *
 * 调度器三件套：`Dispatchers.setMain` + `runTest(testDispatcher)` + `MockApi.api(testDispatcher)`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelCoverSinkTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private class Env(
        private val dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
    ) {
        val albumMock = MockApi()
        private val collectMock = MockApi()
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
        val coverFreshnessPreferences = TestDataStore.coverFreshnessPrefs()
        val coverFreshnessRepository = CoverFreshnessRepository(
            HobbyDetailRepository(MockApi().api(dispatcher)),
            albumRepository,
            coverFreshnessPreferences,
            scope,
        )

        fun viewModel(): AlbumDetailViewModel {
            val collectRepository = CollectRepository(collectMock.api(dispatcher), collectMock.json, session)
            val delegate = CommentInteractionDelegate(
                CommentRepository(commentMock.api(dispatcher), session),
                StickerRepository(albumMock.api(dispatcher)),
                session,
            )
            return AlbumDetailViewModel(
                albumRepository,
                collectRepository,
                ToggleItemCollectUseCase(collectRepository),
                session,
                FollowRepository(followMock.api(dispatcher), followMock.json, session),
                UserHistoryRepository(userMock.api(dispatcher), session, coverFreshnessRepository),
                delegate,
                ExHobbyRepository(ExHobbyApi(HttpClient(exhobbyEngine), json)),
                ViewPreferencesManager(TestDataStore.viewPrefs()),
                coverFreshnessRepository,
            )
        }

        fun stubAlbum(id: Long = 8888L, cover: String = "cover/n/album.jpg") {
            albumMock.route(
                "api/album/detail",
                albumMock.ok(
                    """{"album":{"id":$id,"itemId":12,"itemType":"album","cover":"$cover","sortType":"new"}}"""
                )
            )
        }
    }

    @Test
    fun loadDetail_sinksCoverForHistoryRepairWithoutExtraRequest() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubAlbum()

        val viewModel = env.viewModel()
        viewModel.loadDetail(AlbumNavParam(albumId = 8888L))
        advanceUntilIdle()

        val requestsAfterLoad = env.albumMock.requestCount
        val result = env.coverFreshnessRepository.freshCover(
            id = 8888L,
            type = CoverFreshnessRepository.EntityType.ALBUM,
        )

        assertEquals("cover/n/album.jpg", result.getOrThrow(), "详情页应沉淀当前真实封面")
        assertEquals(requestsAfterLoad, env.albumMock.requestCount, "命中沉淀后不应再请求详情接口")
    }

    /**
     * 工单 #117 AC：相册详情页沉淀必须经 500ms 防抖落盘，冷启动重建仓储后仍能命中。
     */
    @Test
    fun loadDetail_sinksCoverAndPersistsAfterDebounce() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.coverFreshnessRepository.ensureWarmed()
        env.stubAlbum()

        env.viewModel().loadDetail(AlbumNavParam(albumId = 8888L))
        advanceUntilIdle()

        advanceTimeBy(501)
        advanceUntilIdle()
        env.coverFreshnessPreferences.awaitPendingWrites()

        val rebuilt = CoverFreshnessRepository(
            HobbyDetailRepository(MockApi().api(testDispatcher)),
            AlbumDetailRepository(MockApi().api(testDispatcher)),
            env.coverFreshnessPreferences,
            backgroundScope,
        )
        rebuilt.ensureWarmed()
        assertEquals(
            "cover/n/album.jpg",
            rebuilt.getCachedCover(8888L, CoverFreshnessRepository.EntityType.ALBUM),
            "相册详情页沉淀应经防抖落盘，重建仓储后仍能命中",
        )
    }

    /** 空 `edit` 排在所有在途写入之后，作为 DataStore 落盘的同步屏障。 */
    private suspend fun DataStore<Preferences>.awaitPendingWrites() {
        edit { }
    }
}
