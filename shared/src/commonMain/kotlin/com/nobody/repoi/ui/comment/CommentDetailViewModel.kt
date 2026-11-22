package com.nobody.repoi.ui.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentThreadPage
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.data.remote.model.StickerPackData
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.DeletedComment
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.util.CommentMutations
import com.nobody.repoi.util.buildEmoticonParam
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class CommentDetailNavParam(
    val commentId: Long,
    val relateNodeId: Long,
    val snapshot: CommentItem? = null,
    val isHot: Boolean = false,
    val isDiscuss: Boolean = false
)

enum class CommentDetailPhase {
    Loading,
    Success,
    Error
}

data class CommentDetailUiState(
    val commentId: Long = 0L,
    val relateNodeId: Long = 0L,
    val phase: CommentDetailPhase = CommentDetailPhase.Loading,
    val isHot: Boolean = false,
    val isDiscuss: Boolean = false,
    val comment: CommentItem? = null,
    val replies: List<CommentReplyItem> = emptyList(),
    val page: Int = 1,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val isPraisePending: Boolean = false,
    val isSubmittingReply: Boolean = false,
    val inputText: String = "",
    val isReplyDialogOpen: Boolean = false,
    val replyTarget: CommentReplyItem? = null,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

class CommentDetailViewModel(
    private val repository: CommentRepository,
    private val stickerRepository: StickerRepository,
    private val userSession: UserSession
) : ViewModel() {
    companion object {

        private const val REPLY_PAGE_SIZE = 10
    }

    private val _uiState = MutableStateFlow(CommentDetailUiState())
    val uiState: StateFlow<CommentDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var moreJob: Job? = null
    init {
        viewModelScope.launch {
            repository.commentUpdates.collect { updated ->
                _uiState.update { current ->
                    val comment = current.comment
                    if (comment == null || comment.id != updated.id) return@update current
                    if (CommentMutations.isShielded(updated)) {
                        current.toShieldedEmpty()
                    } else {
                        current.copy(
                            comment = comment.copy(
                                praiseState = updated.praiseState,
                                praiseCount = updated.praiseCount
                            )
                        )
                    }
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun openReplyDialog(target: CommentReplyItem? = null) {
        _uiState.update { it.copy(isReplyDialogOpen = true, replyTarget = target) }
    }

    fun closeReplyDialog() {
        _uiState.update { it.copy(isReplyDialogOpen = false, replyTarget = null) }
    }

    fun onReplyTargetChanged(target: CommentReplyItem?) {
        openReplyDialog(target)
    }

    fun clearReplyTarget() {
        closeReplyDialog()
    }

    fun loadDetail(param: CommentDetailNavParam) {
        if (_uiState.value.commentId == param.commentId && _uiState.value.phase != CommentDetailPhase.Loading && _uiState.value.phase != CommentDetailPhase.Error) {
            return
        }
        loadJob?.cancel()
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            val initialSnapshot = param.snapshot?.takeUnless { CommentMutations.isShielded(it) }
            val initialReplies = initialSnapshot?.let { CommentMutations.previewReplies(it) } ?: emptyList()
            _uiState.value = CommentDetailUiState(
                commentId = param.commentId,
                relateNodeId = param.relateNodeId,
                phase = CommentDetailPhase.Loading,
                isHot = param.isHot,
                isDiscuss = param.isDiscuss,
                comment = initialSnapshot,
                replies = initialReplies
            )
            val result = if (param.isDiscuss) {
                repository.getReplies(
                    commentId = param.commentId,
                    page = 1,
                    pageSize = REPLY_PAGE_SIZE,
                    isDiscuss = true,
                    parentSnapshot = param.snapshot
                )
            } else {
                repository.getReplies(param.commentId, page = 1, pageSize = REPLY_PAGE_SIZE)
            }
            result.fold(
                onSuccess = { pageData ->
                    _uiState.update {
                        val calibrated = pageData.comment?.let { server ->
                            val snapshot = param.snapshot?.takeIf { it.id == server.id }
                            val withReplies = if (server.replys.isNullOrEmpty() && !snapshot?.replys.isNullOrEmpty()) {
                                server.copy(replys = snapshot.replys)
                            } else {
                                server
                            }
                            if (snapshot != null) withReplies.copy(praiseState = snapshot.praiseState) else withReplies
                        } ?: param.snapshot
                        val isShielded = calibrated?.let { CommentMutations.isShielded(it) } ?: false
                        if (isShielded) {
                            it.copy(
                                phase = CommentDetailPhase.Success,
                                page = 1,
                                errorMessage = null
                            ).toShieldedEmpty()
                        } else {
                            val cleanCalibrated = calibrated?.let { c ->
                                if (c.replys != null) c.copy(replys = CommentMutations.filterShielded(c.replys)) else c
                            }
                            it.copy(
                                phase = CommentDetailPhase.Success,
                                comment = cleanCalibrated,
                                replies = CommentMutations.filterShielded(pageData.replies),
                                page = 1,
                                hasMore = pageData.replies.size >= REPLY_PAGE_SIZE,
                                errorMessage = null
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            phase = CommentDetailPhase.Error,
                            errorMessage = err.message ?: "获取评论详情失败"
                        )
                    }
                }
            )
        }
    }

    fun loadNextPage() {
        val snapshot = _uiState.value
        if (snapshot.phase != CommentDetailPhase.Success || snapshot.isLoadingMore || !snapshot.hasMore) return
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            val current = _uiState.value
            val commentId = current.commentId
            val nextPage = current.page + 1
            var delayMs = 500L
            var lastErr: Throwable? = null
            var successData: CommentThreadPage? = null

            for (attempt in 1..3) {
                val result = if (current.isDiscuss) {
                    repository.getReplies(
                        commentId = commentId,
                        page = nextPage,
                        pageSize = REPLY_PAGE_SIZE,
                        isDiscuss = true,
                        parentSnapshot = current.comment
                    )
                } else {
                    repository.getReplies(commentId, page = nextPage, pageSize = REPLY_PAGE_SIZE)
                }
                if (result.isSuccess) {
                    successData = result.getOrNull()
                    break
                } else {
                    lastErr = result.exceptionOrNull()
                    if (attempt < 3) {
                        kotlinx.coroutines.delay(delayMs)
                        delayMs *= 2
                    }
                }
            }

            if (successData != null) {

                if (_uiState.value.commentId != commentId) {
                    return@launch
                }
                _uiState.update { current ->
                    val serverComment = successData.comment
                    if (serverComment != null && CommentMutations.isShielded(serverComment)) {
                        return@update current.toShieldedEmpty(isLoadingMore = false)
                    }
                    val ids = current.replies.map { reply -> reply.id }.toSet()
                    val appended = CommentMutations.filterShielded(successData.replies)
                        .filter { it.id !in ids }
                        .distinctBy { it.id }
                    val cleanServerComment = serverComment?.let { c ->
                        if (c.replys != null) c.copy(replys = CommentMutations.filterShielded(c.replys)) else c
                    }
                    current.copy(
                        isLoadingMore = false,
                        comment = cleanServerComment?.let { server ->
                            current.comment?.takeIf { it.id == server.id }?.let { server.copy(praiseState = it.praiseState) } ?: server
                        } ?: current.comment,
                        replies = current.replies + appended,
                        page = nextPage,
                        hasMore = successData.replies.size >= REPLY_PAGE_SIZE
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        actionMessage = lastErr?.message ?: "加载更多回复失败"
                    )
                }
            }
        }
    }

    fun togglePraise() {
        val state = _uiState.value
        val comment = state.comment ?: return
        if (state.isPraisePending || state.relateNodeId <= 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(isPraisePending = true) }
            val optimistic = comment.copy(
                praiseState = !comment.praiseState,
                praiseCount = (comment.praiseCount + if (!comment.praiseState) 1 else -1).coerceAtLeast(0)
            )
            _uiState.update { it.copy(comment = optimistic) }
            val result = repository.togglePraise(
                commentId = comment.id,
                relateNodeId = state.relateNodeId,
                praised = comment.praiseState
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isPraisePending = false) }
                    repository.emitCommentUpdate(optimistic)
                },
                onFailure = { err ->
                    _uiState.update { current ->

                        val rolledBack = current.comment
                            ?.takeIf { it.id == comment.id }
                            ?.copy(
                                praiseState = comment.praiseState,
                                praiseCount = comment.praiseCount
                            ) ?: current.comment
                        current.copy(
                            isPraisePending = false,
                            comment = rolledBack,
                            actionMessage = err.message ?: "点赞操作失败"
                        )
                    }
                }
            )
        }
    }

    fun submitCurrentInput() {
        val state = _uiState.value
        val text = state.inputText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(actionMessage = "请输入评论内容") }
            return
        }
        viewModelScope.launch {
            submitReply(
                content = text,
                replyTarget = state.replyTarget,
                stickers = emptyList(),
                clearInputOnSuccess = true
            )
        }
    }

    suspend fun submitReply(
        content: String,
        replyTarget: CommentReplyItem? = null,
        stickers: List<String>,
        clearInputOnSuccess: Boolean = false
    ): Boolean {
        val snapshot = _uiState.value
        val commentId = snapshot.comment?.id ?: return false
        if (snapshot.isSubmittingReply || (content.isBlank() && stickers.isEmpty())) return false
        _uiState.update { it.copy(isSubmittingReply = true) }
        val result = if (snapshot.isDiscuss) {
            repository.addReply(
                commentId = commentId,
                content = content,
                toUserId = CommentMutations.resolveReplyToUserId(replyTarget),
                toReplyId = replyTarget?.id,
                emoticon = buildEmoticonParam(stickers),
                isDiscuss = true,
                relateNodeId = snapshot.relateNodeId
            )
        } else {
            repository.addReply(
                commentId = commentId,
                content = content,
                toUserId = CommentMutations.resolveReplyToUserId(replyTarget),
                toReplyId = replyTarget?.id,
                emoticon = buildEmoticonParam(stickers)
            )
        }
        return result.fold(
            onSuccess = { reply ->
                var updated: CommentItem? = null
                _uiState.update { current ->

                    if (current.comment?.id != commentId) {
                        return@update current.copy(isSubmittingReply = false)
                    }
                    val base = current.comment
                    val baseReplys = base.replys ?: emptyList()
                    val alreadyInThread = baseReplys.any { it.id == reply.id }
                    val mergedReplys =
                        if (alreadyInThread) baseReplys else baseReplys + reply
                    val newComment = base.copy(
                        replys = mergedReplys,
                        reply = (base.reply) + if (alreadyInThread) 0 else 1
                    )
                    updated = newComment
                    val alreadyInList = current.replies.any { it.id == reply.id }
                    current.copy(
                        isSubmittingReply = false,
                        comment = updated,
                        replies = if (alreadyInList) current.replies else current.replies + reply,
                        actionMessage = "回复发表成功",
                        inputText = if (clearInputOnSuccess) "" else current.inputText,
                        replyTarget = if (clearInputOnSuccess) null else current.replyTarget
                    )
                }
                updated?.let { repository.emitCommentUpdate(it) }
                true
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        isSubmittingReply = false,
                        actionMessage = err.message ?: "回复发表失败"
                    )
                }
                false
            }
        )
    }

    suspend fun loadStickerPacks(): List<StickerPackData> =
        stickerRepository.getCommentPacks().getOrDefault(emptyList())

    fun consumeActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    val currentCommentUserId: Long
        get() = userSession.currentUserId

    suspend fun resolveCommentUserId(): Long {
        val cached = userSession.currentUserId
        if (cached > 0L || userSession.currentToken.isBlank()) return cached
        userSession.refreshProfile()
        return userSession.currentUserId
    }

    suspend fun deleteHeaderComment(): Boolean {
        val comment = _uiState.value.comment ?: return false
        val type = if (_uiState.value.isDiscuss) "discuss" else "comment"
        val result = repository.deleteComment(nodeId = comment.id, type = type)
        return result.fold(
            onSuccess = {
                repository.emitDeleted(DeletedComment(commentId = comment.id))
                _uiState.update { it.copy(actionMessage = "评论已删除") }
                true
            },
            onFailure = { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "删除评论失败") }
                false
            }
        )
    }

    suspend fun deleteDetailReply(reply: CommentReplyItem): Boolean {
        val snapshot = _uiState.value
        val comment = snapshot.comment ?: return false
        val type = if (snapshot.isDiscuss) "discuss_reply" else "comment_reply"
        val result = repository.deleteComment(nodeId = reply.id, type = type)
        return result.fold(
            onSuccess = {
                repository.emitDeleted(DeletedComment(commentId = comment.id, replyId = reply.id))
                _uiState.update { current ->
                    if (current.comment?.id != comment.id) return@update current
                    val remaining = current.replies.filter { it.id != reply.id }
                    val baseReplys = (current.comment.replys ?: emptyList()).filter { it.id != reply.id }
                    current.copy(
                        comment = current.comment.copy(replys = baseReplys, reply = current.comment.reply.minus(1).coerceAtLeast(0)),
                        replies = remaining,
                        actionMessage = "回复已删除"
                    )
                }
                true
            },
            onFailure = { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "删除回复失败") }
                false
            }
        )
    }

    private fun CommentDetailUiState.toShieldedEmpty(isLoadingMore: Boolean = false): CommentDetailUiState {
        return copy(
            comment = null,
            replies = emptyList(),
            hasMore = false,
            isLoadingMore = isLoadingMore
        )
    }
}
