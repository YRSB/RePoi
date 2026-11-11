package com.nobody.repoi.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.platform.nowMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
        val repository = CoverFreshnessRepository(
            HobbyDetailRepository(hobbyMock.api()),
            AlbumDetailRepository(albumMock.api()),
            preferences,
            scope,
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
    ): CoverFreshnessRepository = CoverFreshnessRepository(
        HobbyDetailRepository(hobbyMock.api()),
        AlbumDetailRepository(albumMock.api()),
        preferences,
        backgroundScope,
    )

    /**
     * 等待 DataStore 处理完已排队的写入。
     *
     * DataStore 的 `data.first()` 可能直接返回内存缓存，看不到「已入队、尚未落定」的写入；
     * 空 `edit` 会排在所有在途写入之后，用它作为可观测的同步屏障，避免测试断言读到旧缓存。
     */
    private suspend fun DataStore<Preferences>.awaitPendingWrites() {
        edit { }
    }

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
        val repository = CoverFreshnessRepository(
            HobbyDetailRepository(hobbyMock.api()),
            AlbumDetailRepository(MockApi().api()),
            prefs,
            backgroundScope,
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
        val repository = CoverFreshnessRepository(
            HobbyDetailRepository(hobbyMock.api()),
            AlbumDetailRepository(MockApi().api()),
            prefs,
            backgroundScope,
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
            TestDataStore.coverFreshnessPrefs(),
            backgroundScope,
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

        env.repository.clear()
        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val after = repositoryOver(prefs)
        after.ensureWarmed()
        assertNull(after.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY), "clear 应同步清空落盘内容")
    }
}
