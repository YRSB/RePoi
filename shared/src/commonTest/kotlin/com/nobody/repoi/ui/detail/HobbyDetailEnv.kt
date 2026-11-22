package com.nobody.repoi.ui.detail

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
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
import kotlinx.serialization.json.Json

class HobbyDetailEnv(

    val scope: CoroutineScope,
    val detailMock: MockApi = MockApi(),
    val collectMock: MockApi = MockApi(),
    val commentMock: MockApi = MockApi(),
    val userMock: MockApi = MockApi(),
    val albumMock: MockApi = MockApi(),

    private val dispatcher: CoroutineDispatcher? = null,
) {
    private val session = FakeUserSession("utoken-test")
    val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val exhobbyEngine = MockEngine {
        respond(
            content = "[]",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    val detailRepository = HobbyDetailRepository(detailMock.api(dispatcher))
    val collectRepository = CollectRepository(collectMock.api(dispatcher), collectMock.json)
    val commentRepository = CommentRepository(commentMock.api(dispatcher))
    val stickerRepository = StickerRepository(detailMock.api(dispatcher))
    val exHobbyRepository = ExHobbyRepository(ExHobbyApi(HttpClient(exhobbyEngine), json))
    val preferences = ViewPreferencesManager(TestDataStore.inMemory())
    val albumDetailRepository = AlbumDetailRepository(albumMock.api(dispatcher))
    val coverFreshnessPreferences = TestDataStore.coverFreshnessPrefs()
    val coverFreshnessRepository = testCoverFreshnessRepository(
        scope = scope,
        hobbyDetailRepository = detailRepository,
        albumDetailRepository = albumDetailRepository,
        preferences = coverFreshnessPreferences,
    )
    val userHistoryRepository = UserHistoryRepository(
        userMock.api(dispatcher), coverFreshnessRepository
    )

    val commentDelegate = CommentInteractionDelegate(
        commentRepository, stickerRepository, session
    )

    fun viewModel(): HobbyDetailViewModel = HobbyDetailViewModel(
        detailRepository, collectRepository, commentDelegate,
        exHobbyRepository, preferences, userHistoryRepository, coverFreshnessRepository
    )

    fun stubBasics(nodeId: Long = 100L, itemId: Long = 59570L) {
        detailMock.enqueue(
            detailMock.ok("""{"itemData":{"id":$nodeId,"nodeId":$nodeId,"itemId":$itemId,"nameCN":"测试手办","cover":"cover/n/test.jpg","collect":100}}""")
        )
        detailMock.enqueue(detailMock.ok("""{"list":[]}"""))
        detailMock.enqueue(detailMock.ok("""{"list":[]}"""))
        detailMock.enqueue(detailMock.ok("""{"list":[]}"""))
        detailMock.enqueue(detailMock.ok("""{"list":[]}"""))
        detailMock.enqueue(detailMock.ok("""{"groups":[],"hobbys":[]}"""))
        detailMock.enqueue(detailMock.ok("""{"list":[]}"""))
        detailMock.enqueue(detailMock.ok("""{"list":[]}"""))
    }
}
