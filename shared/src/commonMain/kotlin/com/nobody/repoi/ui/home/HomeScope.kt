package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneQueryScope

/**
 * 由一级分类派生查询作用域（禁止调用点散拼 zone 参数）。
 *
 * - `all` → [ZoneQueryScope.All]
 * - `follow` → [ZoneQueryScope.Follow]，`zoneIds` 逗号串拆成 id 列表；空关注标记 `-1` 与非法值一律丢弃，
 *   得到空集合后由上层短路（不发起「降级成全站」的假关注请求）
 * - `z{nodeId}` → [ZoneQueryScope.Single]；首帧分类尚未回填 `nodeId` 时从 tabKey 兜底解析
 *
 * 板块可见性与顺序、同步兜底列表一律直接使用 `ZoneBoardCapability`，本文件不再保留转发函数
 * （避免中间人层制造第二份隐藏规则）。
 */
internal fun FeedCategory.toZoneQueryScope(): ZoneQueryScope = when (tabKey) {
    ZONE_TAB_KEY_ALL -> ZoneQueryScope.All
    ZONE_TAB_KEY_FOLLOW -> ZoneQueryScope.Follow(
        zoneIds.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L }
    )

    else -> {
        val zoneNodeId = nodeId.takeIf { it > 0L }
            ?: tabKey.removePrefix("z").toLongOrNull()?.takeIf { it > 0L }
        if (zoneNodeId != null) ZoneQueryScope.Single(zoneNodeId) else ZoneQueryScope.All
    }
}
