package com.nobody.repoi.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.data.remote.model.HobbyAlbumItem
import com.nobody.repoi.data.remote.model.HobbyDetailData
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.HobbyTimelineItem
import com.nobody.repoi.data.remote.model.InfoListEntry
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 手办详情页路由导航参数。
 */
@Serializable
data class DetailNavParam(
    val itemId: Long,
    val itemType: String = "hobby",
    val initialName: String? = null,
    val initialCover: String? = null
)

/**
 * 手办详情页渐进式加载 UI 状态机。
 */
data class HobbyDetailUiState(
    val itemId: Long = 0L,
    val itemType: String = "hobby",
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    // 核心条目详情
    val detail: HobbyDetailData? = null,

    // 结构化属性参数列表
    val isInfoListLoading: Boolean = false,
    val infoList: List<InfoListEntry> = emptyList(),

    // 发售情报时间轴
    val isTimelineLoading: Boolean = false,
    val timeline: List<HobbyTimelineItem> = emptyList(),

    // 官方宣发定妆照
    val isOfficialPicsLoading: Boolean = false,
    val officialPics: List<PicItem> = emptyList(),

    // 玩家摄影相册
    val isUserAlbumsLoading: Boolean = false,
    val userAlbums: List<HobbyAlbumItem> = emptyList(),

    // ExHobby 扩展图库
    val isExHobbyEnabled: Boolean = true,
    val isExHobbyLoading: Boolean = false,
    val exHobbyPics: List<ExHobbyImageItem> = emptyList(),

    // 相关推荐手办（同款/再版 + 推荐）
    val relatedHobbies: List<HobbyItemSummary> = emptyList(),

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

    // 互动状态（收藏爱心与详情）
    val isFavorite: Boolean = false,
    val currentCollectData: CollectData? = null,
    val collectCountDelta: Int = 0
) : com.nobody.repoi.ui.common.comment.CommentPagedUiState {
    override fun copyWithComments(
        hotComments: List<CommentItem>,
        latestComments: List<CommentItem>,
        pendingPraiseIds: Set<Long>,
        commentActionMessage: String?
    ): HobbyDetailUiState = copy(
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

    /** 提取制作厂商条目的 companyId */
    val makerCompanyId: Long
        get() {
            val entry = infoList.firstOrNull { it.displayKey == "制作" || it.link?.startsWith("company/") == true }
            return entry?.link?.removePrefix("company/")?.toLongOrNull() ?: 0L
        }

    val makerName: String?
        get() = infoList.firstOrNull { it.displayKey == "制作" || it.link?.startsWith("company/") == true }
            ?.displayValue?.ifBlank { null }

    val makerCover: String?
        get() = infoList.firstOrNull { it.displayKey == "制作" || it.link?.startsWith("company/") == true }
            ?.cover?.ifBlank { null }
}

class HobbyDetailViewModel(
    private val repository: HobbyDetailRepository,
    private val collectRepository: CollectRepository,
    private val commentDelegate: com.nobody.repoi.ui.common.comment.CommentInteractionDelegate,
    private val exHobbyRepository: ExHobbyRepository,
    private val viewPreferencesManager: ViewPreferencesManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HobbyDetailUiState())
    val uiState: StateFlow<HobbyDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {
        // 合并评论详情页回写的主楼更新（点赞/回复）
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
        // 监听全局收藏状态变更广播，实时原子更新详情页的爱心高亮与计数值
        viewModelScope.launch {
            collectRepository.collectUpdates.collect { (nodeId, collectData) ->
                _uiState.update { current ->
                    val currentNodeId = current.detail?.actualNodeId ?: current.itemId
                    if (currentNodeId != nodeId) return@update current
                    val isCollected = collectData != null && collectData.isCollected
                    val deltaChange = when {
                        isCollected && !current.isFavorite -> 1
                        !isCollected && current.isFavorite -> -1
                        else -> 0
                    }
                    current.copy(
                        isFavorite = isCollected,
                        currentCollectData = collectData,
                        collectCountDelta = current.collectCountDelta + deltaChange
                    )
                }
            }
        }
        // 监听全局 ExHobby 扩展图库开关，动态同步状态并按需触发补录
        viewModelScope.launch {
            viewPreferencesManager.isExHobbyEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(isExHobbyEnabled = enabled) }
                if (enabled) {
                    val currentDetail = _uiState.value.detail
                    if (currentDetail != null && _uiState.value.exHobbyPics.isEmpty() && !_uiState.value.isExHobbyLoading) {
                        val targetExHobbyId = if (currentDetail.actualNodeId > 0L) currentDetail.actualNodeId else _uiState.value.itemId
                        loadExHobby(targetExHobbyId)
                    }
                }
            }
        }
    }

    /**
     * 渐进多阶段加载手办详情。
     */
    fun loadDetail(param: DetailNavParam) {
        if (_uiState.value.itemId == param.itemId && !_uiState.value.isInitialLoading && _uiState.value.detail != null) {
            return // 已加载相同条目，避免重复刷新
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val currentExHobbyEnabled = _uiState.value.isExHobbyEnabled
            _uiState.value = HobbyDetailUiState(
                itemId = param.itemId,
                itemType = param.itemType,
                initialName = param.initialName,
                initialCover = param.initialCover,
                isInitialLoading = true,
                isExHobbyEnabled = currentExHobbyEnabled,
                errorMessage = null
            )

            // ── 阶段 1：拉取核心条目基本信息 ──
            val detailResult = repository.getHobbyDetail(param.itemId, param.itemType)
            if (detailResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        errorMessage = detailResult.exceptionOrNull()?.message ?: "获取手办详情失败"
                    )
                }
                return@launch
            }

            val detail = detailResult.getOrThrow()
            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    detail = detail
                )
            }

            val nodeId = detail.actualNodeId
            val actualItemId = if (detail.actualItemId > 0L) detail.actualItemId else param.itemId
            val actualItemType = detail.itemType?.ifBlank { null } ?: param.itemType

            // ── 阶段 2：并发拉取规格属性、发售情报、官方图集、玩家相册、扩展图库、首批评论与当前用户收藏状态 ──
            if (nodeId > 0) {
                launch { loadInfoList(nodeId, actualItemType) }
                launch { loadTimeline(nodeId) }
                launch { loadUserAlbums(nodeId, actualItemType) }
                launch { loadInitialComments(nodeId) }
                launch { loadCollectState(nodeId) }
                launch { loadRelatedHobbies(nodeId, actualItemId) }
                launch { recordHistory(nodeId) }
            }
            launch { loadOfficialPics(actualItemId, actualItemType) }
            if (_uiState.value.isExHobbyEnabled) {
                val targetExHobbyId = if (nodeId > 0L) nodeId else actualItemId
                launch { loadExHobby(targetExHobbyId) }
            }
        }
    }

    private suspend fun recordHistory(nodeId: Long) {
        runCatching {
            userRepository.addUserHistory(nodeId)
        }
    }

    /**
     * 加载 ExHobby 扩展图库（首屏静默加载第 1 页，失败静默忽略）。
     */
    private suspend fun loadExHobby(id: Long) {
        if (!_uiState.value.isExHobbyEnabled) return
        _uiState.update { it.copy(isExHobbyLoading = true) }
        val result = exHobbyRepository.getPictures(id = id, page = 1)
        val pics = result?.getOrNull() ?: emptyList()
        _uiState.update {
            it.copy(
                isExHobbyLoading = false,
                exHobbyPics = pics
            )
        }
    }

    /**
     * 拉取当前条目的用户收藏状态。
     */
    private suspend fun loadCollectState(nodeId: Long) {
        collectRepository.getCollectState(nodeId).onSuccess { collectData ->
            _uiState.update {
                it.copy(
                    isFavorite = collectData?.isCollected == true,
                    currentCollectData = collectData
                )
            }
        }
    }

    /**
     * 加载条目属性详情矩阵。
     */
    private suspend fun loadInfoList(nodeId: Long, itemType: String) {
        _uiState.update { it.copy(isInfoListLoading = true) }
        val result = repository.getInfoList(nodeId, itemType)
        _uiState.update {
            it.copy(
                isInfoListLoading = false,
                infoList = result.getOrDefault(emptyList())
            )
        }
    }

    /**
     * 加载发售情报时间轴数据。
     */
    private suspend fun loadTimeline(nodeId: Long) {
        _uiState.update { it.copy(isTimelineLoading = true) }
        val result = repository.getTimeline(nodeId = nodeId, page = 1, pageSize = 20)
        _uiState.update { current ->
            current.copy(
                isTimelineLoading = false,
                timeline = result.getOrDefault(emptyList())
            )
        }
    }

    /**
     * 加载官方定妆照。
     */
    private suspend fun loadOfficialPics(itemId: Long, itemType: String) {
        _uiState.update { it.copy(isOfficialPicsLoading = true) }
        val result = repository.getOfficialPics(itemId, itemType)
        _uiState.update {
            it.copy(
                isOfficialPicsLoading = false,
                officialPics = result.getOrDefault(emptyList())
            )
        }
    }

    /**
     * 加载玩家摄影相册。
     */
    private suspend fun loadUserAlbums(nodeId: Long, itemType: String) {
        _uiState.update { it.copy(isUserAlbumsLoading = true) }
        val result = repository.getUserAlbums(nodeId, itemType)
        _uiState.update {
            it.copy(
                isUserAlbumsLoading = false,
                userAlbums = result.getOrDefault(emptyList())
            )
        }
    }

    /**
     * 加载相关手办推荐列表。
     */
    private suspend fun loadRelatedHobbies(nodeId: Long, currentItemId: Long) {
        val result = repository.getRelatedHobbies(nodeId, currentItemId)
        _uiState.update {
            it.copy(
                relatedHobbies = result.getOrDefault(emptyList())
            )
        }
    }

    /**
     * 首次拉取评论流：同时获取热门神评与最新评论第一页。
     */
    private suspend fun loadInitialComments(nodeId: Long) {
        commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
    }

    /**
     * 向上滑动触底时自动拉取更多最新评论。
     */
    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.detail?.actualNodeId ?: state.itemId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    /**
     * 切换主评论点赞状态（乐观更新，失败回滚）。
     */
    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.detail?.actualNodeId ?: _uiState.value.itemId
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
        val nodeId = _uiState.value.detail?.actualNodeId ?: _uiState.value.itemId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    /**
     * 发表成功后重拉第一页评论流。
     */
    fun refreshComments() {
        val nodeId = _uiState.value.detail?.actualNodeId ?: _uiState.value.itemId
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
}
