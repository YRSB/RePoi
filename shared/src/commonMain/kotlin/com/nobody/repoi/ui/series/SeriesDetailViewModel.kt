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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 系列详情页渐进式加载 UI 状态机。
 */
data class SeriesDetailUiState(
    val seriesId: Long = 0L,
    val seriesNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    // 核心系列主体数据
    val series: SeriesData? = null,

    // 关联周边手办 (3x3 纯图九宫格)
    val isHobbysLoading: Boolean = false,
    val hobbys: List<RelatedHobbyItem> = emptyList(),

    // 旗下子系列 (isChild=1)
    val isSubSeriesLoading: Boolean = false,
    val subSeries: List<SeriesLinkItem> = emptyList(),

    // 相关系列 (isChild=2)
    val isRelateSeriesLoading: Boolean = false,
    val relateSeries: List<SeriesLinkItem> = emptyList(),

    // 关联相册画廊
    val isAlbumsLoading: Boolean = false,
    val albums: List<AlbumData> = emptyList(),

    // 在售周边商品 (淘宝推荐)
    val isGoodsLoading: Boolean = false,
    val goods: List<SeriesGoodsItem> = emptyList(),

    // 社区论坛与帖子
    val isForumLoading: Boolean = false,
    val forum: SeriesForumPayload? = null,

    // 评论区（热门神评置顶 + 最新时序流）
    override val isCommentsLoading: Boolean = false,
    override val hotComments: List<CommentItem> = emptyList(),
    override val latestComments: List<CommentItem> = emptyList(),
    override val commentsPage: Int = 1,
    override val isLoadingMoreComments: Boolean = false,
    override val hasMoreComments: Boolean = true,
    override val commentsError: String? = null,
    override val pendingPraiseIds: Set<Long> = emptySet(),
    override val commentActionMessage: String? = null,

    // 用户交互状态
    val isCollected: Boolean = false,
    val originalCollected: Boolean = false,
    val collectCountDelta: Int = 0,
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

class SeriesDetailViewModel constructor(
    private val repository: SeriesDetailRepository,
    private val commentDelegate: com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {
        // 合并评论详情页回写的主楼更新（点赞/回复）
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    /**
     * 渐进多阶段加载系列详情所有数据。
     */
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

            // ══════════════════════════════════════════════════════════
            // 阶段 1：首屏核心主体加载 (api/item/get-v2?itemType=series)
            // ══════════════════════════════════════════════════════════
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

            // ══════════════════════════════════════════════════════════
            // 阶段 2：次级板块并行拉取（周边、子系列、相关系列、相册、在售、论坛、评论）
            // ══════════════════════════════════════════════════════════

            // 2.1 周边手办 3x3 九宫格 (api/item/relate/hobby)
            launch {
                val hobbysResult = repository.getSeriesHobbys(nodeId, pageSize = 9)
                _uiState.update {
                    it.copy(
                        isHobbysLoading = false,
                        hobbys = hobbysResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.2 子系列列表 (api/series/link/series?isChild=1)
            launch {
                val subSeriesResult = repository.getSeriesSubSeries(param.seriesId, page = 1, pageSize = 6)
                _uiState.update {
                    it.copy(
                        isSubSeriesLoading = false,
                        subSeries = subSeriesResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.3 相关系列列表 (api/series/link/series?isChild=2)
            launch {
                val relateSeriesResult = repository.getSeriesRelateSeries(param.seriesId, page = 1, pageSize = 6)
                _uiState.update {
                    it.copy(
                        isRelateSeriesLoading = false,
                        relateSeries = relateSeriesResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.4 系列相册列表 (api/series/album)
            launch {
                val albumsResult = repository.getSeriesAlbums(param.seriesId, page = 1, pageSize = 6)
                _uiState.update {
                    it.copy(
                        isAlbumsLoading = false,
                        albums = albumsResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.5 在售周边商品 (api/goods/tb/more-v2)
            launch {
                val goodsResult = repository.getSeriesGoods(nodeId, page = 1, pageSize = 10)
                _uiState.update {
                    it.copy(
                        isGoodsLoading = false,
                        goods = goodsResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.6 社区论坛与讨论帖 (api/series/forum)
            launch {
                val forumResult = repository.getSeriesForum(param.seriesId)
                _uiState.update {
                    it.copy(
                        isForumLoading = false,
                        forum = forumResult.getOrNull()
                    )
                }
            }

            // 2.7 收藏与屏蔽状态查询
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

            // 2.8 评论区（热评置顶 + 普通评论时序流）
            launch {
                commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
            }
        }
    }

    /**
     * 翻页追加最新评论流。
     */
    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.seriesNodeId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    /**
     * 切换收藏状态。
     */
    fun toggleCollect() {
        _uiState.update {
            val nextCollected = !it.isCollected
            val delta = when {
                it.originalCollected && !nextCollected -> -1
                !it.originalCollected && nextCollected -> 1
                else -> 0
            }
            it.copy(isCollected = nextCollected, collectCountDelta = delta)
        }
    }

    /**
     * 切换屏蔽状态。
     */
    fun toggleMask() {
        _uiState.update { it.copy(isMasked = !it.isMasked) }
    }

    /**
     * 切换主评论点赞状态（乐观更新，失败回滚）。
     */
    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.seriesNodeId
        commentDelegate.toggleCommentPraise(viewModelScope, _uiState, commentId, nodeId)
    }

    /**
     * 发表子评论回复（成功后追加到主楼并广播更新）。
     */
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

    /**
     * 发表主楼评论（成功后重拉第一页评论流）。
     */
    suspend fun submitTopLevelComment(content: String, stickers: List<String>): Boolean {
        val nodeId = _uiState.value.seriesNodeId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    /**
     * 发表成功后重拉第一页评论流（与首屏加载同一口径）。
     */
    fun refreshComments() {
        val nodeId = _uiState.value.seriesNodeId
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

    /**
     * 展开 / 折叠系列简介。
     */
    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }
}
