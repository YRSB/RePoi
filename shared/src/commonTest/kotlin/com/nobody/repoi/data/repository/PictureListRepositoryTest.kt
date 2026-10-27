package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.recordedBody
import com.nobody.repoi.test.recordedPath
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.serialization.json.Json

class PictureListRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: PictureListRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = PictureListRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    private fun parseForm(body: String): Map<String, String> {
        return body.split("&")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].decodeURLPart() to
                    (parts.getOrElse(1) { "" }).decodeURLPart()
            }
    }

    @Test
    fun getPictureList_sendsAllParamsAndMapsItems() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"path":"items/pic_1.jpg","subType":"official"}]}}"""
            )

        val result = repository.getPictureList(
            itemId = 59570L,
            itemType = "hobby",
            subType = "official",
            page = 2,
            pageSize = 20,
            order = "top",
            nodeId = 7788L
        )

        assertTrue(result.isSuccess)
        assertEquals("items/pic_1.jpg", result.getOrThrow().single().path)
        val request = mock.takeRequest()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/pic/list/relate-v2", request.recordedPath)
        val form = parseForm(request.recordedBody())
        assertEquals("59570", form["itemId"])
        assertEquals("hobby", form["itemType"])
        assertEquals("official", form["subType"])
        assertEquals("2", form["page"])
        assertEquals("20", form["pageSize"])
        assertEquals("top", form["order"])
        assertEquals("7788", form["nodeId"])
    }

    @Test
    fun getPictureList_omitsOptionalNulls() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        val result = repository.getPictureList(
            itemId = 59570L,
            itemType = "hobby",
            subType = null
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("59570", form["itemId"])
        assertEquals("hobby", form["itemType"])
        assertEquals("1", form["page"])
        assertEquals("20", form["pageSize"])
        assertFalse(form.containsKey("subType"))
        assertFalse(form.containsKey("order"))
        assertFalse(form.containsKey("nodeId"))
    }

    @Test
    fun getPictureList_failureThrowsApiException() = runTest {
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录过期","data":null}""")

        val result = repository.getPictureList(1L, "hobby", "official")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(4001, error.code)
        assertEquals("登录过期", error.message)
    }
}
