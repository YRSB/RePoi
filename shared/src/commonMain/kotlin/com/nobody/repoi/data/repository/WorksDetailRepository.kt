package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.WorksCharacterItem
import com.nobody.repoi.data.remote.model.WorksDetailResult

class WorksDetailRepository(
    private val api: HpoiApi
) {
    suspend fun getWorksDetail(worksId: Long): Result<WorksDetailResult> = runCatching {
        val response = api.getWorksDetail(worksId = worksId)
        if (response.isSuccess) {
            response.data?.result ?: throw ApiException(response.code, "未找到该作品数据")
        } else {
            throw ApiException(response.code, response.msg ?: "获取作品详情失败")
        }
    }

    suspend fun getWorksHobbys(nodeId: Long): Result<List<HobbyItemSummary>> = runCatching {
        val response = api.queryHobbyList(
            mapOf(
                "works" to nodeId.toString(),
                "page" to "1",
                "pageSize" to "9",
                "order" to "hits7Day"
            )
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            emptyList()
        }
    }

    suspend fun getWorksAlbums(worksId: Long): Result<List<AlbumData>> = runCatching {
        val response = api.getWorksAlbums(worksId = worksId)
        if (response.isSuccess) {
            response.data?.items ?: emptyList()
        } else {
            emptyList()
        }
    }

    suspend fun getWorksCharacters(worksId: Long): Result<List<WorksCharacterItem>> = runCatching {
        val response = api.getWorksCharacters(worksId = worksId)
        if (response.isSuccess) {
            response.data?.worksLinkCharacter ?: emptyList()
        } else {
            emptyList()
        }
    }

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
            throw ApiException(response.code, response.msg ?: "获取评论失败")
        }
    }
}
