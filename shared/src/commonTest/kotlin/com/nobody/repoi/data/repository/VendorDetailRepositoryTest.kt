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

class VendorDetailRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var collectRepository: CollectRepository
    private lateinit var repository: VendorDetailRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        collectRepository = CollectRepository(api, json)
        repository = VendorDetailRepository(api, collectRepository)
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

    private fun body(data: String): String {
        return """{"success":true,"code":200,"msg":"ok","data":$data}"""
    }

    @Test
    fun getCompanyDetail_mapsCompanyAndSendsCompanyId() = runTest {
        mock.enqueue(
            body("""{"company":{"id":1,"itemId":100,"nameCN":"厂商A","website":"https://a.example"}}""")
        )

        val result = repository.getCompanyDetail(100L)

        assertTrue(result.isSuccess)
        assertEquals("厂商A", result.getOrThrow().nameCN)
        assertEquals("https://a.example", result.getOrThrow().website)
        val url = mock.takeRequest().url
        assertEquals("/api/company/v2/get", url.encodedPath)
        assertEquals("100", url.parameters["companyId"])
    }

    @Test
    fun getCompanyDetail_throwsOnMissingCompanyAndFailure() = runTest {
        mock.enqueue(body("{}"))
        val missing = repository.getCompanyDetail(100L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue("""{"success":false,"code":404,"msg":"厂商不存在","data":null}""")
        val failure = repository.getCompanyDetail(100L)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("厂商不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getCompanyActions_forwardsFiltersAndDegradesToEmpty() = runTest {
        mock.enqueue(
            body("""{"list":[{"id":1,"actionMessage":"开订时间","actionData":"preorder","actionType":1}]}""")
        )

        val result = repository.getCompanyActions(20L, subType = "news", category = 2, lastId = 33L)

        val action = result.getOrThrow().single()
        assertEquals("开订时间", action.actionTitle)
        val url = mock.takeRequest().url
        assertEquals("/api/user-action/company/list", url.encodedPath)
        assertEquals("20", url.parameters["companyNodeId"])
        assertEquals("news", url.parameters["subType"])
        assertEquals("2", url.parameters["category"])
        assertEquals("33", url.parameters["lastId"])

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        assertTrue(repository.getCompanyActions(20L).getOrThrow().isEmpty())
    }

    @Test
    fun getCompanyMyVotes_mapsTagsAndVotesAndFallsBackToDefaults() = runTest {
        mock.enqueue(
            body(
                """{"allTags":[{"id":1,"name":"做工","allowScoring":1},{"id":2,"name":"性价比"}],"userVotes":[{"tagId":1,"score":4.5}]}"""
            )
        )

        val votes = repository.getCompanyMyVotes(100L).getOrThrow()

        assertTrue(votes.allTags.first().isScoreTag)
        assertFalse(votes.allTags.last().isScoreTag)
        assertEquals(4.5, votes.userVotes.single().score)
        val url = mock.takeRequest().url
        assertEquals("/api/company/tag/myVotes", url.encodedPath)
        assertEquals("100", url.parameters["companyId"])

        mock.enqueue(body("null"))
        val defaults = repository.getCompanyMyVotes(100L).getOrThrow()
        assertTrue(defaults.allTags.isEmpty())
        assertTrue(defaults.userVotes.isEmpty())

        mock.enqueue("""{"success":false,"code":401,"msg":"请先登录","data":null}""")
        assertEquals(401, (repository.getCompanyMyVotes(100L).exceptionOrNull() as ApiException).code)
    }

    @Test
    fun submitCompanyTagVote_encodesNormalAndScoreTags() = runTest {
        mock.enqueue("""{"success":true,"code":200,"data":{}}""")

        val result = repository.submitCompanyTagVote(
            companyId = 100L,
            normalTagIds = listOf(1L, 2L),
            scoreVotes = mapOf(3L to 4.5, 4L to 3.0)
        )

        assertTrue(result.isSuccess)
        val request = mock.takeRequest()
        assertEquals("/api/company/tag/vote", request.url.encodedPath)
        val form = parseForm(request.recordedBody())
        assertEquals("100", form["companyId"])
        assertEquals("[1,2]", form["normalTagIds"])
        assertEquals("""[{"tagId":3,"score":4.5},{"tagId":4,"score":3.0}]""", form["scoreTagIds"])

        mock.enqueue("""{"success":false,"code":500,"msg":"投票失败","data":null}""")
        val failure = repository.submitCompanyTagVote(100L, emptyList(), emptyMap())
        assertEquals(500, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("投票失败", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getCompanySeriesAndTags_mapListsAndDegradeToEmpty() = runTest {
        mock.enqueue(body("""{"seriesList":[{"id":1,"nameCN":"系列A","hobbyCount":8}]}"""))
        mock.enqueue(body("""{"list":[{"id":2,"name":"品牌A","tagText":"品牌A"}]}"""))

        val series = repository.getCompanySeries(20L, page = 2, pageSize = 5)
        val tags = repository.getCompanyTags(100L)

        assertEquals("系列A", series.getOrThrow().single().displayName)
        assertEquals("品牌A", tags.getOrThrow().single().displayTag)

        val seriesUrl = mock.takeRequest().url
        assertEquals("/api/company/series", seriesUrl.encodedPath)
        assertEquals("20", seriesUrl.parameters["companyNodeId"])
        assertEquals("2", seriesUrl.parameters["page"])
        assertEquals("5", seriesUrl.parameters["pageSize"])

        val tagUrl = mock.takeRequest().url
        assertEquals("/api/company/tag/display", tagUrl.encodedPath)
        assertEquals("100", tagUrl.parameters["companyId"])

        val failure = """{"success":false,"code":500,"msg":"error","data":null}"""
        repeat(2) { mock.enqueue(failure) }
        assertTrue(repository.getCompanySeries(20L).getOrThrow().isEmpty())
        assertTrue(repository.getCompanyTags(100L).getOrThrow().isEmpty())
    }

    @Test
    fun getRelatedHobbies_sendsCompanyTypeAndMapsHobbys() = runTest {
        mock.enqueue(body("""{"hobbys":[{"id":1,"nameCN":"手办A","itemType":"hobby"}]}"""))

        val result = repository.getRelatedHobbies(20L)

        assertEquals("手办A", result.getOrThrow().single().displayName)
        val request = mock.takeRequest()
        assertEquals("/api/item/relate/hobby", request.url.encodedPath)
        val form = parseForm(request.recordedBody())
        assertEquals("20", form["id"])
        assertEquals("company", form["itemType"])
        assertEquals("9", form["pageSize"])
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndThrowsOthers() = runTest {
        mock.enqueue(body("""{"list":[{"id":1,"content":"评论A"}]}"""))
        val comments = repository.getComments(20L, type = "hot_top", page = 2, pageSize = 5)
        assertEquals("评论A", comments.getOrThrow().single().content)
        val request = mock.takeRequest()
        assertEquals("/api/comment/get", request.url.encodedPath)
        val form = parseForm(request.recordedBody())
        assertEquals("20", form["id"])
        assertEquals("hot_top", form["type"])
        assertEquals("2", form["page"])
        assertEquals("5", form["pageSize"])

        mock.enqueue("""{"success":false,"code":-9007,"msg":"找不到该评论","data":null}""")
        assertTrue(repository.getComments(20L).getOrThrow().isEmpty())

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        assertEquals(500, (repository.getComments(20L).exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getCollectState_delegatesToCollectRepository() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"collect":{"state":"want"}}}""")
        assertTrue(repository.getCollectState(20L).getOrThrow())

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"collect":{"state":"delete"}}}""")
        assertFalse(repository.getCollectState(20L).getOrThrow())

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        assertTrue(repository.getCollectState(20L).isFailure)

        assertEquals(3, mock.requestCount)
    }

    @Test
    fun getMaskState_readsStateFieldAndSwallowsFailures() = runTest {
        mock.enqueue(body("""{"state":1}"""))
        assertTrue(repository.getMaskState(20L).getOrThrow())
        val url = mock.takeRequest().url
        assertEquals("/api/mask/state-v2", url.encodedPath)
        assertEquals("20", url.parameters["itemNodeId"])

        mock.enqueue(body("""{"state":0}"""))
        assertFalse(repository.getMaskState(20L).getOrThrow())

        mock.enqueue(body("null"))
        assertFalse(repository.getMaskState(20L).getOrThrow())

        mock.enqueue("""{"success":false,"code":401,"msg":"请先登录","data":null}""")
        assertEquals(false, repository.getMaskState(20L).getOrThrow())
    }
}
