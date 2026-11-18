package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.WorksCharacterItem
import com.nobody.repoi.data.remote.model.WorksDetailResult

class WorksDetailRepository(
    private val api: HpoiApi
) {
    suspend fun getWorksDetail(worksId: Long): Result<WorksDetailResult> = runCatching {
        api.getWorksDetail(worksId = worksId).result
            ?: throw ApiException(-1, "未找到该作品数据")
    }

    suspend fun getWorksHobbys(nodeId: Long): Result<List<HobbyItemSummary>> = runCatching {
        api.queryHobbyList(
            mapOf(
                "works" to nodeId.toString(),
                "page" to "1",
                "pageSize" to "9",
                "order" to "hits7Day"
            )
        ).list.orEmpty()
    }

    suspend fun getWorksAlbums(worksId: Long): Result<List<AlbumData>> = runCatching {
        api.getWorksAlbums(worksId = worksId).items
    }

    suspend fun getWorksCharacters(worksId: Long): Result<List<WorksCharacterItem>> = runCatching {
        api.getWorksCharacters(worksId = worksId).worksLinkCharacter.orEmpty()
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
}
