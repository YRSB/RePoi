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

class CharacterDetailRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: CharacterDetailRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = CharacterDetailRepository(api)
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
    fun getCharacterDetail_mapsCharactarAndCopiesFavState() = runTest {
        mock.enqueue(
            body("""{"charactar":{"id":7,"itemId":70,"nameCN":"角色A","cover":"c.jpg"},"isAddFav":true}""")
        )

        val result = repository.getCharacterDetail(7L)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("角色A", data.nameCN)
        assertEquals(7L, data.actualNodeId)
        assertTrue(data.isAddFav)
        val request = mock.takeRequest()
        assertEquals("/api/charactar/get", request.requestUrl?.encodedPath)
        assertEquals("7", request.requestUrl?.queryParameter("charactarId"))
    }

    @Test
    fun getCharacterDetail_acceptsCharacterAliasAndThrowsOnNullData() = runTest {
        mock.enqueue(body("""{"character":{"id":8,"nameCN":"别名角色"}}"""))
        assertEquals("别名角色", repository.getCharacterDetail(8L).getOrThrow().nameCN)

        mock.enqueue(body("null"))
        val missing = repository.getCharacterDetail(8L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue("""{"success":false,"code":404,"msg":"角色不存在","data":null}""")
        val failure = repository.getCharacterDetail(8L)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("角色不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getCharacterWorks_forwardsPagingAndMapsWorksList() = runTest {
        mock.enqueue(
            body("""{"worksList":[{"worksId":9,"wNameCN":"作品A","wNames":"作品A原名","cNameCN":"主角"}]}""")
        )

        val result = repository.getCharacterWorks(7L, page = 2, pageSize = 5)

        assertTrue(result.isSuccess)
        val work = result.getOrThrow().single()
        assertEquals("作品A", work.displayName)
        assertEquals("主角", work.cNameCN)
        val url = mock.takeRequest().requestUrl
        assertEquals("/api/charactar/works/get", url?.encodedPath)
        assertEquals("7", url?.queryParameter("charactarId"))
        assertEquals("2", url?.queryParameter("page"))
        assertEquals("5", url?.queryParameter("pageSize"))
    }

    @Test
    fun getCharacterHobbys_sendsFiveLegacyFormFields() = runTest {
        mock.enqueue(body("""{"list":[{"id":14,"nameCN":"周边A"}]}"""))

        val result = repository.getCharacterHobbys(77L)

        assertEquals("周边A", result.getOrThrow().single().nameCN)
        val request = mock.takeRequest()
        assertEquals("/api/hobby/query-v2", request.requestUrl?.encodedPath)
        val form = parseForm(request.recordedBody())
        assertEquals("77", form["charactar"])
        assertEquals("77", form["character"])
        assertEquals("1", form["page"])
        assertEquals("9", form["pageSize"])
        assertEquals("hits7Day", form["order"])
    }

    @Test
    fun getCharacterAlbumsRelevantAndZones_mapPayloadsAndParams() = runTest {
        mock.enqueue(body("""{"albumList":[{"id":11,"nameCN":"相册A"}]}"""))
        mock.enqueue(body("""{"charactarList":[{"id":12,"nameCN":"相关角色"}]}"""))
        mock.enqueue(body("""{"zones":[{"nodeId":13,"nameCN":"分区A"}]}"""))

        val albums = repository.getCharacterAlbums(7L, page = 3, pageSize = 4)
        val relevant = repository.getCharacterRelevant(7L)
        val zones = repository.getItemZones(99L)

        assertEquals("相册A", albums.getOrThrow().single().nameCN)
        assertEquals("相关角色", relevant.getOrThrow().single().nameCN)
        assertEquals("分区A", zones.getOrThrow().single().displayName)

        val albumUrl = mock.takeRequest().requestUrl
        assertEquals("/api/charactar/album/get", albumUrl?.encodedPath)
        assertEquals("7", albumUrl?.queryParameter("charactarId"))
        assertEquals("3", albumUrl?.queryParameter("page"))
        assertEquals("4", albumUrl?.queryParameter("pageSize"))

        val relevantUrl = mock.takeRequest().requestUrl
        assertEquals("/api/charactar/relevant/get", relevantUrl?.encodedPath)
        assertEquals("7", relevantUrl?.queryParameter("charactarId"))
        assertEquals("10", relevantUrl?.queryParameter("pageSize"))

        val zoneUrl = mock.takeRequest().requestUrl
        assertEquals("/api/zone/item", zoneUrl?.encodedPath)
        assertEquals("99", zoneUrl?.queryParameter("nodeId"))
    }

    @Test
    fun getCharacterLists_propagateBusinessFailure() = runTest {
        val failure = """{"success":false,"code":500,"msg":"error","data":null}"""
        repeat(3) { mock.enqueue(failure) }

        listOf(
            repository.getCharacterWorks(7L),
            repository.getCharacterAlbums(7L),
            repository.getCharacterRelevant(7L)
        ).forEach { result ->
            assertTrue(result.isFailure)
            assertEquals(500, (result.exceptionOrNull() as ApiException).code)
        }
    }

    @Test
    fun toggleCollect_picksAddOrDelEndpointAndInvertsState() = runTest {
        mock.enqueue("""{"success":true,"code":200,"data":{}}""")
        assertTrue(repository.toggleCollect(7L, isCollected = false).getOrThrow())
        val addRequest = mock.takeRequest()
        assertEquals("/api/collect/item/add", addRequest.requestUrl?.encodedPath)
        assertEquals("7", parseForm(addRequest.body.readUtf8())["id"])

        mock.enqueue("""{"success":true,"code":200,"data":{}}""")
        assertFalse(repository.toggleCollect(7L, isCollected = true).getOrThrow())
        val delRequest = mock.takeRequest()
        assertEquals("/api/collect/del", delRequest.requestUrl?.encodedPath)
        assertEquals("7", parseForm(delRequest.body.readUtf8())["id"])

        mock.enqueue("""{"success":false,"code":401,"msg":"请先登录","data":null}""")
        val failure = repository.toggleCollect(7L, isCollected = false)
        assertTrue(failure.isFailure)
        assertEquals(401, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("请先登录", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndChecksParams() = runTest {
        mock.enqueue(body("""{"list":[{"id":1,"content":"评论A"}]}"""))
        val comments = repository.getComments(7L, type = "hot_top", page = 2, pageSize = 5)
        assertEquals("评论A", comments.getOrThrow().single().content)
        val request = mock.takeRequest()
        assertEquals("/api/comment/get", request.requestUrl?.encodedPath)
        val form = parseForm(request.recordedBody())
        assertEquals("7", form["id"])
        assertEquals("hot_top", form["type"])
        assertEquals("2", form["page"])
        assertEquals("5", form["pageSize"])

        mock.enqueue("""{"success":false,"code":-9007,"msg":"找不到该评论","data":null}""")
        val empty = repository.getComments(7L)
        assertTrue(empty.isSuccess)
        assertTrue(empty.getOrThrow().isEmpty())
        assertFalse(parseForm(mock.takeRequest().recordedBody()).containsKey("type"))

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val failure = repository.getComments(7L)
        assertEquals(500, (failure.exceptionOrNull() as ApiException).code)
    }
}
