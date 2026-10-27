package com.nobody.repoi.util.paging

import com.nobody.repoi.test.TestDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private data class TestItem(val id: Long, val name: String)

@OptIn(ExperimentalCoroutinesApi::class)
class PagedListLoaderTest {

    private val dispatchers = TestDispatchers()

    @BeforeTest
    fun setUp() = dispatchers.setUp()

    @AfterTest
    fun tearDown() = dispatchers.tearDown()

    @Test
    fun loadInitial_successPopulatesItemsAndResetsFlags() = runTest {
        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2,
            keySelector = { it.id }
        ) { page, _ ->
            Result.success(listOf(TestItem(1, "A"), TestItem(2, "B")))
        }

        assertEquals(LoadStage.Idle, loader.state.value.stage)
        loader.loadInitial()
        advanceUntilIdle()

        val state = loader.state.value
        assertEquals(LoadStage.Idle, state.stage)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoadingMore)
        assertTrue(state.hasMore)
        assertEquals(1, state.page)
        assertNull(state.errorMessage)
        assertEquals(listOf(TestItem(1, "A"), TestItem(2, "B")), state.items)
    }

    @Test
    fun loadInitial_failureSetsInitialError() = runTest {
        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2
        ) { _, _ ->
            Result.failure(RuntimeException("网络超时"))
        }

        loader.loadInitial()
        advanceUntilIdle()

        val state = loader.state.value
        assertEquals(LoadStage.Idle, state.stage)
        assertFalse(state.isLoading)
        assertEquals("网络超时", state.errorMessage)
        assertTrue(state.items.isEmpty())
        assertTrue(state.isInitialError)
    }

    @Test
    fun refresh_replacesItemsAndMaintainsVisibilityDuringLoading() = runTest {
        var fetchCount = 0
        val deferred = CompletableDeferred<List<TestItem>>()

        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2,
            keySelector = { it.id }
        ) { _, _ ->
            fetchCount++
            if (fetchCount == 1) {
                Result.success(listOf(TestItem(1, "旧数据1"), TestItem(2, "旧数据2")))
            } else {
                Result.success(deferred.await())
            }
        }

        loader.loadInitial()
        advanceUntilIdle()
        assertEquals(2, loader.state.value.items.size)

        // 触发下拉刷新
        loader.refresh()
        assertTrue(loader.state.value.isRefreshing)
        // 刷新中旧数据依然可见，不发生闪烁
        assertEquals(2, loader.state.value.items.size)

        deferred.complete(listOf(TestItem(10, "新数据1"), TestItem(20, "新数据2")))
        advanceUntilIdle()

        val state = loader.state.value
        assertFalse(state.isRefreshing)
        assertEquals(listOf(TestItem(10, "新数据1"), TestItem(20, "新数据2")), state.items)
        assertEquals(1, state.page)
    }

    @Test
    fun loadMore_appendsItemsAndDeduplicatesByStableKey() = runTest {
        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2,
            keySelector = { it.id }
        ) { page, _ ->
            if (page == 1) {
                Result.success(listOf(TestItem(1, "条目1"), TestItem(2, "条目2")))
            } else {
                // 返回包含重复键 2 的新页
                Result.success(listOf(TestItem(2, "重复条目2"), TestItem(3, "条目3")))
            }
        }

        loader.loadInitial()
        advanceUntilIdle()
        assertEquals(2, loader.state.value.items.size)

        loader.loadMore()
        advanceUntilIdle()

        val state = loader.state.value
        assertFalse(state.isLoadingMore)
        assertEquals(2, state.page)
        // 稳定键 2 去重，保留已有项，新条目 3 成功追加
        assertEquals(listOf(TestItem(1, "条目1"), TestItem(2, "条目2"), TestItem(3, "条目3")), state.items)
    }

    @Test
    fun loadMore_concurrencyGuardDropsRepeatedCalls() = runTest {
        var fetchCalls = 0
        val firstPageGate = CompletableDeferred<Unit>()
        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2
        ) { page, _ ->
            fetchCalls++
            if (page == 1) {
                firstPageGate.await()
                Result.success(listOf(TestItem(1, "条目1"), TestItem(2, "条目2")))
            } else {
                Result.success(listOf(TestItem(3, "条目3")))
            }
        }

        loader.loadInitial()
        runCurrent()
        assertEquals(1, fetchCalls)
        assertEquals(LoadStage.InitialLoading, loader.state.value.stage)

        // 首屏仍在途，第二次 loadMore 必须被 stage 守卫拦截，而不是被 hasMore 拦截
        loader.loadMore()
        runCurrent()
        assertEquals(1, fetchCalls)

        firstPageGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, loader.state.value.items.size)

        // 首屏完成后，loadMore 才能正常发起第二页请求
        loader.loadMore()
        advanceUntilIdle()
        assertEquals(2, fetchCalls)
        assertEquals(3, loader.state.value.items.size)
    }

    @Test
    fun inFlightCancellation_cancelsOldJobWhenNewInitialLoaded() = runTest {
        val slowDeferred = CompletableDeferred<List<TestItem>>()
        var callCount = 0

        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2,
            keySelector = { it.id }
        ) { _, _ ->
            callCount++
            if (callCount == 1) {
                Result.success(slowDeferred.await())
            } else {
                Result.success(listOf(TestItem(99, "最新请求")))
            }
        }

        loader.loadInitial()
        testScheduler.runCurrent()
        // 此时第一个请求挂起在 slowDeferred
        assertEquals(1, callCount)
        assertEquals(LoadStage.InitialLoading, loader.state.value.stage)

        // 再次调用 loadInitial 发起第二个请求
        loader.loadInitial()
        advanceUntilIdle()
        assertEquals(2, callCount)

        // 即使之后 slowDeferred 完成，也不会污染新请求的状态
        slowDeferred.complete(listOf(TestItem(1, "过期请求")))
        advanceUntilIdle()

        assertEquals(listOf(TestItem(99, "最新请求")), loader.state.value.items)
    }

    @Test
    fun mutateItems_updateAndRemoveItemsLocally() = runTest {
        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 3,
            keySelector = { it.id }
        ) { _, _ ->
            Result.success(listOf(TestItem(1, "A"), TestItem(2, "B"), TestItem(3, "C")))
        }

        loader.loadInitial()
        advanceUntilIdle()

        // 更新单个条目
        loader.updateItem({ it.id == 2L }) { it.copy(name = "B-已修改") }
        assertEquals("B-已修改", loader.state.value.items.find { it.id == 2L }?.name)

        // 删除单个条目
        loader.removeItem { it.id == 1L }
        assertEquals(listOf(TestItem(2, "B-已修改"), TestItem(3, "C")), loader.state.value.items)
    }

    @Test
    fun retry_intelligentlyRecoversFromInitialAndAppendErrors() = runTest {
        var failFirst = true
        var failMore = true

        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2,
            keySelector = { it.id }
        ) { page, _ ->
            if (page == 1) {
                if (failFirst) {
                    Result.failure(RuntimeException("首次失败"))
                } else {
                    Result.success(listOf(TestItem(1, "A"), TestItem(2, "B")))
                }
            } else {
                if (failMore) {
                    Result.failure(RuntimeException("追加失败"))
                } else {
                    Result.success(listOf(TestItem(3, "C"), TestItem(4, "D")))
                }
            }
        }

        loader.loadInitial()
        advanceUntilIdle()
        assertEquals("首次失败", loader.state.value.errorMessage)

        // 修复首次失败后重试
        failFirst = false
        loader.retry()
        advanceUntilIdle()
        assertNull(loader.state.value.errorMessage)
        assertEquals(2, loader.state.value.items.size)

        // 加载更多失败
        loader.loadMore()
        advanceUntilIdle()
        assertEquals("追加失败", loader.state.value.loadMoreError)

        // 修复追加失败后重试
        failMore = false
        loader.retry()
        advanceUntilIdle()
        assertNull(loader.state.value.loadMoreError)
        assertEquals(4, loader.state.value.items.size)
    }

    @Test
    fun pagedChunkLoader_explicitHasMoreHonored() = runTest {
        val loader = pagedChunkLoader<TestItem>(
            scope = this,
            pageSize = 2
        ) { _, _ ->
            // 虽然返回了 2 条（达到 pageSize），但服务端显式告知 hasMore = false
            Result.success(PageChunk(items = listOf(TestItem(1, "A"), TestItem(2, "B")), hasMore = false))
        }

        loader.loadInitial()
        advanceUntilIdle()

        val state = loader.state.value
        assertEquals(2, state.items.size)
        assertFalse(state.hasMore)
        assertTrue(state.isEndReached)
    }

    @Test
    fun loadMore_whenInitialError_isGuardedAndDoesNotSkipPage() = runTest {
        var requestedPage = 0
        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2
        ) { page, _ ->
            requestedPage = page
            Result.failure(Exception("首屏网络超时"))
        }

        loader.loadInitial()
        advanceUntilIdle()

        assertEquals(1, requestedPage)
        assertEquals("首屏网络超时", loader.state.value.errorMessage)
        assertTrue(loader.state.value.items.isEmpty())

        // 尝试在首屏错误状态下调用 loadMore，验证被守卫拦截，不会越页请求第 2 页
        loader.loadMore()
        advanceUntilIdle()

        assertEquals(1, requestedPage) // 仍为 1，未发起第 2 页请求
        assertEquals(LoadStage.Idle, loader.state.value.stage)
    }

    @Test
    fun loadMore_silent_suppressesVisualLoadingMoreState() = runTest {
        val loader = pagedListLoader<TestItem>(
            scope = this,
            pageSize = 2
        ) { page, size ->
            val start = (page - 1) * size + 1
            Result.success(listOf(TestItem(start.toLong(), "Item $start"), TestItem((start + 1).toLong(), "Item ${start + 1}")))
        }

        loader.loadInitial()
        advanceUntilIdle()
        assertEquals(2, loader.state.value.items.size)

        // 触发静默加载
        loader.loadMore(isSilent = true)
        // 验证静默模式下虽然处于 Appending 阶段，但面向 UI 的 isLoadingMore 被抑制为 false
        assertEquals(LoadStage.Appending, loader.state.value.stage)
        assertFalse("静默模式下 isLoadingMore 应为 false", loader.state.value.isLoadingMore)
        assertTrue(loader.state.value.isSilent)

        advanceUntilIdle()
        assertEquals(4, loader.state.value.items.size)
        assertFalse(loader.state.value.isSilent)
        assertFalse(loader.state.value.isLoadingMore)
    }
}
