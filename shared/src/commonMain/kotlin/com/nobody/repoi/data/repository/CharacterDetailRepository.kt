package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CharacterData
import com.nobody.repoi.data.remote.model.CharacterRelevantItem
import com.nobody.repoi.data.remote.model.CharacterWorksItem
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.ZoneItem

class CharacterDetailRepository(
    private val api: HpoiApi,
) {

    suspend fun getCharacterDetail(characterId: Long): Result<CharacterData> = runCatching {
        api.getCharacterDetail(characterId = characterId).data
            ?: throw ApiException(-1, "未找到该角色数据")
    }

    suspend fun getCharacterWorks(
        characterId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<CharacterWorksItem>> = runCatching {
        api.getCharacterWorks(
            characterId = characterId,
            page = page,
            pageSize = pageSize
        ).items
    }

    suspend fun getCharacterHobbys(nodeId: Long): Result<List<HobbyItemSummary>> = runCatching {
        api.queryHobbyList(
            mapOf(
                "charactar" to nodeId.toString(),
                "character" to nodeId.toString(),
                "page" to "1",
                "pageSize" to "9",
                "order" to "hits7Day"
            )
        ).list.orEmpty()
    }

    suspend fun getCharacterAlbums(
        characterId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<AlbumData>> = runCatching {
        api.getCharacterAlbums(
            characterId = characterId,
            page = page,
            pageSize = pageSize
        ).items
    }

    suspend fun getCharacterRelevant(characterId: Long): Result<List<CharacterRelevantItem>> = runCatching {
        api.getCharacterRelevant(characterId = characterId, pageSize = 10).items
    }

    suspend fun getItemZones(nodeId: Long): Result<List<ZoneItem>> = runCatching {
        api.getItemZones(nodeId = nodeId).items
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
