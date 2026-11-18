package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType

/**
 * 首页动态信息流数据仓储。
 */
class HomeRepository(
    private val api: HpoiApi
) {
    /**
     * 拉取动态信息流。
     * @param categoryId 分类 ID（0=综合, 100=手办, 200=模型, 400=潮玩等）
     * @param subType 动态筛选键值（取值见 [ActionSubType]；默认 "recommend"。
     *   注意旧键 "all" 服务端不识别、会静默回落「推荐」，调用方须先归一为 "allInfo"）
     * @param lastTime 游标翻页时间戳，首次或刷新传 0L
     * @param pageSize 每页条数（默认 20）
     * @param zoneNodeId 单个专区 nodeId（专区详情页消费）
     * @param zoneIds 专区 ID 集合（逗号分隔，如 "100,200"，关注专区与指定专区消费）
     */
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
