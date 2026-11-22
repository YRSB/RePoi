package com.nobody.repoi.ui.character

import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.CharacterData
import com.nobody.repoi.data.remote.model.CharacterRelevantItem
import com.nobody.repoi.data.remote.model.CharacterWorksItem
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.ui.common.comment.CommentPagedUiState

data class CharacterDetailUiState(
    val characterId: Long = 0L,
    val characterNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
    val isInitialLoading: Boolean = true,
    val errorMessage: String? = null,

    val character: CharacterData? = null,
    val isIntroExpanded: Boolean = false,

    val isCollected: Boolean = false,
    val originalCollected: Boolean = false,
    val collectCountDelta: Int = 0,

    val collectCountBase: Int? = null,

    val isZonesLoading: Boolean = false,
    val zones: List<ZoneItem> = emptyList(),

    val isWorksLoading: Boolean = false,
    val works: List<CharacterWorksItem> = emptyList(),

    val isHobbysLoading: Boolean = false,
    val hobbys: List<HobbyItemSummary> = emptyList(),

    val isAlbumsLoading: Boolean = false,
    val albums: List<AlbumData> = emptyList(),

    val isRelevantLoading: Boolean = false,
    val relevantCharacters: List<CharacterRelevantItem> = emptyList(),

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

    val displayCollectCount: Int
        get() = ((collectCountBase ?: (character?.collect ?: 0)) + collectCountDelta).coerceAtLeast(0)

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
