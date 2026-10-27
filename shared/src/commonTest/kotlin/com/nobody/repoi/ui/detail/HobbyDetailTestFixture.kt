package com.nobody.repoi.ui.detail

import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json

/** 读取 x-www-form-urlencoded 请求体的单个字段（MockEngine dynamicResponder 内按参数分流用）。 */
internal fun HttpRequestData.formField(key: String): String? =
    (body as? FormDataContent)?.formData?.get(key)

/** 请求 path 是否包含给定片段（dynamicResponder 内按端点分流用）。 */
internal fun HttpRequestData.pathHas(fragment: String): Boolean =
    url.encodedPath.contains(fragment)

/**
 * HobbyDetailViewModel 五个测试类的共享装配器（真实仓储 + MockEngine + 真 DataStore，替代 Mockito）。
 *
 * [HobbyDetailViewModel.loadDetail] 第二阶段会并发打出约 11 个请求（规格/时间轴/官图/相册/
 * 热评+最新两路评论/收藏态/同款分组+推荐两路/浏览历史/ExHobby），队列顺序不稳定，
 * 因此 [stubBasics] 全部走 path 规则；需要按参数区分的端点（评论 type/page、二次加载的不同
 * itemId/nodeId）由各测试用 [MockRepoEngine.dynamicResponder] 覆盖（优先级高于规则）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailTestEnv {

    val dispatchers = TestDispatchers()
    lateinit var mock: MockRepoEngine
    lateinit var client: HttpClient
    lateinit var api: HpoiApi
    lateinit var json: Json
    lateinit var stores: TestDataStores
    lateinit var prefs: ViewPreferencesManager
    lateinit var tokenManager: TokenManager
    lateinit var detailRepo: HobbyDetailRepository
    lateinit var collectRepo: CollectRepository
    lateinit var commentRepo: CommentRepository
    lateinit var stickerRepo: StickerRepository
    lateinit var userRepo: UserRepository
    lateinit var delegate: CommentInteractionDelegate
    lateinit var exMock: MockRepoEngine
    lateinit var exClient: HttpClient
    lateinit var exApi: ExHobbyApi
    lateinit var exRepo: ExHobbyRepository

    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        prefs = ViewPreferencesManager(stores.createDataStore("view_prefs"))
        tokenManager = TokenManager(stores.createDataStore("token"))
        detailRepo = HobbyDetailRepository(api)
        collectRepo = CollectRepository(api, json)
        commentRepo = CommentRepository(api, tokenManager)
        stickerRepo = StickerRepository(api)
        userRepo = UserRepository(api, tokenManager)
        delegate = CommentInteractionDelegate(commentRepo, stickerRepo, tokenManager, userRepo)
        exMock = MockRepoEngine()
        exClient = HttpClient(exMock.engine) { expectSuccess = true }
        exApi = ExHobbyApi(exClient, json)
        exRepo = ExHobbyRepository(exApi)
    }

    fun tearDown() {
        client.close()
        exClient.close()
        stores.cleanUp()
        dispatchers.tearDown()
    }

    fun viewModel(): HobbyDetailViewModel =
        HobbyDetailViewModel(detailRepo, collectRepo, delegate, exRepo, prefs, userRepo)

    /**
     * 为详情页全部 10 个主端点 + ExHobby 注册成功响应规则。
     *
     * @param commentsBody 传 null 时跳过评论规则注册（由调用方 dynamicResponder 按 type/page 分流）。
     */
    fun stubBasics(
        detailBody: String = detailJson(59570L, 100L),
        infoListBody: String = okList("list", "[]"),
        timelineBody: String = okList("list", "[]"),
        officialBody: String = okList("list", "[]"),
        albumsBody: String = okList("list", "[]"),
        commentsBody: String? = okList("list", "[]"),
        groupBody: String = """{"success":true,"code":200,"msg":"ok","data":{"groups":[]}}""",
        pushBody: String = """{"success":true,"code":200,"msg":"ok","data":{"hobbys":[]}}""",
        collectBody: String = """{"success":true,"code":200,"msg":"ok","data":{"collect":null}}""",
        historyBody: String = """{"success":true,"code":200,"msg":"ok","data":{}}""",
        exHobbyBody: String = "[]"
    ) {
        mock.respondWhen(Regex.fromLiteral("api/item/get-v2"), detailBody)
        mock.respondWhen(Regex.fromLiteral("api/item/infoList"), infoListBody)
        mock.respondWhen(Regex.fromLiteral("api/user-action/item-info/list"), timelineBody)
        mock.respondWhen(Regex.fromLiteral("api/pic/list/relate-v2"), officialBody)
        mock.respondWhen(Regex.fromLiteral("api/hobby/album"), albumsBody)
        if (commentsBody != null) {
            mock.respondWhen(Regex.fromLiteral("api/comment/get"), commentsBody)
        }
        mock.respondWhen(Regex.fromLiteral("api/hobby/group"), groupBody)
        mock.respondWhen(Regex.fromLiteral("api/hobby/push/list/V2"), pushBody)
        mock.respondWhen(Regex.fromLiteral("api/collect/state"), collectBody)
        mock.respondWhen(Regex.fromLiteral("api/user/read/item/add"), historyBody)
        exMock.respondWhen(Regex.fromLiteral("picture/"), exHobbyBody)
    }

    fun detailJson(itemId: Long, nodeId: Long): String =
        """{"success":true,"code":200,"msg":"ok","data":{"itemData":{"id":$nodeId,"nodeId":$nodeId,"itemId":$itemId,"nameCN":"测试手办"}}}"""

    fun okList(key: String, rawItems: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"$key":$rawItems}}"""

    fun failEnvelope(code: Int = 500, msg: String = "Network error"): String =
        """{"success":false,"code":$code,"msg":"$msg","data":null}"""
}
