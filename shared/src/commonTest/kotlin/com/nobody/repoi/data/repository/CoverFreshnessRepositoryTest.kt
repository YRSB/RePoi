package com.nobody.repoi.data.repository

import com.nobody.repoi.platform.nowMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 封面新鲜度修复仓储测试。
 *
 * 覆盖四条关键契约：缓存命中不请求、失败可重试、空封面进负缓存、详情页沉淀优先于接口。
 */
class CoverFreshnessRepositoryTest {

    private val hobbyCover = "2026/11/new_hobby.jpg"
    private val albumCover = "2026/11/new_album.jpg"

    private class Env {
        val hobbyMock = MockApi()
        val albumMock = MockApi()
        val repository = CoverFreshnessRepository(
            HobbyDetailRepository(hobbyMock.api()),
            AlbumDetailRepository(albumMock.api()),
        )

        fun stubHobbyCover(cover: String?) {
            val coverJson = cover?.let { "\"$it\"" } ?: "null"
            hobbyMock.route(
                "api/item/get",
                hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":$coverJson}}""")
            )
        }

        fun stubAlbumCover(cover: String?) {
            val coverJson = cover?.let { "\"$it\"" } ?: "null"
            albumMock.route(
                "api/album/detail",
                albumMock.ok("""{"album":{"id":8888,"cover":$coverJson}}""")
            )
        }
    }

    @Test
    fun freshCover_hobby_returnsCoverFromDetailApi() = runTest {
        val env = Env()
        env.stubHobbyCover(hobbyCover)

        val result = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(hobbyCover, result.getOrThrow())
        assertEquals(1, env.hobbyMock.requestCount)
    }

    @Test
    fun freshCover_album_hitsAlbumDetailEndpoint() = runTest {
        val env = Env()
        env.stubAlbumCover(albumCover)

        val result = env.repository.freshCover(8888L, CoverFreshnessRepository.EntityType.ALBUM)

        assertEquals(albumCover, result.getOrThrow())
        assertEquals(1, env.albumMock.requestCount)
        assertEquals(0, env.hobbyMock.requestCount)
    }

    @Test
    fun freshCover_secondCall_servedFromCacheWithoutExtraRequest() = runTest {
        val env = Env()
        env.stubHobbyCover(hobbyCover)

        env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        val second = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(hobbyCover, second.getOrThrow())
        assertEquals(1, env.hobbyMock.requestCount, "命中缓存后不应重复请求详情接口")
    }

    @Test
    fun freshCover_failure_isNotNegativeCachedAndCanRetry() = runTest {
        val env = Env()
        env.hobbyMock.replaceRoute("api/item/get", env.hobbyMock.failure(500, "Network error"))

        val first = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        assertTrue(first.isFailure)

        env.hobbyMock.replaceRoute(
            "api/item/get",
            env.hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}""")
        )

        val second = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(hobbyCover, second.getOrThrow(), "失败后应允许下一次重试成功")
        assertEquals(2, env.hobbyMock.requestCount)
    }

    @Test
    fun freshCover_blankCover_entersNegativeCacheAndStopsRequesting() = runTest {
        val env = Env()
        env.stubHobbyCover(cover = null)

        val first = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        val second = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(null, first.getOrThrow())
        assertEquals(null, second.getOrThrow())
        assertEquals(1, env.hobbyMock.requestCount, "服务端也没有封面时应走负缓存，避免空请求轰炸")
    }

    @Test
    fun rememberFreshCover_shortCircuitsNetworkRequest() = runTest {
        val env = Env()
        env.repository.rememberFreshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            cover = hobbyCover,
        )

        val result = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(hobbyCover, result.getOrThrow())
        assertEquals(0, env.hobbyMock.requestCount, "详情页沉淀的封面应直接命中，不再请求接口")
    }

    @Test
    fun freshCover_concurrentCalls_sendSingleRequest() = runTest {
        val env = Env()
        env.stubHobbyCover(hobbyCover)

        val results = listOf(
            async { env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY) },
            async { env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY) },
            async { env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY) },
        ).awaitAll()

        results.forEach { assertEquals(hobbyCover, it.getOrThrow()) }
        assertEquals(1, env.hobbyMock.requestCount, "并发修复应单飞，只发一次请求")
    }

    @Test
    fun freshCover_invalidId_returnsNullWithoutRequest() = runTest {
        val env = Env()

        val result = env.repository.freshCover(0L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(null, result.getOrThrow())
        assertEquals(0, env.hobbyMock.requestCount)
    }

    @Test
    fun freshCover_afterTtlExpires_refetches() = runTest {
        var now = nowMillis()
        val hobbyMock = MockApi()
        val repository = CoverFreshnessRepository(
            HobbyDetailRepository(hobbyMock.api()),
            AlbumDetailRepository(MockApi().api()),
            clock = { now },
        )
        hobbyMock.route(
            "api/item/get",
            hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}""")
        )

        repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        // 越过 30 分钟新鲜窗口
        now += 31L * 60L * 1000L
        val refreshed = repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertNotNull(refreshed.getOrThrow())
        assertEquals(2, hobbyMock.requestCount, "TTL 到期后应重新取一次")
    }

    @Test
    fun freshCover_negativeCacheExpires_afterWindow() = runTest {
        var now = nowMillis()
        val hobbyMock = MockApi()
        val repository = CoverFreshnessRepository(
            HobbyDetailRepository(hobbyMock.api()),
            AlbumDetailRepository(MockApi().api()),
            clock = { now },
        )
        hobbyMock.route(
            "api/item/get",
            hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":null}}""")
        )

        assertEquals(null, repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow())

        // 窗口内即使服务端已有封面，也仍在负缓存里，不发请求
        hobbyMock.replaceRoute(
            "api/item/get",
            hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}""")
        )
        assertEquals(null, repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow())
        assertEquals(1, hobbyMock.requestCount)

        // 越过 10 分钟负缓存窗口后必须重新探测
        now += 10L * 60L * 1000L + 1
        assertEquals(
            hobbyCover,
            repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow()
        )
        assertEquals(2, hobbyMock.requestCount)
    }

    @Test
    fun freshCover_afterOwnerCancelled_allowsRetry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val hobbyMock = MockApi()
        hobbyMock.routeWhenSuspend(
            predicate = { path, _, _ -> path.contains("api/item/get") },
            body = { _, _, _ ->
                gate.await()
                hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}""")
            },
        )
        val repository = CoverFreshnessRepository(
            HobbyDetailRepository(hobbyMock.api(dispatcher)),
            AlbumDetailRepository(MockApi().api(dispatcher)),
        )

        val owner = launch {
            repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        }
        advanceUntilIdle()
        assertEquals(1, hobbyMock.requestCount, "owner 应已发出请求")

        owner.cancel()
        advanceUntilIdle()

        // 在途占位必须已清理：否则后续调用会一直等在已取消 owner 的 deferred 上
        val retry = async {
            repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(hobbyCover, retry.await().getOrThrow())
        assertEquals(2, hobbyMock.requestCount, "取消后应允许重新发起请求")
    }
}
