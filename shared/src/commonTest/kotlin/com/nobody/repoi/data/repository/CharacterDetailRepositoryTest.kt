package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 角色详情仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `CharacterDetailRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test + `runTest`）。
 */
class CharacterDetailRepositoryTest {

    private fun setup(): Pair<MockApi, CharacterDetailRepository> {
        val mock = MockApi()
        return mock to CharacterDetailRepository(mock.api())
    }

    @Test
    fun getCharacterDetail_mapsCharactarAndCopiesFavState() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            mock.ok("""{"charactar":{"id":7,"itemId":70,"nameCN":"角色A","cover":"c.jpg"},"isAddFav":true}""")
        )

        val result = repository.getCharacterDetail(7L)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("角色A", data.nameCN)
        assertEquals(7L, data.actualNodeId)
        assertTrue(data.isAddFav)
        val recorded = mock.takeRequest()
        assertEquals("/api/charactar/get", recorded.encodedPath)
        assertEquals("7", recorded.queryParameter("charactarId"))
    }

    @Test
    fun getCharacterDetail_acceptsCharacterAliasAndThrowsOnNullData() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"character":{"id":8,"nameCN":"别名角色"}}"""))
        assertEquals("别名角色", repository.getCharacterDetail(8L).getOrThrow().nameCN)

        mock.enqueue(mock.ok("null"))
        val missing = repository.getCharacterDetail(8L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue(mock.failure(404, "角色不存在"))
        val failure = repository.getCharacterDetail(8L)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("角色不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getCharacterWorks_forwardsPagingAndMapsWorksList() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            mock.ok("""{"worksList":[{"worksId":9,"wNameCN":"作品A","wNames":"作品A原名","cNameCN":"主角"}]}""")
        )

        val result = repository.getCharacterWorks(7L, page = 2, pageSize = 5)

        assertTrue(result.isSuccess)
        val work = result.getOrThrow().single()
        assertEquals("作品A", work.displayName)
        assertEquals("主角", work.cNameCN)
        val recorded = mock.takeRequest()
        assertEquals("/api/charactar/works/get", recorded.encodedPath)
        assertEquals("7", recorded.queryParameter("charactarId"))
        assertEquals("2", recorded.queryParameter("page"))
        assertEquals("5", recorded.queryParameter("pageSize"))
    }

    @Test
    fun getCharacterHobbys_sendsFiveLegacyFormFields() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[{"id":14,"nameCN":"周边A"}]}"""))

        val result = repository.getCharacterHobbys(77L)

        assertEquals("周边A", result.getOrThrow().single().nameCN)
        val recorded = mock.takeRequest()
        assertEquals("/api/hobby/query-v2", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("77", form["charactar"])
        assertEquals("77", form["character"])
        assertEquals("1", form["page"])
        assertEquals("9", form["pageSize"])
        assertEquals("hits7Day", form["order"])
    }

    @Test
    fun getCharacterAlbumsRelevantAndZones_mapPayloadsAndParams() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"albumList":[{"id":11,"nameCN":"相册A"}]}"""))
        mock.enqueue(mock.ok("""{"charactarList":[{"id":12,"nameCN":"相关角色"}]}"""))
        mock.enqueue(mock.ok("""{"zones":[{"nodeId":13,"nameCN":"分区A"}]}"""))

        val albums = repository.getCharacterAlbums(7L, page = 3, pageSize = 4)
        val relevant = repository.getCharacterRelevant(7L)
        val zones = repository.getItemZones(99L)

        assertEquals("相册A", albums.getOrThrow().single().nameCN)
        assertEquals("相关角色", relevant.getOrThrow().single().nameCN)
        assertEquals("分区A", zones.getOrThrow().single().displayName)

        val albumRecorded = mock.takeRequest()
        assertEquals("/api/charactar/album/get", albumRecorded.encodedPath)
        assertEquals("7", albumRecorded.queryParameter("charactarId"))
        assertEquals("3", albumRecorded.queryParameter("page"))
        assertEquals("4", albumRecorded.queryParameter("pageSize"))

        val relevantRecorded = mock.takeRequest()
        assertEquals("/api/charactar/relevant/get", relevantRecorded.encodedPath)
        assertEquals("7", relevantRecorded.queryParameter("charactarId"))
        assertEquals("10", relevantRecorded.queryParameter("pageSize"))

        val zoneRecorded = mock.takeRequest()
        assertEquals("/api/zone/item", zoneRecorded.encodedPath)
        assertEquals("99", zoneRecorded.queryParameter("nodeId"))
    }

    @Test
    fun getCharacterLists_propagateBusinessFailure() = runTest {
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"data":{}}""")
        assertTrue(repository.toggleCollect(7L, isCollected = false).getOrThrow())
        val addRecorded = mock.takeRequest()
        assertEquals("/api/collect/item/add", addRecorded.encodedPath)
        assertEquals("7", addRecorded.formEntries()["id"])

        mock.enqueue("""{"success":true,"code":200,"data":{}}""")
        assertFalse(repository.toggleCollect(7L, isCollected = true).getOrThrow())
        val delRecorded = mock.takeRequest()
        assertEquals("/api/collect/del", delRecorded.encodedPath)
        assertEquals("7", delRecorded.formEntries()["id"])

        mock.enqueue(mock.failure(401, "请先登录"))
        val failure = repository.toggleCollect(7L, isCollected = false)
        assertTrue(failure.isFailure)
        assertEquals(401, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("请先登录", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndChecksParams() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[{"id":1,"content":"评论A"}]}"""))
        val comments = repository.getComments(7L, type = "hot_top", page = 2, pageSize = 5)
        assertEquals("评论A", comments.getOrThrow().single().content)
        val recorded = mock.takeRequest()
        assertEquals("/api/comment/get", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("7", form["id"])
        assertEquals("hot_top", form["type"])
        assertEquals("2", form["page"])
        assertEquals("5", form["pageSize"])

        mock.enqueue(mock.failure(-9007, "找不到该评论"))
        val empty = repository.getComments(7L)
        assertTrue(empty.isSuccess)
        assertTrue(empty.getOrThrow().isEmpty())
        assertFalse(mock.takeRequest().formEntries().containsKey("type"))

        mock.enqueue(mock.failure(500, "error"))
        val failure = repository.getComments(7L)
        assertEquals(500, (failure.exceptionOrNull() as ApiException).code)
    }
}
