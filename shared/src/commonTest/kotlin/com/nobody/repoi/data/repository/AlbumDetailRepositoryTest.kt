package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
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

class AlbumDetailRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: AlbumDetailRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = AlbumDetailRepository(api)
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
                decodeURLPart(parts[0]) to
                    decodeURLPart(parts.getOrElse(1) { "" })
            }
    }

    private fun body(data: String): String {
        return """{"success":true,"code":200,"msg":"ok","data":$data}"""
    }

    @Test
    fun getAlbumDetail_sendsIdsAndMapsAlbum() = runTest {
        mock.enqueue(body("""{"album":{"id":1,"nameCN":"相册A","cover":"c.jpg"}}"""))

        val result = repository.getAlbumDetail(albumId = 1L, itemId = 5L)

        assertTrue(result.isSuccess)
        assertEquals("相册A", result.getOrThrow().nameCN)
        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("1", form["id"])
        assertEquals("5", form["itemId"])
    }

    @Test
    fun getAlbumDetail_missingAlbumThrowsAndFailureKeepsCode() = runTest {
        mock.enqueue(body("{}"))
        val missing = repository.getAlbumDetail(1L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue("""{"success":false,"code":404,"msg":"相册不存在","data":null}""")
        val failure = repository.getAlbumDetail(1L)
        assertTrue(failure.isFailure)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("相册不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getAlbumTags_mapsNonBlankTagsAndDegradesToEmpty() = runTest {
        mock.enqueue(
            body("""{"tags":[{"count":2,"tag":{"tagValue":"标签A"}},{"count":0,"tag":{"tagValue":""}}]}""")
        )

        val success = repository.getAlbumTags(1L)
        assertTrue(success.isSuccess)
        assertEquals(listOf("标签A"), success.getOrThrow())
        assertEquals("1", parseForm(mock.takeRequest().recordedBody())["id"])

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val degraded = repository.getAlbumTags(1L)
        assertTrue(degraded.isSuccess)
        assertTrue(degraded.getOrThrow().isEmpty())
    }

    @Test
    fun getAlbumPictures_forwardsPagingAndOrderParams() = runTest {
        mock.enqueue(body("""{"list":[{"id":1,"path":"pics/a.jpg"}]}"""))

        val result = repository.getAlbumPictures(
            albumId = 1L,
            itemId = 9L,
            page = 2,
            pageSize = 20,
            order = "top"
        )

        assertTrue(result.isSuccess)
        assertEquals("pics/a.jpg", result.getOrThrow().single().path)
        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("9", form["itemId"])
        assertEquals("album", form["itemType"])
        assertEquals("1", form["nodeId"])
        assertEquals("2", form["page"])
        assertEquals("20", form["pageSize"])
        assertEquals("top", form["order"])
    }

    @Test
    fun getRelatedHobbiesAndAlbums_mapListsAndParams() = runTest {
        mock.enqueue(body("""{"hobbys":[{"id":10,"nameCN":"关联手办"}]}"""))
        mock.enqueue(body("""{"list":[{"id":20,"nameCN":"相关相册"}]}"""))

        val hobbies = repository.getRelatedHobbies(1L)
        val albums = repository.getRelatedAlbums(1L)

        assertEquals("关联手办", hobbies.getOrThrow().single().nameCN)
        assertEquals("相关相册", albums.getOrThrow().single().nameCN)
        val hobbyForm = parseForm(mock.takeRequest().recordedBody())
        val albumForm = parseForm(mock.takeRequest().recordedBody())
        assertEquals("1", hobbyForm["id"])
        assertEquals("album", hobbyForm["itemType"])
        assertEquals("8", hobbyForm["pageSize"])
        assertEquals("1", albumForm["nodeId"])
        assertEquals("1", albumForm["page"])
        assertEquals("8", albumForm["pageSize"])
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndThrowsOtherErrors() = runTest {
        mock.enqueue(body("""{"list":[{"id":1,"content":"评论"}]}"""))
        assertEquals("评论", repository.getComments(2L).getOrThrow().single().content)
        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("2", form["id"])
        assertEquals("1", form["page"])
        assertEquals("10", form["pageSize"])

        mock.enqueue("""{"success":false,"code":-9007,"msg":"找不到该评论","data":null}""")
        val empty = repository.getComments(2L)
        assertTrue(empty.isSuccess)
        assertTrue(empty.getOrThrow().isEmpty())

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val failure = repository.getComments(2L)
        assertTrue(failure.isFailure)
        assertEquals(500, (failure.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun likeInfoAndPraiseEndpoints_sendAlbumTypeAndMapState() = runTest {
        mock.enqueue(body("""{"praiseState":true}"""))

        assertTrue(repository.getAlbumLikeInfo(1L).getOrThrow())
        val infoForm = parseForm(mock.takeRequest().recordedBody())
        assertEquals("1", infoForm["nodeId"])
        assertEquals("album", infoForm["itemType"])

        mock.enqueue("""{"success":true,"code":200,"data":{}}""")
        assertTrue(repository.praiseAlbumAdd(1L).isSuccess)
        val addForm = parseForm(mock.takeRequest().recordedBody())
        assertEquals("1", addForm["id"])
        assertEquals("album", addForm["itemType"])

        mock.enqueue("""{"success":true,"code":200,"data":{}}""")
        assertTrue(repository.praiseAlbumDel(1L).isSuccess)
        val delForm = parseForm(mock.takeRequest().recordedBody())
        assertEquals("1", delForm["id"])
        assertEquals("album", delForm["itemType"])

        mock.enqueue("""{"success":false,"code":500,"msg":"点赞失败","data":null}""")
        val failure = repository.praiseAlbumAdd(1L)
        assertTrue(failure.isFailure)
        assertFalse(failure.isSuccess)
        assertEquals("点赞失败", failure.exceptionOrNull()?.message)
    }
}
