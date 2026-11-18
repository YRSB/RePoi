package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 收藏变更代数单测（ADR-0025 写侧闭环缝隙）。
 *
 * 覆盖：全量写操作在网络成功后原子自增 [CollectRepository.collectMutationGeneration]、
 * 失败与校验拦截绝不递增、`toggleItemCollect` 统一切换接缝的各分支与状态池流转幂等、
 * 并发 CAS 下「同 nodeId 同实例」与代数计数的稳定性。
 *
 * 断言全部经由 [CollectRepository] 公开接口进行，不触达私有实现；
 * 命名刻意避开 `*ViewModelTest`（CI `excludeTestsMatching` 隔离名单）。
 */
class CollectRepositoryMutationGenerationTest {

    private fun setup(): Pair<MockApi, CollectRepository> {
        val mock = MockApi()
        return mock to CollectRepository(mock.api(), mock.json)
    }

    @Test
    fun collectMutationGeneration_startsAtZero() {
        val (_, repository) = setup()
        assertEquals(0L, repository.collectMutationGeneration.value)
    }

    /** 七类写操作（手办保存/删除、条目增/删、批量取消、作品保存/取消）各自成功推进 1 代。 */
    @Test
    fun allWriteOps_onNetworkSuccess_advanceGenerationOnceEach() = runTest {
        val (mock, repository) = setup()
        val ok = """{"success":true,"code":200,"msg":"ok","data":{}}"""
        var generation = 0L

        mock.enqueue(ok)
        assertTrue(
            repository.saveCollect(1L, CollectData(id = 1L, state = CollectConstants.STATE_BUY)).isSuccess
        )
        assertEquals(++generation, repository.collectMutationGeneration.value)

        mock.enqueue(ok)
        assertTrue(
            repository.deleteCollect(1L, CollectData(id = 1L, state = CollectConstants.STATE_BUY)).isSuccess
        )
        assertEquals(++generation, repository.collectMutationGeneration.value)

        mock.enqueue(ok)
        assertTrue(repository.collectItemAdd(2L).isSuccess)
        assertEquals(++generation, repository.collectMutationGeneration.value)

        mock.enqueue(ok)
        assertTrue(repository.collectItemDel(2L).isSuccess)
        assertEquals(++generation, repository.collectMutationGeneration.value)

        mock.enqueue(ok)
        assertTrue(repository.cancelCollectBatch(listOf(1L, 2L)).isSuccess)
        assertEquals(++generation, repository.collectMutationGeneration.value)

        mock.enqueue(ok)
        assertTrue(
            repository.saveWorksCollect(worksId = 20001L, state = 2, tagStr = "热血,校园", comment = "追番").isSuccess
        )
        assertEquals(++generation, repository.collectMutationGeneration.value)

        mock.enqueue(ok)
        assertTrue(repository.deleteWorksCollect(20001L).isSuccess)
        assertEquals(++generation, repository.collectMutationGeneration.value)
    }

    /** 网络失败时七类写操作全部不推进代数（也不写池，池行为由状态池测试另行锁定）。 */
    @Test
    fun allWriteOps_onNetworkFailure_neverAdvanceGeneration() = runTest {
        val (mock, repository) = setup()

        mock.enqueue(mock.failure(500, "保存失败"))
        assertTrue(
            repository.saveCollect(1L, CollectData(id = 1L, state = CollectConstants.STATE_BUY)).isFailure
        )
        mock.enqueue(mock.failure(500, "取消失败"))
        assertTrue(
            repository.deleteCollect(1L, CollectData(id = 1L, state = CollectConstants.STATE_BUY)).isFailure
        )
        mock.enqueue(mock.failure(500, "收藏失败"))
        assertTrue(repository.collectItemAdd(2L).isFailure)
        mock.enqueue(mock.failure(500, "取消收藏失败"))
        assertTrue(repository.collectItemDel(2L).isFailure)
        mock.enqueue(mock.failure(500, "批量取消失败"))
        assertTrue(repository.cancelCollectBatch(listOf(1L)).isFailure)
        mock.enqueue(mock.failure(500, "作品保存失败"))
        assertTrue(repository.saveWorksCollect(20001L, state = 2, tagStr = "热血", comment = "追番").isFailure)
        mock.enqueue(mock.failure(500, "作品取消失败"))
        assertTrue(repository.deleteWorksCollect(20001L).isFailure)

        assertEquals(0L, repository.collectMutationGeneration.value)
    }

    /** 空集合批量取消属校验拦截：成功短路、零请求、不推进代数。 */
    @Test
    fun cancelCollectBatch_emptyIds_shortCircuitsWithoutAdvancingGeneration() = runTest {
        val (mock, repository) = setup()

        val result = repository.cancelCollectBatch(emptyList())

        assertTrue(result.isSuccess)
        assertEquals(0, mock.requestCount)
        assertEquals(0L, repository.collectMutationGeneration.value)
    }

    @Test
    fun toggleItemCollect_whenNotCollected_dispatchesAddAndAdvancesGeneration() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.toggleItemCollect(100L, isCollected = false)

        assertTrue(result.getOrThrow())
        assertEquals("/api/collect/item/add", mock.lastRequest().encodedPath)
        assertEquals(CollectConstants.STATE_CARE, observed.value?.state)
        assertEquals(1L, repository.collectMutationGeneration.value)
    }

    @Test
    fun toggleItemCollect_whenCollected_dispatchesDelAndAdvancesGeneration() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        val ok = """{"success":true,"code":200,"msg":"ok","data":{}}"""
        mock.enqueue(ok)
        repository.collectItemAdd(100L).getOrThrow()

        mock.enqueue(ok)
        val result = repository.toggleItemCollect(100L, isCollected = true)

        assertFalse(result.getOrThrow())
        assertEquals("/api/collect/del", mock.lastRequest().encodedPath)
        assertNull(observed.value)
        assertEquals(2L, repository.collectMutationGeneration.value)
    }

    @Test
    fun toggleItemCollect_onAddFailure_keepsPoolAndGenerationUnchanged() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue(mock.failure(500, "收藏失败"))

        val result = repository.toggleItemCollect(100L, isCollected = false)

        assertTrue(result.isFailure)
        assertNull(observed.value)
        assertEquals(0L, repository.collectMutationGeneration.value)
    }

    @Test
    fun toggleItemCollect_onDelFailure_keepsPoolAndGenerationUnchanged() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        repository.collectItemAdd(100L).getOrThrow()

        mock.enqueue(mock.failure(500, "取消收藏失败"))
        val result = repository.toggleItemCollect(100L, isCollected = true)

        assertTrue(result.isFailure)
        assertEquals(CollectConstants.STATE_CARE, observed.value?.state)
        assertEquals(1L, repository.collectMutationGeneration.value)
    }

    /** 收藏 → 取消 → 再收藏：池值往返流转，观察句柄全程同一实例，每次成功推进 1 代。 */
    @Test
    fun toggleItemCollect_roundTrip_transitionsPoolAndKeepsSingleInstance() = runTest {
        val (mock, repository) = setup()
        val ok = """{"success":true,"code":200,"msg":"ok","data":{}}"""
        val observed = repository.observeCollectState(100L)

        mock.enqueue(ok)
        assertTrue(repository.toggleItemCollect(100L, isCollected = false).getOrThrow())
        mock.enqueue(ok)
        assertFalse(repository.toggleItemCollect(100L, isCollected = true).getOrThrow())
        assertNull(observed.value)
        mock.enqueue(ok)
        assertTrue(repository.toggleItemCollect(100L, isCollected = false).getOrThrow())

        assertEquals(CollectConstants.STATE_CARE, observed.value?.state)
        assertSame(observed, repository.observeCollectState(100L))
        assertEquals(3L, repository.collectMutationGeneration.value)
    }

    /** 过渡代理对齐：手动标记画廊写操作同时推进旧计数与新代数，两边不遗漏。 */
    @Test
    fun markGalleryMutation_transitionalProxy_advancesBothCounters() {
        val (_, repository) = setup()
        assertEquals(0L, repository.galleryMutationCount.value)
        assertEquals(0L, repository.collectMutationGeneration.value)

        repository.markGalleryMutation()

        assertEquals(1L, repository.galleryMutationCount.value)
        assertEquals(1L, repository.collectMutationGeneration.value)
    }

    /**
     * 并发首访 + 并发写：多线程同时观察与切换同一批 nodeId，
     * CAS 状态池仍保证「同 nodeId 同实例」，代数恰好等于成功写操作数。
     */
    @Test
    fun concurrentObserveAndToggle_casPoolStaysSingleInstancePerNode() = runTest {
        val (mock, repository) = setup()
        mock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        val observers = (1..8).map {
            async(Dispatchers.IO) { repository.observeCollectState(1000L + it) }
        }
        val toggles = (1..24).map { index ->
            async(Dispatchers.IO) { repository.toggleItemCollect(1000L + index, isCollected = false) }
        }
        toggles.awaitAll().forEach { assertTrue(it.getOrThrow()) }
        val raced = observers.awaitAll()

        assertEquals(24L, repository.collectMutationGeneration.value)
        (1..24).forEach { index ->
            assertEquals(
                CollectConstants.STATE_CARE,
                repository.observeCollectState(1000L + index).value?.state
            )
        }
        // 8 个并发首访句柄与写后再观察逐 nodeId 对位仍是同一实例（CAS 幂等）
        raced.forEachIndexed { index, flow ->
            assertSame(flow, repository.observeCollectState(1001L + index))
        }
    }

    /**
     * 并发混合结局：按 id 奇偶确定性分发成功/失败响应，
     * 代数只计成功次数，失败条目状态池保持原值（null）。
     */
    @Test
    fun concurrentItemAdds_mixedNetworkOutcomes_generationCountsSuccessesOnly() = runTest {
        val (mock, repository) = setup()
        val okBody = """{"success":true,"code":200,"msg":"ok","data":{}}"""
        mock.routeWhen(
            { _, form, _ -> (form["id"]?.toLongOrNull() ?: -1L) % 2L == 0L },
            { _, _, _ -> okBody }
        )
        mock.routeWhen(
            { _, form, _ -> (form["id"]?.toLongOrNull() ?: -1L) % 2L == 1L },
            { _, _, _ -> mock.failure(500, "收藏失败") }
        )
        val ids = (1L..30L).toList()

        val results = ids.map { id -> async(Dispatchers.IO) { repository.collectItemAdd(id) } }.awaitAll()

        assertEquals(15, results.count { it.isSuccess })
        assertEquals(15L, repository.collectMutationGeneration.value)
        ids.forEach { id ->
            val state = repository.observeCollectState(id).value
            if (id % 2L == 0L) {
                assertEquals(CollectConstants.STATE_CARE, state?.state)
            } else {
                assertNull(state)
            }
        }
    }
}
