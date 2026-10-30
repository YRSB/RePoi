package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.ResaleDetailItem
import com.nobody.repoi.data.remote.model.ResaleSearchPayload

/**
 * 二手交易数据仓储，负责管理二手检索与交易列表加载。
 */
class ResaleRepository(
    private val api: HpoiApi
) {

    /**
     * 多维检索二手交易列表。
     *
     * @param zones 专区节点 ID (如指定则筛选该专区下的二手)
     * @param hobbyId 关联手办 ID (如指定则筛选该手办的同款二手)
     * @param type 交易类型: 1 出售, 2 求购 (默认 1)
     * @param keyword 检索关键词
     * @param category 分类过滤 ID (如手办 10000)
     * @param puritys 成色多选过滤 (如 "11,9")
     * @param regionNodeId 地区节点 ID
     * @param lastTime 分页游标时间 (上一批次末尾的 addDate)
     * @param pageSize 每页条数 (默认 24)
     */
    suspend fun searchResale(
        zones: Long? = null,
        type: Int = 1,
        keyword: String? = null,
        category: String? = null,
        puritys: String? = null,
        regionNodeId: String? = null,
        lastTime: String? = null,
        pageSize: Int = 24,
        hobbyId: Long? = null,
        itemId: Long? = null
    ): Result<ResaleSearchPayload> = runCatching {
        val params = mutableMapOf<String, String>()
        params["type"] = type.toString()
        params["pageSize"] = pageSize.toString()
        params["state"] = "1"

        zones?.takeIf { it > 0L }?.let { params["zones"] = it.toString() }
        val targetItemId = itemId?.takeIf { it > 0L } ?: hobbyId?.takeIf { it > 0L }
        targetItemId?.let { params["itemId"] = it.toString() }
        keyword?.trim()?.takeIf { it.isNotBlank() }?.let { params["keyword"] = it }
        category?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let { params["category"] = it }
        puritys?.trim()?.takeIf { it.isNotBlank() }?.let { params["puritys"] = it }
        regionNodeId?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let { params["regionNodeId"] = it }
        lastTime?.trim()?.takeIf { it.isNotBlank() }?.let { params["lastTime"] = it }

        val response = api.searchResale(params)
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "获取二手交易列表失败")
        }
        response.data ?: ResaleSearchPayload()
    }

    /**
     * 获取指定二手交易详情 (api/resale/data/get)。
     *
     * @param nodeId 二手交易 ID
     */
    suspend fun getResaleDetail(nodeId: Long): Result<ResaleDetailItem> = runCatching {
        val response = api.getResaleDetail(nodeId)
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "获取二手交易详情失败")
        }
        response.data?.resale
            ?: throw ApiException(-1, "二手交易详情数据为空")
    }
}
