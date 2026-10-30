package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.PersonDetailResult
import com.nobody.repoi.data.remote.model.PersonHobbyEntry
import com.nobody.repoi.data.remote.model.PersonWorksEntry

/**
 * 人物详情多端点聚合数据仓储。
 */
class PersonDetailRepository(
    private val api: HpoiApi,
    private val collectRepository: CollectRepository
) {

    /**
     * 获取人物主体详情 (api/person/get)。
     * 注意：必须传入 personId (即 itemId)，评论/收藏用的节点 id 从返回的 person.id 获取。
     */
    suspend fun getPersonDetail(personId: Long): Result<PersonDetailResult> = runCatching {
        val response = api.getPersonDetail(personId = personId)
        if (response.isSuccess) {
            response.data?.result ?: throw ApiException(response.code, "未找到该人物数据")
        } else {
            throw ApiException(response.code, response.msg ?: "获取人物详情失败")
        }
    }

    /**
     * 获取人物参与作品列表 (api/person/works/get)。
     */
    suspend fun getPersonWorks(personId: Long): Result<List<PersonWorksEntry>> = runCatching {
        val response = api.getPersonWorks(personId = personId)
        if (response.isSuccess) {
            response.data?.worksList ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取人物出演角色列表 (api/person/character/get)。
     */
    suspend fun getPersonCharacter(personId: Long): Result<List<PersonWorksEntry>> = runCatching {
        val response = api.getPersonCharacter(personId = personId)
        if (response.isSuccess) {
            response.data?.characterList ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取人物参与周边列表 (api/person/hobby/get)。
     */
    suspend fun getPersonHobby(personId: Long): Result<List<PersonHobbyEntry>> = runCatching {
        val response = api.getPersonHobby(personId = personId)
        if (response.isSuccess) {
            response.data?.hobby ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 获取人物讨论与评论列表 (api/comment/get)。
     * @param type null 为拉取时序最新评论，"hot_top" 为拉取热门神评。
     */
    suspend fun getComments(
        personNodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<CommentItem>> = runCatching {
        val response = api.getComments(
            nodeId = personNodeId,
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

    /**
     * 查询人物收藏状态 (api/collect/state)。
     * 未收藏返回 false，失败时透出由调用方回退到主体详情初始值。
     */
    suspend fun getCollectState(personNodeId: Long): Result<Boolean> =
        collectRepository.getCollectState(personNodeId).map { it?.isCollected == true }
}
