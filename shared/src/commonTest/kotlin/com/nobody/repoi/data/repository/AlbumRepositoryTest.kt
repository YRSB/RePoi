package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.AlbumOrder
import com.nobody.repoi.data.remote.model.AlbumZoneTab
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 相册仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `AlbumRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test）。
 */
class AlbumRepositoryTest {

    private fun setup(): Pair<MockApi, AlbumRepository> {
        val mock = MockApi()
        return mock to AlbumRepository(mock.api())
    }

    private fun albumItem(id: Long): String =
        """{"id":$id,"itemId":${1000 + id},"nameCN":"相册$id","cover":"c.jpg","praiseCount":68,"user":{"userId":9,"nickname":"胶友"}}"""

    @Test
    fun getAlbums_successMapsItemsAndOrder() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[${albumItem(1)},${albumItem(2)}],"page":1}"""))
        val result = repository.getAlbums(order = AlbumOrder.Recommend)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(2, page.items.size)
        assertEquals(1L, page.items[0].targetAlbumId)
        assertEquals("相册1", page.items[0].displayName)
        assertFalse(page.hasMore)
        val body = mock.takeRequest().formBodyText()
        assertTrue(body.contains("order=top"))
    }

    @Test
    fun getAlbums_customOrderStringOverridesEnum() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[${albumItem(1)}],"page":1}"""))
        val result = repository.getAlbums(orderString = "hitsDay", zones = "33476094")
        assertTrue(result.isSuccess)
        val body = mock.takeRequest().formBodyText()
        assertTrue(body.contains("order=hitsDay"))
        assertTrue(body.contains("zones=33476094"))
    }

    @Test
    fun getAlbums_fullPageHasMoreTrue() = runTest {
        val (mock, repository) = setup()
        val items = (1..24).joinToString(",") { albumItem(it.toLong()) }
        mock.enqueue(mock.ok("""{"list":[$items],"page":1}"""))
        val result = repository.getAlbums(order = AlbumOrder.Latest)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().hasMore)
        val body = mock.takeRequest().formBodyText()
        assertTrue(body.contains("order=updTime"))
    }

    @Test
    fun getAlbums_failureThrowsWithCode() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录过期"))
        val result = repository.getAlbums()
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getAlbums_tolerantFields() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","unknownKey":1,"data":{"list":[{"id":0,"itemId":0,"name":"纯名","cover":"none.jpg","praiseCount":"68"},{"id":5,"itemId":1005,"nameCN":"回退名","cover":"","praiseCount":null}],"page":1}}""")
        val result = repository.getAlbums()
        assertTrue(result.isSuccess)
        val items = result.getOrThrow().items
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("回退名", item.displayName)
        assertEquals(0L, item.praiseCountValue)
        assertFalse(item.hasCover)
    }

    @Test
    fun getAlbums_stringPraiseCountParses() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[{"id":3,"itemId":1003,"name":"串赞","cover":"c.jpg","praiseCount":"191"}],"page":1}"""))
        val result = repository.getAlbums()
        assertTrue(result.isSuccess)
        assertEquals(191L, result.getOrThrow().items.single().praiseCountValue)
    }

    @Test
    fun getZoneTabs_successBuildsAllFollowAndZones() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"settings":{"showAll":true},"focusZones":[{"nodeId":7}],"homeZones":[{"nodeId":12,"nameCN":"手办"},{"nodeId":0,"nameCN":"无效"}]}"""))
        val result = repository.getZoneTabs()
        assertTrue(result.isSuccess)
        val tabs = result.getOrThrow()
        assertEquals(
            listOf(AlbumZoneTab.ALL, AlbumZoneTab("follow", "关注", "7"), AlbumZoneTab("z12", "手办", "12")),
            tabs
        )
    }

    @Test
    fun getZoneTabs_failureFallsBackToAll() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "error"))
        val result = repository.getZoneTabs()
        assertTrue(result.isSuccess)
        assertEquals(listOf(AlbumZoneTab.ALL), result.getOrThrow())
    }

    @Test
    fun getRankingWindow_successForwardsWindow() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[${albumItem(4)}],"page":1}"""))
        val result = repository.getRankingWindow("2026-09-01 00:00:00", "2026-09-11 13:00:00")
        assertTrue(result.isSuccess)
        assertEquals(4L, result.getOrThrow().single().targetAlbumId)
        val body = mock.takeRequest().formBodyText()
        assertTrue(body.contains("order=praise"))
        assertTrue(body.contains("releaseStart=2026-09-01+00%3A00%3A00") || body.contains("releaseStart=2026-09-01"))
        assertTrue(body.contains("releaseEnd=2026-09-11"))
    }

    @Test
    fun getPraiseTop_successMapsListAndType() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"list":[${albumItem(7)}]}"""))
        val result = repository.getPraiseTop()
        assertTrue(result.isSuccess)
        assertEquals(7L, result.getOrThrow().items.first().targetAlbumId)
        val recorded = mock.takeRequest()
        assertTrue(recorded.queryParameter("type") == "month" || recorded.formBodyText().contains("type=month"))
    }

    @Test
    fun getPraiseTop_failureThrows() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "error"))
        val result = repository.getPraiseTop()
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(500, error.code)
        assertEquals("error", error.message)
    }
}
