package com.nobody.repoi.ui.board

/**
 * 全站 / 关注 / 单专区 三态查询作用域与请求参数派生（纯逻辑，可单测）。
 *
 * 依据 `docs/research/aggregate-api-verification.md` §2.4、§4.3、§5：
 * - `zones` 与 `homeScope` 互斥：只要 `homeScope` 非空，服务端会**忽略 `zones`** 而改走服务端作用域，
 *   因此调用方必须保证「[zonesParam] 非空时不下发 [homeScope]」；
 * - 讨论板块只认 `zoneNodeId`（String、单值），关注聚合无多专区通道（关注态应隐藏该板块）；
 * - 关注集合为空时必须由上层**短路、不发起请求**：[zonesParam] 返回 null 与 [All] 同形，
 *   若直接上网会被服务端当作「全站」，属于假「关注」内容（比隐藏更危险）。
 */
sealed interface ZoneQueryScope {
    /** 聚合·全部：不带任何 zone 过滤（接口省略 zone 参数即等价全站）。 */
    data object All : ZoneQueryScope

    /** 聚合·关注：携带关注专区 id 的**并集**（逗号串）。空集合必须由上层短路。 */
    data class Follow(val zoneIds: List<Long>) : ZoneQueryScope

    /** 单专区：动态板块同时使用 `zoneIds` 与 `zoneNodeId` 双轨参数。 */
    data class Single(val nodeId: Long) : ZoneQueryScope

    /** 关注集合为空（上层据此展示空态并短路请求，不要在空集合时发请求）。 */
    val isFollowEmpty: Boolean
        get() = this is Follow && zoneIds.isEmpty()

    /**
     * `zones` 参数（分享/产品/二手/实体 5 类）。
     * - [All] → null（省略参数）
     * - [Follow] → `"1,2,3"`；空集合 → null（上层已短路，绝不能下发空串——空串等价全站）
     * - [Single] → `nodeId` 字符串
     */
    fun zonesParam(): String? = when (this) {
        All -> null
        is Follow -> zoneIds.takeIf { it.isNotEmpty() }?.joinToString(",")
        is Single -> nodeId.toString()
    }

    /** `zoneNodeId` 参数：仅 [Single] 有值（动态与讨论板块使用，讨论不接受逗号串）。 */
    fun zoneNodeId(): Long? = (this as? Single)?.nodeId

    /**
     * `homeScope` 参数（仅产品 / 实体 5 类在**无 zones**时可选使用）。
     * - [All] → `"all"`；[Follow] → `"follow"`；[Single] → null
     *
     * 注意：产品关注态暂不启用；且调用方必须遵守「`zonesParam()` 非空时不得发送本参数」。
     */
    fun homeScope(): String? = when (this) {
        All -> "all"
        is Follow -> "follow"
        is Single -> null
    }

    /** 滚动 / 筛选记忆键：`all` / `follow:1,2` / `z123`（对齐原版 `categoryId|supType|zoneNodeId|zoneIds` 的作用域维度）。 */
    fun stateKey(): String = when (this) {
        All -> "all"
        is Follow -> "follow:" + zoneIds.joinToString(",")
        is Single -> "z$nodeId"
    }
}
