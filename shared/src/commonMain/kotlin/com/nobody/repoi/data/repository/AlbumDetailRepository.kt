package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.data.remote.model.RelatedAlbumItem
import com.nobody.repoi.data.remote.model.RelatedHobbyItem

class AlbumDetailRepository(
    private val api: HpoiApi
) {

    suspend fun getAlbumDetail(albumId: Long, itemId: Long? = null): Result<AlbumData> = runCatching {
        api.getAlbumDetail(albumId = albumId, itemId = itemId).album
            ?: throw ApiException(-1, "未找到相册详情数据")
    }

    suspend fun getAlbumTags(albumId: Long): Result<List<String>> = runCatching {
        api.getTags(albumId = albumId).tags.orEmpty().mapNotNull { it.tagText.ifBlank { null } }
    }

    suspend fun getAlbumPictures(
        albumId: Long,
        itemId: Long? = null,
        page: Int = 1,
        pageSize: Int = 20,
        order: String? = null
    ): Result<List<PicItem>> = runCatching {
        api.getPicList(
            itemId = itemId ?: albumId,
            itemType = "album",
            page = page,
            pageSize = pageSize,
            order = order,
            nodeId = albumId
        ).list.orEmpty()
    }

    suspend fun getRelatedHobbies(
        albumId: Long,
        itemType: String = "album",
        pageSize: Int = 8
    ): Result<List<RelatedHobbyItem>> = runCatching {
        api.getRelatedHobbies(
            albumId = albumId,
            itemType = itemType,
            pageSize = pageSize
        ).hobbys.orEmpty()
    }

    suspend fun getRelatedAlbums(
        albumId: Long,
        page: Int = 1,
        pageSize: Int = 8
    ): Result<List<RelatedAlbumItem>> = runCatching {
        api.getRelatedAlbums(
            albumId = albumId,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }

    suspend fun getComments(
        nodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<CommentItem>> = runCatching {
        api.getComments(
            nodeId = nodeId,
            type = type,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }.recoverCatching { error ->
        if (error is CommentNotFoundException) emptyList() else throw error
    }

    suspend fun getAlbumLikeInfo(albumId: Long): Result<Boolean> = runCatching {
        api.getAlbumLikeInfo(nodeId = albumId, itemType = "album").praiseState
    }

    suspend fun praiseAlbumAdd(albumId: Long): Result<Unit> = runCatching {
        api.praiseAlbumAdd(id = albumId, itemType = "album")
    }

    suspend fun praiseAlbumDel(albumId: Long): Result<Unit> = runCatching {
        api.praiseAlbumDel(id = albumId, itemType = "album")
    }
}
