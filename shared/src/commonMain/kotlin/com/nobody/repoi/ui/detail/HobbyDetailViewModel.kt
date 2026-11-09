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
import com.nobody.repoi.ui.collect.collectDeltaOf
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.UserHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    /** 收藏数增量：语义为「当前值 - 最近一次网络基线」，取值仅 -1/0/1，非累加计数 */
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
    private val userHistoryRepository: UserHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HobbyDetailUiState())
    val uiState: StateFlow<HobbyDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    /** 当前条目最近一次网络收藏结果（「基线 + delta」里的基线，只由本页主动拉取写入） */
    private var collectBaselineCollected: Boolean = false

    /** 当前条目是否已拿到过一次网络收藏结果；未拿到前状态池里的 `null` 不代表「未收藏」 */
    private var hasCollectNetworkResult: Boolean = false

    /**
     * 状态驱动订阅当前条目的收藏状态流。
     *
     * 要点：
     * - 节点 id 保留回退链 `detail.actualNodeId ?: itemId`（`itemId` 是 Nav 参数，`actualNodeId` 才是收藏节点）；
     * - `refreshCollectState` 是唯一拉取入口，拉到结果后由 [CollectRepository] 把值推入状态池，
     *   本订阅只负责把池内值映射到 UI 状态；
     * - 首次 `null` 不能被当成「未收藏」：本页拿到首个网络结果之前一律忽略状态池的 `null`，
     *   避免爱心闪回未收藏；拿到过结果后的 `null`（取消收藏）则正常生效；
     * - `collectCountDelta` 采用「基线 + delta」语义：基线是状态池最近一次网络结果驱动出的
     *   `isFavorite`，delta 恒为 `当前值 - 基线值`（±1 / 0），不再逐次累加，重复发射也不会重复计数。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCollectState() {
        viewModelScope.launch {
            uiState
                .map { it.detail?.actualNodeId ?: it.itemId }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id > 0L) {
                        // 带上 id：切条目后迟到的旧发射会被下面的 id 比对丢弃
                        collectRepository.observeCollectState(id).map { id to it }
                    } else {
                        flowOf(id to null)
                    }
                }
                .collect { (nodeId, collectData) ->
                    // 首个网络结果之前的 null 只是状态池默认值，不代表「未收藏」
                    if (collectData == null && !hasCollectNetworkResult) return@collect
                    _uiState.update { current ->
                        // 切条目后迟到的旧发射直接丢弃
                        if (currentNodeIdOf(current) != nodeId) return@update current
                        applyCollectState(current, collectData)
                    }
                }
        }
    }

    /** 把状态池值映射进 UI 状态：爱心与计数只看状态池，不做任何本地推断。 */
    private fun applyCollectState(
        current: HobbyDetailUiState,
        collectData: CollectData?
    ): HobbyDetailUiState {
        val isCollected = collectData != null && collectData.isCollected
        return current.copy(
            isFavorite = isCollected,
            currentCollectData = collectData,
            collectCountDelta = collectDeltaOf(collectBaselineCollected, isCollected)
        )
    }

    /** 当前条目收藏节点 id（回退链：详情实际节点 → Nav 参数 itemId）。 */
    private fun currentNodeIdOf(state: HobbyDetailUiState): Long =
        state.detail?.actualNodeId ?: state.itemId



    init {
        // 合并评论详情页回写的主楼更新（点赞/回复）
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
        // 状态驱动：订阅当前条目的收藏状态流，原子更新爱心高亮与计数值
        observeCollectState()
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
            // 换条目即重置收藏基线与就绪标记，避免把上一条目的结果当成新条目的基线
            collectBaselineCollected = false
            hasCollectNetworkResult = false
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
            userHistoryRepository.addUserHistory(nodeId)
        }
    }

    /**
     * 加载 ExHobby 扩展图库（首屏静默加载第 1 页，失败静默忽略）。
     */
    private suspend fun loadExHobby(id: Long) {
        if (!_uiState.value.isExHobbyEnabled) return
        _uiState.update { it.copy(isExHobbyLoading = true) }
        val result = exHobbyRepository.getPictures(id = id, page = 1)
        val pics = result.getOrNull() ?: emptyList()
        _uiState.update {
            it.copy(
                isExHobbyLoading = false,
                exHobbyPics = pics
            )
        }
    }

    /**
     * 主动拉取当前条目的用户收藏状态。
     *
     * 只负责触发网络拉取：成功后由 [CollectRepository] 把结果写入状态池，
     * 再经 [observeCollectState] 订阅回填 UI（单一数据流，不再在此处二次写入）。
     * 失败保持静默——既不清空 `isFavorite` 也不写基线，避免把「网络异常」误报成「未收藏」。
     */
    private suspend fun loadCollectState(nodeId: Long) {
        collectRepository.refreshCollectState(nodeId).onSuccess {
            hasCollectNetworkResult = true
            // 基线取自本次网络结果；随后按状态池当前值回填一次，保证「已确认未收藏」能显式生效
            collectBaselineCollected = collectRepository.observeCollectState(nodeId).value?.isCollected == true
            _uiState.update { current ->
                if (currentNodeIdOf(current) != nodeId) return@update current
                applyCollectState(current, collectRepository.observeCollectState(nodeId).value)
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
