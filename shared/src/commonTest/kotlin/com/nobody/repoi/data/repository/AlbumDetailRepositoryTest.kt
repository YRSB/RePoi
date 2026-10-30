package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 相册详情仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `AlbumDetailRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test）。
 */
class AlbumDetailRepositoryTest {

    private fun setup(): Pair<MockApi, AlbumDetailRepository> {
        val mock = MockApi()
        return mock to AlbumDetailRepository(mock.api())
    }

    @Test
    fun getAlbumDetail_sendsIdsAndMapsAlbum() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"album":{"id":1,"nameCN":"相册A","cover":"c.jpg"}}"""))

        val result = repository.getAlbumDetail(albumId = 1L, itemId = 5L)

        assertTrue(result.isSuccess)
        assertEquals("相册A", result.getOrThrow().nameCN)
        val form = mock.takeRequest().formEntries()
        assertEquals("1", form["id"])
        assertEquals("5", form["itemId"])
    }

    @Test
    fun getAlbumDetail_missingAlbumThrowsAndFailureKeepsCode() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("{}"))
        val missing = repository.getAlbumDetail(1L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue(mock.failure(404, "相册不存在"))
        val failure = repository.getAlbumDetail(1L)
        assertTrue(failure.isFailure)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("相册不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getAlbumTags_mapsNonBlankTagsAndDegradesToEmpty() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            mock.ok("""{"tags":[{"count":2,"tag":{"tagValue":"标签A"}},{"count":0,"tag":{"tagValue":""}}]}""")
        )

        val success = repository.getAlbumTags(1L)
        assertTrue(success.isSuccess)
        assertEquals(listOf("标签A"), success.getOrThrow())
        assertEquals("1", mock.takeRequest().formEntries()["id"])

        mock.enqueue(mock.failure(500, "error"))
        val degraded = repository.getAlbumTags(1L)
        assertTrue(degraded.isSuccess)
        assertTrue(degraded.getOrThrow().isEmpty())
    }

    @Test
    fun getAlbumPictures_forwardsPagingAndOrderParams() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[{"id":1,"path":"pics/a.jpg"}]}"""))

        val result = repository.getAlbumPictures(
            albumId = 1L,
            itemId = 9L,
            page = 2,
            pageSize = 20,
            order = "top"
        )

        assertTrue(result.isSuccess)
        assertEquals("pics/a.jpg", result.getOrThrow().single().path)
        val form = mock.takeRequest().formEntries()
        assertEquals("9", form["itemId"])
        assertEquals("album", form["itemType"])
        assertEquals("1", form["nodeId"])
        assertEquals("2", form["page"])
        assertEquals("20", form["pageSize"])
        assertEquals("top", form["order"])
    }

    @Test
    fun getRelatedHobbiesAndAlbums_mapListsAndParams() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"hobbys":[{"id":10,"nameCN":"关联手办"}]}"""))
        mock.enqueue(mock.ok("""{"list":[{"id":20,"nameCN":"相关相册"}]}"""))

        val hobbies = repository.getRelatedHobbies(1L)
        val albums = repository.getRelatedAlbums(1L)

        assertEquals("关联手办", hobbies.getOrThrow().single().nameCN)
        assertEquals("相关相册", albums.getOrThrow().single().nameCN)
        val hobbyForm = mock.takeRequest().formEntries()
        val albumForm = mock.takeRequest().formEntries()
        assertEquals("1", hobbyForm["id"])
        assertEquals("album", hobbyForm["itemType"])
        assertEquals("8", hobbyForm["pageSize"])
        assertEquals("1", albumForm["nodeId"])
        assertEquals("1", albumForm["page"])
        assertEquals("8", albumForm["pageSize"])
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndThrowsOtherErrors() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[{"id":1,"content":"评论"}]}"""))
        assertEquals("评论", repository.getComments(2L).getOrThrow().single().content)
        val form = mock.takeRequest().formEntries()
        assertEquals("2", form["id"])
        assertEquals("1", form["page"])
        assertEquals("10", form["pageSize"])

        mock.enqueue(mock.failure(-9007, "找不到该评论"))
        val empty = repository.getComments(2L)
        assertTrue(empty.isSuccess)
        assertTrue(empty.getOrThrow().isEmpty())

        mock.enqueue(mock.failure(500, "error"))
        val failure = repository.getComments(2L)
        assertTrue(failure.isFailure)
        assertEquals(500, (failure.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun likeInfoAndPraiseEndpoints_sendAlbumTypeAndMapState() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"praiseState":true}"""))

        assertTrue(repository.getAlbumLikeInfo(1L).getOrThrow())
        val infoForm = mock.takeRequest().formEntries()
        assertEquals("1", infoForm["nodeId"])
        assertEquals("album", infoForm["itemType"])

        mock.enqueue("""{"success":true,"code":200,"data":{}}""")
        assertTrue(repository.praiseAlbumAdd(1L).isSuccess)
        val addForm = mock.takeRequest().formEntries()
        assertEquals("1", addForm["id"])
        assertEquals("album", addForm["itemType"])

        mock.enqueue("""{"success":true,"code":200,"data":{}}""")
        assertTrue(repository.praiseAlbumDel(1L).isSuccess)
        val delForm = mock.takeRequest().formEntries()
        assertEquals("1", delForm["id"])
        assertEquals("album", delForm["itemType"])

        mock.enqueue(mock.failure(500, "点赞失败"))
        val failure = repository.praiseAlbumAdd(1L)
        assertTrue(failure.isFailure)
        assertFalse(failure.isSuccess)
        assertEquals("点赞失败", failure.exceptionOrNull()?.message)
    }
}
