package com.nobody.repoi.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.awaitPendingWrites
import com.nobody.repoi.platform.nowMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 封面新鲜度仓储测试。
 *
 * 覆盖关键契约：缓存命中不请求、失败可重试、空封面进负缓存且不落盘、详情页沉淀优先于接口、
 * 冷启动从真实测试存储文件预热、1000 条容量 FIFO 淘汰、500ms 防抖落盘与批量合并。
 */
class CoverFreshnessRepositoryTest {

    private val hobbyCover = "2026/11/new_hobby.jpg"
    private val albumCover = "2026/11/new_album.jpg"

    private class Env(
        scope: CoroutineScope,
        val hobbyMock: MockApi = MockApi(),
        val albumMock: MockApi = MockApi(),
        val preferences: DataStore<Preferences> = TestDataStore.coverFreshnessPrefs(),
    ) {
        val repository = testCoverFreshnessRepository(
            scope = scope,
            preferences = preferences,
            hobbyMock = hobbyMock,
            albumMock = albumMock,
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

    /** 模拟冷启动：在同一份真实 DataStore 上重建仓储实例，只依赖公开接口观察恢复结果。 */
    private fun TestScope.repositoryOver(
        preferences: DataStore<Preferences>,
        hobbyMock: MockApi = MockApi(),
        albumMock: MockApi = MockApi(),
    ): CoverFreshnessRepository = testCoverFreshnessRepository(
        scope = backgroundScope,
        preferences = preferences,
        hobbyMock = hobbyMock,
        albumMock = albumMock,
    )

    @Test
    fun freshCover_hobby_returnsCoverFromDetailApi() = runTest {
        val env = Env(backgroundScope)
        env.stubHobbyCover(hobbyCover)

        val result = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(hobbyCover, result.getOrThrow())
        assertEquals(1, env.hobbyMock.requestCount)
    }

    @Test
    fun freshCover_album_hitsAlbumDetailEndpoint() = runTest {
        val env = Env(backgroundScope)
        env.stubAlbumCover(albumCover)

        val result = env.repository.freshCover(8888L, CoverFreshnessRepository.EntityType.ALBUM)

        assertEquals(albumCover, result.getOrThrow())
        assertEquals(1, env.albumMock.requestCount)
        assertEquals(0, env.hobbyMock.requestCount)
    }

    @Test
    fun freshCover_secondCall_servedFromCacheWithoutExtraRequest() = runTest {
        val env = Env(backgroundScope)
        env.stubHobbyCover(hobbyCover)

        env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        val second = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(hobbyCover, second.getOrThrow())
        assertEquals(1, env.hobbyMock.requestCount, "命中缓存后不应重复请求详情接口")
    }

    @Test
    fun freshCover_forceRefresh_bypassesPositiveCacheAndPersistsNewCover() = runTest {
        val staleCover = "2020/01/stale_hobby.jpg"
        val prefs = TestDataStore.coverFreshnessPrefs()
        val env = Env(backgroundScope, preferences = prefs)
        env.repository.ensureWarmed()
        env.stubHobbyCover(staleCover)
        assertEquals(
            staleCover,
            env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow(),
        )

        // 已缓存地址在服务端再次失效（404）：穿透正缓存重新回源
        env.hobbyMock.replaceRoute(
            "api/item/get",
            env.hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}"""),
        )
        val refreshed = env.repository.freshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            forceRefresh = true,
        )

        assertEquals(hobbyCover, refreshed.getOrThrow())
        assertEquals(2, env.hobbyMock.requestCount, "forceRefresh 应跳过正缓存重新请求详情接口")
        assertEquals(
            hobbyCover,
            env.repository.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "回源成功应立即原子覆写内存正缓存",
        )

        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val persisted = awaitPersistedCover(
            preferences = prefs,
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            expected = hobbyCover,
        )
        assertEquals(hobbyCover, persisted, "穿透结果应经 500ms 防抖落盘，重建仓储后仍为新值")
    }

    @Test
    fun freshCover_forceRefreshFailure_keepsOldCoverAndLaterQueriesStayOffline() = runTest {
        val staleCover = "2020/01/stale_hobby.jpg"
        val prefs = TestDataStore.coverFreshnessPrefs()
        val env = Env(backgroundScope, preferences = prefs)
        env.repository.ensureWarmed()
        env.stubHobbyCover(staleCover)
        env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow()

        env.hobbyMock.replaceRoute("api/item/get", env.hobbyMock.failure(500, "Network error"))
        val failed = env.repository.freshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            forceRefresh = true,
        )

        assertTrue(failed.isFailure)
        assertEquals(
            staleCover,
            env.repository.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "穿透失败绝不修改旧正缓存",
        )

        val normal = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        assertEquals(staleCover, normal.getOrThrow())
        assertEquals(2, env.hobbyMock.requestCount, "失败后普通查询仍命中旧缓存，零新增请求")

        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val persisted = awaitPersistedCover(
            preferences = prefs,
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            expected = staleCover,
        )
        assertEquals(staleCover, persisted, "穿透失败不得污染已落盘的旧封面")
    }

    @Test
    fun freshCover_forceRefreshWithinNegativeWindow_skipsRequest() = runTest {
        val env = Env(backgroundScope)
        env.stubHobbyCover(cover = null)
        assertNull(
            env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow()
        )

        // 服务端此时已有封面，但负缓存窗口未过：forceRefresh 也不得穿透，避免空白封面请求风暴
        env.hobbyMock.replaceRoute(
            "api/item/get",
            env.hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}"""),
        )
        val forced = env.repository.freshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            forceRefresh = true,
        )

        assertNull(forced.getOrThrow(), "负缓存窗口内 forceRefresh 只能返回空")
        assertEquals(1, env.hobbyMock.requestCount, "负缓存应拦截穿透请求")
    }

    @Test
    fun freshCover_forceRefresh_joinsInFlightRequest() = runTest {
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
        val repository = testCoverFreshnessRepository(
            scope = backgroundScope,
            hobbyMock = hobbyMock,
            dispatcher = dispatcher,
        )

        val forced = async {
            repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY, forceRefresh = true)
        }
        advanceUntilIdle()
        assertEquals(1, hobbyMock.requestCount, "穿透请求应已发出")

        val joined = async {
            repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY, forceRefresh = true)
        }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(hobbyCover, forced.await().getOrThrow())
        assertEquals(hobbyCover, joined.await().getOrThrow())
        assertEquals(1, hobbyMock.requestCount, "已有同类在途请求时应并入等待，不重复发请求")
    }

    @Test
    fun freshCover_failure_isNotNegativeCachedAndCanRetry() = runTest {
        val env = Env(backgroundScope)
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
        val env = Env(backgroundScope)
        env.stubHobbyCover(cover = null)

        val first = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        val second = env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(null, first.getOrThrow())
        assertEquals(null, second.getOrThrow())
        assertEquals(1, env.hobbyMock.requestCount, "服务端也没有封面时应走负缓存，避免空请求轰炸")
    }

    @Test
    fun rememberFreshCover_shortCircuitsNetworkRequest() = runTest {
        val env = Env(backgroundScope)
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
        val env = Env(backgroundScope)
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
        val env = Env(backgroundScope)

        val result = env.repository.freshCover(0L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(null, result.getOrThrow())
        assertEquals(0, env.hobbyMock.requestCount)
    }

    @Test
    fun freshCover_staysCachedIndefinitely() = runTest {
        var now = nowMillis()
        val prefs = TestDataStore.coverFreshnessPrefs()
        val hobbyMock = MockApi()
        val repository = testCoverFreshnessRepository(
            scope = backgroundScope,
            preferences = prefs,
            hobbyMock = hobbyMock,
            clock = { now },
        )
        hobbyMock.route(
            "api/item/get",
            hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}""")
        )

        repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)
        // 永久有效：跨过旧实现的 30 分钟窗口（甚至跨天）后仍是同一份缓存
        now += 365L * 24L * 60L * 60L * 1000L
        val cached = repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(hobbyCover, cached.getOrThrow())
        assertEquals(1, hobbyMock.requestCount, "正缓存永久有效，不应按时间重新取")
    }

    @Test
    fun freshCover_negativeCacheExpires_afterWindow() = runTest {
        var now = nowMillis()
        val prefs = TestDataStore.coverFreshnessPrefs()
        val hobbyMock = MockApi()
        val repository = testCoverFreshnessRepository(
            scope = backgroundScope,
            preferences = prefs,
            hobbyMock = hobbyMock,
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
        val repository = testCoverFreshnessRepository(
            scope = backgroundScope,
            hobbyMock = hobbyMock,
            dispatcher = dispatcher,
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

    // ------------------------------------------------------------------ 持久化与容量

    @Test
    fun getCachedCover_isSynchronousPureMemoryRead() = runTest {
        val env = Env(backgroundScope)
        env.stubHobbyCover(hobbyCover)

        assertNull(env.repository.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY))
        assertEquals(0, env.hobbyMock.requestCount, "纯内存查询不应触发请求")

        env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY)

        assertEquals(
            hobbyCover,
            env.repository.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
        )
        assertEquals(1, env.hobbyMock.requestCount)
    }

    @Test
    fun coldStart_restoresPersistedCoversWithoutNetwork() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val writer = Env(backgroundScope, preferences = prefs)
        writer.repository.ensureWarmed()
        writer.repository.rememberFreshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY, hobbyCover)
        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val coldStartMock = MockApi()
        val restored = repositoryOver(prefs, hobbyMock = coldStartMock)
        restored.ensureWarmed()

        assertEquals(
            hobbyCover,
            restored.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "冷启动应从真实存储文件预热出已沉淀封面",
        )
        assertEquals(
            hobbyCover,
            restored.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow()
        )
        assertEquals(0, coldStartMock.requestCount, "冷启动命中持久化缓存后应零网络请求")
    }

    @Test
    fun capacity_evictsOldestBeyondThousandEntries() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val env = Env(backgroundScope, preferences = prefs)
        env.repository.ensureWarmed()

        for (id in 1L..1001L) {
            env.repository.rememberFreshCover(
                id = id,
                type = CoverFreshnessRepository.EntityType.HOBBY,
                cover = "covers/$id.jpg",
            )
        }

        assertNull(env.repository.getCachedCover(1L, CoverFreshnessRepository.EntityType.HOBBY), "最旧条目应被淘汰")
        assertEquals("covers/1001.jpg", env.repository.getCachedCover(1001L, CoverFreshnessRepository.EntityType.HOBBY))

        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val restored = repositoryOver(prefs)
        restored.ensureWarmed()

        assertNull(restored.getCachedCover(1L, CoverFreshnessRepository.EntityType.HOBBY), "淘汰结果应随快照落盘")
        assertEquals("covers/1000.jpg", restored.getCachedCover(1000L, CoverFreshnessRepository.EntityType.HOBBY))
        assertEquals("covers/1001.jpg", restored.getCachedCover(1001L, CoverFreshnessRepository.EntityType.HOBBY))
        assertNull(restored.getCachedCover(1002L, CoverFreshnessRepository.EntityType.HOBBY))
    }

    @Test
    fun capacity_overwriteMovesEntryToTail() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val env = Env(backgroundScope, preferences = prefs)

        for (id in 1L..1000L) {
            env.repository.rememberFreshCover(id, CoverFreshnessRepository.EntityType.HOBBY, "covers/$id.jpg")
        }
        // 覆写最旧条目：应视为最近使用而移到队尾
        env.repository.rememberFreshCover(1L, CoverFreshnessRepository.EntityType.HOBBY, "covers/1-new.jpg")
        env.repository.rememberFreshCover(1001L, CoverFreshnessRepository.EntityType.HOBBY, "covers/1001.jpg")

        assertEquals(
            "covers/1-new.jpg",
            env.repository.getCachedCover(1L, CoverFreshnessRepository.EntityType.HOBBY),
            "覆写应移到队尾，从而避开本次淘汰",
        )
        assertNull(env.repository.getCachedCover(2L, CoverFreshnessRepository.EntityType.HOBBY))
        assertEquals("covers/1001.jpg", env.repository.getCachedCover(1001L, CoverFreshnessRepository.EntityType.HOBBY))
    }

    @Test
    fun rememberFreshCover_defersDiskWriteUntilDebounceElapses() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val env = Env(backgroundScope, preferences = prefs)
        env.repository.ensureWarmed()
        env.repository.rememberFreshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY, hobbyCover)

        advanceTimeBy(400)
        prefs.awaitPendingWrites()
        val early = repositoryOver(prefs)
        early.ensureWarmed()
        assertNull(
            early.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "500ms 防抖窗口内不应落盘",
        )

        advanceTimeBy(200)
        advanceUntilIdle()
        prefs.awaitPendingWrites()
        val late = repositoryOver(prefs)
        late.ensureWarmed()
        assertEquals(
            hobbyCover,
            late.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "防抖窗口结束后应落盘",
        )
    }

    @Test
    fun rapidWrites_coalesceIntoSamePersistedSnapshot() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val env = Env(backgroundScope, preferences = prefs)
        env.repository.ensureWarmed()

        env.repository.rememberFreshCover(1L, CoverFreshnessRepository.EntityType.HOBBY, "covers/1.jpg")
        advanceTimeBy(100)
        env.repository.rememberFreshCover(2L, CoverFreshnessRepository.EntityType.HOBBY, "covers/2.jpg")
        advanceTimeBy(100)
        env.repository.rememberFreshCover(3L, CoverFreshnessRepository.EntityType.HOBBY, "covers/3.jpg")

        advanceTimeBy(200)
        prefs.awaitPendingWrites()
        val early = repositoryOver(prefs)
        early.ensureWarmed()
        assertNull(
            early.getCachedCover(3L, CoverFreshnessRepository.EntityType.HOBBY),
            "最后一条写入未满 500ms 前不应落盘",
        )

        advanceTimeBy(200)
        advanceUntilIdle()
        prefs.awaitPendingWrites()
        val flushed = repositoryOver(prefs)
        flushed.ensureWarmed()
        assertEquals("covers/1.jpg", flushed.getCachedCover(1L, CoverFreshnessRepository.EntityType.HOBBY))
        assertEquals("covers/2.jpg", flushed.getCachedCover(2L, CoverFreshnessRepository.EntityType.HOBBY))
        assertEquals("covers/3.jpg", flushed.getCachedCover(3L, CoverFreshnessRepository.EntityType.HOBBY))
    }

    @Test
    fun negativeCache_isNeverPersisted() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val env = Env(backgroundScope, preferences = prefs)
        env.stubHobbyCover(cover = null)

        assertNull(env.repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow())
        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val restored = repositoryOver(prefs)
        restored.ensureWarmed()
        assertNull(
            restored.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "负缓存只保留在内存，绝不能落盘",
        )

        // 负缓存未落盘：重建仓储（相当于进程重启）后不得再拦截请求
        val retryMock = MockApi()
        retryMock.route(
            "api/item/get",
            retryMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}"""),
        )
        val retried = repositoryOver(prefs, hobbyMock = retryMock)
        retried.ensureWarmed()
        assertEquals(
            hobbyCover,
            retried.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY).getOrThrow(),
            "重建仓储后负缓存已失效，应重新探测并拿到封面",
        )
        assertEquals(1, retryMock.requestCount, "负缓存不落盘，新进程必须重新请求")
    }

    @Test
    fun clear_removesPersistedCovers() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val env = Env(backgroundScope, preferences = prefs)
        env.repository.ensureWarmed()
        env.repository.rememberFreshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY, hobbyCover)
        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val before = repositoryOver(prefs)
        before.ensureWarmed()
        assertNotNull(before.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY))

        // clear() 是终态：挂起返回即已落盘清空，不再依赖 501ms 防抖推进
        env.repository.clear()
        prefs.awaitPendingWrites()

        assertTrue(
            prefs.data.first().asMap().isEmpty(),
            "clear() 应立即清空持久化内容（不得留下任何 key），而不是等防抖窗口",
        )
        val after = repositoryOver(prefs)
        after.ensureWarmed()
        assertNull(after.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY), "clear 应同步清空落盘内容")
    }

    @Test
    fun clear_afterInFlightFetch_doesNotResurrectCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val prefs = TestDataStore.coverFreshnessPrefs()
        val hobbyMock = MockApi()
        hobbyMock.routeWhenSuspend(
            predicate = { path, _, _ -> path.contains("api/item/get") },
            body = { _, _, _ ->
                gate.await()
                hobbyMock.ok("""{"itemData":{"id":100,"nodeId":100,"itemId":59570,"cover":"$hobbyCover"}}""")
            },
        )
        val repository = testCoverFreshnessRepository(
            scope = backgroundScope,
            preferences = prefs,
            hobbyMock = hobbyMock,
            dispatcher = dispatcher,
        )

        val pending = async { repository.freshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY) }
        advanceUntilIdle()
        assertEquals(1, hobbyMock.requestCount, "回源请求应已在途")

        // 登出：在途回源尚未返回时清空
        repository.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(hobbyCover, pending.await().getOrThrow(), "调用方仍应拿到本次回源结果")
        assertNull(
            repository.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "clear 之后完成的旧回源不得写回内存缓存",
        )
        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()
        assertTrue(
            prefs.data.first().asMap().isEmpty(),
            "clear 之后完成的旧回源不得触发落盘复活",
        )
    }

    @Test
    fun persist_retriesAfterWarmReadFailure_andStillWrites() = runTest {
        val inner = TestDataStore.coverFreshnessPrefs()
        // 初始化预热与首次落盘尝试（persistSnapshot 的 ensureWarmed）各消耗一次读盘失败，
        // 因此落盘必须依赖有界重试才能成功。
        val flaky = FlakyDataStore(inner, failures = 2)
        val env = Env(backgroundScope, preferences = flaky)

        env.repository.rememberFreshCover(59570L, CoverFreshnessRepository.EntityType.HOBBY, hobbyCover)

        // 第一个防抖窗口：预热读盘失败，落盘被放弃但信号未丢弃
        advanceTimeBy(501)
        advanceUntilIdle()
        inner.awaitPendingWrites()
        assertTrue(
            inner.data.first().asMap().isEmpty(),
            "预热未落定时不应写盘（等待重试）",
        )

        // 后续窗口：重试读到磁盘后落盘成功（无重试逻辑时该断言会失败）
        assertEquals(
            hobbyCover,
            awaitPersistedCover(
                preferences = inner,
                id = 59570L,
                type = CoverFreshnessRepository.EntityType.HOBBY,
                expected = hobbyCover,
                message = "预热读失败一次后，写入仍应在有界重试后落盘",
            ),
        )
    }
}

/**
 * 可注入读盘失败的 DataStore 包装：前 [failures] 次 `data` 收集抛 [IOException]，
 * 其余全部委托真实 TestDataStore（写入始终委托，保证落盘可观察）。
 */
private class FlakyDataStore(
    private val delegate: DataStore<Preferences>,
    private val failures: Int,
) : DataStore<Preferences> {
    private var reads = 0

    override val data: Flow<Preferences> = flow {
        if (reads++ < failures) throw IOException("模拟封面新鲜度读盘失败")
        emitAll(delegate.data)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        delegate.updateData(transform)
}
