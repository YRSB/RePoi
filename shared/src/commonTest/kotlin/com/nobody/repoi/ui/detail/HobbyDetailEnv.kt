package com.nobody.repoi.ui.detail

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.CoverFreshnessRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserHistoryRepository
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

/**
 * 手办详情 ViewModel 测试环境：真实 7 依赖 + MockEngine。
 *
 * - Hpoi 系仓储各配独立 [MockApi]（请求隔离、断言独立），含相册详情（供封面沉淀测试观察）。
 * - ExHobby 系配空数组 MockEngine（非 ExHobby 用例不触发网络）。
 * - VPM 为真实实例（[TestDataStore] 隔离目录）。
 */
class HobbyDetailEnv(
    // 封面新鲜度仓储的预热/落盘协程作用域：测试传 runTest 的 backgroundScope（虚拟时间可控）
    val scope: CoroutineScope,
    val detailMock: MockApi = MockApi(),
    val collectMock: MockApi = MockApi(),
    val commentMock: MockApi = MockApi(),
    val userMock: MockApi = MockApi(),
    val albumMock: MockApi = MockApi(),
    // 调度器三件套之一：引擎响应分发落在测试调度器上，否则 advanceUntilIdle 推不动请求体
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
    val collectRepository = CollectRepository(collectMock.api(dispatcher), collectMock.json, session)
    val commentRepository = CommentRepository(commentMock.api(dispatcher), session)
    val stickerRepository = StickerRepository(detailMock.api(dispatcher))
    val exHobbyRepository = ExHobbyRepository(ExHobbyApi(HttpClient(exhobbyEngine), json))
    val preferences = ViewPreferencesManager(TestDataStore.viewPrefs())
    val albumDetailRepository = AlbumDetailRepository(albumMock.api(dispatcher))
    val coverFreshnessRepository = CoverFreshnessRepository(
        detailRepository,
        albumDetailRepository,
        TestDataStore.coverFreshnessPrefs(),
        scope,
    )
    val userHistoryRepository = UserHistoryRepository(
        userMock.api(dispatcher), session, coverFreshnessRepository
    )

    val commentDelegate = CommentInteractionDelegate(
        commentRepository, stickerRepository, session
    )

    fun viewModel(): HobbyDetailViewModel = HobbyDetailViewModel(
        detailRepository, collectRepository, commentDelegate,
        exHobbyRepository, preferences, userHistoryRepository, coverFreshnessRepository
    )

    /** loadDetail 基础 7 件套（详情/信息/官图/相册/时间轴/关联/评论×2）。 */
    fun stubBasics(nodeId: Long = 100L, itemId: Long = 59570L) {
        detailMock.enqueue(
            detailMock.ok("""{"itemData":{"id":$nodeId,"nodeId":$nodeId,"itemId":$itemId,"nameCN":"测试手办","cover":"cover/n/test.jpg","collect":100}}""")
        )
        detailMock.enqueue(detailMock.ok("""{"list":[]}""")) // infoList
        detailMock.enqueue(detailMock.ok("""{"list":[]}""")) // officialPics
        detailMock.enqueue(detailMock.ok("""{"list":[]}""")) // userAlbums
        detailMock.enqueue(detailMock.ok("""{"list":[]}""")) // timeline
        detailMock.enqueue(detailMock.ok("""{"groups":[],"hobbys":[]}""")) // relatedHobbies
        detailMock.enqueue(detailMock.ok("""{"list":[]}""")) // comments hot_top
        detailMock.enqueue(detailMock.ok("""{"list":[]}""")) // comments default
    }
}
