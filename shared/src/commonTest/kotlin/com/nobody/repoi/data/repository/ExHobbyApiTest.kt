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
