package com.nobody.repoi.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.RelatedHobbyItem
import com.nobody.repoi.data.remote.model.SeriesData
import com.nobody.repoi.data.remote.model.SeriesForumPayload
import com.nobody.repoi.data.remote.model.SeriesGoodsItem
import com.nobody.repoi.data.remote.model.SeriesLinkItem
import com.nobody.repoi.data.repository.SeriesDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.ui.collect.collectDeltaOf
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class SeriesNavParam(
    val seriesId: Long,
    val seriesNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)

data class SeriesDetailUiState(
    val seriesId: Long = 0L,
    val seriesNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    val series: SeriesData? = null,

    val isHobbysLoading: Boolean = false,
    val hobbys: List<RelatedHobbyItem> = emptyList(),

    val isSubSeriesLoading: Boolean = false,
    val subSeries: List<SeriesLinkItem> = emptyList(),

    val isRelateSeriesLoading: Boolean = false,
    val relateSeries: List<SeriesLinkItem> = emptyList(),

    val isAlbumsLoading: Boolean = false,
    val albums: List<AlbumData> = emptyList(),

    val isGoodsLoading: Boolean = false,
    val goods: List<SeriesGoodsItem> = emptyList(),

    val isForumLoading: Boolean = false,
    val forum: SeriesForumPayload? = null,

    override val isCommentsLoading: Boolean = false,
    override val hotComments: List<CommentItem> = emptyList(),
    override val latestComments: List<CommentItem> = emptyList(),
    override val commentsPage: Int = 1,
    override val isLoadingMoreComments: Boolean = false,
    override val hasMoreComments: Boolean = true,
    override val commentsError: String? = null,
    override val pendingPraiseIds: Set<Long> = emptySet(),
    override val commentActionMessage: String? = null,

    val isCollected: Boolean = false,
    val originalCollected: Boolean = false,
    val collectCountDelta: Int = 0,

    val collectCountBase: Int? = null,
    val isMasked: Boolean = false,
    val isIntroExpanded: Boolean = false
) : com.nobody.repoi.ui.common.comment.CommentPagedUiState {
    override fun copyWithComments(
        hotComments: List<CommentItem>,
        latestComments: List<CommentItem>,
        pendingPraiseIds: Set<Long>,
        commentActionMessage: String?
    ): SeriesDetailUiState = copy(
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
    ): com.nobody.repoi.ui.common.comment.CommentPagedUiState = copy(
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

class SeriesDetailViewModel(
    private val repository: SeriesDetailRepository,
    private val collectRepository: CollectRepository,
    private val commentDelegate: com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {

        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    fun loadDetail(param: SeriesNavParam) {
        if (_uiState.value.seriesId == param.seriesId && !_uiState.value.isInitialLoading && _uiState.value.series != null) {
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = SeriesDetailUiState(
                seriesId = param.seriesId,
                seriesNodeId = param.seriesNodeId,
                initialName = param.initialName,
                initialCover = param.initialCover,
                isInitialLoading = true
            )

            val detailResult = repository.getSeriesDetail(param.seriesId)
            if (detailResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        errorMessage = detailResult.exceptionOrNull()?.message ?: "系列数据加载失败"
                    )
                }
                return@launch
            }

            val seriesData = detailResult.getOrNull()
            val nodeId = seriesData?.actualNodeId ?: param.seriesNodeId

            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    series = seriesData,
                    seriesNodeId = nodeId,
                    isHobbysLoading = true,
                    isSubSeriesLoading = true,
                    isRelateSeriesLoading = true,
                    isAlbumsLoading = true,
                    isGoodsLoading = true,
                    isForumLoading = true,
                    isCommentsLoading = true
                )
            }

            launch {
                val hobbysResult = repository.getSeriesHobbys(nodeId, pageSize = 9)
                _uiState.update {
                    it.copy(
                        isHobbysLoading = false,
                        hobbys = hobbysResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val subSeriesResult = repository.getSeriesSubSeries(param.seriesId, page = 1, pageSize = 6)
                _uiState.update {
                    it.copy(
                        isSubSeriesLoading = false,
                        subSeries = subSeriesResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val relateSeriesResult = repository.getSeriesRelateSeries(param.seriesId, page = 1, pageSize = 6)
                _uiState.update {
                    it.copy(
                        isRelateSeriesLoading = false,
                        relateSeries = relateSeriesResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val albumsResult = repository.getSeriesAlbums(param.seriesId, page = 1, pageSize = 6)
                _uiState.update {
                    it.copy(
                        isAlbumsLoading = false,
                        albums = albumsResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val goodsResult = repository.getSeriesGoods(nodeId, page = 1, pageSize = 10)
                _uiState.update {
                    it.copy(
                        isGoodsLoading = false,
                        goods = goodsResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val forumResult = repository.getSeriesForum(param.seriesId)
                _uiState.update {
                    it.copy(
                        isForumLoading = false,
                        forum = forumResult.getOrNull()
                    )
                }
            }

            launch {
                val collectDeferred = async { repository.getCollectState(nodeId) }
                val maskDeferred = async { repository.getMaskState(nodeId) }
                val isCollected = collectDeferred.await().getOrDefault(false)
                val isMasked = maskDeferred.await().getOrDefault(false)
                _uiState.update {
                    it.copy(
                        isCollected = isCollected,
                        originalCollected = isCollected,
                        collectCountDelta = 0,
                        isMasked = isMasked
                    )
                }
            }

            launch {
                commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
            }
        }
    }

    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.seriesNodeId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    fun toggleCollect() {
        val state = _uiState.value
        val nodeId = state.seriesNodeId
        if (nodeId <= 0L) return
        val previousCollected = state.isCollected
        val nextCollected = !previousCollected

        _uiState.update {
            it.copy(
                isCollected = nextCollected,
                collectCountDelta = collectDeltaOf(it.originalCollected, nextCollected)
            )
        }

        viewModelScope.launch {
            val result = collectRepository.toggleItemCollect(nodeId, previousCollected)
            if (result.isFailure) {

                _uiState.update {
                    it.copy(
                        isCollected = previousCollected,
                        collectCountDelta = collectDeltaOf(it.originalCollected, previousCollected),
                        commentActionMessage = result.exceptionOrNull()?.message ?: "收藏状态更新失败"
                    )
                }
            }
            else {

                collectRepository.getCollectCount(nodeId).onSuccess { count ->
                    _uiState.update { current ->
                        current.copy(
                            collectCountBase = count.total,
                            collectCountDelta = 0,
                            originalCollected = nextCollected
                        )
                    }
                }
            }
        }
    }

    fun toggleMask() {
        _uiState.update { it.copy(isMasked = !it.isMasked) }
    }

    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.seriesNodeId
        commentDelegate.toggleCommentPraise(viewModelScope, _uiState, commentId, nodeId)
    }

    suspend fun submitCommentReply(
        commentId: Long,
        target: com.nobody.repoi.data.remote.model.CommentReplyItem?,
        content: String,
        stickers: List<String>
    ): Boolean {
        return commentDelegate.submitCommentReply(_uiState, commentId, target, content, stickers)
    }

    suspend fun loadStickerPacks(): List<com.nobody.repoi.data.remote.model.StickerPackData> =
        commentDelegate.loadStickerPacks()

    val currentCommentUserId: Long
        get() = commentDelegate.currentUserId
    suspend fun resolveCommentUserId(): Long = commentDelegate.resolveCurrentUserId()
    suspend fun deleteComment(comment: CommentItem): Boolean = commentDelegate.deleteMainComment(_uiState, comment.id)
    suspend fun deleteReply(comment: CommentItem, reply: CommentReplyItem): Boolean = commentDelegate.deleteSubReply(_uiState, comment.id, reply.id)

    suspend fun submitTopLevelComment(content: String, stickers: List<String>): Boolean {
        val nodeId = _uiState.value.seriesNodeId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    fun refreshComments() {
        val nodeId = _uiState.value.seriesNodeId
        if (nodeId <= 0L) return
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
        }
    }

    fun consumeCommentActionMessage() {
        commentDelegate.consumeCommentActionMessage(_uiState)
    }

    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }
}
