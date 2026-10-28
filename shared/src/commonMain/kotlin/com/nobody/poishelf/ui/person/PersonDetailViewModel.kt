package com.nobody.poishelf.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.CommentReplyItem
import com.nobody.poishelf.data.remote.model.PersonData
import com.nobody.poishelf.data.remote.model.PersonHobbyEntry
import com.nobody.poishelf.data.remote.model.PersonWorksEntry
import com.nobody.poishelf.data.repository.PersonDetailRepository
import com.nobody.poishelf.ui.common.comment.CommentInteractionDelegate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 人物详情页路由导航参数。
 */
@Serializable
data class PersonNavParam(
    val personId: Long,
    val personNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)

/**
 * 人物详情页渐进式加载 UI 状态机。
 */
data class PersonDetailUiState(
    val personId: Long = 0L,
    val personNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    // 核心人物主体数据
    val person: PersonData? = null,
    val careerType: Int = 0,
    val isIntroExpanded: Boolean = false,

    // 参与作品与出演角色
    val isWorksLoading: Boolean = false,
    val works: List<PersonWorksEntry> = emptyList(),
    val isCharactersLoading: Boolean = false,
    val characters: List<PersonWorksEntry> = emptyList(),

    // 参与周边
    val isHobbiesLoading: Boolean = false,
    val hobbies: List<PersonHobbyEntry> = emptyList(),

    // 当前 Tab
    val selectedTab: Int = 0,

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
    val collectCountDelta: Int = 0
) : com.nobody.poishelf.ui.common.comment.CommentPagedUiState {
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

class PersonDetailViewModel(
    private val repository: PersonDetailRepository,
    private val commentDelegate: com.nobody.poishelf.ui.common.comment.CommentInteractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var loadCommentsJob: Job? = null

    init {
        // 合并评论详情页回写的主楼更新（点赞/回复）
        commentDelegate.observeCommentUpdates(viewModelScope, _uiState)
    }

    /**
     * 渐进多阶段加载人物详情所有数据。
     */
    fun loadDetail(param: PersonNavParam) {
        if (_uiState.value.personId == param.personId && !_uiState.value.isInitialLoading && _uiState.value.person != null) {
            return // 已加载过该人物，保留现有数据
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

            // ── 阶段 1：首屏核心渲染，拉取人物主体基本信息 ──
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

            // ── 阶段 2：并行发起次级各板块网络请求 ──
            // 2.1 参与作品
            launch {
                val worksResult = repository.getPersonWorks(param.personId)
                _uiState.update {
                    it.copy(
                        isWorksLoading = false,
                        works = worksResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.2 出演角色
            launch {
                val charactersResult = repository.getPersonCharacter(param.personId)
                _uiState.update {
                    it.copy(
                        isCharactersLoading = false,
                        characters = charactersResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.3 参与周边
            launch {
                val hobbiesResult = repository.getPersonHobby(param.personId)
                _uiState.update {
                    it.copy(
                        isHobbiesLoading = false,
                        hobbies = hobbiesResult.getOrDefault(emptyList())
                    )
                }
            }

            // 2.4 评论区（热门置顶 + 最新第 1 页）
            launch {
                commentDelegate.loadInitialComments(_uiState, nodeId, repository::getComments)
            }

            // 2.5 查询收藏状态，未登录时静默降级为服务端初始值
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

    /**
     * 切换人物详情 Tab。
     */
    fun onSelectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    /**
     * 翻页追加最新评论流。
     */
    fun loadNextCommentsPage() {
        val state = _uiState.value
        val nodeId = state.personNodeId
        loadCommentsJob?.cancel()
        loadCommentsJob = viewModelScope.launch {
            commentDelegate.loadNextCommentsPage(_uiState, nodeId, repository::getComments)
        }
    }

    /**
     * 切换简介展开/折叠状态。
     */
    fun toggleIntroExpanded() {
        _uiState.update { it.copy(isIntroExpanded = !it.isIntroExpanded) }
    }

    /**
     * 切换主评论点赞状态（乐观更新，失败回滚）。
     */
    fun toggleCommentPraise(commentId: Long) {
        val nodeId = _uiState.value.personNodeId
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
        val nodeId = _uiState.value.personNodeId
        val ok = commentDelegate.postTopLevelComment(_uiState, nodeId, content, stickers)
        if (ok) refreshComments()
        return ok
    }

    /**
     * 发表成功后重拉第一页评论流（与首屏加载同一口径）。
     */
    fun refreshComments() {
        val nodeId = _uiState.value.personNodeId
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
     * 切换收藏人物状态与计数。
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
}
