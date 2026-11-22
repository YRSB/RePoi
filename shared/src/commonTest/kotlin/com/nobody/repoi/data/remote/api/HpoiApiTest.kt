package com.nobody.repoi.data.remote.api

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.plugin.BypassTokenSelfHealingAttributeKey
import com.nobody.repoi.data.remote.plugin.installCommonParams
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HpoiApiTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun envelope(code: Int, data: String?, msg: String = "ok", success: Boolean = true): String =
        """{"success":$success,"code":$code,"msg":"$msg","data":$data}"""

    private fun apiOf(
        body: String,
        token: String = "utoken-test",
        onTokenExpired: suspend (String) -> Unit = {},
        onRequest: (HttpRequestData) -> Unit = {},
    ): HpoiApi {
        val handler: MockRequestHandler = { request ->
            onRequest(request)
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(MockEngine(handler)) { expectSuccess = false }
            .installCommonParams { token }
        return HpoiApi(client, json, onTokenExpired)
    }

    @Test
    fun getUserSelf_成功响应交付洁净载荷() = runTest {
        val api = apiOf(
            envelope(200, """{"user":{"id":42,"nickname":"胶友"}}"""),
        )

        val payload = api.getUserSelf()

        assertEquals(42L, payload.user?.id)
        assertEquals("胶友", payload.user?.nickname)
    }

    @Test
    fun getUserSelf_成功白名单覆盖200和0和负1() = runTest {
        for (code in listOf(200, 0, -1)) {
            val api = apiOf(envelope(code, """{"user":{"id":7}}"""))

            assertEquals(7L, api.getUserSelf().user?.id)
        }
    }

    @Test
    fun getUserSelf_业务失败抛出带服务端消息的ApiException() = runTest {
        val api = apiOf(envelope(503, "null", msg = "服务暂不可用", success = false))

        val error = assertFailsWith<ApiException> { api.getUserSelf() }

        assertEquals(503, error.code)
        assertEquals("服务暂不可用", error.message)
    }

    @Test
    fun getUserSelf_成功但数据为空时抛出非空断言异常() = runTest {
        val api = apiOf(envelope(200, "null"))

        val error = assertFailsWith<ApiException> { api.getUserSelf() }

        assertEquals(200, error.code)
        assertEquals("服务端响应数据为空", error.message)
    }

    @Test
    fun getUserSelf_评论未命中码抛出专用异常() = runTest {
        val api = apiOf(envelope(-9007, "null", msg = "找不到该评论，可能已被删除", success = false))

        val error = assertFailsWith<CommentNotFoundException> { api.getUserSelf() }

        assertEquals(-9007, error.code)
        assertEquals("找不到该评论，可能已被删除", error.message)
    }

    @Test
    fun getUserSelf_消息含找不到该评论时抛出专用异常() = runTest {
        val api = apiOf(envelope(500, "null", msg = "找不到该评论", success = false))

        val error = assertFailsWith<CommentNotFoundException> { api.getUserSelf() }

        assertEquals(500, error.code)
    }

    @Test
    fun getUserSelf_遇4001按在途快照驱动令牌自愈() = runTest {
        val notified = mutableListOf<String>()
        val api = apiOf(
            envelope(4001, "null", msg = "登录已过期", success = false),
            token = "tok-7",
            onTokenExpired = { notified.add(it) },
        )

        val error = assertFailsWith<ApiException> { api.getUserSelf() }

        assertEquals(4001, error.code)
        assertEquals(listOf("tok-7"), notified)
    }

    @Test
    fun getUserSelfWithToken_候选验权4001旁路不通知全局会话失效() = runTest {
        val notified = mutableListOf<String>()
        var bypassFlag: Boolean? = null
        val api = apiOf(
            envelope(4001, "null", msg = "登录凭证已过期", success = false),
            token = "active-token",
            onTokenExpired = { notified.add(it) },
            onRequest = { request ->
                bypassFlag = request.attributes.getOrNull(BypassTokenSelfHealingAttributeKey)
            },
        )

        val error = assertFailsWith<ApiException> { api.getUserSelfWithToken("candidate-token") }

        assertEquals(4001, error.code)
        assertEquals(true, bypassFlag)
        assertTrue(notified.isEmpty())
    }

    @Test
    fun updateRiakSetting_写操作成功返回Unit且失败抛出异常() = runTest {
        val successApi = apiOf(envelope(-1, "null"))
        successApi.updateRiakSetting("setting_r18", 1)

        val failureApi = apiOf(envelope(4002, "null", msg = "更新失败", success = false))
        val error = assertFailsWith<ApiException> { failureApi.updateRiakSetting("setting_r18", 1) }
        assertEquals(4002, error.code)
    }

    @Test
    fun updateRiakSetting_遇4001同样驱动在途令牌自愈() = runTest {
        val notified = mutableListOf<String>()
        val api = apiOf(
            envelope(4001, "null", msg = "登录已过期", success = false),
            token = "tok-9",
            onTokenExpired = { notified.add(it) },
        )

        assertFailsWith<ApiException> { api.updateRiakSetting("setting_r18", 1) }

        assertEquals(listOf("tok-9"), notified)
    }

    @Test
    fun getUserPrivacy_显式可空端点在数据缺省时返回null() = runTest {
        val withData = apiOf(envelope(200, """{"phone":"13800000000","email":"a@b.com"}"""))
        assertEquals("13800000000", withData.getUserPrivacy()?.phone)

        val withoutData = apiOf(envelope(200, "null"))
        assertNull(withoutData.getUserPrivacy())
    }

    @Test
    fun getUserPrivacy_显式可空端点业务失败仍抛出异常() = runTest {
        val api = apiOf(envelope(500, "null", msg = "服务异常", success = false))

        val error = assertFailsWith<ApiException> { api.getUserPrivacy() }

        assertEquals(500, error.code)
    }

    @Test
    fun login_成功响应交付洁净载荷() = runTest {
        val api = apiOf(envelope(200, """{"utoken":"token-a","user":{"id":31,"userName":"alice"}}"""))

        val data = api.login("alice", "pwd-123")

        assertEquals("token-a", data.utoken)
        assertEquals(31L, data.user?.actualUserId)
    }

    @Test
    fun login_白名单外成功码但携带有效令牌时仍交付载荷() = runTest {
        val api = apiOf(
            envelope(1001, """{"utoken":"token-a","user":{"id":31,"userName":"alice"}}""", success = true),
        )

        val data = api.login("alice", "pwd-123")

        assertEquals("token-a", data.utoken)
    }

    @Test
    fun login_业务失败抛ApiException且4001驱动在途令牌自愈() = runTest {
        val failureApi = apiOf(envelope(1001, "null", msg = "账号或密码错误", success = false))
        val error = assertFailsWith<ApiException> { failureApi.login("alice", "wrong") }
        assertEquals(1001, error.code)
        assertEquals("账号或密码错误", error.message)

        val notified = mutableListOf<String>()
        val expiredApi = apiOf(
            envelope(4001, "null", msg = "登录已过期", success = false),
            token = "tok-login",
            onTokenExpired = { notified.add(it) },
        )
        val expiredError = assertFailsWith<ApiException> { expiredApi.login("alice", "expired") }
        assertEquals(4001, expiredError.code)
        assertEquals(listOf("tok-login"), notified)
    }
}
