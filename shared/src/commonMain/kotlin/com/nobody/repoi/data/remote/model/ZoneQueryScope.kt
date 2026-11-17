package com.nobody.repoi.data.remote.model

/**
 * 全站 / 关注 / 单专区 三态查询作用域与请求参数派生（纯逻辑，可单测）。
 *
 * 归属数据层（`data.remote.model`）：仓储签名直接消费本类型（如 `ZoneRepository.getZoneItems`、
 * `ItemRepository.queryHobbyBoard`、`ResaleRepository.searchResale`），因此不能再放在 `ui.board`
 * 里造成 data → ui 反向依赖。UI 层（能力矩阵、面板、ViewModel）按需 import。
 *
 * 依据 `docs/research/aggregate-api-verification.md` §2.4、§4.3、§5：
 * - `zones` 与 `homeScope` 互斥：只要 `homeScope` 非空，服务端会**忽略 `zones`** 而改走服务端作用域，
 *   因此调用方必须保证「[zonesParam] 非空时不下发 `homeScope`」。当前产品关注态隐藏，
 *   生产代码**不下发 `homeScope`**（曾有的 `homeScope()` 派生因全库无调用点已删除，避免保留假 API）；
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
     * `zones` 参数（分享 / 实体 5 类；动态与讨论用 [zoneNodeId]）。
     * - [All] → null（省略参数）
     * - [Follow] → `"1,2,3"`；空集合 → null（上层已短路，绝不能下发空串——空串等价全站）
     * - [Single] → `nodeId` 字符串
     *
     * 注意：**产品与二手不得传 [Follow]**——产品关注态需登录态复验（当前隐藏），二手 `zones`
     * 逗号串在服务端是**交集**语义（实测 0 条）；两者由 `ZoneBoardCapability` 在关注态隐藏板块，
     * 本方法不做运行时拦截。
     */
    fun zonesParam(): String? = when (this) {
        All -> null
        is Follow -> zoneIds.takeIf { it.isNotEmpty() }?.joinToString(",")
        is Single -> nodeId.toString()
    }

    /** `zoneNodeId` 参数：仅 [Single] 有值（动态与讨论板块使用，讨论不接受逗号串）。 */
    fun zoneNodeId(): Long? = (this as? Single)?.nodeId

    /** 滚动 / 筛选记忆键：`all` / `follow:1,2` / `z123`（对齐原版 `categoryId|supType|zoneNodeId|zoneIds` 的作用域维度）。 */
    fun stateKey(): String = when (this) {
        All -> "all"
        is Follow -> "follow:" + zoneIds.joinToString(",")
        is Single -> "z$nodeId"
    }
}
