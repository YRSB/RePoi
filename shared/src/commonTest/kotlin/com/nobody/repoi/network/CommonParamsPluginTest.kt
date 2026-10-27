package com.nobody.repoi.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.parameters
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CommonParamsPlugin] 行为测试，移植自老项目 `CommonParamsInterceptorTest`
 * （MockWebServer → Ktor MockEngine）。
 */
class CommonParamsPluginTest {

    private fun testClient(): Pair<HttpClient, MockEngine> {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("{}"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(CommonParamsPlugin) {
                tokenProvider = { "test_token_123" }
            }
        }
        return client to engine
    }

    @Test
    fun getRequestIncludesRefererAndCommonParams() = runTest {
        val (client, engine) = testClient()

        client.get("https://example.com/api/user-action/list-v3?categoryId=0")
        client.close()

        val request = engine.requestHistory.single()
        assertEquals(CommonParamsConfig.REFERER_URL, request.headers["Referer"])
        assertEquals(CommonParamsConfig.USER_AGENT, request.headers["User-Agent"])

        val params = request.url.parameters
        assertEquals("test_token_123", params["utoken"])
        assertEquals("android", params["platform"])
        assertEquals(CommonParamsConfig.API_APP_VERSION, params["appVersion"])
        assertEquals("official", params["channel"])
        assertEquals(CommonParamsConfig.API_MACHINE, params["machine"])
        assertEquals(CommonParamsConfig.API_SYSTEM_VERSION, params["system"])
        // 原有参数保留
        assertEquals("0", params["categoryId"])
    }

    @Test
    fun getRequestDoesNotOverrideExistingUtoken() = runTest {
        val (client, engine) = testClient()

        client.get("https://example.com/api/user-action/list-v3?utoken=old&categoryId=0")
        client.close()

        val request = engine.requestHistory.single()
        val params = request.url.parameters
        assertEquals("old", params["utoken"])
        assertEquals("android", params["platform"])
    }

    @Test
    fun postFormRequestIncludesCommonParamsInBody() = runTest {
        val (client, engine) = testClient()

        client.post("https://example.com/api/item/infoList") {
            setBody(FormDataContent(parameters { append("id", "12345") }))
        }
        client.close()

        val request = engine.requestHistory.single()
        val body = request.body as FormDataContent
        assertEquals("12345", body.formData["id"])
        assertEquals("test_token_123", body.formData["utoken"])
        assertEquals("android", body.formData["platform"])
        assertEquals(CommonParamsConfig.API_APP_VERSION, body.formData["appVersion"])
        assertEquals("official", body.formData["channel"])
        assertEquals(CommonParamsConfig.API_MACHINE, body.formData["machine"])
        assertEquals(CommonParamsConfig.API_SYSTEM_VERSION, body.formData["system"])
    }

    @Test
    fun emptyPostRequestBuildsFormBodyWithCommonParams() = runTest {
        val (client, engine) = testClient()

        client.post("https://example.com/api/category/web/all")
        client.close()

        val request = engine.requestHistory.single()
        val body = request.body as FormDataContent
        assertEquals("test_token_123", body.formData["utoken"])
        assertEquals("android", body.formData["platform"])
        assertEquals(CommonParamsConfig.API_APP_VERSION, body.formData["appVersion"])
        assertEquals("official", body.formData["channel"])
        assertEquals(CommonParamsConfig.API_MACHINE, body.formData["machine"])
        assertEquals(CommonParamsConfig.API_SYSTEM_VERSION, body.formData["system"])
    }

    @Test
    fun postFormRequestDoesNotOverrideExistingParams() = runTest {
        val (client, engine) = testClient()

        client.post("https://example.com/api/item/infoList") {
            setBody(FormDataContent(parameters { append("utoken", "explicit") }))
        }
        client.close()

        val request = engine.requestHistory.single()
        val body = request.body as FormDataContent
        assertEquals("explicit", body.formData["utoken"])
        assertEquals("android", body.formData["platform"])
    }

    @Test
    fun nonFormPostBodyIsLeftUntouched() = runTest {
        val (client, engine) = testClient()

        client.post("https://example.com/api/message/upload-v2") {
            setBody(TextContent("raw-bytes", io.ktor.http.ContentType.Application.OctetStream))
        }
        client.close()

        val request = engine.requestHistory.single()
        // 非表单 body（multipart 上传等）不合并参数，但头照加
        assertTrue(request.body !is FormDataContent)
        assertEquals(CommonParamsConfig.REFERER_URL, request.headers["Referer"])
        assertEquals(CommonParamsConfig.USER_AGENT, request.headers["User-Agent"])
    }

    @Test
    fun headersPresentOnPostRequests() = runTest {
        val (client, engine) = testClient()

        client.post("https://example.com/api/category/web/all")
        client.close()

        val request = engine.requestHistory.single()
        assertEquals(CommonParamsConfig.REFERER_URL, request.headers["Referer"])
        assertEquals(CommonParamsConfig.USER_AGENT, request.headers["User-Agent"])
    }
}
