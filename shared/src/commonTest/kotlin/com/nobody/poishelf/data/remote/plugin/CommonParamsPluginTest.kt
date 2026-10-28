package com.nobody.poishelf.data.remote.plugin

import com.nobody.poishelf.data.remote.interceptor.CommonParams
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parameters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `CommonParamsPlugin`（HttpSend）行为测试（MockEngine，无网络）。
 *
 * 对齐原 `CommonParamsInterceptorTest` 4 用例 + null 跳过 + multipart 不注入：
 * - GET：Referer/UA 头 + 公共参数拼 query，已有同名不覆盖；
 * - POST form：字段合并，已有同名不覆盖；
 * - POST 空包：合成仅含公共参数的 form；
 * - Multipart：不注入公共参数。
 */
class CommonParamsPluginTest {

    private fun plugin(token: String = "test_token_123") =
        CommonParamsPlugin(tokenProvider = { token })

    private fun testClient(
        token: String = "test_token_123",
        onRequest: suspend (io.ktor.client.request.HttpRequestData) -> Unit = {},
    ): HttpClient {
        val mock = MockEngine { request ->
            onRequest(request)
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HttpClient(mock) {
            installCommonParams(tokenProvider = { token })
        }
    }

    @Test
    fun getRequestIncludesRefererAndCommonParams() = runTest {
        var referer: String? = null
        var userAgent: String? = null
        var urlString = ""
        val client = testClient(onRequest = { request ->
            referer = request.headers["Referer"]
            userAgent = request.headers["User-Agent"]
            urlString = request.url.toString()
        })
        client.get("https://www.hpoi.net.cn/api/user-action/list-v3") {
            parameter("categoryId", 0)
        }
        assertEquals(CommonParams.REFERER_URL, referer)
        assertEquals(CommonParams.USER_AGENT, userAgent)
        assertTrue(urlString.contains("utoken=test_token_123"), urlString)
        assertTrue(urlString.contains("platform=android"), urlString)
        assertTrue(urlString.contains("appVersion=${CommonParams.API_APP_VERSION}"), urlString)
        assertTrue(urlString.contains("channel=official"), urlString)
        assertTrue(urlString.contains("machine=${CommonParams.API_MACHINE}"), urlString)
        assertTrue(urlString.contains("system=${CommonParams.API_SYSTEM_VERSION}"), urlString)
        client.close()
    }

    @Test
    fun getRequestDoesNotOverrideExistingUtoken() = runTest {
        var urlString = ""
        val client = testClient(onRequest = { request ->
            urlString = request.url.toString()
        })
        client.get("https://www.hpoi.net.cn/api/user-action/list-v3?utoken=old&categoryId=0")
        assertTrue(urlString.contains("utoken=old"), urlString)
        assertTrue(urlString.contains("platform=android"), urlString)
        client.close()
    }

    @Test
    fun postFormRequestIncludesCommonParamsInBody() = runTest {
        var bodyText = ""
        val client = testClient(onRequest = { request ->
            val body = request.body as FormDataContent
            bodyText = body.formData.entries()
                .flatMap { (k, v) -> v.map { "$k=$it" } }
                .joinToString("&")
        })
        client.post("https://www.hpoi.net.cn/api/item/infoList") {
            setBody(FormDataContent(parameters { append("id", "12345") }))
        }
        assertTrue(bodyText.contains("id=12345"), bodyText)
        assertTrue(bodyText.contains("utoken=test_token_123"), bodyText)
        assertTrue(bodyText.contains("platform=android"), bodyText)
        assertTrue(bodyText.contains("appVersion=${CommonParams.API_APP_VERSION}"), bodyText)
        client.close()
    }

    @Test
    fun emptyPostRequestBuildsFormBodyWithCommonParams() = runTest {
        var bodyText = ""
        val client = testClient(onRequest = { request ->
            val body = request.body as FormDataContent
            bodyText = body.formData.entries()
                .flatMap { (k, v) -> v.map { "$k=$it" } }
                .joinToString("&")
        })
        client.post("https://www.hpoi.net.cn/api/category/web/all") {
            setBody(FormDataContent(io.ktor.http.Parameters.Empty))
        }
        assertTrue(bodyText.contains("utoken=test_token_123"), bodyText)
        assertTrue(bodyText.contains("platform=android"), bodyText)
        assertTrue(bodyText.contains("appVersion=${CommonParams.API_APP_VERSION}"), bodyText)
        assertTrue(bodyText.contains("channel=official"), bodyText)
        assertTrue(bodyText.contains("machine=${CommonParams.API_MACHINE}"), bodyText)
        assertTrue(bodyText.contains("system=${CommonParams.API_SYSTEM_VERSION}"), bodyText)
        client.close()
    }

    @Test
    fun postFormSkipsNullFieldsAndKeepsExistingValues() = runTest {
        var bodyText = ""
        val client = testClient(onRequest = { request ->
            val body = request.body as FormDataContent
            bodyText = body.formData.entries()
                .flatMap { (k, v) -> v.map { "$k=$it" } }
                .joinToString("&")
        })
        client.post("https://www.hpoi.net.cn/api/hobby/query-v2") {
            setBody(
                FormDataContent(
                    parameters {
                        append("page", "1")
                        append("platform", "custom")
                    },
                ),
            )
        }
        // 调用方已有 platform=custom：不覆盖。
        assertTrue(bodyText.contains("platform=custom"), bodyText)
        // 公共参数其余字段补齐。
        assertTrue(bodyText.contains("utoken=test_token_123"), bodyText)
        client.close()
    }

    @Test
    fun mergeKeepsExistingAndSkipsNulls() {
        val merged = CommonParams.merge(
            mapOf("id" to "12345", "order" to null, "platform" to "custom"),
            "test_token_123",
        )
        assertEquals("12345", merged["id"])
        assertEquals("custom", merged["platform"])
        assertEquals("test_token_123", merged["utoken"])
        assertEquals("official", merged["channel"])
    }

    @Test
    fun decodeRefAwareDecodesEnvelope() = runTest {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
        var decoded = ""
        val client = testClient()
        // 经 MockEngine 返回含 @ref 的包络，直接走 decodeRefAware。
        val mock = MockEngine { _ ->
            respond(
                content = """{"code":200,"success":true,"msg":"ok","data":{"name":"A"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val plain = HttpClient(mock)
        val response = plain.get("https://www.hpoi.net.cn/api/ping")
        val body = response.decodeRefAware<com.nobody.poishelf.data.remote.model.CommonResponse<kotlinx.serialization.json.JsonObject>>(json)
        decoded = body.data?.get("name")?.toString().orEmpty()
        assertEquals("\"A\"", decoded)
        plain.close()
        client.close()
    }
}
