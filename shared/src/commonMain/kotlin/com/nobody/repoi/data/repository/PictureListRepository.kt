package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.PicItem

/**
 * 通用图片列表数据仓储 (api/pic/list/relate-v2)。
 */
class PictureListRepository(
    private val api: HpoiApi
) {

    /**
     * 获取关联图片列表 (api/pic/list/relate-v2)。
     *
     * @param itemId 条目 ID (如手办 itemId)
     * @param itemType 条目类型 (如 hobby, album 等)
     * @param subType 子分类类型 (如 official 等，传 null 时拉取全量)
     * @param page 分页页码，从 1 开始
     * @param pageSize 每页条目数，默认 20
     * @param order 排序字段，如 "top"
     * @param nodeId 节点 ID (可选)
     */
    suspend fun getPictureList(
        itemId: Long,
        itemType: String,
        subType: String?,
        page: Int = 1,
        pageSize: Int = 20,
        order: String? = null,
        nodeId: Long? = null
    ): Result<List<PicItem>> = runCatching {
        val response = api.getPicList(
            itemId = itemId,
            itemType = itemType,
            picSubType = subType,
            page = page,
            pageSize = pageSize,
            order = order,
            nodeId = nodeId
        )
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取图片列表失败")
        }
    }
}
