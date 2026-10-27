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

class SeriesDetailRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var collectRepository: CollectRepository
    private lateinit var repository: SeriesDetailRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        collectRepository = CollectRepository(api, json)
        repository = SeriesDetailRepository(
            api,
            collectRepository
        )
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
    fun getSeriesDetail_sendsQueryAndMapsItemData() = runTest {
        mock.enqueue(
            body("""{"itemData":{"id":1,"nodeId":2,"nameCN":"系列A","commentCount":8}}""")
        )

        val result = repository.getSeriesDetail(1L)

        assertTrue(result.isSuccess)
        assertEquals("系列A", result.getOrThrow().nameCN)
        assertEquals(8, result.getOrThrow().commentCount)
        val path = mock.takeRequest().recordedPath.orEmpty()
        assertTrue(path.startsWith("/api/item/get-v2"))
        assertTrue(path.contains("itemId=1"))
        assertTrue(path.contains("itemType=series"))
    }

    @Test
    fun getSeriesDetail_missingItemDataThrowsAndApiFailureKeepsCode() = runTest {
        mock.enqueue(body("{}"))
        val missing = repository.getSeriesDetail(1L)
        assertTrue(missing.isFailure)
        assertTrue(missing.exceptionOrNull() is ApiException)

        mock.enqueue("""{"success":false,"code":404,"msg":"系列不存在","data":null}""")
        val failure = repository.getSeriesDetail(1L)
        assertTrue(failure.isFailure)
        assertEquals(404, (failure.exceptionOrNull() as ApiException).code)
        assertEquals("系列不存在", failure.exceptionOrNull()?.message)
    }

    @Test
    fun getSeriesHobbys_sendsNodeIdAndDegradesToEmpty() = runTest {
        mock.enqueue(body("""{"hobbys":[{"id":10,"nameCN":"关联手办"}]}"""))

        val success = repository.getSeriesHobbys(2L)
        assertTrue(success.isSuccess)
        assertEquals("关联手办", success.getOrThrow().single().nameCN)
        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("2", form["id"])
        assertEquals("series", form["itemType"])
        assertEquals("9", form["pageSize"])

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val degraded = repository.getSeriesHobbys(2L)
        assertTrue(degraded.isSuccess)
        assertTrue(degraded.getOrThrow().isEmpty())
    }

    @Test
    fun getSeriesSubAndRelateSeries_useIsChildFlag() = runTest {
        mock.enqueue(body("""{"list":[{"id":20,"nameCN":"子系列"}]}"""))
        mock.enqueue(body("""{"list":[{"id":30,"nameCN":"相关系列"}]}"""))

        val sub = repository.getSeriesSubSeries(1L)
        val relate = repository.getSeriesRelateSeries(1L)

        assertEquals("子系列", sub.getOrThrow().single().nameCN)
        assertEquals("相关系列", relate.getOrThrow().single().nameCN)
        val subPath = mock.takeRequest().recordedPath.orEmpty()
        val relatePath = mock.takeRequest().recordedPath.orEmpty()
        assertTrue(subPath.contains("seriesId=1"))
        assertTrue(subPath.contains("isChild=1"))
        assertTrue(subPath.contains("page=1"))
        assertTrue(subPath.contains("pageSize=6"))
        assertTrue(relatePath.contains("isChild=2"))
    }

    @Test
    fun getSeriesAlbumsAndGoods_mapListsAndRequestParams() = runTest {
        mock.enqueue(body("""{"album":[{"id":40,"name":"相册A"}]}"""))
        mock.enqueue(body("""{"list":[{"id":50,"name":"商品A"}]}"""))

        val albums = repository.getSeriesAlbums(1L)
        val goods = repository.getSeriesGoods(2L)

        assertEquals("相册A", albums.getOrThrow().single().name)
        assertEquals("商品A", goods.getOrThrow().single().name)
        val albumPath = mock.takeRequest().recordedPath.orEmpty()
        val goodsPath = mock.takeRequest().recordedPath.orEmpty()
        assertTrue(albumPath.contains("/api/series/album"))
        assertTrue(albumPath.contains("seriesId=1"))
        assertTrue(albumPath.contains("pageSize=6"))
        assertTrue(goodsPath.contains("/api/goods/tb/more-v2"))
        assertTrue(goodsPath.contains("nodeId=2"))
        assertTrue(goodsPath.contains("pageSize=10"))
    }

    @Test
    fun getSeriesForum_mapsPayloadAndFailureReturnsNull() = runTest {
        mock.enqueue(body("""{"forum":{"id":1,"name":"讨论区","postCount":3}}"""))
        val success = repository.getSeriesForum(1L)
        assertTrue(success.isSuccess)
        assertEquals("讨论区", success.getOrThrow()?.forum?.name)
        assertTrue(mock.takeRequest().recordedPath.orEmpty().contains("seriesId=1"))

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val failure = repository.getSeriesForum(1L)
        assertTrue(failure.isSuccess)
        assertNull(failure.getOrThrow())
    }

    @Test
    fun getMaskState_readsStateFlagAndFallsBackToFalse() = runTest {
        mock.enqueue(body("""{"state":1}"""))
        mock.enqueue(body("""{"state":0}"""))

        assertTrue(repository.getMaskState(2L).getOrThrow())
        assertFalse(repository.getMaskState(2L).getOrThrow())
        val path = mock.takeRequest().recordedPath.orEmpty()
        assertTrue(path.contains("/api/mask/state-v2"))
        assertTrue(path.contains("itemNodeId=2"))

        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val failure = repository.getMaskState(2L)
        assertTrue(failure.isSuccess)
        assertFalse(failure.getOrThrow())
    }

    @Test
    fun getCollectState_delegatesAndMapsCollectedFlag() = runTest {
        mock.enqueue("{"success":true,"code":200,"msg":"ok","data":{"collect":{"state":"buy"}}}")
        mock.enqueue("{"success":true,"code":200,"msg":"ok","data":{"collect":null}}")

        assertTrue(repository.getCollectState(2L).getOrThrow())
        assertFalse(repository.getCollectState(2L).getOrThrow())
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
}
