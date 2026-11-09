package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.session.FakeUserSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 收藏仓储条目状态池单测（状态驱动架构的 SSOT 缝隙）。
 *
 * 覆盖：`observeCollectState` 幂等同实例、`refreshCollectState` 成功后写池 / 失败不改写、
 * 写操作（保存 / 取消 / 通用条目增删）成功后才更新池、失败绝不污染。
 * 命名刻意避开 `*ViewModelTest`（CI `excludeTestsMatching` 隔离名单）。
 */
class CollectRepositoryStateTest {

    private fun setup(): Pair<MockApi, CollectRepository> {
        val mock = MockApi()
        return mock to CollectRepository(mock.api(), mock.json, FakeUserSession("utoken-test"))
    }

    @Test
    fun observeCollectState_sameNodeId_returnsSameInstanceAndStartsNull() {
        val (_, repository) = setup()

        val first = repository.observeCollectState(100L)
        val second = repository.observeCollectState(100L)

        assertSame(first, second)
        assertNull(first.value)
        // 不同 nodeId 互不干扰
        assertTrue(first !== repository.observeCollectState(200L))
    }

    @Test
    fun refreshCollectState_onSuccess_publishesToStatePool() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.ok("""{"collect":{"id":100,"state":"care"}}"""))
        val observed = repository.observeCollectState(100L)

        val result = repository.refreshCollectState(100L)

        assertTrue(result.isSuccess)
        assertEquals("care", observed.value?.state)
        assertSame(observed, repository.observeCollectState(100L))
    }

    @Test
    fun refreshCollectState_onFailure_keepsPreviousValue() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue(mock.ok("""{"collect":{"id":100,"state":"buy"}}"""))
        repository.refreshCollectState(100L).getOrThrow()
        assertEquals("buy", observed.value?.state)

        mock.enqueue(mock.failure(500, "网络异常"))
        val failed = repository.refreshCollectState(100L)

        assertTrue(failed.isFailure)
        assertEquals("buy", observed.value?.state)
    }

    @Test
    fun saveCollect_onSuccess_updatesStatePool() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.saveCollect(100L, CollectData(id = 100L, state = CollectConstants.STATE_BUY))

        assertTrue(result.isSuccess)
        assertEquals(CollectConstants.STATE_BUY, observed.value?.state)
    }

    @Test
    fun saveCollect_onFailure_keepsStatePoolUnchanged() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue(mock.ok("""{"collect":{"id":100,"state":"care"}}"""))
        repository.refreshCollectState(100L).getOrThrow()

        mock.enqueue(mock.failure(500, "保存失败"))
        val result = repository.saveCollect(100L, CollectData(id = 100L, state = CollectConstants.STATE_BUY))

        assertTrue(result.isFailure)
        assertEquals("care", observed.value?.state)
    }

    @Test
    fun deleteCollect_onSuccess_clearsStatePool() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue(mock.ok("""{"collect":{"id":100,"state":"buy"}}"""))
        repository.refreshCollectState(100L).getOrThrow()

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.deleteCollect(100L, CollectData(id = 100L, state = CollectConstants.STATE_BUY))

        assertTrue(result.isSuccess)
        assertNull(observed.value)
    }

    @Test
    fun deleteCollect_onFailure_keepsStatePoolUnchanged() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue(mock.ok("""{"collect":{"id":100,"state":"buy"}}"""))
        repository.refreshCollectState(100L).getOrThrow()

        mock.enqueue(mock.failure(500, "取消失败"))
        val result = repository.deleteCollect(100L, CollectData(id = 100L, state = CollectConstants.STATE_BUY))

        assertTrue(result.isFailure)
        assertEquals("buy", observed.value?.state)
    }

    /**
     * `care` 仅作为「已收藏」占位值（服务端 `api/collect/item/add` 无详情返回），
     * 不代表手办心愿档；无旧值时以空 [CollectData] 为底。
     */
    @Test
    fun collectItemAdd_onSuccessWithNoPreviousValue_marksCollectedPlaceholder() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.collectItemAdd(100L)

        assertTrue(result.isSuccess)
        assertEquals(CollectConstants.STATE_CARE, observed.value?.state)
        assertTrue(observed.value?.isCollected == true)
    }

    /** 旧值存在时只改 `state`，其余字段原样保留。 */
    @Test
    fun collectItemAdd_onSuccess_preservesExistingFields() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        repository.saveCollect(
            100L,
            CollectData(id = 100L, state = CollectConstants.STATE_BUY, rating = 4, comment = "已入手")
        ).getOrThrow()

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        repository.collectItemAdd(100L).getOrThrow()

        assertEquals(CollectConstants.STATE_CARE, observed.value?.state)
        assertEquals(4, observed.value?.rating)
        assertEquals("已入手", observed.value?.comment)
        assertEquals(100L, observed.value?.id)
    }

    @Test
    fun collectItemAdd_onFailure_keepsStatePoolUnchanged() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue(mock.failure(500, "收藏失败"))

        val result = repository.collectItemAdd(100L)

        assertTrue(result.isFailure)
        assertNull(observed.value)
    }

    @Test
    fun collectItemDel_onSuccess_clearsStatePool() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        repository.collectItemAdd(100L).getOrThrow()

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.collectItemDel(100L)

        assertTrue(result.isSuccess)
        assertNull(observed.value)
    }

    @Test
    fun collectItemDel_onFailure_keepsStatePoolUnchanged() = runTest {
        val (mock, repository) = setup()
        val observed = repository.observeCollectState(100L)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        repository.collectItemAdd(100L).getOrThrow()

        mock.enqueue(mock.failure(500, "取消收藏失败"))
        val result = repository.collectItemDel(100L)

        assertTrue(result.isFailure)
        assertEquals(CollectConstants.STATE_CARE, observed.value?.state)
    }

    @Test
    fun markGalleryMutated_incrementsMonotonically() {
        val (_, repository) = setup()
        assertEquals(0L, repository.galleryMutationTicks.value)

        repository.markGalleryMutated()
        repository.markGalleryMutated()

        assertEquals(2L, repository.galleryMutationTicks.value)
    }
}
