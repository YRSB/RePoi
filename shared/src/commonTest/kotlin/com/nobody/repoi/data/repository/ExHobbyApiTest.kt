package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.ExHobbyApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ExHobby 裸数组端点纯逻辑测试（W4-W5 pure-logic；KMP commonTest）。
 *
 * `picture/{id}` 返回裸 JSON 数组（无 envelope；见 [ExHobbyApi] KDoc）；
 * MockEngine 直配 `[...]`
 *（HobbyDetailEnv 的 exhobbyEngine 同形；plain `runTest`，无 Main/DataStore）。
 */
class ExHobbyApiTest {

    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private fun apiOf(body: String): ExHobbyApi {
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return ExHobbyApi(HttpClient(engine), json)
    }

    @Test
    fun pictureList_bareArrayDecodesPaths() = runTest {
        val api = apiOf("""[{"path":"2023/05/abc.jpg"},{"path":"2023/05/def.jpg"}]""")

        val list = api.getPictureList(id = 100L, page = 1, item = 100L)

        assertEquals(2, list.size)
        assertEquals("2023/05/abc.jpg", list[0].path)
    }

    @Test
    fun pictureList_emptyArrayDecodesEmpty() = runTest {
        val api = apiOf("[]")

        assertTrue(api.getPictureList(id = 100L, page = 1, item = 100L).isEmpty())
    }
}
