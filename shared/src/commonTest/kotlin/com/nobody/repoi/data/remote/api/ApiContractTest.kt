package com.nobody.repoi.data.remote.api

import com.nobody.repoi.network.appJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * `HpoiApi` / `ExHobbyApi` 请求构造合约测试（Ktor MockEngine）。
 *
 * 覆盖全部 Retrofit 注解形态的翻译正确性：GET query、可空省略、POST 表单、
 * `@QueryMap`/`@FieldMap`、`@Path` 替换、空参 POST、multipart、`$ref` 解码。
 */
class ApiContractTest {

    private val json: Json = appJson()

    private fun mockClient(body: String = SUCCESS_JSON): Pair<HttpClient, MockEngine> {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = false
        }
        return client to engine
    }

    @Test
    fun getQueryParamsEncodedAndNullableOmitted() = runTest {
        val (client, engine) = mockClient(ACTION_LIST_JSON)
        val api = HpoiApi(client, json)

        val res = api.getActionList(categoryId = 0, subType = "recommend", pageSize = 20)
        client.close()

        assertTrue(res.success, "响应应解码成功")
        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/user-action/list-v3", request.url.encodedPath)
        val params = request.url.parameters
        assertEquals("0", params["categoryId"])
        assertEquals("recommend", params["subType"])
        assertEquals("20", params["pageSize"])
        // 可空且无默认的参数（zoneNodeId/zoneIds）未传时不应出现在 query 中
        assertEquals(null, params["zoneNodeId"])
        assertEquals(null, params["zoneIds"])
    }

    @Test
    fun postFormFieldsEncodedInBody() = runTest {
        val (client, engine) = mockClient(HOBBY_LIST_JSON)
        val api = HpoiApi(client, json)

        val res = api.getHobbyList(page = 1, categoryId = 10000)
        client.close()

        assertTrue(res.success, "响应应解码成功")
        assertEquals(1, res.data?.list?.size)
        assertEquals("测试手办", res.data?.list?.first()?.displayName)
        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/hobby/query-v2", request.url.encodedPath)
        val body = assertIs<FormDataContent>(request.body)
        assertEquals("1", body.formData["page"])
        assertEquals("20", body.formData["pageSize"])
        assertEquals("10000", body.formData["categoryId"])
        assertEquals("hits7Day", body.formData["order"])
    }

    @Test
    fun fieldMapMergedIntoFormBody() = runTest {
        val (client, engine) = mockClient(HOBBY_LIST_JSON)
        val api = HpoiApi(client, json)

        api.queryHobbyList(mapOf("order" to "hits7Day", "page" to "1", "releaseStart" to "2026-11-05"))
        client.close()

        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Post, request.method)
        val body = assertIs<FormDataContent>(request.body)
        assertEquals("hits7Day", body.formData["order"])
        assertEquals("1", body.formData["page"])
        assertEquals("2026-11-05", body.formData["releaseStart"])
    }

    @Test
    fun queryMapMergedIntoUrl() = runTest {
        val (client, engine) = mockClient()
        val api = HpoiApi(client, json)

        api.getUserCollectHobby(mapOf("page" to "2", "pageSize" to "10"))
        client.close()

        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/collect/hobby-v2", request.url.encodedPath)
        assertEquals("2", request.url.parameters["page"])
        assertEquals("10", request.url.parameters["pageSize"])
    }

    @Test
    fun pathParamsSubstituted() = runTest {
        val (client, engine) = mockClient()
        val api = HpoiApi(client, json)

        api.getForumDetail(forumId = 123L)
        client.close()

        val request = engine.requestHistory.single()
        assertEquals("/api/forum/123", request.url.encodedPath)
    }

    @Test
    fun pathParamsSubstituted_postWithFields() = runTest {
        val (client, engine) = mockClient()
        val api = HpoiApi(client, json)

        api.getPushMessages(type = "system", page = 2)
        client.close()

        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/push/system", request.url.encodedPath)
        val body = assertIs<FormDataContent>(request.body)
        assertEquals("2", body.formData["page"])
        assertEquals("20", body.formData["pageSize"])
    }

    @Test
    fun emptyPostHasNoBody() = runTest {
        val (client, engine) = mockClient(CATEGORY_TREE_JSON)
        val api = HpoiApi(client, json)

        val res = api.getAllCategories()
        client.close()

        assertTrue(res.success, "响应应解码成功")
        assertEquals(1, res.data?.list?.size)
        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/category/web/all", request.url.encodedPath)
        // 空参 POST 不设 body（公共参数由 CommonParamsPlugin 在 Transform 阶段注入表单体）
        assertTrue(request.body is io.ktor.client.utils.EmptyContent)
    }

    @Test
    fun multipartUploadHasFileAndToIdParts() = runTest {
        val (client, engine) = mockClient()
        val api = HpoiApi(client, json)

        api.uploadChatMessagePicture(toId = 42L, fileName = "pic.jpg", bytes = byteArrayOf(1, 2, 3))
        client.close()

        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/message/upload-v2", request.url.encodedPath)
        val body = assertIs<MultiPartFormDataContent>(request.body)
        val parts = body.parts.associate { part ->
            val disposition = part.headers["Content-Disposition"] ?: ""
            val name = Regex("name=\"?([^\"]+)\"?").find(disposition)?.groupValues?.get(1) ?: disposition
            name to part
        }
        assertTrue(parts.containsKey("toId"), "应包含 toId 文本 part")
        val filePart = parts["file"] ?: error("应包含 file part")
        assertEquals("image/jpeg", filePart.headers["Content-Type"])
        assertTrue(
            filePart.headers["Content-Disposition"]?.contains("pic.jpg") == true,
            "file part 应携带 filename",
        )
    }

    @Test
    fun jsonRefResponseDecoded() = runTest {
        val (client, _) = mockClient(JSON_REF_JSON)
        val api = HpoiApi(client, json)

        val res = api.getHobbyList(page = 1, categoryId = 10000)
        client.close()

        assertTrue(res.success, "响应应解码成功")
        val list = res.data?.list ?: error("data.list 不应为空")
        assertEquals(2, list.size)
        // 第二项是 {"$ref": "1"} 引用体，应还原为第一项的完整对象
        assertEquals("引用手办", list[1].displayName)
    }

    @Test
    fun exHobbyPostWithQueryAndRawList() = runTest {
        val (client, engine) = mockClient(EXHOBBY_JSON)
        val api = ExHobbyApi(client, json)

        val list = api.getPictureList(id = 59570L, page = 1, item = 59570L)
        client.close()

        assertEquals(1, list.size)
        assertEquals("2023/05/abc.jpg", list.first().path)
        val request = engine.requestHistory.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/picture/59570", request.url.encodedPath)
        assertEquals("1", request.url.parameters["page"])
        assertEquals("59570", request.url.parameters["item"])
        assertEquals("all", request.url.parameters["type"])
    }

    companion object {
        private const val SUCCESS_JSON = """{"success":true,"code":200,"msg":"操作成功"}"""
        private const val ACTION_LIST_JSON =
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        private const val HOBBY_LIST_JSON =
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"itemId":59570,"name":"测试手办"}]}}"""
        private const val CATEGORY_TREE_JSON =
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":10000}]}}"""
        private const val JSON_REF_JSON =
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"${'$'}id":"1","id":1,"itemId":59570,"name":"引用手办"},{"${'$'}ref":"1"}]}}"""
        private const val EXHOBBY_JSON = """[{"path":"2023/05/abc.jpg"}]"""
    }
}
