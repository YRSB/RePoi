package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 厂商详情仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `VendorDetailRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；Mockito `mock(CollectRepository)` →
 * 真实 CollectRepository 背后独立 MockApi 队列；JUnit4 → kotlin.test）。
 */
class VendorDetailRepositoryTest {

    private fun setup(): Triple<MockApi, MockApi, VendorDetailRepository> {
        val mock = MockApi()
        val collectMock = MockApi()
        val collectRepository = CollectRepository(collectMock.api(), collectMock.json)
        return Triple(mock, collectMock, VendorDetailRepository(mock.api(), collectRepository))
    }

    @Test
    fun getCompanyDetail_mapsCompanyAndSendsCompanyId() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            mock.ok("""{"company":{"id":1,"itemId":100,"nameCN":"厂商A","website":"https://a.example"}}""")
        )

        val result = repository.getCompanyDetail(100L)

        assertTrue(result.isSuccess)
        assertEquals("厂商A", result.getOrThrow().nameCN)
        assertEquals("https://a.example", result.getOrThrow().website)
        val recorded = mock.takeRequest()
        assertEquals("/api/company/v2/get", recorded.encodedPath)
        assertEquals("100", recorded.queryParameter("companyId"))
    }

    @Test
    fun getCompanyDetail_throwsOnMissingCompanyAndFailure() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("{}"))
        val missing = repository.getCompanyDetail(100L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue(mock.failure(404, "厂商不存在"))
        val failure = repository.getCompanyDetail(100L)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("厂商不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getCompanyActions_forwardsFiltersAndDegradesToEmpty() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            mock.ok("""{"list":[{"id":1,"actionMessage":"开订时间","actionData":"preorder","actionType":1}]}""")
        )

        val result = repository.getCompanyActions(20L, subType = "news", category = 2, lastId = 33L)

        val action = result.getOrThrow().single()
        assertEquals("开订时间", action.actionTitle)
        val recorded = mock.takeRequest()
        assertEquals("/api/user-action/company/list", recorded.encodedPath)
        assertEquals("20", recorded.queryParameter("companyNodeId"))
        assertEquals("news", recorded.queryParameter("subType"))
        assertEquals("2", recorded.queryParameter("category"))
        assertEquals("33", recorded.queryParameter("lastId"))

        mock.enqueue(mock.failure(500, "error"))
        assertTrue(repository.getCompanyActions(20L).getOrThrow().isEmpty())
    }

    @Test
    fun getCompanyMyVotes_mapsTagsAndVotesAndFallsBackToDefaults() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            mock.ok("""{"allTags":[{"id":1,"name":"做工","allowScoring":1},{"id":2,"name":"性价比"}],"userVotes":[{"tagId":1,"score":4.5}]}""")
        )

        val votes = repository.getCompanyMyVotes(100L).getOrThrow()

        assertTrue(votes.allTags.first().isScoreTag)
        assertFalse(votes.allTags.last().isScoreTag)
        assertEquals(4.5, votes.userVotes.single().score)
        val recorded = mock.takeRequest()
        assertEquals("/api/company/tag/myVotes", recorded.encodedPath)
        assertEquals("100", recorded.queryParameter("companyId"))

        mock.enqueue(mock.ok("null"))
        val defaults = repository.getCompanyMyVotes(100L).getOrThrow()
        assertTrue(defaults.allTags.isEmpty())
        assertTrue(defaults.userVotes.isEmpty())

        mock.enqueue(mock.failure(401, "请先登录"))
        assertEquals(401, (repository.getCompanyMyVotes(100L).exceptionOrNull() as ApiException).code)
    }

    @Test
    fun submitCompanyTagVote_encodesNormalAndScoreTags() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"data":{}}""")

        val result = repository.submitCompanyTagVote(
            companyId = 100L,
            normalTagIds = listOf(1L, 2L),
            scoreVotes = mapOf(3L to 4.5, 4L to 3.0)
        )

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals("/api/company/tag/vote", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("100", form["companyId"])
        assertEquals("[1,2]", form["normalTagIds"])
        assertEquals("""[{"tagId":3,"score":4.5},{"tagId":4,"score":3.0}]""", form["scoreTagIds"])

        mock.enqueue(mock.failure(500, "投票失败"))
        val failure = repository.submitCompanyTagVote(100L, emptyList(), emptyMap())
        assertEquals(500, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("投票失败", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getCompanySeriesAndTags_mapListsAndDegradeToEmpty() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"seriesList":[{"id":1,"nameCN":"系列A","hobbyCount":8}]}"""))
        mock.enqueue(mock.ok("""{"list":[{"id":2,"name":"品牌A","tagText":"品牌A"}]}"""))

        val series = repository.getCompanySeries(20L, page = 2, pageSize = 5)
        val tags = repository.getCompanyTags(100L)

        assertEquals("系列A", series.getOrThrow().single().displayName)
        assertEquals("品牌A", tags.getOrThrow().single().displayTag)

        val seriesRecorded = mock.takeRequest()
        assertEquals("/api/company/series", seriesRecorded.encodedPath)
        assertEquals("20", seriesRecorded.queryParameter("companyNodeId"))
        assertEquals("2", seriesRecorded.queryParameter("page"))
        assertEquals("5", seriesRecorded.queryParameter("pageSize"))

        val tagRecorded = mock.takeRequest()
        assertEquals("/api/company/tag/display", tagRecorded.encodedPath)
        assertEquals("100", tagRecorded.queryParameter("companyId"))

        val failure = """{"success":false,"code":500,"msg":"error","data":null}"""
        repeat(2) { mock.enqueue(failure) }
        assertTrue(repository.getCompanySeries(20L).getOrThrow().isEmpty())
        assertTrue(repository.getCompanyTags(100L).getOrThrow().isEmpty())
    }

    @Test
    fun getRelatedHobbies_sendsCompanyTypeAndMapsHobbys() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"hobbys":[{"id":1,"nameCN":"手办A","itemType":"hobby"}]}"""))

        val result = repository.getRelatedHobbies(20L)

        assertEquals("手办A", result.getOrThrow().single().displayName)
        val recorded = mock.takeRequest()
        assertEquals("/api/item/relate/hobby", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("20", form["id"])
        assertEquals("company", form["itemType"])
        assertEquals("9", form["pageSize"])
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndThrowsOthers() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[{"id":1,"content":"评论A"}]}"""))
        val comments = repository.getComments(20L, type = "hot_top", page = 2, pageSize = 5)
        assertEquals("评论A", comments.getOrThrow().single().content)
        val recorded = mock.takeRequest()
        assertEquals("/api/comment/get", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("20", form["id"])
        assertEquals("hot_top", form["type"])
        assertEquals("2", form["page"])
        assertEquals("5", form["pageSize"])

        mock.enqueue(mock.failure(-9007, "找不到该评论"))
        assertTrue(repository.getComments(20L).getOrThrow().isEmpty())

        mock.enqueue(mock.failure(500, "error"))
        assertEquals(500, (repository.getComments(20L).exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getCollectState_delegatesToCollectRepository() = runTest {
        val (_, collectMock, repository) = setup()
        collectMock.enqueue(collectMock.ok("""{"collect":{"state":"want"}}"""))
        assertTrue(repository.getCollectState(20L).getOrThrow())

        collectMock.enqueue(collectMock.ok("""{"collect":{"state":"delete"}}"""))
        assertFalse(repository.getCollectState(20L).getOrThrow())

        collectMock.enqueue(collectMock.failure(500, "error"))
        assertTrue(repository.getCollectState(20L).isFailure)

        assertEquals(3, collectMock.requestCount)
    }

    @Test
    fun getMaskState_readsStateFieldAndSwallowsFailures() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"state":1}"""))
        assertTrue(repository.getMaskState(20L).getOrThrow())
        val recorded = mock.takeRequest()
        assertEquals("/api/mask/state-v2", recorded.encodedPath)
        assertEquals("20", recorded.queryParameter("itemNodeId"))

        mock.enqueue(mock.ok("""{"state":0}"""))
        assertFalse(repository.getMaskState(20L).getOrThrow())

        mock.enqueue(mock.ok("null"))
        assertFalse(repository.getMaskState(20L).getOrThrow())

        mock.enqueue(mock.failure(401, "请先登录"))
        assertEquals(false, repository.getMaskState(20L).getOrThrow())
    }
}
