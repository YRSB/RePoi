package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.ResaleDetailItem
import com.nobody.repoi.data.remote.model.ResaleSearchPayload
import com.nobody.repoi.data.remote.model.ZoneQueryScope

class ResaleRepository(
    private val api: HpoiApi
) {

    suspend fun searchResale(
        scope: ZoneQueryScope = ZoneQueryScope.All,
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

        scope.zonesParam()?.let { params["zones"] = it }
        val targetItemId = itemId?.takeIf { it > 0L } ?: hobbyId?.takeIf { it > 0L }
        targetItemId?.let { params["itemId"] = it.toString() }
        keyword?.trim()?.takeIf { it.isNotBlank() }?.let { params["keyword"] = it }
        category?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let { params["category"] = it }
        puritys?.trim()?.takeIf { it.isNotBlank() }?.let { params["puritys"] = it }
        regionNodeId?.trim()?.takeIf { it.isNotBlank() && it != "0" }?.let { params["regionNodeId"] = it }
        lastTime?.trim()?.takeIf { it.isNotBlank() }?.let { params["lastTime"] = it }

        api.searchResale(params)
    }

    suspend fun getResaleDetail(nodeId: Long): Result<ResaleDetailItem> = runCatching {
        api.getResaleDetail(nodeId).resale
            ?: throw ApiException(-1, "二手交易详情数据为空")
    }
}
