package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.api.HpoiApi
import com.nobody.poishelf.data.remote.model.AlbumData
import com.nobody.poishelf.data.remote.model.ApiException
import com.nobody.poishelf.data.remote.model.CharacterData
import com.nobody.poishelf.data.remote.model.CharacterRelevantItem
import com.nobody.poishelf.data.remote.model.CharacterWorksItem
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.data.remote.model.ZoneItem

/**
 * 角色/要素详情多端点聚合数据仓储。
 */
class CharacterDetailRepository(
    private val api: HpoiApi
) {
    /**
     * 获取角色主体详情 (api/charactar/get)
     */
    suspend fun getCharacterDetail(characterId: Long): Result<CharacterData> = runCatching {
        val response = api.getCharacterDetail(characterId = characterId)
        if (response.isSuccess) {
            response.data?.data ?: throw ApiException(response.code, "未找到该角色数据")
        } else {
            throw ApiException(response.code, response.msg ?: "获取角色详情失败")
        }
    }

    /**
     * 获取角色登场作品列表 (api/charactar/works/get)
     */
    suspend fun getCharacterWorks(
        characterId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<CharacterWorksItem>> = runCatching {
        val response = api.getCharacterWorks(
            characterId = characterId,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.items ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取角色登场作品失败")
        }
    }

    /**
     * 获取角色相关周边列表 (api/hobby/query-v2)
     */
    suspend fun getCharacterHobbys(nodeId: Long): Result<List<HobbyItemSummary>> = runCatching {
        val response = api.queryHobbyList(
            mapOf(
                "charactar" to nodeId.toString(),
                "character" to nodeId.toString(),
                "page" to "1",
                "pageSize" to "9",
                "order" to "hits7Day"
            )
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取角色相关周边失败")
        }
    }

    /**
     * 获取角色相关相册 (api/charactar/album/get)
     */
    suspend fun getCharacterAlbums(
        characterId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<AlbumData>> = runCatching {
        val response = api.getCharacterAlbums(
            characterId = characterId,
            page = page,
            pageSize = pageSize
        )
        if (response.isSuccess) {
            response.data?.items ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取角色相关相册失败")
        }
    }

    /**
     * 获取推荐要素/相关角色 (api/charactar/relevant/get)
     */
    suspend fun getCharacterRelevant(characterId: Long): Result<List<CharacterRelevantItem>> = runCatching {
        val response = api.getCharacterRelevant(characterId = characterId, pageSize = 10)
        if (response.isSuccess) {
            response.data?.items ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取推荐要素失败")
        }
    }

    /**
     * 获取条目关联的分区/属性标签 (api/zone/item)
     */
    suspend fun getItemZones(nodeId: Long): Result<List<ZoneItem>> = runCatching {
        val response = api.getItemZones(nodeId = nodeId)
        if (response.isSuccess) {
            response.data?.items ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取属性标签失败")
        }
    }

    /**
     * 切换收藏/想买状态 (api/collect/item/add, api/collect/del)
     * @param isCollected 当前是否已收藏，true 表示操作取消收藏，false 表示操作新增收藏
     * @return 切换后的最新收藏状态
     */
    suspend fun toggleCollect(nodeId: Long, isCollected: Boolean): Result<Boolean> = runCatching {
        val response = if (isCollected) {
            api.collectItemDel(id = nodeId)
        } else {
            api.collectItemAdd(id = nodeId)
        }
        if (response.isSuccess) {
            !isCollected
        } else {
            throw ApiException(response.code, response.msg ?: "收藏操作失败")
        }
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
