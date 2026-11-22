package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem

data class ZoneBoardItemActions(

    val onActionClick: ((ActionItem) -> Unit)? = null,

    val onActionImageClick: ((imageUrls: List<String>, initialIndex: Int) -> Unit)? = null,

    val onPostClick: ((PostItem) -> Unit)? = null,

    val onPostImageClick: ((imageUrls: List<String>, initialIndex: Int) -> Unit)? = null,

    val onPostUserClick: ((userId: Long) -> Unit)? = null,

    val onAlbumClick: ((AlbumListItem) -> Unit)? = null,

    val onResaleClick: ((ResaleSearchItem) -> Unit)? = null,

    val onResaleUserClick: ((Long) -> Unit)? = null,

    val onResaleContactClick: ((ResaleSearchItem) -> Unit)? = null,

    val onResaleMoreClick: ((ResaleSearchItem) -> Unit)? = null,

    val onEntityClick: ((ZoneBoard, ZoneEntityItem) -> Unit)? = null,

    val onHobbyClick: ((HobbyItemSummary) -> Unit)? = null,

    val onUrlClick: ((String) -> Unit)? = null
)
