package com.nobody.poishelf.ui.character

import com.nobody.poishelf.data.remote.model.AlbumData
import com.nobody.poishelf.data.remote.model.CharacterData
import com.nobody.poishelf.data.remote.model.CharacterRelevantItem
import com.nobody.poishelf.data.remote.model.CharacterWorksItem
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.data.remote.model.ZoneItem
import com.nobody.poishelf.ui.common.comment.CommentPagedUiState

/**
 * 角色/要素详情页渐进式加载 UI 状态机。
 */
data class CharacterDetailUiState(
    val characterId: Long = 0L,
    val characterNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    // 角色主体信息
    val character: CharacterData? = null,
    val isIntroExpanded: Boolean = false,

    // 收藏状态
    val isCollected: Boolean = false,
    val originalCollected: Boolean = false,
    val collectCountDelta: Int = 0,

    // 分区/属性标签
    val isZonesLoading: Boolean = false,
    val zones: List<ZoneItem> = emptyList(),

    // 登场作品
    val isWorksLoading: Boolean = false,
    val works: List<CharacterWorksItem> = emptyList(),

    // 周边手办
    val isHobbysLoading: Boolean = false,
    val hobbys: List<HobbyItemSummary> = emptyList(),

    // 相关相册
    val isAlbumsLoading: Boolean = false,
    val albums: List<AlbumData> = emptyList(),

    // 推荐要素/相关角色
    val isRelevantLoading: Boolean = false,
    val relevantCharacters: List<CharacterRelevantItem> = emptyList(),

    // 评论系统（神评 + 无限流）
    override val isCommentsLoading: Boolean = false,
    override val hotComments: List<CommentItem> = emptyList(),
    override val latestComments: List<CommentItem> = emptyList(),
    override val commentsPage: Int = 1,
    override val isLoadingMoreComments: Boolean = false,
    override val hasMoreComments: Boolean = true,
    override val commentsError: String? = null,
    override val pendingPraiseIds: Set<Long> = emptySet(),
    override val commentActionMessage: String? = null
) : CommentPagedUiState {

    /** 计算展示的收藏数量（基准数 + 本地乐观增量） */
    val displayCollectCount: Int
        get() = ((character?.collect ?: 0) + collectCountDelta).coerceAtLeast(0)

    override fun copyWithComments(
        hotComments: List<CommentItem>,
        latestComments: List<CommentItem>,
        pendingPraiseIds: Set<Long>,
        commentActionMessage: String?
    ): CharacterDetailUiState = copy(
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
    ): CommentPagedUiState = copy(
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
