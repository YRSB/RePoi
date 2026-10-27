package com.nobody.repoi.ui.person

import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.PersonDetailRepository
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PersonDetailViewModel 的 KMP 移植版（真实仓储 + MockEngine + 真 CommentInteractionDelegate，
 * 替代 Mockito）。
 *
 * `loadDetail` 第二阶段并发 5 路（作品/角色/周边/评论两路/收藏态），队列顺序不稳定，
 * 因此评论端点走 [MockRepoEngine.dynamicResponder] 按表单 `(type, page)` 分流，
 * 其余端点走 path 规则。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var stores: TestDataStores
    private lateinit var tokenManager: TokenManager
    private lateinit var repository: PersonDetailRepository
    private lateinit var delegate: CommentInteractionDelegate

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine(dispatchers.engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        tokenManager = TokenManager(stores.createDataStore("token"))
        tokenManager.currentToken = "utoken-test"
        repository = PersonDetailRepository(api, CollectRepository(api, json))
        delegate = CommentInteractionDelegate(
            CommentRepository(api, tokenManager),
            StickerRepository(api),
            tokenManager,
            UserRepository(api, tokenManager)
        )
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
        dispatchers.tearDown()
    }

    private fun commentJson(id: Long): String = """{"id":$id,"content":"第${id}条"}"""

    private fun commentsJson(range: IntRange): String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[${range.joinToString(",") { commentJson(it.toLong()) }}]}}"""

    private val emptyComments: String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""

    private fun detailJson(personIdJson: Long): String =
        """{"success":true,"code":200,"msg":"ok","data":{"result":{"person":{"id":$personIdJson,"itemId":1589,"nameCN":"八宝备仁"}}}}"""

    /**
     * 按 `(type, page)` 注册评论响应；key 形如 `"hot_top|1"`、`"latest|2"`（type 缺失记为 latest）。
     */
    private fun stubComments(pages: Map<String, String>) {
        mock.dynamicResponder = { req: HttpRequestData ->
            if (!req.url.encodedPath.contains("api/comment/get")) {
                null
            } else {
                val body = req.body as? io.ktor.client.request.forms.FormDataContent
                val form = body?.formData
                val type = form?.get("type") ?: "latest"
                val page = form?.get("page") ?: "1"
                (pages["$type|$page"] ?: emptyComments) to HttpStatusCode.OK
            }
        }
    }

    private fun stubBasics(personIdJson: Long = 3966L) {
        mock.respondWhen(Regex.fromLiteral("api/person/get"), detailJson(personIdJson))
        mock.respondWhen(Regex.fromLiteral("api/person/works/get"), """{"success":true,"code":200,"msg":"ok","data":{"worksList":[]}}""")
        mock.respondWhen(Regex.fromLiteral("api/person/character/get"), """{"success":true,"code":200,"msg":"ok","data":{"characterList":[]}}""")
        mock.respondWhen(Regex.fromLiteral("api/person/hobby/get"), """{"success":true,"code":200,"msg":"ok","data":{"hobby":[]}}""")
        mock.respondWhen(Regex.fromLiteral("api/collect/state"), """{"success":true,"code":200,"msg":"ok","data":{"collect":null}}""")
    }

    @Test
    fun loadDetail_fallsBackToParamNodeIdWhenPersonIdIsZero() = runTest(dispatchers.testDispatcher) {
        stubBasics(personIdJson = 0L)
        stubComments(mapOf("hot_top|1" to emptyComments, "latest|1" to emptyComments))

        val viewModel = PersonDetailViewModel(repository, delegate)
        viewModel.loadDetail(PersonNavParam(personId = 1589L, personNodeId = 3966L))
        advanceUntilIdle()

        assertEquals("八宝备仁", viewModel.uiState.value.person?.displayName)
        assertEquals(3966L, viewModel.uiState.value.personNodeId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadNextCommentsPage_appendsDeduplicated() = runTest(dispatchers.testDispatcher) {
        stubBasics()
        val page2 = """{"success":true,"code":200,"msg":"ok","data":{"list":[${commentJson(10L)},${commentJson(11L)}]}}"""
        stubComments(mapOf("hot_top|1" to emptyComments, "latest|1" to commentsJson(1..10), "latest|2" to page2))

        val viewModel = PersonDetailViewModel(repository, delegate)
        viewModel.loadDetail(PersonNavParam(personId = 1589L))
        advanceUntilIdle()
        viewModel.loadNextCommentsPage()
        advanceUntilIdle()

        assertEquals((1L..11L).toList(), viewModel.uiState.value.latestComments.map { it.id })
        assertEquals(2, viewModel.uiState.value.commentsPage)
        viewModel.viewModelScope.cancel()
    }
}
