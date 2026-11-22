package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.PicItem

class PictureListRepository(
    private val api: HpoiApi
) {

    suspend fun getPictureList(
        itemId: Long,
        itemType: String,
        subType: String?,
        page: Int = 1,
        pageSize: Int = 20,
        order: String? = null,
        nodeId: Long? = null
    ): Result<List<PicItem>> = runCatching {
        api.getPicList(
            itemId = itemId,
            itemType = itemType,
            picSubType = subType,
            page = page,
            pageSize = pageSize,
            order = order,
            nodeId = nodeId
        ).list.orEmpty()
    }
}
