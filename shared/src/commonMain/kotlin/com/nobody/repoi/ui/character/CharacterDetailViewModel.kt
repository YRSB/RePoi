package com.nobody.repoi.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.StickerPackData
import com.nobody.repoi.data.repository.CharacterDetailRepository
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.ui.navigation.CharacterNavParam
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.data.remote.model.CommentItem

class CharacterDetailViewModel(
    private val repository: CharacterDetailRepository,
    private val commentDelegate: CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterDetailUiState())
    val uiState: StateFlow<CharacterDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {
        // 合并评论详情页回写的主楼更新（点赞/回复）
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    /**
     * 渐进多阶段加载角色/要素详情全部数据。
     */
    fun loadDetail(param: CharacterNavParam) {
        if (_uiState.value.characterId == param.characterId && !_uiState.value.isInitialLoading && _uiState.value.character != null) {
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = CharacterDetailUiState(
                characterId = param.characterId,
                characterNodeId = param.characterNodeId,
                initialName = param.initialName,
                initialCover = param.initialCover,
                isInitialLoading = true,
                errorMessage = null
            )

            // ── 阶段 1：首屏核心渲染，拉取角色主体基本信息 ──
            val detailResult = repository.getCharacterDetail(param.characterId)
            if (detailResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        errorMessage = detailResult.exceptionOrNull()?.message ?: "获取角色信息失败"
                    )
                }
                return@launch
            }

            val character = detailResult.getOrThrow()
            val nodeId = if (character.actualNodeId > 0L) character.actualNodeId else param.characterNodeId

            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    character = character,
                    characterNodeId = nodeId,
                    isCollected = character.isAddFav,
                    originalCollected = character.isAddFav,
                    isWorksLoading = true,
                    isHobbysLoading = true,
                    isAlbumsLoading = true,
                    isRelevantLoading = true,
                    isZonesLoading = true,
                    isCommentsLoading = true
                )
            }

            // ── 阶段 2：并行发起次级各板块网络请求 ──
            // 2.1 登场作品
            launch {
                val worksResult = repository.getCharacterWorks(param.characterId)
                _uiState.update {
                    it.copy(
                        isWorksLoading = false,
                        works = worksResult?.getOrDefault(emptyList()) ?: emptyList()
                    )
                }
            }

            // 2.2 周边手办
            launch {
                val hobbysResult = repository.getCharacterHobbys(nodeId)
                _uiState.update {
                    it.copy(
                        isHobbysLoading = false,
                        hobbys = hobbysResult?.getOrDefault(emptyList()) ?: emptyList()
                    )
                }
            }

            // 2.3 相关相册
            launch {
                val albumsResult = repository.getCharacterAlbums(param.characterId)
                _uiState.update {
                    it.copy(
                        isAlbumsLoading = false,
                        albums = albumsResult?.getOrDefault(emptyList()) ?: emptyList()
                    )
                }
            }

            // 2.4 推荐要素/相关角色
            launch {
                val relevantResult = repository.getCharacterRelevant(param.characterId)
                _uiState.update {
                    it.copy(
                        isRelevantLoading = false,
                        relevantCharacters = relevantResult?.getOrDefault(emptyList()) ?: emptyList()
                    )
                }
            }

            // 2.5 所属分区/属性标签
            if (nodeId > 0L) {
                launch {
                    val zonesResult = repository.getItemZones(nodeId)
                    _uiState.update {
                        it.copy(
                            isZonesLoading = false,
                            zones = zonesResult?.getOrDefault(emptyList()) ?: emptyList()
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(isZonesLoading = false) }
            }

            // 2.6 首屏评论加载
            if (nodeId > 0L) {
                launch {
                    commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
                }
            } else {
                _uiState.update { it.copy(isCommentsLoading = false) }
            }
        }
    }

    /**
     * 切换角色详细介绍的展开/折叠状态。
     */
    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }

    /**
     * 切换收藏/想买状态（乐观更新 + 失败回滚）。
     */
    fun toggleCollect() {
        val state = _uiState.value
        val nodeId = state.characterNodeId
        if (nodeId <= 0L) return
        val currentCollected = state.isCollected
        val nextCollected = !currentCollected
        val delta = when {
            state.originalCollected && !nextCollected -> -1
            !state.originalCollected && nextCollected -> 1
            else -> 0
        }

        // 乐观更新
        _uiState.update { it.copy(isCollected = nextCollected, collectCountDelta = delta) }

        viewModelScope.launch {
            val result = repository.toggleCollect(nodeId, currentCollected)
            if (result.isFailure) {
                // 失败回滚
                _uiState.update {
                    val origDelta = when {
                        it.originalCollected && !currentCollected -> -1
                        !it.originalCollected && currentCollected -> 1
                        else -> 0
                    }
                    it.copy(
                        isCollected = currentCollected,
                        collectCountDelta = origDelta,
                        commentActionMessage = result.exceptionOrNull()?.message ?: "收藏状态更新失败"
                    )
                }
            }
        }
    }

    /**
     * 加载更多最新评论。
     */
    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.characterNodeId
        if (nodeId <= 0L) return
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    /**
     * 刷新评论列表。
     */
    fun refreshComments() {
        val nodeId = _uiState.value.characterNodeId
        if (nodeId <= 0L) return
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
        }
    }

    /**
     * 切换主评论点赞状态（乐观更新）。
     */
    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.characterNodeId
        if (nodeId <= 0L) return
        commentDelegate.toggleCommentPraise(viewModelScope, _uiState, commentId, nodeId)
    }

    /**
     * 发表子评论回复。
     */
    suspend fun submitCommentReply(
        commentId: Long,
        target: CommentReplyItem?,
        content: String,
        stickers: List<String>
    ): Boolean {
        return commentDelegate.submitCommentReply(_uiState, commentId, target, content, stickers)
    }

    /**
     * 发表主楼评论。
     */
    suspend fun submitTopLevelComment(content: String, stickers: List<String>): Boolean {
        val nodeId = _uiState.value.characterNodeId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    suspend fun deleteComment(comment: CommentItem): Boolean =
        commentDelegate.deleteMainComment(_uiState, comment.id)

    suspend fun deleteReply(comment: CommentItem, reply: CommentReplyItem): Boolean =
        commentDelegate.deleteSubReply(_uiState, comment.id, reply.id)

    suspend fun loadStickerPacks(): List<StickerPackData> =
        commentDelegate.loadStickerPacks()

    val currentCommentUserId: Long
        get() = commentDelegate.currentUserId

    suspend fun resolveCommentUserId(): Long =
        commentDelegate.resolveCurrentUserId()

    fun consumeCommentActionMessage() {
        commentDelegate.consumeCommentActionMessage(_uiState)
    }
}
