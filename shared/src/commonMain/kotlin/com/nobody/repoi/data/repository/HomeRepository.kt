package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType

class HomeRepository(
    private val api: HpoiApi
) {

    @JvmOverloads
    suspend fun getHomeFeed(
        categoryId: Int = 0,
        subType: String? = ActionSubType.DEFAULT.apiValue,
        lastTime: Long = 0L,
        pageSize: Int = 20,
        zoneNodeId: Long? = null,
        zoneIds: String? = null
    ): Result<List<ActionItem>> = runCatching {
        api.getActionList(
            categoryId = categoryId,
            lastTime = lastTime,
            pageSize = pageSize,
            subType = subType,
            zoneNodeId = zoneNodeId,
            zoneIds = zoneIds
        ).list.orEmpty()
    }
}
