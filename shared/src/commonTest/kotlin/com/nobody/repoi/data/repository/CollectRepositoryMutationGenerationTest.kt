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

        raced.forEachIndexed { index, flow ->
            assertSame(flow, repository.observeCollectState(1001L + index))
        }
    }

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
