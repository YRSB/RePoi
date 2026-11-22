package com.nobody.repoi.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.PersonData
import com.nobody.repoi.data.remote.model.PersonHobbyEntry
import com.nobody.repoi.data.remote.model.PersonWorksEntry
import com.nobody.repoi.data.repository.PersonDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.ui.collect.collectDeltaOf
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class PersonNavParam(
    val personId: Long,
    val personNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)

data class PersonDetailUiState(
    val personId: Long = 0L,
    val personNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    val person: PersonData? = null,
    val careerType: Int = 0,
    val isIntroExpanded: Boolean = false,

    val isWorksLoading: Boolean = false,
    val works: List<PersonWorksEntry> = emptyList(),
    val isCharactersLoading: Boolean = false,
    val characters: List<PersonWorksEntry> = emptyList(),

    val isHobbiesLoading: Boolean = false,
    val hobbies: List<PersonHobbyEntry> = emptyList(),

    val selectedTab: Int = 0,

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

    val collectCountBase: Int? = null
) : com.nobody.repoi.ui.common.comment.CommentPagedUiState {
    override fun copyWithComments(
        hotComments: List<CommentItem>,
        latestComments: List<CommentItem>,
        pendingPraiseIds: Set<Long>,
        commentActionMessage: String?
    ): PersonDetailUiState = copy(
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

class PersonDetailViewModel(
    private val repository: PersonDetailRepository,
    private val collectRepository: CollectRepository,
    private val commentDelegate: com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {

        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    fun loadDetail(param: PersonNavParam) {
        if (_uiState.value.personId == param.personId && !_uiState.value.isInitialLoading && _uiState.value.person != null) {
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _uiState.value = PersonDetailUiState(
                personId = param.personId,
                personNodeId = param.personNodeId,
                initialName = param.initialName,
                initialCover = param.initialCover,
                isInitialLoading = true,
                errorMessage = null
            )

            val detailResult = repository.getPersonDetail(param.personId)
            if (detailResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isInitialLoading = false,
                        errorMessage = detailResult.exceptionOrNull()?.message ?: "获取人物信息失败"
                    )
                }
                return@launch
            }

            val result = detailResult.getOrThrow()
            val person = result.person
            val nodeId = if (person.id > 0) person.id else param.personNodeId

            _uiState.update {
                it.copy(
                    isInitialLoading = false,
                    person = person,
                    careerType = result.careerType,
                    personNodeId = nodeId,
                    isCollected = result.isAddFav,
                    originalCollected = result.isAddFav,
                    isWorksLoading = true,
                    isCharactersLoading = true,
                    isHobbiesLoading = true,
                    isCommentsLoading = true
                )
            }

            launch {
                val worksResult = repository.getPersonWorks(param.personId)
                _uiState.update {
                    it.copy(
                        isWorksLoading = false,
                        works = worksResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val charactersResult = repository.getPersonCharacter(param.personId)
                _uiState.update {
                    it.copy(
                        isCharactersLoading = false,
                        characters = charactersResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                val hobbiesResult = repository.getPersonHobby(param.personId)
                _uiState.update {
                    it.copy(
                        isHobbiesLoading = false,
                        hobbies = hobbiesResult.getOrDefault(emptyList())
                    )
                }
            }

            launch {
                commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
            }

            launch {
                val isCollected = repository.getCollectState(nodeId).getOrDefault(result.isAddFav)
                _uiState.update {
                    it.copy(
                        isCollected = isCollected,
                        originalCollected = isCollected,
                        collectCountDelta = 0
                    )
                }
            }
        }
    }

    fun onSelectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.personNodeId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }

    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.personNodeId
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
        val nodeId = _uiState.value.personNodeId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    fun refreshComments() {
        val nodeId = _uiState.value.personNodeId
        if (nodeId <= 0L) return
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
        }
    }

    fun consumeCommentActionMessage() {
        commentDelegate.consumeCommentActionMessage(_uiState)
    }

    fun toggleCollect() {
        val state = _uiState.value
        val nodeId = state.personNodeId
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
}
