package com.nobody.repoi.data.remote.plugin

import com.nobody.repoi.data.remote.model.CommonResponse
import com.nobody.repoi.data.remote.model.UserSearchPayload
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RefAwareTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun unwrapDoubleEncodedJson_plainObjectReturnsSelf() {
        val raw = """{"code":200,"success":true}"""
        assertEquals(raw, unwrapDoubleEncodedJson(raw, json))
    }

    @Test
    fun unwrapDoubleEncodedJson_plainArrayReturnsSelf() {
        val raw = """[{"id":1},{"id":2}]"""
        assertEquals(raw, unwrapDoubleEncodedJson(raw, json))
    }

    @Test
    fun unwrapDoubleEncodedJson_doubleEncodedJsonObjectUnwraps() {
        val encoded = """"{\"code\":200,\"success\":true,\"msg\":\"ok\"}""""
        val expected = """{"code":200,"success":true,"msg":"ok"}"""
        assertEquals(expected, unwrapDoubleEncodedJson(encoded, json))
    }

    @Test
    fun unwrapDoubleEncodedJson_doubleEncodedJsonWithNewlinesAndEscapes() {
        val encoded = """"{\"code\":200,\"msg\":\"操作成功\",\"data\":{\"sign\":\"黄鸡千年\\n\\nAL永存\"}}""""
        val expected = """{"code":200,"msg":"操作成功","data":{"sign":"黄鸡千年\n\nAL永存"}}"""
        assertEquals(expected, unwrapDoubleEncodedJson(encoded, json))
    }

    @Test
    fun unwrapDoubleEncodedJson_plainStringLiteralReturnsSelf() {
        val raw = """"just a plain text string""""
        assertEquals(raw, unwrapDoubleEncodedJson(raw, json))
    }

    @Test
    fun decodeRefAware_decodesDoubleEncodedResponseSuccessfully() = runTest {
        // 模拟 Hpoi 真实 API 在 Accept: application/json 时返回的 Spring MVC 双重 JSON 序列化字符串
        val doubleEncoded = """"{\"@id\":\"1\",\"success\":true,\"msg\":\"操作成功\",\"code\":200,\"data\":{\"userList\":[{\"def\":2,\"nickname\":\"碧蓝航线\",\"sign\":\"还没有信仰_(:з」∠)_\",\"hp\":0,\"header\":\"none.jpg\",\"atk\":0,\"id\":939873,\"userId\":160541}]}}""""

        val mock = MockEngine { _ ->
            respond(
                content = doubleEncoded,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json;charset=utf-8"),
            )
        }
        val client = HttpClient(mock)
        val response = client.get("https://www.hpoi.net.cn/api/search/user")
        val body = response.decodeRefAware<CommonResponse<UserSearchPayload>>(json)

        assertEquals(200, body.code)
        assertEquals(true, body.success)
        val payload = body.data
        assertNotNull(payload)
        val list = payload.userList
        assertNotNull(list)
        assertEquals(1, list.size)
        assertEquals(160541L, list[0].userId)
        assertEquals("碧蓝航线", list[0].nickname)
        assertEquals("none.jpg", list[0].header)

        client.close()
    }

    @Test
    fun decodeRefAware_decodesDoubleEncodedResponseWithRef() = runTest {
        val doubleEncodedWithRef = """"{\"code\":200,\"success\":true,\"data\":{\"first\":{\"@id\":\"obj1\",\"id\":10,\"name\":\"A\"},\"second\":{\"@ref\":\"obj1\"}}}""""

        val mock = MockEngine { _ ->
            respond(
                content = doubleEncodedWithRef,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json;charset=utf-8"),
            )
        }
        val client = HttpClient(mock)
        val response = client.get("https://www.hpoi.net.cn/api/test")
        val body = response.decodeRefAware<CommonResponse<JsonObject>>(json)

        assertEquals(200, body.code)
        val data = body.data
        assertNotNull(data)
        val second = data["second"] as? JsonObject
        assertNotNull(second)
        assertEquals("10", second["id"]?.toString())

        client.close()
    }
}
