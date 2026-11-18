package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.model.CompanyActionItem
import com.nobody.repoi.data.remote.model.CompanyData
import com.nobody.repoi.data.remote.model.CompanyMyVotesData
import com.nobody.repoi.data.remote.model.CompanySeriesItem
import com.nobody.repoi.data.remote.model.CompanyTagItem
import com.nobody.repoi.data.remote.model.RelatedHobbyItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
private data class CompanyTagScoreRequest(
    val tagId: Long,
    val score: Double
)

/**
 * 厂商详情多端点聚合数据仓储。
 */
class VendorDetailRepository(
    private val api: HpoiApi,
    private val collectRepository: CollectRepository
) {

    /**
     * 获取厂商主体详情 (api/company/v2/get)。
     * 注意：必须传入 companyId (即 itemId)。
     */
    suspend fun getCompanyDetail(companyId: Long): Result<CompanyData> = runCatching {
        api.getCompanyDetail(companyId = companyId).company
            ?: throw ApiException(-1, "未找到该厂商数据")
    }

    /**
     * 获取厂商动态列表 (api/user-action/company/list)。
     */
    suspend fun getCompanyActions(
        companyNodeId: Long,
        subType: String? = null,
        category: Int = 0,
        lastId: Long = 0L
    ): Result<List<CompanyActionItem>> = runCatching {
        api.getCompanyActions(
            companyNodeId = companyNodeId,
            subType = subType,
            category = category,
            lastId = lastId
        ).list.orEmpty()
    }

    /**
     * 获取当前用户的厂商印象/投票状态及所有候选标签 (api/company/tag/myVotes)。
     */
    suspend fun getCompanyMyVotes(companyId: Long): Result<CompanyMyVotesData> = runCatching {
        api.getCompanyMyVotes(companyId = companyId)
    }

    /**
     * 提交用户对该厂商的印象投票 (api/company/tag/vote)。
     * @param normalTagIds 选中的普通标签 ID 列表
     * @param scoreVotes 评分打星映射表 (tagId -> starScore 1.0~5.0)
     */
    suspend fun submitCompanyTagVote(
        companyId: Long,
        normalTagIds: List<Long>,
        scoreVotes: Map<Long, Double>
    ): Result<Unit> = runCatching {
        val normalTagJson = Json.encodeToString(normalTagIds)
        val scoreTagList = scoreVotes.map { (tagId, score) ->
            CompanyTagScoreRequest(tagId = tagId, score = score)
        }
        val scoreTagJson = Json.encodeToString(scoreTagList)
        api.submitCompanyTagVote(
            companyId = companyId,
            normalTagIds = normalTagJson,
            scoreTagIds = scoreTagJson
        )
    }

    /**
     * 获取厂商系列列表 (api/company/series)。
     */
    suspend fun getCompanySeries(
        companyNodeId: Long,
        page: Int = 1,
        pageSize: Int = 9
    ): Result<List<CompanySeriesItem>> = runCatching {
        api.getCompanySeries(
            companyNodeId = companyNodeId,
            page = page,
            pageSize = pageSize
        ).seriesList.orEmpty()
    }

    /**
     * 获取厂商标签列表 (api/company/tag/display)。
     */
    suspend fun getCompanyTags(companyId: Long): Result<List<CompanyTagItem>> = runCatching {
        api.getCompanyTags(companyId = companyId).list.orEmpty()
    }

    /**
     * 获取厂商代表手办产品列表 (api/item/relate/hobby)。
     * 默认取前 9 项以供 3x3 九宫格展示。
     */
    suspend fun getRelatedHobbies(
        companyNodeId: Long,
        pageSize: Int = 9
    ): Result<List<RelatedHobbyItem>> = runCatching {
        api.getRelatedHobbies(
            albumId = companyNodeId,
            itemType = "company",
            pageSize = pageSize
        ).hobbys.orEmpty()
    }

    /**
     * 获取厂商讨论与评论列表 (api/comment/get)。
     * @param type null 为拉取时序最新评论，"hot_top" 为拉取热门神评。
     */
    suspend fun getComments(
        companyNodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<List<CommentItem>> = runCatching {
        api.getComments(
            nodeId = companyNodeId,
            type = type,
            page = page,
            pageSize = pageSize
        ).list.orEmpty()
    }.recoverCatching { error ->
        if (error is CommentNotFoundException) emptyList() else throw error
    }

    /**
     * 查询厂商收藏状态 (api/collect/state)。
     * 未收藏返回 false，失败时透出由调用方兜底。
     */
    suspend fun getCollectState(companyNodeId: Long): Result<Boolean> =
        collectRepository.refreshCollectState(companyNodeId).map { it?.isCollected == true }

    /**
     * 查询厂商屏蔽状态 (api/mask/state-v2)。
     * state == 1 表示已屏蔽，若未登录或未屏蔽安全返回 false。
     */
    suspend fun getMaskState(companyNodeId: Long): Result<Boolean> = runCatching {
        val jsonObject = api.getMaskState(companyNodeId) as? JsonObject
        val state = (jsonObject?.get("state") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        state == 1
    }
}
