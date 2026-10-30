package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 系列详情仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `SeriesDetailRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；Mockito `mock(CollectRepository)` →
 * 真实 CollectRepository 背后独立 MockApi 队列；JUnit4 → kotlin.test）。
 */
class SeriesDetailRepositoryTest {

    private fun setup(): Triple<MockApi, MockApi, SeriesDetailRepository> {
        val mock = MockApi()
        val collectMock = MockApi()
        val collectRepository = CollectRepository(collectMock.api(), collectMock.json)
        return Triple(mock, collectMock, SeriesDetailRepository(mock.api(), collectRepository))
    }

    @Test
    fun getSeriesDetail_sendsQueryAndMapsItemData() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            mock.ok("""{"itemData":{"id":1,"nodeId":2,"nameCN":"系列A","commentCount":8}}""")
        )

        val result = repository.getSeriesDetail(1L)

        assertTrue(result.isSuccess)
        assertEquals("系列A", result.getOrThrow().nameCN)
        assertEquals(8, result.getOrThrow().commentCount)
        val path = mock.takeRequest().path.orEmpty()
        assertTrue(path.startsWith("/api/item/get-v2"))
        assertTrue(path.contains("itemId=1"))
        assertTrue(path.contains("itemType=series"))
    }

    @Test
    fun getSeriesDetail_missingItemDataThrowsAndApiFailureKeepsCode() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("{}"))
        val missing = repository.getSeriesDetail(1L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue(mock.failure(404, "系列不存在"))
        val failure = repository.getSeriesDetail(1L)
        assertTrue(failure.isFailure)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("系列不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getSeriesHobbys_sendsNodeIdAndDegradesToEmpty() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"hobbys":[{"id":10,"nameCN":"关联手办"}]}"""))

        val success = repository.getSeriesHobbys(2L)
        assertTrue(success.isSuccess)
        assertEquals("关联手办", success.getOrThrow().single().nameCN)
        val form = mock.takeRequest().formEntries()
        assertEquals("2", form["id"])
        assertEquals("series", form["itemType"])
        assertEquals("9", form["pageSize"])

        mock.enqueue(mock.failure(500, "error"))
        val degraded = repository.getSeriesHobbys(2L)
        assertTrue(degraded.isSuccess)
        assertTrue(degraded.getOrThrow().isEmpty())
    }

    @Test
    fun getSeriesSubAndRelateSeries_useIsChildFlag() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[{"id":20,"nameCN":"子系列"}]}"""))
        mock.enqueue(mock.ok("""{"list":[{"id":30,"nameCN":"相关系列"}]}"""))

        val sub = repository.getSeriesSubSeries(1L)
        val relate = repository.getSeriesRelateSeries(1L)

        assertEquals("子系列", sub.getOrThrow().single().nameCN)
        assertEquals("相关系列", relate.getOrThrow().single().nameCN)
        val subPath = mock.takeRequest().path.orEmpty()
        val relatePath = mock.takeRequest().path.orEmpty()
        assertTrue(subPath.contains("seriesId=1"))
        assertTrue(subPath.contains("isChild=1"))
        assertTrue(subPath.contains("page=1"))
        assertTrue(subPath.contains("pageSize=6"))
        assertTrue(relatePath.contains("isChild=2"))
    }

    @Test
    fun getSeriesAlbumsAndGoods_mapListsAndRequestParams() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"album":[{"id":40,"name":"相册A"}]}"""))
        mock.enqueue(mock.ok("""{"list":[{"id":50,"name":"商品A"}]}"""))

        val albums = repository.getSeriesAlbums(1L)
        val goods = repository.getSeriesGoods(2L)

        assertEquals("相册A", albums.getOrThrow().single().name)
        assertEquals("商品A", goods.getOrThrow().single().name)
        val albumPath = mock.takeRequest().path.orEmpty()
        val goodsPath = mock.takeRequest().path.orEmpty()
        assertTrue(albumPath.contains("/api/series/album"))
        assertTrue(albumPath.contains("seriesId=1"))
        assertTrue(albumPath.contains("pageSize=6"))
        assertTrue(goodsPath.contains("/api/goods/tb/more-v2"))
        assertTrue(goodsPath.contains("nodeId=2"))
        assertTrue(goodsPath.contains("pageSize=10"))
    }

    @Test
    fun getSeriesForum_mapsPayloadAndFailureReturnsNull() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"forum":{"id":1,"name":"讨论区","postCount":3}}"""))
        val success = repository.getSeriesForum(1L)
        assertTrue(success.isSuccess)
        assertEquals("讨论区", success.getOrThrow()?.forum?.name)
        assertTrue(mock.takeRequest().path.orEmpty().contains("seriesId=1"))

        mock.enqueue(mock.failure(500, "error"))
        val failure = repository.getSeriesForum(1L)
        assertTrue(failure.isSuccess)
        assertNull(failure.getOrThrow())
    }

    @Test
    fun getMaskState_readsStateFlagAndFallsBackToFalse() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(mock.ok("""{"state":1}"""))
        mock.enqueue(mock.ok("""{"state":0}"""))

        assertTrue(repository.getMaskState(2L).getOrThrow())
        assertFalse(repository.getMaskState(2L).getOrThrow())
        val path = mock.takeRequest().path.orEmpty()
        assertTrue(path.contains("/api/mask/state-v2"))
        assertTrue(path.contains("itemNodeId=2"))

        mock.enqueue(mock.failure(500, "error"))
        val failure = repository.getMaskState(2L)
        assertTrue(failure.isSuccess)
        assertFalse(failure.getOrThrow())
    }

    @Test
    fun getCollectState_delegatesAndMapsCollectedFlag() = runTest {
        val (_, collectMock, repository) = setup()
        collectMock.enqueue(collectMock.ok("""{"collect":{"state":"buy"}}"""))
        collectMock.enqueue(collectMock.ok("""{}"""))

        assertTrue(repository.getCollectState(2L).getOrThrow())
        assertFalse(repository.getCollectState(2L).getOrThrow())
    }

    @Test
    fun getComments_mapsListTreatsNotFoundAsEmptyAndThrowsOtherErrors() = runTest {
        val (mock, _, repository) = setup()
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
}
