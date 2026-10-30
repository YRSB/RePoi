package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentUser
import com.nobody.repoi.data.remote.model.StickerPackData
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.DeletedComment
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserRepository
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

/**
 * 评论交互协调委托：统一详情页评论点赞/回复与跨页广播合并。
 *
 * 负责统一管理：
 * 1. 监听 [CommentRepository.commentUpdates] 广播并自动合并回写；
 * 2. 主评论点赞切换（乐观更新、接口调用、广播与失败回滚）；
 * 3. 子评论回复发表（接口调用、本地追加、广播与失败处理，返回 Boolean 表明成功与否）；
 * 4. 主楼评论发表（接口调用与失败处理，成功后由调用方刷新列表，返回 Boolean 表明成功与否）；
 * 5. 消费操作反馈提示。
 */
class CommentInteractionDelegate(
    private val commentRepository: CommentRepository,
    private val stickerRepository: StickerRepository,
    private val tokenManager: TokenManager,
    private val userRepository: UserRepository
) {

    val currentUserId: Long
        get() = tokenManager.currentUserId

    suspend fun resolveCurrentUserId(): Long {
        val cached = tokenManager.currentUserId
        if (cached > 0L || tokenManager.currentToken.isBlank()) return cached
        userRepository.getUserProfile()
        return tokenManager.currentUserId
    }

    fun isOwnComment(user: CommentUser?, currentId: Long = currentUserId): Boolean {
        if (currentId <= 0L || user == null) return false
        return user.id == currentId || user.userId == currentId
    }

    suspend fun loadStickerPacks(): List<StickerPackData> =
        stickerRepository.getCommentPacks().getOrDefault(emptyList())

    /**
     * 监听外部/评论详情页回写的主楼更新广播，并原子合并到热/最新评论列表中。
     */
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
    /** 进行中的主评论点赞（按 commentId 去重，协程内原子判重，防止双击并发）。 */
    private val praisingCommentIds = mutableSetOf<Long>()

    /**
     * 在途去重守卫（原 `synchronized` 块的 KMP 等价实现；common 无 JVM 监视器）。
     *
     * 所有判重 + 占位都在同一 [Mutex] 内完成，与原语义一致：调用方协程在
     * `withLock` 上挂起点即排队，不存在“检查-占用”竞态。
     */
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

    /**
     * 切换主评论点赞状态（乐观更新，失败回滚）。
     */
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

    /** 进行中的回复发表（按主楼 id 去重，防止双击重复发表）。 */
    private val submittingReplyIds = mutableSetOf<Long>()

    /**
     * 发表子评论回复（成功后追加到主楼并广播更新）。
     *
     * 挂起函数，返回是否发表成功，便于 UI 决定是否清空草稿或关框。
     */
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

    /** 进行中的主楼发表（全局去重，防止双击重复发表）。 */
    private var submittingTopLevel = false

    /**
     * 发表主楼评论（成功后由调用方刷新列表，原版同样直接刷新）。
     *
     * 挂起函数，返回是否发表成功，便于 UI 决定是否清空草稿或关框。
     */
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

    /**
     * 消费一次性操作反馈信息。
     */
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
                    if (err is ApiException && (err.code == -9007 || err.message?.contains("找不到该评论") == true)) {
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
                if (err is ApiException && (err.code == -9007 || err.message?.contains("找不到该评论") == true)) {
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
