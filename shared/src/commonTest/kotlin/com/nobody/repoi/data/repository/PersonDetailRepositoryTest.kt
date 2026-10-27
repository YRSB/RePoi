package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.recordedBody
import com.nobody.repoi.test.recordedPath
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
import com.nobody.repoi.data.remote.model.CollectData
import kotlinx.serialization.json.Json

class PersonDetailRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var collectRepository: CollectRepository
    private lateinit var repository: PersonDetailRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        collectRepository = CollectRepository(api, json)
        repository = PersonDetailRepository(api, collectRepository)
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    private fun body(data: String): String {
        return """{"success":true,"code":200,"msg":"ok","data":$data}"""
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
    fun getPersonDetail_mapsResultAndSendsPersonId() = runTest {
        mock.enqueue(
            body(
                """{"result":{"careerType":2,"person":{"id":3966,"itemId":1589,"nameCN":"人物A","cover":"p.jpg"}}}"""
            )
        )

        val result = repository.getPersonDetail(1589L)

        assertTrue(result.isSuccess)
        val detail = result.getOrThrow()
        assertEquals("人物A", detail.person.displayName)
        assertEquals(3966L, detail.person.id)
        val url = mock.takeRequest().url
        assertEquals("/api/person/get", url.encodedPath)
        assertEquals("1589", url.parameters["personId"))
    }

    @Test
    fun getPersonDetail_rejectsNullPayloadAndBusinessFailure() = runTest {
        mock.enqueue(body("null"))
        val missing = repository.getPersonDetail(1589L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue("""{"success":false,"code":404,"msg":"人物不存在","data":null}""")
        val failure = repository.getPersonDetail(1589L)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("人物不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getPersonWorksAndCharacters_mapNestedWrappersAndParams() = runTest {
        mock.enqueue(body("""{"worksList":[{"w":{"id":1,"itemId":10,"nameCN":"作品A"}}]}"""))
        mock.enqueue(body("""{"characterList":[{"w":{"id":2,"itemId":20,"name":"角色原名"}}]}"""))

        val works = repository.getPersonWorks(1589L)
        val characters = repository.getPersonCharacter(1589L)

        assertEquals("作品A", works.getOrThrow().single().w.displayName)
        assertEquals("角色原名", characters.getOrThrow().single().w.displayName)

        val worksUrl = mock.takeRequest().url
        assertEquals("/api/person/works/get", worksUrl.encodedPath)
        assertEquals("1589", worksUrl.parameters["personId"))
        assertEquals("1", worksUrl.parameters["page"))
        assertEquals("20", worksUrl.parameters["pageSize"))

        val characterUrl = mock.takeRequest().url
        assertEquals("/api/person/character/get", characterUrl.encodedPath)
        assertEquals("1589", characterUrl.parameters["personId"))
    }

    @Test
    fun getPersonHobby_mapsJobNamesAndHobbyItem() = runTest {
        mock.enqueue(
            body("""{"hobby":[{"jobName":["原画","作画監督"],"h":{"id":3,"nameCN":"周边A"}}]}""")
        )

        val result = repository.getPersonHobby(1589L)

        val entry = result.getOrThrow().single()
        assertEquals(listOf("原画", "作画監督"), entry.jobName)
        assertEquals("周边A", entry.h.displayName)
        val url = mock.takeRequest().url
        assertEquals("/api/person/hobby/get", url.encodedPath)
        assertEquals("1589", url.parameters["personId"))
    }

    @Test
    fun personLists_degradeToEmptyOnBusinessFailure() = runTest {
        val failure = """{"success":false,"code":500,"msg":"error","data":null}"""
        repeat(3) { mock.enqueue(failure) }

        assertTrue(repository.getPersonWorks(1589L).getOrThrow().isEmpty())
        assertTrue(repository.getPersonCharacter(1589L).getOrThrow().isEmpty())
        assertTrue(repository.getPersonHobby(1589L).getOrThrow().isEmpty())
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndChecksParams() = runTest {
        mock.enqueue(body("""{"list":[{"id":1,"content":"评论A"}]}"""))
        val comments = repository.getComments(3966L, type = "hot_top", page = 2, pageSize = 5)
        assertEquals("评论A", comments.getOrThrow().single().content)
        val request = mock.takeRequest()
        assertEquals("/api/comment/get", request.url.encodedPath)
        val form = parseForm(request.recordedBody())
        assertEquals("3966", form["id"])
        assertEquals("hot_top", form["type"])
        assertEquals("2", form["page"])
        assertEquals("5", form["pageSize"])

        mock.enqueue("""{"success":false,"code":-9007,"msg":"找不到该评论","data":null}""")
        assertTrue(repository.getComments(3966L).getOrThrow().isEmpty())

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        assertEquals(500, (repository.getComments(3966L).exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getCollectState_delegatesAndMapsCollectData() = runTest {
        mock.enqueue("{"success":true,"code":200,"msg":"ok","data":{"collect":{"state":"care"}}}")
        assertTrue(repository.getCollectState(3966L).getOrThrow())

        mock.enqueue("{"success":true,"code":200,"msg":"ok","data":{"collect":{"state":"delete"}}}")
        assertFalse(repository.getCollectState(3966L).getOrThrow())

        mock.enqueue("{"success":true,"code":200,"msg":"ok","data":{"collect":null}}")
        assertFalse(repository.getCollectState(3966L).getOrThrow())

        mock.enqueue("""{"success":false,"code":401,"msg":"请先登录","data":null}""")
        val failure = repository.getCollectState(3966L)
        assertEquals(401, (failure.exceptionOrNull() as ApiException).code)

        assertEquals(4, mock.requestCount)
    }
}
