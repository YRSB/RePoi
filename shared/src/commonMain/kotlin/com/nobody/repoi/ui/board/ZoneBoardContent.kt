package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem

data class ZoneBoardPageView(
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val isEndReached: Boolean = false
)

sealed interface ZoneBoardContent {

    val page: ZoneBoardPageView

    data class Action(
        val items: List<ActionItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    data class Album(
        val items: List<AlbumListItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    data class Posts(
        val pinnedPosts: List<PostItem>,
        val items: List<PostItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    data class Hobby(
        val items: List<HobbyItemSummary>,
        val isGridMode: Boolean,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    data class Resale(
        val items: List<ResaleSearchItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    data class Entity(
        val items: List<ZoneEntityItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent
}

val ZoneBoardContent.itemCount: Int
    get() = when (this) {
        is ZoneBoardContent.Action -> items.size
        is ZoneBoardContent.Album -> items.size
        is ZoneBoardContent.Posts -> items.size + pinnedPosts.size
        is ZoneBoardContent.Hobby -> items.size
        is ZoneBoardContent.Resale -> items.size
        is ZoneBoardContent.Entity -> items.size
    }
