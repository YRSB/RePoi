package com.nobody.repoi.ui.album

import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json

/**
 * AlbumDetailViewModel 测试的共享装配器（真实仓储 + MockEngine + 真 DataStore，替代 Mockito）。
 *
 * 与 [com.nobody.repoi.ui.detail.HobbyDetailTestEnv] 同构：主引擎承载全部 HpoiApi 端点，
 * ExHobby 独立引擎（`picture/` 规则）；评论端点默认空列表，互动态端点由各测试按需覆盖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailTestEnv {

    val dispatchers = TestDispatchers()
    lateinit var mock: MockRepoEngine
    lateinit var client: HttpClient
    lateinit var api: HpoiApi
    lateinit var json: Json
    lateinit var stores: TestDataStores
    lateinit var prefs: ViewPreferencesManager
    lateinit var tokenManager: TokenManager
    lateinit var detailRepo: AlbumDetailRepository
    lateinit var collectRepo: CollectRepository
    lateinit var commentRepo: CommentRepository
    lateinit var stickerRepo: StickerRepository
    lateinit var userRepo: UserRepository
    lateinit var delegate: CommentInteractionDelegate
    lateinit var exMock: MockRepoEngine
    lateinit var exClient: HttpClient
    lateinit var exApi: ExHobbyApi
    lateinit var exRepo: ExHobbyRepository

    fun setUp(useVirtualEngine: Boolean = true) {
        dispatchers.setUp()
        val engineDispatcher = if (useVirtualEngine) dispatchers.engineDispatcher else null
        mock = MockRepoEngine(engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        prefs = ViewPreferencesManager(stores.createDataStore("view_prefs"))
        tokenManager = TokenManager(stores.createDataStore("token"))
        detailRepo = AlbumDetailRepository(api)
        collectRepo = CollectRepository(api, json)
        commentRepo = CommentRepository(api, tokenManager)
        stickerRepo = StickerRepository(api)
        userRepo = UserRepository(api, tokenManager)
        delegate = CommentInteractionDelegate(commentRepo, stickerRepo, tokenManager, userRepo)
        exMock = MockRepoEngine(engineDispatcher)
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

    fun viewModel(): AlbumDetailViewModel =
        AlbumDetailViewModel(detailRepo, collectRepo, userRepo, delegate, exRepo, prefs)

    /** Login via real savers (currentUserId is private set). */
    suspend fun login(userId: Long = 999L, token: String = "test_token") {
        tokenManager.saveToken(token)
        tokenManager.saveCurrentUser(userId)
    }

    /**
     * 为详情页全部端点注册成功响应规则。
     *
     * @param likeBody 点赞态规则；传 null 跳过注册（阶段 5 的 like 请求改走 dynamicResponder）。
     * @param collectBody 收藏态规则；同理。
     * @param fellowBody 关注态规则；同理。
     */
    fun stubBasics(
        albumId: Long = 123L,
        authorId: Long = 888L,
        detailBody: String = albumDetailJson(albumId, authorId),
        tagsBody: String = okData(""""tags":[{"count":1,"tag":{"id":1,"tagValue":"手办"}},{"count":1,"tag":{"id":2,"tagValue":"摄影"}}]"""),
        picsBody: String = okList("list", "[]"),
        relatedHobbiesBody: String = okList("hobbys", "[]"),
        relatedAlbumsBody: String = okList("list", "[]"),
        commentsBody: String? = okList("list", "[]"),
        likeBody: String? = likeInfoJson(false),
        collectBody: String? = collectStateJson(null),
        fellowBody: String? = fellowJson(-1),
        historyBody: String = okData(""),
        praiseAddBody: String = okData(""),
        praiseDelBody: String = okData(""),
        collectAddBody: String = okData(""),
        collectDelBody: String = okData(""),
        friendUpdBody: String = okData(""),
        exHobbyBody: String = "[]"
    ) {
        mock.respondWhen(Regex.fromLiteral("api/album/detail"), detailBody)
        mock.respondWhen(Regex.fromLiteral("api/item/tags"), tagsBody)
        mock.respondWhen(Regex.fromLiteral("api/pic/list/relate-v2"), picsBody)
        mock.respondWhen(Regex.fromLiteral("api/item/relate/hobby"), relatedHobbiesBody)
        mock.respondWhen(Regex.fromLiteral("api/album/relate/album"), relatedAlbumsBody)
        if (commentsBody != null) {
            mock.respondWhen(Regex.fromLiteral("api/comment/get"), commentsBody)
        }
        if (likeBody != null) {
            mock.respondWhen(Regex.fromLiteral("api/item/likeInfo"), likeBody)
        }
        if (collectBody != null) {
            mock.respondWhen(Regex.fromLiteral("api/collect/state"), collectBody)
        }
        if (fellowBody != null) {
            mock.respondWhen(Regex.fromLiteral("api/user/fellow/type"), fellowBody)
        }
        mock.respondWhen(Regex.fromLiteral("api/user/read/item/add"), historyBody)
        mock.respondWhen(Regex.fromLiteral("api/item/praise/add"), praiseAddBody)
        mock.respondWhen(Regex.fromLiteral("api/item/praise/del"), praiseDelBody)
        mock.respondWhen(Regex.fromLiteral("api/collect/item/add"), collectAddBody)
        mock.respondWhen(Regex.fromLiteral("api/collect/del"), collectDelBody)
        mock.respondWhen(Regex.fromLiteral("api/user/friend/upd"), friendUpdBody)
        exMock.respondWhen(Regex.fromLiteral("picture/"), exHobbyBody)
    }

    fun albumDetailJson(albumId: Long, authorId: Long): String =
        """{"success":true,"code":200,"msg":"ok","data":{"album":{"id":$albumId,"itemId":$albumId,"name":"测试相册","praiseCount":10,"collect":5,"userId":$authorId,"user":{"id":$authorId,"nickname":"相册作者"}}}}"""

    fun okList(key: String, rawItems: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"$key":$rawItems}}"""

    fun okData(inner: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{$inner}}"""

    fun likeInfoJson(praised: Boolean): String =
        okData(""""praiseState":$praised""")

    /** @param state null=未收藏；"care"=已收藏。 */
    fun collectStateJson(state: String?): String =
        if (state == null) {
            """{"success":true,"code":200,"msg":"ok","data":{"collect":null}}"""
        } else {
            """{"success":true,"code":200,"msg":"ok","data":{"collect":{"id":100,"state":"$state"}}}"""
        }

    /** @param code FriendState.code：0=关注中，2=互相关注，5=未关注，-1=未知。 */
    fun fellowJson(code: Int): String =
        okData(""""friendState":$code""")
}
