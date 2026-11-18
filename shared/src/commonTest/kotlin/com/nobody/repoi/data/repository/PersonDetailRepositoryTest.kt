package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 人物详情仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `PersonDetailRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；Mockito `mock(CollectRepository)` →
 * 真实 CollectRepository 背后独立 MockApi 队列（`verify(times(4))` →
 * `collectMock.requestCount == 4`）；JUnit4 → kotlin.test）。
 */
class PersonDetailRepositoryTest {

    private fun setup(): Triple<MockApi, MockApi, PersonDetailRepository> {
        val mock = MockApi()
        val collectMock = MockApi()
        val collectRepository = CollectRepository(collectMock.api(), collectMock.json)
        return Triple(mock, collectMock, PersonDetailRepository(mock.api(), collectRepository))
    }

    @Test
    fun getPersonDetail_mapsResultAndSendsPersonId() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            mock.ok("""{"result":{"careerType":2,"person":{"id":3966,"itemId":1589,"nameCN":"人物A","cover":"p.jpg"}}}""")
        )

        val result = repository.getPersonDetail(1589L)

        assertTrue(result.isSuccess)
        val detail = result.getOrThrow()
        assertEquals("人物A", detail.person.displayName)
        assertEquals(3966L, detail.person.id)
        val recorded = mock.takeRequest()
        assertEquals("/api/person/get", recorded.encodedPath)
        assertEquals("1589", recorded.queryParameter("personId"))
    }

    @Test
    fun getPersonDetail_rejectsNullPayloadAndBusinessFailure() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("null"))
        val missing = repository.getPersonDetail(1589L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue(mock.failure(404, "人物不存在"))
        val failure = repository.getPersonDetail(1589L)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("人物不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getPersonWorksAndCharacters_mapNestedWrappersAndParams() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"worksList":[{"w":{"id":1,"itemId":10,"nameCN":"作品A"}}]}"""))
        mock.enqueue(mock.ok("""{"characterList":[{"w":{"id":2,"itemId":20,"name":"角色原名"}}]}"""))

        val works = repository.getPersonWorks(1589L)
        val characters = repository.getPersonCharacter(1589L)

        assertEquals("作品A", works.getOrThrow().single().w.displayName)
        assertEquals("角色原名", characters.getOrThrow().single().w.displayName)

        val worksRecorded = mock.takeRequest()
        assertEquals("/api/person/works/get", worksRecorded.encodedPath)
        assertEquals("1589", worksRecorded.queryParameter("personId"))
        assertEquals("1", worksRecorded.queryParameter("page"))
        assertEquals("20", worksRecorded.queryParameter("pageSize"))

        val characterRecorded = mock.takeRequest()
        assertEquals("/api/person/character/get", characterRecorded.encodedPath)
        assertEquals("1589", characterRecorded.queryParameter("personId"))
    }

    @Test
    fun getPersonHobby_mapsJobNamesAndHobbyItem() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            mock.ok("""{"hobby":[{"jobName":["原画","作画監督"],"h":{"id":3,"nameCN":"周边A"}}]}""")
        )

        val result = repository.getPersonHobby(1589L)

        val entry = result.getOrThrow().single()
        assertEquals(listOf("原画", "作画監督"), entry.jobName)
        assertEquals("周边A", entry.h.displayName)
        val recorded = mock.takeRequest()
        assertEquals("/api/person/hobby/get", recorded.encodedPath)
        assertEquals("1589", recorded.queryParameter("personId"))
    }

    @Test
    fun personLists_degradeToEmptyOnBusinessFailure() = runTest {
        val (mock, _, repository) = setup()
        val failure = """{"success":false,"code":500,"msg":"error","data":null}"""
        repeat(3) { mock.enqueue(failure) }

        assertTrue(repository.getPersonWorks(1589L).getOrThrow().isEmpty())
        assertTrue(repository.getPersonCharacter(1589L).getOrThrow().isEmpty())
        assertTrue(repository.getPersonHobby(1589L).getOrThrow().isEmpty())
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndChecksParams() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[{"id":1,"content":"评论A"}]}"""))
        val comments = repository.getComments(3966L, type = "hot_top", page = 2, pageSize = 5)
        assertEquals("评论A", comments.getOrThrow().single().content)
        val recorded = mock.takeRequest()
        assertEquals("/api/comment/get", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("3966", form["id"])
        assertEquals("hot_top", form["type"])
        assertEquals("2", form["page"])
        assertEquals("5", form["pageSize"])

        mock.enqueue(mock.failure(-9007, "找不到该评论"))
        assertTrue(repository.getComments(3966L).getOrThrow().isEmpty())

        mock.enqueue(mock.failure(500, "error"))
        assertEquals(500, (repository.getComments(3966L).exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getCollectState_delegatesAndMapsCollectData() = runTest {
        val (_, collectMock, repository) = setup()
        // care → 已收藏；delete → 未收藏；无 collect → 未收藏；401 → 透传
        collectMock.enqueue(collectMock.ok("""{"collect":{"state":"care"}}"""))
        collectMock.enqueue(collectMock.ok("""{"collect":{"state":"delete"}}"""))
        collectMock.enqueue(collectMock.ok("""{}"""))
        collectMock.enqueue(collectMock.failure(401, "请先登录"))

        assertTrue(repository.getCollectState(3966L).getOrThrow())
        assertFalse(repository.getCollectState(3966L).getOrThrow())
        assertFalse(repository.getCollectState(3966L).getOrThrow())
        val failure = repository.getCollectState(3966L)
        assertEquals(401, (failure.exceptionOrNull() as ApiException).code)

        assertEquals(4, collectMock.requestCount)
    }
}
