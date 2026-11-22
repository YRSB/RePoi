package com.nobody.repoi.ui.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.StickerPackData
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.ui.common.comment.CommentPagedUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class HobbyCommentsNavParam(
    val nodeId: Long,
    val itemType: String = "hobby",
    val title: String = "评论",
    val initialCommentCount: Int = 0
)

data class HobbyCommentsUiState(
    val nodeId: Long = 0L,
    val itemType: String = "hobby",
    val title: String = "评论",
    val totalCommentCount: Int = 0,
    override val isCommentsLoading: Boolean = true,
    override val hotComments: List<CommentItem> = emptyList(),
    override val latestComments: List<CommentItem> = emptyList(),
    override val commentsPage: Int = 1,
    override val isLoadingMoreComments: Boolean = false,
    override val hasMoreComments: Boolean = true,
    override val commentsError: String? = null,
    override val pendingPraiseIds: Set<Long> = emptySet(),
    override val commentActionMessage: String? = null
) : CommentPagedUiState {
    override fun copyWithComments(
        hotComments: List<CommentItem>,
        latestComments: List<CommentItem>,
        pendingPraiseIds: Set<Long>,
        commentActionMessage: String?
    ): HobbyCommentsUiState = copy(
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

class HobbyCommentsViewModel(
    private val repository: HobbyDetailRepository,
    private val commentDelegate: CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(HobbyCommentsUiState())
    val uiState: StateFlow<HobbyCommentsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    fun loadInitial(param: HobbyCommentsNavParam) {
        if (_uiState.value.nodeId == param.nodeId && _uiState.value.latestComments.isNotEmpty()) {
            _uiState.update {
                it.copy(title = param.title, totalCommentCount = param.initialCommentCount)
            }
            return
        }
        _uiState.value = HobbyCommentsUiState(
            nodeId = param.nodeId,
            itemType = param.itemType,
            title = param.title,
            totalCommentCount = param.initialCommentCount
        )
        refresh()
    }

    fun refresh() {
        val nodeId = _uiState.value.nodeId
        if (nodeId <= 0L) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
        }
    }

    fun loadNextPage() {
        val nodeId = _uiState.value.nodeId
        if (nodeId <= 0L) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    fun togglePraise(commentId: Long) {
        commentDelegate.toggleCommentPraise(viewModelScope, _uiState, commentId, _uiState.value.nodeId)
    }

    suspend fun submitReply(
        comment: CommentItem,
        target: CommentReplyItem?,
        content: String,
        stickers: List<String>
    ): Boolean = commentDelegate.submitCommentReply(_uiState, comment.id, target, content, stickers)

    suspend fun submitTopLevel(content: String, stickers: List<String>): Boolean {
        val nodeId = _uiState.value.nodeId
        if (nodeId <= 0L) return false
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refresh()
        return ok
    }

    suspend fun loadStickerPacks(): List<StickerPackData> = commentDelegate.loadStickerPacks()

    val currentUserId: Long get() = commentDelegate.currentUserId
    suspend fun resolveUserId(): Long = commentDelegate.resolveCurrentUserId()

    fun consumeMessage() = commentDelegate.consumeCommentActionMessage(_uiState)

    suspend fun deleteComment(comment: CommentItem): Boolean =
        commentDelegate.deleteMainComment(_uiState, comment.id)

    suspend fun deleteReply(comment: CommentItem, reply: CommentReplyItem): Boolean =
        commentDelegate.deleteSubReply(_uiState, comment.id, reply.id)
}
