package com.nobody.poishelf.ui.works

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.CommentReplyItem
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.data.remote.model.WorksCharacterItem
import com.nobody.poishelf.data.remote.model.WorksData
import com.nobody.poishelf.data.remote.model.WorksSeriesRef
import com.nobody.poishelf.data.repository.WorksDetailRepository
import com.nobody.poishelf.ui.common.comment.CommentInteractionDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class WorksNavParam(
    val worksId: Long,
    val initialName: String? = null,
    val initialCover: String? = null
)

data class WorksDetailUiState(
    val worksId: Long = 0L,
    val worksNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,
    val works: WorksData? = null,
    val worksSeries: WorksSeriesRef? = null,
    val ratingAvg: Float = 0f,
    val isHobbysLoading: Boolean = false,
    val hobbys: List<HobbyItemSummary> = emptyList(),
    val isCharactersLoading: Boolean = false,
    val characters: List<WorksCharacterItem> = emptyList(),
    val isAlbumsLoading: Boolean = false,
    val albums: List<AlbumData> = emptyList(),
    override val isCommentsLoading: Boolean = false,
    override val hotComments: List<CommentItem> = emptyList(),
    override val latestComments: List<CommentItem> = emptyList(),
    override val commentsPage: Int = 1,
    override val isLoadingMoreComments: Boolean = false,
    override val hasMoreComments: Boolean = true,
    override val commentsError: String? = null,
    override val pendingPraiseIds: Set<Long> = emptySet(),
    override val commentActionMessage: String? = null,
    val isIntroExpanded: Boolean = false
) : com.nobody.poishelf.ui.common.comment.CommentPagedUiState {
    override fun copyWithComments(
        hotComments: List<CommentItem>,
        latestComments: List<CommentItem>,
        pendingPraiseIds: Set<Long>,
        commentActionMessage: String?
    ): WorksDetailUiState = copy(
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
    ): com.nobody.poishelf.ui.common.comment.CommentPagedUiState = copy(
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

class WorksDetailViewModel(
    private val repository: WorksDetailRepository,
    private val commentDelegate: com.nobody.poishelf.ui.common.comment.CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorksDetailUiState())
    val uiState: StateFlow<WorksDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {
        // 合并评论详情页回写的主楼更新（点赞/回复）
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    fun loadDetail(param: WorksNavParam) {
        if (_uiState.value.worksId == param.worksId && !_uiState.value.isInitialLoading && _uiState.value.works != null) {
            return
        }
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = WorksDetailUiState(
                worksId = param.worksId,
                worksNodeId = param.worksId,
                initialName = param.initialName,
                initialCover = param.initialCover,
                isInitialLoading = true
            )
            val detailResult = repository.getWorksDetail(param.worksId)
            if (detailResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        errorMessage = detailResult.exceptionOrNull()?.message ?: "获取作品详情失败"
                    )
                }
                return@launch
            }
            val result = detailResult.getOrThrow()
            val works = result.works
            val nodeId = works?.actualNodeId ?: 0L
            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    works = works,
                    worksSeries = result.worksSeries,
                    worksNodeId = nodeId,
                    isHobbysLoading = true,
                    isCharactersLoading = true,
                    isAlbumsLoading = true,
                    isCommentsLoading = true
                )
            }
            launch {
                val hobbysResult = repository.getWorksHobbys(if (nodeId > 0L) nodeId else param.worksId)
                _uiState.update {
                    it.copy(
                        isHobbysLoading = false,
                        hobbys = hobbysResult.getOrDefault(emptyList())
                    )
                }
            }
            launch {
                val charactersResult = repository.getWorksCharacters(param.worksId)
                _uiState.update {
                    it.copy(
                        isCharactersLoading = false,
                        characters = charactersResult.getOrDefault(emptyList())
                    )
                }
            }
            launch {
                val albumsResult = repository.getWorksAlbums(param.worksId)
                _uiState.update {
                    it.copy(
                        isAlbumsLoading = false,
                        albums = albumsResult.getOrDefault(emptyList())
                    )
                }
            }
            if (nodeId > 0L) {
                launch { loadInitialComments(nodeId) }
            } else {
                _uiState.update { it.copy(isCommentsLoading = false) }
            }
        }
    }

    private suspend fun loadInitialComments(nodeId: Long) {
        commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
    }

    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.worksNodeId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    /**
     * 切换主评论点赞状态（乐观更新，失败回滚）。
     */
    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.worksNodeId
        commentDelegate.toggleCommentPraise(viewModelScope, _uiState, commentId, nodeId)
    }

    /**
     * 发表子评论回复（成功后追加到主楼并广播更新）。
     */
    suspend fun submitCommentReply(
        commentId: Long,
        target: com.nobody.poishelf.data.remote.model.CommentReplyItem?,
        content: String,
        stickers: List<String>
    ): Boolean {
        return commentDelegate.submitCommentReply(_uiState, commentId, target, content, stickers)
    }

    suspend fun loadStickerPacks(): List<com.nobody.poishelf.data.remote.model.StickerPackData> =
        commentDelegate.loadStickerPacks()

    val currentCommentUserId: Long
        get() = commentDelegate.currentUserId
    suspend fun resolveCommentUserId(): Long = commentDelegate.resolveCurrentUserId()
    suspend fun deleteComment(comment: CommentItem): Boolean = commentDelegate.deleteMainComment(_uiState, comment.id)
    suspend fun deleteReply(comment: CommentItem, reply: CommentReplyItem): Boolean = commentDelegate.deleteSubReply(_uiState, comment.id, reply.id)

    /**
     * 发表主楼评论（成功后重拉第一页评论流）。
     */
    suspend fun submitTopLevelComment(content: String, stickers: List<String>): Boolean {
        val nodeId = _uiState.value.worksNodeId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    /**
     * 发表成功后重拉第一页评论流。
     */
    fun refreshComments() {
        val nodeId = _uiState.value.worksNodeId
        if (nodeId <= 0L) return
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
        }
    }

    /**
     * 消费评论互动一次性提示文案。
     */
    fun consumeCommentActionMessage() {
        commentDelegate.consumeCommentActionMessage(_uiState)
    }

    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }
}
