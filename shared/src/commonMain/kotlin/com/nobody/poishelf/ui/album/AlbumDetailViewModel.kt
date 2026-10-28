package com.nobody.poishelf.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.AlbumData
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.CommentReplyItem
import com.nobody.poishelf.data.remote.model.ExHobbyImageItem
import com.nobody.poishelf.data.remote.model.PicItem
import com.nobody.poishelf.data.remote.model.RelatedAlbumItem
import com.nobody.poishelf.data.remote.model.RelatedHobbyItem
import com.nobody.poishelf.data.repository.AlbumDetailRepository
import com.nobody.poishelf.data.local.ViewPreferencesManager
import com.nobody.poishelf.ui.common.comment.CommentInteractionDelegate
import com.nobody.poishelf.data.repository.CollectRepository
import com.nobody.poishelf.data.repository.CommentRepository
import com.nobody.poishelf.data.repository.ExHobbyRepository
import com.nobody.poishelf.data.repository.UserRepository
import com.nobody.poishelf.util.ListPaging
import com.nobody.poishelf.data.repository.FriendState
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 相册详情路由导航参数。
 */
@Serializable
data class AlbumNavParam(
    val albumId: Long,
    val initialTitle: String? = null,
    val initialCover: String? = null
)

/**
 * 相册详情页四阶段状态机模型。
 */
data class AlbumDetailUiState(
    val albumId: Long = 0L,
    val initialTitle: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    // 阶段 1：相册核心主信息与分类标签
    val album: AlbumData? = null,
    val tags: List<String> = emptyList(),

    // 阶段 2：高清原图长图流
    val pictures: List<PicItem> = emptyList(),
    val isPicturesLoading: Boolean = false,
    val isLoadingMorePictures: Boolean = false,
    val hasMorePictures: Boolean = true,
    val picturesPage: Int = 1,

    // 阶段 3：关联手办与同款相册
    val isRelatedLoading: Boolean = false,
    val relatedHobbies: List<RelatedHobbyItem> = emptyList(),
    val relatedAlbums: List<RelatedAlbumItem> = emptyList(),

    // ExHobby 扩展图库
    val isExHobbyEnabled: Boolean = true,
    val isExHobbyLoading: Boolean = false,
    val exHobbyPics: List<ExHobbyImageItem> = emptyList(),

    // 阶段 4：评论区（热门神评置顶 + 最新时序流）
    override val isCommentsLoading: Boolean = false,
    override val hotComments: List<CommentItem> = emptyList(),
    override val latestComments: List<CommentItem> = emptyList(),
    override val commentsPage: Int = 1,
    override val isLoadingMoreComments: Boolean = false,
    override val hasMoreComments: Boolean = true,
    override val commentsError: String? = null,
    override val pendingPraiseIds: Set<Long> = emptySet(),
    override val commentActionMessage: String? = null,

    // 互动操作状态
    val isLiked: Boolean = false,
    val isCollected: Boolean = false,
    val followState: FriendState = FriendState.UNKNOWN,
    val isAuthor: Boolean = false,
    val isPraisePending: Boolean = false,
    val isCollectPending: Boolean = false,
    val isFollowPending: Boolean = false,
    val praiseCountDelta: Long = 0L,
    val collectCountDelta: Long = 0L,
    val interactionActionMessage: String? = null
) : com.nobody.poishelf.ui.common.comment.CommentPagedUiState {
    val isFollowed: Boolean get() = followState.isFollowed
    override fun copyWithComments(
        hotComments: List<CommentItem>,
        latestComments: List<CommentItem>,
        pendingPraiseIds: Set<Long>,
        commentActionMessage: String?
    ): AlbumDetailUiState = copy(
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

class AlbumDetailViewModel(
    private val repository: AlbumDetailRepository,
    private val collectRepository: CollectRepository,
    private val userRepository: UserRepository,
    private val commentDelegate: com.nobody.poishelf.ui.common.comment.CommentInteractionDelegate,
    private val exHobbyRepository: ExHobbyRepository,
    private val viewPreferencesManager: ViewPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadPicturesJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {
        // 合并评论详情页回写的主楼更新（点赞/回复）
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
        // 监听全局 ExHobby 扩展图库开关，动态同步状态并按需触发补录
        viewModelScope.launch {
            viewPreferencesManager.isExHobbyEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(isExHobbyEnabled = enabled) }
                if (enabled) {
                    val currentAlbum = _uiState.value.album
                    if (currentAlbum != null && _uiState.value.exHobbyPics.isEmpty() && !_uiState.value.isExHobbyLoading) {
                        loadExHobby(currentAlbum.id)
                    }
                }
            }
        }
        // 监听登录态变化，登录后自动补录互动状态，退出后重置
        viewModelScope.launch {
            userRepository.tokenFlow.collect { token ->
                val currentAlbum = _uiState.value.album
                if (token.isNotBlank()) {
                    if (currentAlbum != null) {
                        loadInteractionStates(currentAlbum)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isAuthor = false,
                            isLiked = false,
                            isCollected = false,
                            followState = FriendState.UNKNOWN
                        )
                    }
                }
            }
        }
    }

    /**
     * 四阶段渐进式拉取相册详情。
     */
    fun loadDetail(param: AlbumNavParam) {
        if (_uiState.value.albumId == param.albumId && !_uiState.value.isInitialLoading && _uiState.value.album != null) {
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val currentExHobbyEnabled = _uiState.value.isExHobbyEnabled
            _uiState.value = AlbumDetailUiState(
                albumId = param.albumId,
                initialTitle = param.initialTitle,
                initialCover = param.initialCover,
                isInitialLoading = true,
                isExHobbyEnabled = currentExHobbyEnabled,
                errorMessage = null
            )

            // ── 阶段 1：相册基础信息与标签并发拉取 ──
            val albumDeferred = async { repository.getAlbumDetail(param.albumId) }
            val tagsDeferred = async { repository.getAlbumTags(param.albumId) }

            val albumResult = albumDeferred.await()
            val tagsResult = tagsDeferred.await()

            if (albumResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        errorMessage = albumResult.exceptionOrNull()?.message ?: "相册数据加载失败"
                    )
                }
                return@launch
            }

            val album = albumResult.getOrNull()
            val tags = tagsResult.getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    album = album,
                    tags = tags
                )
            }

            if (album != null) {
                // ── 阶段 2：拉取高清图集第 1 页 ──
                loadPicturesFirstPage(album.id, album.itemId, album.sortType)

                // ── 阶段 3：后台静默拉取关联手办与同款相册 (Fire-and-Forget) ──
                loadRelated(album.id, album.itemType)

                // ── 阶段 3.5：后台静默拉取 ExHobby 扩展图库 ──
                if (_uiState.value.isExHobbyEnabled) {
                    loadExHobby(album.id)
                }

                // ── 阶段 4：装载评论列表 ──
                loadComments(album.id)

                // ── 阶段 5：并发拉取互动初始状态 (点赞/收藏/关注) ──
                loadInteractionStates(album)

                // ── 阶段 6：后台静默上报浏览历史 (Fire-and-Forget) ──
                recordHistory(album.id)
            }
        }
    }

    /**
     * 加载相册对应的 ExHobby 扩展图库（首屏静默加载第 1 页）。
     */
    private fun loadExHobby(albumId: Long) {
        if (!_uiState.value.isExHobbyEnabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExHobbyLoading = true) }
            val result = exHobbyRepository.getPictures(id = albumId, page = 1)
            val pics = result?.getOrNull() ?: emptyList()
            _uiState.update {
                it.copy(
                    isExHobbyLoading = false,
                    exHobbyPics = pics
                )
            }
        }
    }

    /**
     * 加载高清图集首屏
     */
    private fun loadPicturesFirstPage(albumId: Long, itemId: Long?, sortType: String?) {
        loadPicturesJob?.cancel()
        loadPicturesJob = viewModelScope.launch {
            _uiState.update { it.copy(isPicturesLoading = true) }

            val result = repository.getAlbumPictures(
                albumId = albumId,
                itemId = itemId,
                page = 1,
                pageSize = 20,
                order = sortType
            )

            result.fold(
                onSuccess = { pics ->
                    _uiState.update {
                        it.copy(
                            isPicturesLoading = false,
                            pictures = pics,
                            picturesPage = 1,
                            hasMorePictures = ListPaging.hasMoreByFullPage(pics.size, 20)
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isPicturesLoading = false) }
                }
            )
        }
    }

    /**
     * 触底追加加载下一页图片
     */
    fun loadNextPicturesPage() {
        val currentState = _uiState.value
        if (currentState.isPicturesLoading || currentState.isLoadingMorePictures || !currentState.hasMorePictures) {
            return
        }

        val album = currentState.album ?: return
        val nextPage = currentState.picturesPage + 1

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMorePictures = true) }

            val result = repository.getAlbumPictures(
                albumId = album.id,
                itemId = album.itemId,
                page = nextPage,
                pageSize = 20,
                order = album.sortType
            )

            result.fold(
                onSuccess = { newPics ->
                    _uiState.update {
                        it.copy(
                            isLoadingMorePictures = false,
                            pictures = it.pictures + newPics,
                            picturesPage = nextPage,
                            hasMorePictures = ListPaging.hasMoreByFullPage(newPics.size, 20)
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMorePictures = false) }
                }
            )
        }
    }

    /**
     * 加载关联手办与同款相册
     */
    private fun loadRelated(albumId: Long, itemType: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRelatedLoading = true) }

            val hobbiesDeferred = async {
                repository.getRelatedHobbies(albumId = albumId, itemType = itemType ?: "album", pageSize = 8)
            }
            val albumsDeferred = async {
                repository.getRelatedAlbums(albumId = albumId, page = 1, pageSize = 8)
            }

            val hobbies = hobbiesDeferred.await().getOrDefault(emptyList())
            val albums = albumsDeferred.await().getOrDefault(emptyList())

            _uiState.update {
                it.copy(
                    isRelatedLoading = false,
                    relatedHobbies = hobbies,
                    relatedAlbums = albums
                )
            }
        }
    }

    /**
     * 装载相册评论（热门神评 + 最新评论）
     */
    private fun loadComments(albumId: Long) {
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadInitialComments(_uiState, albumId, repository::getComments)
        }
    }

    /**
     * 上拉触底翻页加载更多评论
     */
    fun loadNextCommentsPage() {
        val currentState = _uiState.value
        val albumId = currentState.album?.actualAlbumId ?: currentState.albumId
        viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, albumId, repository::getComments)
        }
    }

    /**
     * 并发拉取点赞、收藏及作者关注的初始状态（仅登录用户拉取）。
     */
    private fun loadInteractionStates(album: AlbumData) {
        val currentUserId = userRepository.currentUserId
        val authorId = album.authorUserId
        val isAuthor = currentUserId > 0L && authorId > 0L && currentUserId == authorId

        _uiState.update { it.copy(isAuthor = isAuthor) }

        if (!userRepository.isLoggedIn) return

        val actualAlbumId = album.actualAlbumId

        viewModelScope.launch {
            val likeDeferred = async { repository.getAlbumLikeInfo(actualAlbumId) }
            val collectDeferred = async { collectRepository.getCollectState(actualAlbumId) }
            val followDeferred = if (!isAuthor && authorId > 0L) {
                async { userRepository.getFellowType(authorId) }
            } else null

            val likeResult = runCatching { likeDeferred.await() }.getOrNull()
            val collectResult = runCatching { collectDeferred.await() }.getOrNull()
            val followResult = runCatching { followDeferred?.await() }.getOrNull()

            _uiState.update { current ->
                current.copy(
                    isLiked = likeResult?.getOrDefault(current.isLiked) ?: current.isLiked,
                    isCollected = collectResult?.map { it?.isCollected == true }?.getOrDefault(current.isCollected) ?: current.isCollected,
                    followState = followResult?.getOrDefault(current.followState) ?: current.followState
                )
            }
        }
    }

    /**
     * 点赞微交互（乐观更新，防抖锁，失败回滚）。
     */
    fun toggleLike() {
        if (!userRepository.isLoggedIn) {
            _uiState.update { it.copy(interactionActionMessage = "请先登录后再进行操作") }
            return
        }
        val currentState = _uiState.value
        if (currentState.isPraisePending) return

        val albumId = currentState.album?.actualAlbumId ?: currentState.albumId
        if (albumId <= 0L) return

        val previousLiked = currentState.isLiked
        val previousDelta = currentState.praiseCountDelta
        val nextLiked = !previousLiked
        val nextDelta = if (nextLiked) previousDelta + 1 else previousDelta - 1

        _uiState.update {
            it.copy(
                isLiked = nextLiked,
                praiseCountDelta = nextDelta,
                isPraisePending = true
            )
        }

        viewModelScope.launch {
            val result = if (nextLiked) {
                repository.praiseAlbumAdd(albumId)
            } else {
                repository.praiseAlbumDel(albumId)
            }

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isPraisePending = false,
                            interactionActionMessage = if (nextLiked) "感谢你的点赞 ♥" else "已取消点赞"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLiked = previousLiked,
                            praiseCountDelta = previousDelta,
                            isPraisePending = false,
                            interactionActionMessage = err.message ?: "点赞操作失败"
                        )
                    }
                }
            )
        }
    }

    /**
     * 收藏微交互（乐观更新，防抖锁，失败回滚）。
     */
    fun toggleCollect() {
        if (!userRepository.isLoggedIn) {
            _uiState.update { it.copy(interactionActionMessage = "请先登录后再进行操作") }
            return
        }
        val currentState = _uiState.value
        if (currentState.isCollectPending) return

        val albumId = currentState.album?.actualAlbumId ?: currentState.albumId
        if (albumId <= 0L) return

        val previousCollected = currentState.isCollected
        val previousDelta = currentState.collectCountDelta
        val nextCollected = !previousCollected
        val nextDelta = if (nextCollected) previousDelta + 1 else previousDelta - 1

        _uiState.update {
            it.copy(
                isCollected = nextCollected,
                collectCountDelta = nextDelta,
                isCollectPending = true
            )
        }

        viewModelScope.launch {
            val result = if (nextCollected) {
                collectRepository.collectItemAdd(albumId)
            } else {
                collectRepository.collectItemDel(albumId)
            }

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isCollectPending = false,
                            interactionActionMessage = if (nextCollected) "已收藏该相册 ★" else "已取消收藏"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isCollected = previousCollected,
                            collectCountDelta = previousDelta,
                            isCollectPending = false,
                            interactionActionMessage = err.message ?: "收藏操作失败"
                        )
                    }
                }
            )
        }
    }

    /**
     * 切换主评论点赞状态（乐观更新，失败回滚）。
     */
    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.album?.actualAlbumId ?: _uiState.value.albumId
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
        val albumId = _uiState.value.album?.actualAlbumId ?: _uiState.value.albumId
        val ok = commentDelegate.postTopLevelComment(_uiState, albumId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    /**
     * 发表成功后重拉第一页评论流。
     */
    fun refreshComments() {
        val albumId = _uiState.value.album?.actualAlbumId ?: _uiState.value.albumId
        if (albumId <= 0L) return
        loadComments(albumId)
    }

    /**
     * 消费评论互动一次性提示文案。
     */
    fun consumeCommentActionMessage() {
        commentDelegate.consumeCommentActionMessage(_uiState)
    }

    /**
     * 关注作者（乐观更新，防抖锁，失败回滚）。
     */
    fun followAuthor() {
        if (!userRepository.isLoggedIn) {
            _uiState.update { it.copy(interactionActionMessage = "请先登录后再进行操作") }
            return
        }
        val currentState = _uiState.value
        if (currentState.isFollowPending || currentState.isAuthor) return

        val authorId = currentState.album?.authorUserId ?: 0L
        if (authorId <= 0L) return

        val previousFollowState = currentState.followState
        if (previousFollowState.isFollowed) return

        _uiState.update {
            it.copy(
                followState = FriendState.FOLLOWING,
                isFollowPending = true
            )
        }

        viewModelScope.launch {
            val result = userRepository.updateFriend(userId = authorId, state = FriendState.FOLLOWING)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isFollowPending = false,
                            interactionActionMessage = "已关注作者 (´▽`ʃ♡ƪ)"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            followState = previousFollowState,
                            isFollowPending = false,
                            interactionActionMessage = err.message ?: "关注失败"
                        )
                    }
                }
            )
        }
    }

    /**
     * 取消关注作者（弹窗确认后调用，乐观更新，防抖锁，失败回滚）。
     */
    fun unfollowAuthor() {
        if (!userRepository.isLoggedIn) {
            _uiState.update { it.copy(interactionActionMessage = "请先登录后再进行操作") }
            return
        }
        val currentState = _uiState.value
        if (currentState.isFollowPending || currentState.isAuthor) return

        val authorId = currentState.album?.authorUserId ?: 0L
        if (authorId <= 0L) return

        val previousFollowState = currentState.followState
        if (!previousFollowState.isFollowed) return

        _uiState.update {
            it.copy(
                followState = FriendState.NOT_FOLLOWING,
                isFollowPending = true
            )
        }

        viewModelScope.launch {
            val result = userRepository.updateFriend(userId = authorId, state = FriendState.NOT_FOLLOWING)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isFollowPending = false,
                            interactionActionMessage = "已取消关注"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            followState = previousFollowState,
                            isFollowPending = false,
                            interactionActionMessage = err.message ?: "取消关注失败"
                        )
                    }
                }
            )
        }
    }

    /**
     * 消费互动操作一次性提示文案。
     */
    fun consumeInteractionActionMessage() {
        _uiState.update { it.copy(interactionActionMessage = null) }
    }

    /**
     * 后台静默记录相册浏览足迹。
     */
    private fun recordHistory(albumId: Long) {
        viewModelScope.launch {
            runCatching {
                userRepository.addUserHistory(albumId)
            }
        }
    }
}
