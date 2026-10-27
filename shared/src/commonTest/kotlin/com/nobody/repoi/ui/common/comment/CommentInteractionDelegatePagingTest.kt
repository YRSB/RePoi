package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentInteractionDelegatePagingTest {

    private data class PagedState(
        override val hotComments: List<CommentItem> = emptyList(),
        override val latestComments: List<CommentItem> = emptyList(),
        override val pendingPraiseIds: Set<Long> = emptySet(),
        override val commentActionMessage: String? = null,
        override val isCommentsLoading: Boolean = false,
        override val commentsPage: Int = 1,
        override val isLoadingMoreComments: Boolean = false,
        override val hasMoreComments: Boolean = true,
        override val commentsError: String? = null
    ) : CommentPagedUiState {
        override fun copyWithComments(
            hotComments: List<CommentItem>,
            latestComments: List<CommentItem>,
            pendingPraiseIds: Set<Long>,
            commentActionMessage: String?
        ): PagedState = copy(
            hotComments = hotComments,
            latestComments = latestComments,
            pendingPraiseIds = pendingPraiseIds,
            commentActionMessage = commentActionMessage
        )

        override fun copyWithPaged(
            hotComments: List<CommentItem>,
            latestComments: List<CommentItem>,
            pendingPraiseIds: Set<Long>,
            commentActionMessage: String?,
            isCommentsLoading: Boolean,
            commentsPage: Int,
            isLoadingMoreComments: Boolean,
            hasMoreComments: Boolean,
            commentsError: String?
        ): CommentPagedUiState = copy(
            hotComments = hotComments,
            latestComments = latestComments,
            pendingPraiseIds = pendingPraiseIds,
            commentActionMessage = commentActionMessage,
            isCommentsLoading = isCommentsLoading,
            commentsPage = commentsPage,
            isLoadingMoreComments = isLoadingMoreComments,
            hasMoreComments = hasMoreComments,
            commentsError = commentsError
        )
    }

    private lateinit var delegate: CommentInteractionDelegate

    private fun comments(range: IntRange): List<CommentItem> {
        return range.map { CommentItem(id = it.toLong(), content = "主楼$it") }
    }

    private fun setUp() {
        delegate = FakeCommentDelegates.pagingOnly()
    }

    @Test
    fun initialFull_setsPageAndHasMore() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState())
        delegate.loadInitialComments(state, 100L, fetch = { _, type, _, _ ->
            if (type == "hot_top") Result.success(emptyList()) else Result.success(comments(1..10))
        })
        assertEquals(10, state.value.latestComments.size)
        assertEquals(1, state.value.commentsPage)
        assertTrue(state.value.hasMoreComments)
        assertFalse(state.value.isCommentsLoading)
        assertNull(state.value.commentsError)
    }

    @Test
    fun initialShort_clearsHasMore() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState())
        delegate.loadInitialComments(state, 100L, fetch = { _, type, _, _ ->
            if (type == "hot_top") Result.success(emptyList()) else Result.success(comments(1..3))
        })
        assertEquals(3, state.value.latestComments.size)
        assertFalse(state.value.hasMoreComments)
    }

    @Test
    fun initialLatestFailure_setsError() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState())
        delegate.loadInitialComments(state, 100L, fetch = { _, type, _, _ ->
            if (type == "hot_top") Result.success(comments(1..2))
            else Result.failure(RuntimeException("boom"))
        })
        assertEquals(listOf(1L, 2L), state.value.hotComments.map { it.id })
        assertTrue(state.value.latestComments.isEmpty())
        assertEquals("boom", state.value.commentsError)
        assertFalse(state.value.isCommentsLoading)
    }

    @Test
    fun initialHotFailure_staysSilent() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState())
        delegate.loadInitialComments(state, 100L, fetch = { _, type, _, _ ->
            if (type == "hot_top") Result.failure(RuntimeException("hot boom"))
            else Result.success(comments(1..10))
        })
        assertTrue(state.value.hotComments.isEmpty())
        assertEquals(10, state.value.latestComments.size)
        assertNull(state.value.commentsError)
    }

    @Test
    fun initialBadNodeId_skipsFetch() = runTest {
        setUp()
        var called = false
        val state = MutableStateFlow(PagedState())
        delegate.loadInitialComments(state, 0L, fetch = { _, _, _, _ ->
            called = true
            Result.success(emptyList())
        })
        assertFalse(called)
        assertFalse(state.value.isCommentsLoading)
    }

    @Test
    fun nextPage_mergesDedupAndAdvances() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState(latestComments = comments(1..10)))
        delegate.loadNextCommentsPage(state, 100L, fetch = { _, _, page, _ ->
            assertEquals(2, page)
            Result.success(comments(9..18))
        })
        assertEquals((1..18).toList(), state.value.latestComments.map { it.id.toInt() })
        assertEquals(2, state.value.commentsPage)
        assertTrue(state.value.hasMoreComments)
        assertFalse(state.value.isLoadingMoreComments)
    }

    @Test
    fun nextPageDuplicate_blocksLoopWithoutGrowth() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState(latestComments = comments(1..10)))
        delegate.loadNextCommentsPage(state, 100L, fetch = { _, _, _, _ ->
            Result.success(comments(1..10))
        })
        assertEquals(10, state.value.latestComments.size)
        assertFalse(state.value.hasMoreComments)
    }

    @Test
    fun nextPageFailure_setsErrorAndStopsLoading() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState(latestComments = comments(1..10)))
        delegate.loadNextCommentsPage(state, 100L, fetch = { _, _, _, _ ->
            Result.failure(RuntimeException("boom"))
        })
        assertFalse(state.value.isLoadingMoreComments)
        assertEquals("boom", state.value.commentsError)
        assertEquals(10, state.value.latestComments.size)
    }

    @Test
    fun initialLatestApiExceptionMinus9007_treatsAsEmptyWithoutError() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState())
        delegate.loadInitialComments(state, 100L, fetch = { _, type, _, _ ->
            if (type == "hot_top") Result.success(emptyList())
            else Result.failure(ApiException(-9007, "找不到该评论，可能已被删除"))
        })
        assertTrue(state.value.latestComments.isEmpty())
        assertNull(state.value.commentsError)
        assertFalse(state.value.isCommentsLoading)
        assertFalse(state.value.hasMoreComments)
    }

    @Test
    fun nextPageApiExceptionMinus9007_clearsHasMoreWithoutError() = runTest {
        setUp()
        val state = MutableStateFlow(PagedState(latestComments = comments(1..10)))
        delegate.loadNextCommentsPage(state, 100L, fetch = { _, _, _, _ ->
            Result.failure(ApiException(-9007, "找不到该评论，可能已被删除"))
        })
        assertFalse(state.value.isLoadingMoreComments)
        assertFalse(state.value.hasMoreComments)
        assertNull(state.value.commentsError)
        assertEquals(10, state.value.latestComments.size)
    }

    @Test
    fun nextPageGuards_skipFetch() = runTest {
        setUp()
        var called = false
        val fetch: CommentPageFetch = { _, _, _, _ ->
            called = true
            Result.success(emptyList())
        }
        delegate.loadNextCommentsPage(MutableStateFlow(PagedState(hasMoreComments = false)), 100L, fetch)
        delegate.loadNextCommentsPage(MutableStateFlow(PagedState(isLoadingMoreComments = true)), 100L, fetch)
        delegate.loadNextCommentsPage(MutableStateFlow(PagedState(isCommentsLoading = true)), 100L, fetch)
        delegate.loadNextCommentsPage(MutableStateFlow(PagedState()), 0L, fetch)
        assertFalse(called)
    }
}
