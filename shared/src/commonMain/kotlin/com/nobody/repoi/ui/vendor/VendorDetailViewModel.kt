package com.nobody.repoi.ui.vendor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CompanyActionItem
import com.nobody.repoi.data.remote.model.CompanyData
import com.nobody.repoi.data.remote.model.CompanySeriesItem
import com.nobody.repoi.data.remote.model.CompanyTagItem
import com.nobody.repoi.data.remote.model.CompanyTagVoteItem
import com.nobody.repoi.data.remote.model.RelatedHobbyItem
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.data.repository.VendorDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.ui.collect.collectDeltaOf
import com.nobody.repoi.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.nobody.repoi.data.remote.model.ActionItem

@Serializable
data class VendorNavParam(
    val companyId: Long,
    val companyNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)

data class VendorDetailUiState(
    val companyId: Long = 0L,
    val companyNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    val company: CompanyData? = null,

    val isActionsLoading: Boolean = false,
    val actions: List<CompanyActionItem> = emptyList(),

    val isTagsLoading: Boolean = false,
    val tags: List<CompanyTagItem> = emptyList(),

    val isProductsLoading: Boolean = false,
    val products: List<RelatedHobbyItem> = emptyList(),

    val isSeriesLoading: Boolean = false,
    val series: List<CompanySeriesItem> = emptyList(),

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

    val collectCountBase: Long? = null,
    val isMasked: Boolean = false,
    val isIntroExpanded: Boolean = false,

    val isImpressionSheetVisible: Boolean = false,
    val isImpressionLoading: Boolean = false,
    val isImpressionSubmitting: Boolean = false,
    val impressionAllTags: List<CompanyTagVoteItem> = emptyList(),
    val selectedNormalTagIds: Set<Long> = emptySet(),
    val scoreVotes: Map<Long, Double> = emptyMap(),
    val impressionMessage: String? = null
) : com.nobody.repoi.ui.common.comment.CommentPagedUiState {
    override fun copyWithComments(
        hotComments: List<CommentItem>,
        latestComments: List<CommentItem>,
        pendingPraiseIds: Set<Long>,
        commentActionMessage: String?
    ): VendorDetailUiState = copy(
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

class VendorDetailViewModel(
    private val repository: VendorDetailRepository,
    private val userSession: UserSession,
    private val collectRepository: CollectRepository,
    private val commentDelegate: com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(VendorDetailUiState())
    val uiState: StateFlow<VendorDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {

        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    fun loadDetail(param: VendorNavParam) {
        if (_uiState.value.companyId == param.companyId && !_uiState.value.isInitialLoading && _uiState.value.company != null) {
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = VendorDetailUiState(
                companyId = param.companyId,
                companyNodeId = param.companyNodeId,
                initialName = param.initialName,
                initialCover = param.initialCover,
                isInitialLoading = true,
                errorMessage = null
            )

            AppLogger.i("厂商", "开始加载 companyId=${param.companyId} companyNodeId=${param.companyNodeId}")
            val detailResult = repository.getCompanyDetail(param.companyId)
            if (detailResult.isFailure) {
                AppLogger.w("厂商", "主体加载失败 companyId=${param.companyId} 原因=${detailResult.exceptionOrNull()?.message}")
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        errorMessage = detailResult.exceptionOrNull()?.message ?: "获取厂商信息失败"
                    )
                }
                return@launch
            }

            val company = detailResult.getOrThrow()
            val nodeId = if (company.id > 0) company.id else param.companyNodeId

            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    company = company,
                    companyNodeId = nodeId,
                    isActionsLoading = true,
                    isTagsLoading = true,
                    isProductsLoading = true,
                    isSeriesLoading = true,
                    isCommentsLoading = true
                )
            }

            launch {
                val actionsResult = repository.getCompanyActions(nodeId)
                _uiState.update {
                    it.copy(
                        isActionsLoading = false,
                        actions = actionsResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val tagsResult = repository.getCompanyTags(param.companyId)
                _uiState.update {
                    it.copy(
                        isTagsLoading = false,
                        tags = tagsResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val productsResult = repository.getRelatedHobbies(nodeId, pageSize = 9)
                _uiState.update {
                    it.copy(
                        isProductsLoading = false,
                        products = productsResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val seriesResult = repository.getCompanySeries(nodeId, page = 1, pageSize = 9)
                _uiState.update {
                    it.copy(
                        isSeriesLoading = false,
                        series = seriesResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
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
                        isMasked = isMasked,
                        collectCountDelta = 0
                    )
                }
            }
        }
    }

    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.companyNodeId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    fun toggleCollect() {
        val state = _uiState.value
        val nodeId = state.companyNodeId
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
                            collectCountBase = count.total.toLong(),
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
        val nodeId = _uiState.value.companyNodeId
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
        val nodeId = _uiState.value.companyNodeId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    fun refreshComments() {
        val nodeId = _uiState.value.companyNodeId
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

    val isLoggedIn: Boolean get() = userSession.isAuthenticated

    fun openImpressionSheet(onNeedLogin: () -> Unit) {
        if (!isLoggedIn) {
            onNeedLogin()
            return
        }
        val compId = _uiState.value.companyId
        if (compId <= 0L) return

        _uiState.update {
            it.copy(
                isImpressionSheetVisible = true,
                isImpressionLoading = true
            )
        }

        viewModelScope.launch {
            val myVotesResult = repository.getCompanyMyVotes(compId)
            if (myVotesResult.isSuccess) {
                val data = myVotesResult.getOrThrow()
                val scoreMap = mutableMapOf<Long, Double>()
                val normalSet = mutableSetOf<Long>()

                data.userVotes.forEach { uv ->
                    val isScore = data.allTags.firstOrNull { it.id == uv.tagId }?.isScoreTag == true
                    if (isScore && uv.score != null) {
                        scoreMap[uv.tagId] = uv.score
                    } else if (!isScore) {
                        normalSet.add(uv.tagId)
                    }
                }

                _uiState.update {
                    it.copy(
                        isImpressionLoading = false,
                        impressionAllTags = data.allTags,
                        selectedNormalTagIds = normalSet,
                        scoreVotes = scoreMap
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isImpressionLoading = false,
                        isImpressionSheetVisible = false,
                        impressionMessage = myVotesResult.exceptionOrNull()?.message ?: "获取印象数据失败"
                    )
                }
            }
        }
    }

    fun dismissImpressionSheet() {
        _uiState.update { it.copy(isImpressionSheetVisible = false) }
    }

    fun toggleNormalTag(tagId: Long) {
        _uiState.update {
            val current = it.selectedNormalTagIds
            val updated = if (current.contains(tagId)) current - tagId else current + tagId
            it.copy(selectedNormalTagIds = updated)
        }
    }

    fun setScoreVote(tagId: Long, score: Double) {
        _uiState.update {
            val currentScore = it.scoreVotes[tagId]
            val updated = it.scoreVotes.toMutableMap()
            if (currentScore == score) {
                updated.remove(tagId)
            } else {
                updated[tagId] = score
            }
            it.copy(scoreVotes = updated)
        }
    }

    fun submitImpression() {
        val state = _uiState.value
        val compId = state.companyId
        if (compId <= 0L || state.isImpressionSubmitting) return

        _uiState.update { it.copy(isImpressionSubmitting = true) }

        viewModelScope.launch {
            val result = repository.submitCompanyTagVote(
                companyId = compId,
                normalTagIds = state.selectedNormalTagIds.toList(),
                scoreVotes = state.scoreVotes
            )

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isImpressionSubmitting = false,
                        isImpressionSheetVisible = false,
                        impressionMessage = "印象提交成功"
                    )
                }

                val tagsResult = repository.getCompanyTags(compId)
                if (tagsResult.isSuccess) {
                    _uiState.update { it.copy(tags = tagsResult.getOrDefault(emptyList())) }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isImpressionSubmitting = false,
                        impressionMessage = result.exceptionOrNull()?.message ?: "提交印象失败"
                    )
                }
            }
        }
    }

    fun consumeImpressionMessage() {
        _uiState.update { it.copy(impressionMessage = null) }
    }
}
