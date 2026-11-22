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

class VendorDetailRepository(
    private val api: HpoiApi,
    private val collectRepository: CollectRepository
) {

    suspend fun getCompanyDetail(companyId: Long): Result<CompanyData> = runCatching {
        api.getCompanyDetail(companyId = companyId).company
            ?: throw ApiException(-1, "未找到该厂商数据")
    }

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

    suspend fun getCompanyMyVotes(companyId: Long): Result<CompanyMyVotesData> = runCatching {
        api.getCompanyMyVotes(companyId = companyId)
    }

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

    suspend fun getCompanyTags(companyId: Long): Result<List<CompanyTagItem>> = runCatching {
        api.getCompanyTags(companyId = companyId).list.orEmpty()
    }

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

    suspend fun getCollectState(companyNodeId: Long): Result<Boolean> =
        collectRepository.refreshCollectState(companyNodeId).map { it?.isCollected == true }

    suspend fun getMaskState(companyNodeId: Long): Result<Boolean> = runCatching {
        val jsonObject = api.getMaskState(companyNodeId) as? JsonObject
        val state = (jsonObject?.get("state") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        state == 1
    }
}
