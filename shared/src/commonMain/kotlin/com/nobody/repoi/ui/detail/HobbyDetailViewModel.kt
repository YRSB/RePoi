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
import com.nobody.repoi.data.repository.CoverFreshnessRepository
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

@Serializable
data class DetailNavParam(
    val itemId: Long,
    val itemType: String = "hobby",
    val initialName: String? = null,
    val initialCover: String? = null
)

data class HobbyDetailUiState(
    val itemId: Long = 0L,
    val itemType: String = "hobby",
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    val detail: HobbyDetailData? = null,

    val isInfoListLoading: Boolean = false,
    val infoList: List<InfoListEntry> = emptyList(),

    val isTimelineLoading: Boolean = false,
    val timeline: List<HobbyTimelineItem> = emptyList(),

    val isOfficialPicsLoading: Boolean = false,
    val officialPics: List<PicItem> = emptyList(),

    val isUserAlbumsLoading: Boolean = false,
    val userAlbums: List<HobbyAlbumItem> = emptyList(),

    val isExHobbyEnabled: Boolean = true,
    val isExHobbyLoading: Boolean = false,
    val exHobbyPics: List<ExHobbyImageItem> = emptyList(),

    val relatedHobbies: List<HobbyItemSummary> = emptyList(),

    override val isCommentsLoading: Boolean = false,
    override val hotComments: List<CommentItem> = emptyList(),
    override val latestComments: List<CommentItem> = emptyList(),
    override val commentsPage: Int = 1,
    override val isLoadingMoreComments: Boolean = false,
    override val hasMoreComments: Boolean = true,
    override val commentsError: String? = null,
    override val pendingPraiseIds: Set<Long> = emptySet(),
    override val commentActionMessage: String? = null,

    val isFavorite: Boolean = false,
    val currentCollectData: CollectData? = null,

    val collectCountDelta: Int = 0,

    val collectCountBase: Long? = null
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
    private val userHistoryRepository: UserHistoryRepository,
    private val coverFreshnessRepository: CoverFreshnessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HobbyDetailUiState())
    val uiState: StateFlow<HobbyDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    private var collectBaselineCollected: Boolean = false

    private var hasCollectNetworkResult: Boolean = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCollectState() {
        viewModelScope.launch {
            uiState
                .map { it.detail?.actualNodeId ?: it.itemId }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id > 0L) {

                        collectRepository.observeCollectState(id).map { id to it }
                    } else {
                        flowOf(id to null)
                    }
                }
                .collect { (nodeId, collectData) ->

                    if (collectData == null && !hasCollectNetworkResult) return@collect
                    _uiState.update { current ->

                        if (currentNodeIdOf(current) != nodeId) return@update current
                        applyCollectState(current, collectData)
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCollectCountSnapshot() {
        viewModelScope.launch {
            uiState
                .map { currentNodeIdOf(it) }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id > 0L) {
                        collectRepository.observeCollectCountSnapshot(id).map { id to it }
                    } else {
                        flowOf(id to null)
                    }
                }
                .collect { (nodeId, snapshot) ->
                    if (snapshot == null || currentNodeIdOf(_uiState.value) != nodeId) return@collect

                    collectBaselineCollected =
                        collectRepository.observeCollectState(nodeId).value?.isCollected == true
                    _uiState.update { current ->
                        if (currentNodeIdOf(current) != nodeId) return@update current
                        current.copy(collectCountBase = snapshot.total, collectCountDelta = 0)
                    }
                }
        }
    }

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

    private fun currentNodeIdOf(state: HobbyDetailUiState): Long =
        state.detail?.actualNodeId ?: state.itemId

    init {

        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)

        observeCollectState()

        observeCollectCountSnapshot()

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

    fun loadDetail(param: DetailNavParam) {
        if (_uiState.value.itemId == param.itemId && !_uiState.value.isInitialLoading && _uiState.value.detail != null) {
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val currentExHobbyEnabled = _uiState.value.isExHobbyEnabled

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

            coverFreshnessRepository.rememberFreshCover(
                id = actualItemId,
                type = CoverFreshnessRepository.EntityType.HOBBY,
                cover = detail.cover
            )

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

    private suspend fun loadCollectState(nodeId: Long) {
        collectRepository.refreshCollectState(nodeId).onSuccess {
            hasCollectNetworkResult = true

            collectBaselineCollected = collectRepository.observeCollectState(nodeId).value?.isCollected == true
            _uiState.update { current ->
                if (currentNodeIdOf(current) != nodeId) return@update current
                applyCollectState(current, collectRepository.observeCollectState(nodeId).value)
            }
        }
    }

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

    private suspend fun loadRelatedHobbies(nodeId: Long, currentItemId: Long) {
        val result = repository.getRelatedHobbies(nodeId, currentItemId)
        _uiState.update {
            it.copy(
                relatedHobbies = result.getOrDefault(emptyList())
            )
        }
    }

    private suspend fun loadInitialComments(nodeId: Long) {
        commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
    }

    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.detail?.actualNodeId ?: state.itemId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.detail?.actualNodeId ?: _uiState.value.itemId
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
        val nodeId = _uiState.value.detail?.actualNodeId ?: _uiState.value.itemId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    fun refreshComments() {
        val nodeId = _uiState.value.detail?.actualNodeId ?: _uiState.value.itemId
        if (nodeId <= 0L) return
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
        }
    }

    fun consumeCommentActionMessage() {
        commentDelegate.consumeCommentActionMessage(_uiState)
    }
}
