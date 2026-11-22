package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentUser
import com.nobody.repoi.data.remote.model.StickerPackData
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.DeletedComment
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.util.CommentMutations
import com.nobody.repoi.util.ListPaging
import com.nobody.repoi.util.buildEmoticonParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

typealias CommentPageFetch = suspend (nodeId: Long, type: String?, page: Int, pageSize: Int) -> Result<List<CommentItem>>

class CommentInteractionDelegate(
    private val commentRepository: CommentRepository,
    private val stickerRepository: StickerRepository,
    private val userSession: UserSession
) {

    val currentUserId: Long
        get() = userSession.currentUserId

    suspend fun resolveCurrentUserId(): Long {
        val cached = userSession.currentUserId
        if (cached > 0L || userSession.currentToken.isBlank()) return cached
        userSession.refreshProfile()
        return userSession.currentUserId
    }

    fun isOwnComment(user: CommentUser?, currentId: Long = currentUserId): Boolean {
        if (currentId <= 0L || user == null) return false
        return user.id == currentId || user.userId == currentId
    }

    suspend fun loadStickerPacks(): List<StickerPackData> =
        stickerRepository.getCommentPacks().getOrDefault(emptyList())

    fun <T : CommentAwareUiState> observeCommentUpdates(
        scope: CoroutineScope,
        stateFlow: MutableStateFlow<T>
    ) {
        scope.launch {
            commentRepository.commentUpdates.collect { updated ->
                stateFlow.update { current ->
                    @Suppress("UNCHECKED_CAST")
                    current.copyWithComments(
                        hotComments = CommentMutations.applyExternalUpdate(current.hotComments, updated),
                        latestComments = CommentMutations.applyExternalUpdate(current.latestComments, updated)
                    ) as T
                }
            }
        }
        scope.launch {
            commentRepository.deletedComments.collect { deleted ->
                stateFlow.update { current ->
                    @Suppress("UNCHECKED_CAST")
                    current.copyWithComments(
                        hotComments = applyDeleted(current.hotComments, deleted),
                        latestComments = applyDeleted(current.latestComments, deleted)
                    ) as T
                }
            }
        }
    }

    private fun applyDeleted(comments: List<CommentItem>, deleted: DeletedComment): List<CommentItem> {
        val replyId = deleted.replyId
        return if (replyId == null) {
            CommentMutations.applyCommentRemoved(comments, deleted.commentId)
        } else {
            CommentMutations.applyReplyRemoved(comments, deleted.commentId, replyId)
        }
    }

    private val deletingCommentIds = mutableSetOf<Long>()
    private val deletingReplyIds = mutableSetOf<Pair<Long, Long>>()

    private val praisingCommentIds = mutableSetOf<Long>()

    private val inFlightMutex = Mutex()

    suspend fun <T : CommentAwareUiState> deleteMainComment(
        stateFlow: MutableStateFlow<T>,
        commentId: Long
    ): Boolean {
        val admitted = inFlightMutex.withLock { deletingCommentIds.add(commentId) }
        if (!admitted) return false
        try {
            val result = commentRepository.deleteComment(nodeId = commentId, type = "comment")
            return result.fold(
                onSuccess = {
                    stateFlow.update { current ->
                        @Suppress("UNCHECKED_CAST")
                        current.copyWithComments(
                            hotComments = CommentMutations.applyCommentRemoved(current.hotComments, commentId),
                            latestComments = CommentMutations.applyCommentRemoved(current.latestComments, commentId),
                            commentActionMessage = "评论已删除"
                        ) as T
                    }
                    commentRepository.emitDeleted(DeletedComment(commentId = commentId))
                    true
                },
                onFailure = { err ->
                    stateFlow.update { current ->
                        @Suppress("UNCHECKED_CAST")
                        current.copyWithComments(
                            commentActionMessage = err.message ?: "删除评论失败"
                        ) as T
                    }
                    false
                }
            )
        } finally {
            inFlightMutex.withLock { deletingCommentIds.remove(commentId) }
        }
    }

    suspend fun <T : CommentAwareUiState> deleteSubReply(
        stateFlow: MutableStateFlow<T>,
        commentId: Long,
        replyId: Long
    ): Boolean {
        val key = commentId to replyId
        val admitted = inFlightMutex.withLock { deletingReplyIds.add(key) }
        if (!admitted) return false
        try {
            val result = commentRepository.deleteComment(nodeId = replyId, type = "comment_reply")
            return result.fold(
                onSuccess = {
                    stateFlow.update { current ->
                        @Suppress("UNCHECKED_CAST")
                        current.copyWithComments(
                            hotComments = CommentMutations.applyReplyRemoved(current.hotComments, commentId, replyId),
                            latestComments = CommentMutations.applyReplyRemoved(current.latestComments, commentId, replyId),
                            commentActionMessage = "回复已删除"
                        ) as T
                    }
                    commentRepository.emitDeleted(DeletedComment(commentId = commentId, replyId = replyId))
                    true
                },
                onFailure = { err ->
                    stateFlow.update { current ->
                        @Suppress("UNCHECKED_CAST")
                        current.copyWithComments(
                            commentActionMessage = err.message ?: "删除回复失败"
                        ) as T
                    }
                    false
                }
            )
        } finally {
            inFlightMutex.withLock { deletingReplyIds.remove(key) }
        }
     }

    fun <T : CommentAwareUiState> toggleCommentPraise(
        scope: CoroutineScope,
        stateFlow: MutableStateFlow<T>,
        commentId: Long,
        relateNodeId: Long
    ) {
        if (relateNodeId <= 0L) return
        scope.launch {
            val admitted = inFlightMutex.withLock {
                if (commentId in stateFlow.value.pendingPraiseIds) false
                else praisingCommentIds.add(commentId)
            }
            if (!admitted) return@launch
            try {
                val target = (stateFlow.value.hotComments + stateFlow.value.latestComments)
                    .firstOrNull { it.id == commentId }
                    ?: return@launch
                val snapshotPraised = target.praiseState
                val snapshotCount = target.praiseCount
                stateFlow.update { current ->
                    @Suppress("UNCHECKED_CAST")
                    current.copyWithComments(
                        pendingPraiseIds = current.pendingPraiseIds + commentId,
                        hotComments = CommentMutations.applyPraise(current.hotComments, commentId),
                        latestComments = CommentMutations.applyPraise(current.latestComments, commentId)
                    ) as T
                }
                val result = commentRepository.togglePraise(
                    commentId = commentId,
                    relateNodeId = relateNodeId,
                    praised = snapshotPraised
                )
                result.fold(
                    onSuccess = {
                        stateFlow.update { current ->
                            @Suppress("UNCHECKED_CAST")
                            current.copyWithComments(
                                pendingPraiseIds = current.pendingPraiseIds - commentId
                            ) as T
                        }
                        val updated = stateFlow.value.latestComments.firstOrNull { it.id == commentId }
                            ?: stateFlow.value.hotComments.firstOrNull { it.id == commentId }
                        if (updated != null) commentRepository.emitCommentUpdate(updated)
                    },
                    onFailure = { err ->
                        stateFlow.update { current ->
                            @Suppress("UNCHECKED_CAST")
                            current.copyWithComments(
                                pendingPraiseIds = current.pendingPraiseIds - commentId,
                                hotComments = current.hotComments.map { item ->
                                    if (item.id != commentId) item
                                    else item.copy(praiseState = snapshotPraised, praiseCount = snapshotCount)
                                },
                                latestComments = current.latestComments.map { item ->
                                    if (item.id != commentId) item
                                    else item.copy(praiseState = snapshotPraised, praiseCount = snapshotCount)
                                },
                                commentActionMessage = err.message ?: "点赞操作失败"
                            ) as T
                        }
                        val rolledBack = stateFlow.value.latestComments.firstOrNull { it.id == commentId }
                            ?: stateFlow.value.hotComments.firstOrNull { it.id == commentId }
                        if (rolledBack != null) commentRepository.emitCommentUpdate(rolledBack)
                    }
                )
            } finally {
                inFlightMutex.withLock { praisingCommentIds.remove(commentId) }
            }
        }
    }

    private val submittingReplyIds = mutableSetOf<Long>()

    suspend fun <T : CommentAwareUiState> submitCommentReply(
        stateFlow: MutableStateFlow<T>,
        commentId: Long,
        target: CommentReplyItem?,
        content: String,
        stickers: List<String>
    ): Boolean {
        if (content.isBlank() && stickers.isEmpty()) return false
        val admitted = inFlightMutex.withLock { submittingReplyIds.add(commentId) }
        if (!admitted) return false
        try {
            return submitCommentReplyLocked(stateFlow, commentId, target, content, stickers)
        } finally {
            inFlightMutex.withLock { submittingReplyIds.remove(commentId) }
        }
    }

    private suspend fun <T : CommentAwareUiState> submitCommentReplyLocked(
        stateFlow: MutableStateFlow<T>,
        commentId: Long,
        target: CommentReplyItem?,
        content: String,
        stickers: List<String>
    ): Boolean {
        val result = commentRepository.addReply(
            commentId = commentId,
            content = content,
            toUserId = CommentMutations.resolveReplyToUserId(target),
            toReplyId = target?.id,
            emoticon = buildEmoticonParam(stickers)
        )
        return result.fold(
            onSuccess = { reply ->
                stateFlow.update { current ->
                    @Suppress("UNCHECKED_CAST")
                    current.copyWithComments(
                        hotComments = CommentMutations.applyReplyAppended(current.hotComments, commentId, reply),
                        latestComments = CommentMutations.applyReplyAppended(current.latestComments, commentId, reply),
                        commentActionMessage = "回复发表成功"
                    ) as T
                }
                val updated = stateFlow.value.latestComments.firstOrNull { it.id == commentId }
                    ?: stateFlow.value.hotComments.firstOrNull { it.id == commentId }
                if (updated != null) commentRepository.emitCommentUpdate(updated)
                true
            },
            onFailure = { err ->
                stateFlow.update { current ->
                    @Suppress("UNCHECKED_CAST")
                    current.copyWithComments(
                        commentActionMessage = err.message ?: "回复发表失败"
                    ) as T
                }
                false
            }
        )
    }

    private var submittingTopLevel = false

    suspend fun <T : CommentAwareUiState> postTopLevelComment(
        stateFlow: MutableStateFlow<T>,
        nodeId: Long,
        content: String,
        stickers: List<String>
    ): Boolean {
        if ((content.isBlank() && stickers.isEmpty()) || nodeId <= 0L) return false
        val admitted = inFlightMutex.withLock {
            if (submittingTopLevel) false else {
                submittingTopLevel = true
                true
            }
        }
        if (!admitted) return false
        try {
            val result = commentRepository.addComment(nodeId = nodeId, content = content, emoticon = buildEmoticonParam(stickers))
            return result.fold(
                onSuccess = {
                    stateFlow.update { current ->
                        @Suppress("UNCHECKED_CAST")
                        current.copyWithComments(
                            commentActionMessage = "评论发表成功"
                        ) as T
                    }
                    true
                },
                onFailure = { err ->
                    stateFlow.update { current ->
                        @Suppress("UNCHECKED_CAST")
                        current.copyWithComments(
                            commentActionMessage = err.message ?: "评论发表失败"
                        ) as T
                    }
                    false
                }
            )
        } finally {
            inFlightMutex.withLock { submittingTopLevel = false }
        }
    }

    fun <T : CommentAwareUiState> consumeCommentActionMessage(stateFlow: MutableStateFlow<T>) {
        stateFlow.update { current ->
            @Suppress("UNCHECKED_CAST")
            current.copyWithComments(commentActionMessage = null) as T
        }
    }

    suspend fun <T : CommentPagedUiState> loadInitialComments(
        stateFlow: MutableStateFlow<T>,
        nodeId: Long,
        fetch: CommentPageFetch,
        pageSize: Int = 10
    ) {
        if (nodeId <= 0L) return
        stateFlow.update { current ->
            @Suppress("UNCHECKED_CAST")
            current.copyWithPaged(isCommentsLoading = true, commentsError = null) as T
        }
        val hotResult: Result<List<CommentItem>>
        val latestResult: Result<List<CommentItem>>
        coroutineScope {
            val hotDeferred = async { fetch(nodeId, "hot_top", 1, pageSize) }
            val latestDeferred = async { fetch(nodeId, null, 1, pageSize) }
            hotResult = hotDeferred.await()
            latestResult = latestDeferred.await()
        }
        val hotList = hotResult.getOrDefault(emptyList())
        val latestList = latestResult.getOrDefault(emptyList())
        stateFlow.update { current ->
            @Suppress("UNCHECKED_CAST")
            current.copyWithPaged(
                isCommentsLoading = false,
                hotComments = hotList,
                latestComments = latestList,
                commentsPage = 1,
                hasMoreComments = ListPaging.hasMoreByFullPage(latestList.size, pageSize),
                commentsError = if (latestResult.isFailure && latestList.isEmpty()) {
                    val err = latestResult.exceptionOrNull()
                    if (err is ApiException && (err.code == -9007 || err.message.contains("找不到该评论"))) {
                        null
                    } else {
                        err?.message ?: "拉取评论失败"
                    }
                } else null
            ) as T
        }
    }

    suspend fun <T : CommentPagedUiState> loadNextCommentsPage(
        stateFlow: MutableStateFlow<T>,
        nodeId: Long,
        fetch: CommentPageFetch,
        pageSize: Int = 10
    ) {
        val state = stateFlow.value
        if (nodeId <= 0L || state.isCommentsLoading || state.isLoadingMoreComments || !state.hasMoreComments) return
        stateFlow.update { current ->
            @Suppress("UNCHECKED_CAST")
            current.copyWithPaged(isLoadingMoreComments = true, commentsError = null) as T
        }
        val nextPage = state.commentsPage + 1
        val result = fetch(nodeId, null, nextPage, pageSize)
        result.fold(
            onSuccess = { newItems ->
                stateFlow.update { current ->
                    val merged = ListPaging.appendPage(current.latestComments, newItems) { it.id }
                    val hasMore = ListPaging.hasMoreAfterPage(
                        fetchedCount = newItems.size,
                        addedCount = merged.size - current.latestComments.size,
                        pageSize = pageSize
                    )
                    @Suppress("UNCHECKED_CAST")
                    current.copyWithPaged(
                        isLoadingMoreComments = false,
                        latestComments = merged,
                        commentsPage = nextPage,
                        hasMoreComments = hasMore
                    ) as T
                }
            },
            onFailure = { err ->
                if (err is ApiException && (err.code == -9007 || err.message.contains("找不到该评论"))) {
                    stateFlow.update { current ->
                        @Suppress("UNCHECKED_CAST")
                        current.copyWithPaged(
                            isLoadingMoreComments = false,
                            hasMoreComments = false
                        ) as T
                    }
                } else {
                    stateFlow.update { current ->
                        @Suppress("UNCHECKED_CAST")
                        current.copyWithPaged(
                            isLoadingMoreComments = false,
                            commentsError = err.message ?: "加载更多评论失败"
                        ) as T
                    }
                }
            }
        )
    }
}
