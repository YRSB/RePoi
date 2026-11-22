package com.nobody.repoi.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.StickerPackData
import com.nobody.repoi.data.repository.CharacterDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.ui.collect.collectDeltaOf
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
    private val collectRepository: CollectRepository,
    private val commentDelegate: CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterDetailUiState())
    val uiState: StateFlow<CharacterDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {

        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

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

            launch {
                val worksResult = repository.getCharacterWorks(param.characterId)
                _uiState.update {
                    it.copy(
                        isWorksLoading = false,
                        works = worksResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val hobbysResult = repository.getCharacterHobbys(nodeId)
                _uiState.update {
                    it.copy(
                        isHobbysLoading = false,
                        hobbys = hobbysResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val albumsResult = repository.getCharacterAlbums(param.characterId)
                _uiState.update {
                    it.copy(
                        isAlbumsLoading = false,
                        albums = albumsResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val relevantResult = repository.getCharacterRelevant(param.characterId)
                _uiState.update {
                    it.copy(
                        isRelevantLoading = false,
                        relevantCharacters = relevantResult.getOrDefault(emptyList())
                    )
                }
            }

            if (nodeId > 0L) {
                launch {
                    val zonesResult = repository.getItemZones(nodeId)
                    _uiState.update {
                        it.copy(
                            isZonesLoading = false,
                            zones = zonesResult.getOrDefault(emptyList())
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(isZonesLoading = false) }
            }

            if (nodeId > 0L) {
                launch {
                    commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
                }
            } else {
                _uiState.update { it.copy(isCommentsLoading = false) }
            }
        }
    }

    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }

    fun toggleCollect() {
        val state = _uiState.value
        val nodeId = state.characterNodeId
        if (nodeId <= 0L) return
        val currentCollected = state.isCollected
        val nextCollected = !currentCollected
        val delta = collectDeltaOf(state.originalCollected, nextCollected)

        _uiState.update { it.copy(isCollected = nextCollected, collectCountDelta = delta) }

        viewModelScope.launch {
            val result = collectRepository.toggleItemCollect(nodeId, currentCollected)
            if (result.isFailure) {

                _uiState.update {
                    val origDelta = collectDeltaOf(it.originalCollected, currentCollected)
                    it.copy(
                        isCollected = currentCollected,
                        collectCountDelta = origDelta,
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

    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.characterNodeId
        if (nodeId <= 0L) return
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    fun refreshComments() {
        val nodeId = _uiState.value.characterNodeId
        if (nodeId <= 0L) return
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
        }
    }

    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.characterNodeId
        if (nodeId <= 0L) return
        commentDelegate.toggleCommentPraise(viewModelScope, _uiState, commentId, nodeId)
    }

    suspend fun submitCommentReply(
        commentId: Long,
        target: CommentReplyItem?,
        content: String,
        stickers: List<String>
    ): Boolean {
        return commentDelegate.submitCommentReply(_uiState, commentId, target, content, stickers)
    }

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
