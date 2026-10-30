package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.data.remote.model.RelatedAlbumItem
import com.nobody.repoi.data.remote.model.RelatedHobbyItem

/**
 * 相册详情多端点聚合数据仓储。
 */
class AlbumDetailRepository(
    private val api: HpoiApi
) {

    /**
     * 获取相册详情基础数据 (api/album/detail)。
     */
    suspend fun getAlbumDetail(albumId: Long, itemId: Long? = null): Result<AlbumData> = runCatching {
        val response = api.getAlbumDetail(albumId = albumId, itemId = itemId)
        if (response.isSuccess) {
            response.data?.album ?: throw ApiException(response.code, "未找到相册详情数据")
        } else {
            throw ApiException(response.code, response.msg ?: "获取相册详情失败")
        }
    }

    /**
     * 获取相册分类与属性标签 (api/item/tags)。
     */
    suspend fun getAlbumTags(albumId: Long): Result<List<String>> = runCatching {
        val response = api.getTags(albumId = albumId)
        if (response.isSuccess) {
            response.data?.tags?.mapNotNull { it.tagText.ifBlank { null } } ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取相册原画图集列表 (api/pic/list/relate-v2)。
     */
    suspend fun getAlbumPictures(
        albumId: Long,
        itemId: Long? = null,
        page: Int = 1,
        pageSize: Int = 20,
        order: String? = null
    ): Result<List<PicItem>> = runCatching {
        val response = api.getPicList(
            itemId = itemId ?: albumId,
            itemType = "album",
            page = page,
            pageSize = pageSize,
            order = order,
            nodeId = albumId
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取相册图片列表失败")
        }
    }

    /**
     * 获取相册关联手办列表 (api/item/relate/hobby)。
     */
    suspend fun getRelatedHobbies(
        albumId: Long,
        itemType: String = "album",
        pageSize: Int = 8
    ): Result<List<RelatedHobbyItem>> = runCatching {
        val response = api.getRelatedHobbies(
            albumId = albumId,
            itemType = itemType,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.hobbys ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取同款/相关相册列表 (api/album/relate/album)。
     */
    suspend fun getRelatedAlbums(
        albumId: Long,
        page: Int = 1,
        pageSize: Int = 8
    ): Result<List<RelatedAlbumItem>> = runCatching {
        val response = api.getRelatedAlbums(
            albumId = albumId,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取相册评论列表 (api/comment/get)。
     * @param type 为 null 时拉取最新评论，"hot_top" 拉取热门神评。
     */
    suspend fun getComments(
        nodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<CommentItem>> = runCatching {
        val response = api.getComments(
            nodeId = nodeId,
            type = type,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else if (response.isCommentNotFound) {
            emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取相册评论失败")
        }
    }

    /**
     * 查询相册点赞状态 (api/item/likeInfo)。
     */
    suspend fun getAlbumLikeInfo(albumId: Long): Result<Boolean> = runCatching {
        val response = api.getAlbumLikeInfo(nodeId = albumId, itemType = "album")
        if (response.isSuccess) {
            response.data?.praiseState ?: false
        } else {
            throw ApiException(response.code, response.msg ?: "获取相册点赞状态失败")
        }
    }

    /**
     * 相册点赞 (api/item/praise/add)。
     */
    suspend fun praiseAlbumAdd(albumId: Long): Result<Unit> = runCatching {
        val response = api.praiseAlbumAdd(id = albumId, itemType = "album")
        if (response.isSuccess) {
            Unit
        } else {
            throw ApiException(response.code, response.msg ?: "相册点赞失败")
        }
    }

    /**
     * 取消相册点赞 (api/item/praise/del)。
     */
    suspend fun praiseAlbumDel(albumId: Long): Result<Unit> = runCatching {
        val response = api.praiseAlbumDel(id = albumId, itemType = "album")
        if (response.isSuccess) {
            Unit
        } else {
            throw ApiException(response.code, response.msg ?: "取消相册点赞失败")
        }
    }
}
