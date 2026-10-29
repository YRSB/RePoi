package com.nobody.poishelf.util.paging

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 分页加载器纯逻辑测试（W4-W5 pure-logic；KMP commonTest）。
 *
 * - 零 Main 调度器依赖：loader scope 取 runTest 的 `backgroundScope`
 *   （与生产 `viewModelScope` 同为普通 CoroutineScope），`advanceUntilIdle` 排空。
 * - 零网络/仓储/DataStore：fetcher 为纯内存假页。
 * - 类名刻意避开 `*ViewModel*Test`（M7 quarantine filter 仅放行非 VM 测试）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PagedLoaderLogicTest {

    @Test
    fun pageChunk_ofDerivesHasMoreByCapacity() {
        assertTrue(PageChunk.of(listOf(1, 2, 3), 3).hasMore)
        assertFalse(PageChunk.of(listOf(1, 2), 3).hasMore)
        assertFalse(PageChunk.of(emptyList<Int>(), 3).hasMore)
        val empty = PageChunk.empty<Long>()
        assertTrue(empty.items.isEmpty())
        assertFalse(empty.hasMore)
    }

    @Test
    fun stateDerivedFlags_matchUiContract() {
        val loading = PagedListState<Int>(stage = LoadStage.InitialLoading)
        assertTrue(loading.isLoading)
        assertFalse(loading.isRefreshing)
        val refreshing = PagedListState<Int>(stage = LoadStage.Refreshing)
        assertTrue(refreshing.isRefreshing)
        val appending = PagedListState<Int>(stage = LoadStage.Appending)
        assertTrue(appending.isLoadingMore)
        val silent = PagedListState<Int>(stage = LoadStage.Appending, isSilent = true)
        assertFalse(silent.isLoadingMore)
        val err = PagedListState<Int>(initialError = "boom")
        assertEquals("boom", err.errorMessage)
        assertTrue(err.isInitialError)
        val end = PagedListState<Int>(hasMore = false)
        assertTrue(end.isEndReached)
    }

    @Test
    fun loadInitial_successPopulatesItemsAndPage() = runTest {
        val loader = PagedListLoader<Long>(
            scope = backgroundScope,
            pageSize = 3,
            keySelector = { it }
        ) { page, _ ->
            if (page == 1) Result.success(PageChunk.of(listOf(1L, 2L, 3L), 3))
            else Result.success(PageChunk.of(listOf(4L), 3))
        }

        loader.loadInitial()
        advanceUntilIdle()

        val state = loader.state.value
        assertEquals(listOf(1L, 2L, 3L), state.items)
        assertEquals(1, state.page)
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun loadMore_appendsAndDedups() = runTest {
        val loader = PagedListLoader<Long>(
            scope = backgroundScope,
            pageSize = 3,
            keySelector = { it }
        ) { page, _ ->
            if (page == 1) Result.success(PageChunk.of(listOf(1L, 2L, 3L), 3))
            else Result.success(PageChunk.of(listOf(3L, 4L), 3))
        }

        loader.loadInitial()
        advanceUntilIdle()
        loader.loadMore()
        advanceUntilIdle()

        val state = loader.state.value
        assertEquals(listOf(1L, 2L, 3L, 4L), state.items)
        assertEquals(2, state.page)
        assertFalse(state.hasMore)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun initialFailure_reportsErrorAndRetryRecovers() = runTest {
        var fail = true
        val loader = PagedListLoader<Long>(
            scope = backgroundScope,
            pageSize = 3,
            keySelector = { it }
        ) { _, _ ->
            if (fail) Result.failure(RuntimeException("boom"))
            else Result.success(PageChunk.of(listOf(9L), 3))
        }

        loader.loadInitial()
        advanceUntilIdle()
        assertEquals("boom", loader.state.value.errorMessage)

        fail = false
        loader.retry()
        advanceUntilIdle()

        val state = loader.state.value
        assertNull(state.errorMessage)
        assertEquals(listOf(9L), state.items)
    }
}
