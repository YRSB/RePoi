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

/**
 * 角色/要素详情多端点聚合数据仓储。
 *
 * 本仓储只承载详情/作品/相册等只读聚合查询；收藏切换由 ViewModel 直接调用
 * `CollectRepository.toggleItemCollect`（依赖倒置：详情仓储不再转手写操作）。
 */
class CharacterDetailRepository(
    private val api: HpoiApi,
) {
    /**
     * 获取角色主体详情 (api/charactar/get)
     */
    suspend fun getCharacterDetail(characterId: Long): Result<CharacterData> = runCatching {
        api.getCharacterDetail(characterId = characterId).data
            ?: throw ApiException(-1, "未找到该角色数据")
    }

    /**
     * 获取角色登场作品列表 (api/charactar/works/get)
     */
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

    /**
     * 获取角色相关周边列表 (api/hobby/query-v2)
     */
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

    /**
     * 获取角色相关相册 (api/charactar/album/get)
     */
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

    /**
     * 获取推荐要素/相关角色 (api/charactar/relevant/get)
     */
    suspend fun getCharacterRelevant(characterId: Long): Result<List<CharacterRelevantItem>> = runCatching {
        api.getCharacterRelevant(characterId = characterId, pageSize = 10).items
    }

    /**
     * 获取条目关联的分区/属性标签 (api/zone/item)
     */
    suspend fun getItemZones(nodeId: Long): Result<List<ZoneItem>> = runCatching {
        api.getItemZones(nodeId = nodeId).items
    }

    /**
     * 获取角色评论列表 (api/comment/get)
     */
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
