package com.nobody.repoi.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.PostDetailData
import com.nobody.repoi.data.remote.model.StickerPackData
import com.nobody.repoi.data.repository.PostRepository
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.ui.common.comment.CommentFeedState
import com.nobody.repoi.util.buildEmoticonParam
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 讨论贴详情页 UI 状态模型。
 */
data class PostDetailUiState(
    val detail: PostDetailData? = null,
    val isInitialLoading: Boolean = true,
    val detailError: String? = null,
    val isRefreshing: Boolean = false,
    val comments: List<CommentItem> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = true,
    val commentsPage: Int = 1,
    val order: String = "", // "" 为最新（倒序），"asc" 为最早（正序）
    val commentsError: String? = null,
    val isDeletingPost: Boolean = false,
    val postDeleted: Boolean = false,
    val currentUserId: Long = 0L
) {
    val totalCommentCount: Int
        get() = detail?.discussCount?.takeIf { it > 0 }
            ?: detail?.commentCount?.takeIf { it > 0 }
            ?: comments.size

    val commentFeedState: CommentFeedState
        get() = CommentFeedState(
            totalCommentCount = totalCommentCount,
            hotComments = emptyList(),
            latestComments = comments,
            isCommentsLoading = isCommentsLoading,
            isLoadingMoreComments = isLoadingMoreComments,
            hasMoreComments = hasMoreComments,
            commentsError = commentsError
        )
}

/**
 * 帖子详情页 ViewModel，负责帖子详情、多图与评论列表单向数据流管理。
 */
class PostDetailViewModel(
    private val postRepository: PostRepository,
    private val stickerRepository: StickerRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var activePostId: Long = 0L
    private var activeNodeId: Long? = null

    /**
     * 获取当前帖子有效全局节点 ID (nodeId)。
     * 优先取已明确解析的 activeNodeId 或详情中的 detail.id，若未获取则回退至 activePostId。
     */
    val effectiveNodeId: Long
        get() = activeNodeId ?: _uiState.value.detail?.id?.takeIf { it > 0L } ?: activePostId

    fun setPostId(postId: Long, initialTitle: String? = null, nodeId: Long? = null) {
        if (postId <= 0L || (activePostId == postId && activeNodeId == nodeId)) return
        activePostId = postId
        activeNodeId = nodeId
        _uiState.value = PostDetailUiState(
            currentUserId = tokenManager.currentUserId,
            detail = if (!initialTitle.isNullOrBlank()) {
                PostDetailData(id = nodeId ?: 0L, itemId = postId, title = initialTitle)
            } else null,
            isInitialLoading = true
        )
        loadDetail()
    }

    fun loadDetail() {
        val postId = activePostId
        val initialNodeId = activeNodeId
        if (postId <= 0L && (initialNodeId == null || initialNodeId <= 0L)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInitialLoading = it.detail == null || it.detail.content.isNullOrBlank(),
                    detailError = null,
                    isCommentsLoading = true,
                    commentsError = null,
                    commentsPage = 1,
                    currentUserId = tokenManager.currentUserId
                )
            }

            coroutineScope {
                val detailDeferred = async { postRepository.getPostDetail(postId, initialNodeId) }
                val commentsDeferred = if (initialNodeId != null && initialNodeId > 0L) {
                    async {
                        postRepository.getDiscussList(initialNodeId, order = _uiState.value.order, page = 1)
                    }
                } else null

                val detailResult = detailDeferred.await()
                detailResult.fold(
                    onSuccess = { detail ->
                        activeNodeId = detail.id
                        _uiState.update {
                            it.copy(
                                detail = detail,
                                isInitialLoading = false,
                                detailError = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isInitialLoading = false,
                                detailError = error.message ?: "加载帖子失败"
                            )
                        }
                    }
                )

                val commentsResult = commentsDeferred?.await() ?: run {
                    val resolvedNodeId = activeNodeId
                    if (resolvedNodeId != null && resolvedNodeId > 0L) {
                        postRepository.getDiscussList(resolvedNodeId, order = _uiState.value.order, page = 1)
                    } else {
                        Result.failure(ApiException(-1, "无法获取帖子评论"))
                    }
                }

                commentsResult.fold(
                    onSuccess = { commentList ->
                        _uiState.update {
                            it.copy(
                                comments = commentList,
                                isCommentsLoading = false,
                                commentsError = null,
                                hasMoreComments = commentList.size >= 20,
                                commentsPage = 1
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isCommentsLoading = false,
                                commentsError = error.message ?: "加载回帖失败"
                            )
                        }
                    }
                )
            }
        }
    }

    fun refresh() {
        val postId = activePostId
        val nodeId = effectiveNodeId
        if (postId <= 0L && nodeId <= 0L) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, currentUserId = tokenManager.currentUserId) }

            coroutineScope {
                val detailDeferred = async { postRepository.getPostDetail(postId, nodeId) }
                val commentsDeferred = async {
                    postRepository.getDiscussList(nodeId, order = _uiState.value.order, page = 1)
                }

                val detailResult = detailDeferred.await()
                val commentsResult = commentsDeferred.await()

                detailResult.onSuccess { detail ->
                    activeNodeId = detail.id
                    _uiState.update { it.copy(detail = detail, detailError = null) }
                }

                commentsResult.fold(
                    onSuccess = { commentList ->
                        _uiState.update {
                            it.copy(
                                comments = commentList,
                                commentsPage = 1,
                                hasMoreComments = commentList.size >= 20,
                                commentsError = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(commentsError = error.message ?: "刷新回帖失败") }
                    }
                )

                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun loadNextCommentsPage() {
        val nodeId = effectiveNodeId
        val state = _uiState.value
        if (nodeId <= 0L || state.isLoadingMoreComments || !state.hasMoreComments || state.isCommentsLoading) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreComments = true) }
            val nextPage = state.commentsPage + 1
            val result = postRepository.getDiscussList(
                postId = nodeId,
                order = state.order,
                page = nextPage
            )
            result.fold(
                onSuccess = { newItems ->
                    _uiState.update {
                        it.copy(
                            comments = it.comments + newItems,
                            commentsPage = nextPage,
                            hasMoreComments = newItems.size >= 20,
                            isLoadingMoreComments = false,
                            commentsError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingMoreComments = false,
                            commentsError = error.message ?: "加载更多回帖失败"
                        )
                    }
                }
            )
        }
    }

    fun setOrder(order: String) {
        if (_uiState.value.order == order) return
        val nodeId = effectiveNodeId
        _uiState.update {
            it.copy(
                order = order,
                commentsPage = 1,
                isCommentsLoading = true,
                comments = emptyList()
            )
        }
        if (nodeId <= 0L) return

        viewModelScope.launch {
            val result = postRepository.getDiscussList(nodeId, order = order, page = 1)
            result.fold(
                onSuccess = { commentList ->
                    _uiState.update {
                        it.copy(
                            comments = commentList,
                            isCommentsLoading = false,
                            hasMoreComments = commentList.size >= 20,
                            commentsPage = 1,
                            commentsError = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCommentsLoading = false,
                            commentsError = error.message ?: "切换排序失败"
                        )
                    }
                }
            )
        }
    }

    suspend fun submitTopLevelComment(content: String, stickers: List<String>): Boolean {
        val nodeId = effectiveNodeId
        if (nodeId <= 0L || (content.isBlank() && stickers.isEmpty())) return false

        val emoticonParam = buildEmoticonParam(stickers)
        val result = postRepository.addDiscuss(nodeId, content, emoticonParam)
        return result.fold(
            onSuccess = {
                _uiState.update {
                    val updatedDetail = it.detail?.let { d ->
                        d.copy(discussCount = d.discussCount + 1)
                    }
                    it.copy(detail = updatedDetail)
                }
                refreshCommentsOnly()
                true
            },
            onFailure = { false }
        )
    }

    suspend fun submitReply(
        comment: CommentItem,
        target: CommentReplyItem?,
        content: String,
        stickers: List<String>
    ): Boolean {
        val nodeId = effectiveNodeId
        if (nodeId <= 0L || (content.isBlank() && stickers.isEmpty())) return false

        val emoticonParam = buildEmoticonParam(stickers)
        val toUserId = target?.user?.effectiveUserId
        val toReplyId = target?.id?.takeIf { it > 0L }

        val result = postRepository.addDiscussReply(
            postId = nodeId,
            discussId = comment.id,
            content = content,
            toUserId = toUserId,
            toReplyId = toReplyId,
            emoticon = emoticonParam
        )
        return result.fold(
            onSuccess = {
                refreshCommentsOnly()
                true
            },
            onFailure = { false }
        )
    }

    suspend fun deleteComment(comment: CommentItem): Boolean {
        val result = postRepository.deleteDiscuss(id = comment.id, isSubReply = false)
        return result.fold(
            onSuccess = {
                _uiState.update { state ->
                    val updatedComments = state.comments.filter { it.id != comment.id }
                    val updatedDetail = state.detail?.let { d ->
                        d.copy(discussCount = (d.discussCount - 1).coerceAtLeast(0))
                    }
                    state.copy(comments = updatedComments, detail = updatedDetail)
                }
                true
            },
            onFailure = { false }
        )
    }

    suspend fun deleteReply(comment: CommentItem, reply: CommentReplyItem): Boolean {
        val result = postRepository.deleteDiscuss(id = reply.id, isSubReply = true)
        return result.fold(
            onSuccess = {
                _uiState.update { state ->
                    val updatedComments = state.comments.map { c ->
                        if (c.id == comment.id) {
                            c.copy(
                                reply = (c.reply - 1).coerceAtLeast(0),
                                replys = c.replys?.filter { it.id != reply.id }
                            )
                        } else {
                            c
                        }
                    }
                    state.copy(comments = updatedComments)
                }
                true
            },
            onFailure = { false }
        )
    }

    suspend fun deletePost(): Boolean {
        val nodeId = effectiveNodeId
        if (nodeId <= 0L) return false

        _uiState.update { it.copy(isDeletingPost = true) }
        val result = postRepository.deletePost(nodeId)
        return result.fold(
            onSuccess = {
                _uiState.update { it.copy(isDeletingPost = false, postDeleted = true) }
                true
            },
            onFailure = {
                _uiState.update { it.copy(isDeletingPost = false) }
                false
            }
        )
    }

    suspend fun loadStickerPacks(): List<StickerPackData> =
        stickerRepository.getCommentPacks().getOrDefault(emptyList())

    private fun refreshCommentsOnly() {
        val nodeId = effectiveNodeId
        if (nodeId <= 0L) return

        viewModelScope.launch {
            val result = postRepository.getDiscussList(nodeId, order = _uiState.value.order, page = 1)
            result.onSuccess { commentList ->
                _uiState.update {
                    it.copy(
                        comments = commentList,
                        commentsPage = 1,
                        hasMoreComments = commentList.size >= 20,
                        commentsError = null
                    )
                }
            }
        }
    }
}

private val com.nobody.repoi.data.remote.model.CommentUser.effectiveUserId: Long
    get() = if (id > 0L) id else userId
