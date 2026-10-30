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
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.data.repository.VendorDetailRepository
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

/**
 * 厂商详情页路由导航参数。
 */
@Serializable
data class VendorNavParam(
    val companyId: Long,
    val companyNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)

/**
 * 厂商详情页渐进式加载 UI 状态机。
 */
data class VendorDetailUiState(
    val companyId: Long = 0L,
    val companyNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    // 核心厂商主体数据
    val company: CompanyData? = null,

    // 厂商手办最新动态
    val isActionsLoading: Boolean = false,
    val actions: List<CompanyActionItem> = emptyList(),

    // 官方与用户品牌印象标签
    val isTagsLoading: Boolean = false,
    val tags: List<CompanyTagItem> = emptyList(),

    // 代表手办产品 (3x3 九宫格推荐)
    val isProductsLoading: Boolean = false,
    val products: List<RelatedHobbyItem> = emptyList(),

    // 旗下子系列
    val isSeriesLoading: Boolean = false,
    val series: List<CompanySeriesItem> = emptyList(),

    // 评论区（热门神评置顶 + 最新无限流）
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
    val isIntroExpanded: Boolean = false,

    // 我的印象弹窗交互状态
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
    private val userRepository: UserRepository,
    private val commentDelegate: com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(VendorDetailUiState())
    val uiState: StateFlow<VendorDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {
        // 合并评论详情页回写的主楼更新（点赞/回复）
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    /**
     * 渐进多阶段加载厂商详情所有数据。
     */
    fun loadDetail(param: VendorNavParam) {
        if (_uiState.value.companyId == param.companyId && !_uiState.value.isInitialLoading && _uiState.value.company != null) {
            return // 已加载过该厂商，保留现有数据
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

            // ── 阶段 1：首屏核心渲染，拉取厂商主体基本信息 ──
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

            // ── 阶段 2：并行发起次级各板块网络请求 ──
            // 2.1 最新动态
            launch {
                val actionsResult = repository.getCompanyActions(nodeId)
                _uiState.update {
                    it.copy(
                        isActionsLoading = false,
                        actions = actionsResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.2 品牌标签
            launch {
                val tagsResult = repository.getCompanyTags(param.companyId)
                _uiState.update {
                    it.copy(
                        isTagsLoading = false,
                        tags = tagsResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.3 代表产品 (3x3 九宫格推荐)
            launch {
                val productsResult = repository.getRelatedHobbies(nodeId, pageSize = 9)
                _uiState.update {
                    it.copy(
                        isProductsLoading = false,
                        products = productsResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.4 旗下系列
            launch {
                val seriesResult = repository.getCompanySeries(nodeId, page = 1, pageSize = 9)
                _uiState.update {
                    it.copy(
                        isSeriesLoading = false,
                        series = seriesResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.5 评论区（热门置顶 + 最新第 1 页）
            launch {
                commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
            }

            // 2.6 查询用户个性化状态 (收藏 & 屏蔽)，未登录时静默降级为 false
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

    /**
     * 翻页追加最新评论流。
     */
    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.companyNodeId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    /**
     * 切换收藏厂商状态与计数。
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
        val nodeId = _uiState.value.companyNodeId
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
        val nodeId = _uiState.value.companyNodeId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    /**
     * 发表成功后重拉第一页评论流（与首屏加载同一口径）。
     */
    fun refreshComments() {
        val nodeId = _uiState.value.companyNodeId
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
     * 展开/折叠厂商简介全文。
     */
    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }

    val isLoggedIn: Boolean get() = userRepository.isLoggedIn

    /**
     * 打开“我的印象”打分与标签弹窗。
     * 若未登录，则回调 onNeedLogin() 并在外部引导跳转登录。
     */
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

                // 区分评分项与普通标签项的预选状态
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

    /**
     * 关闭“我的印象”弹窗。
     */
    fun dismissImpressionSheet() {
        _uiState.update { it.copy(isImpressionSheetVisible = false) }
    }

    /**
     * 切换普通印象标签选中状态。
     */
    fun toggleNormalTag(tagId: Long) {
        _uiState.update {
            val current = it.selectedNormalTagIds
            val updated = if (current.contains(tagId)) current - tagId else current + tagId
            it.copy(selectedNormalTagIds = updated)
        }
    }

    /**
     * 设置星级打分 (1.0 ~ 5.0，若重复点击相同分值则支持取消打分)。
     */
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

    /**
     * 提交当前用户的印象投票。
     */
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
                // 实时刷新详情页上的展示标签
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

    /**
     * 消费印象操作提示文案。
     */
    fun consumeImpressionMessage() {
        _uiState.update { it.copy(impressionMessage = null) }
    }
}
