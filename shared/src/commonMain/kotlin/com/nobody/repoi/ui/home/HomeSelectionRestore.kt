package com.nobody.repoi.ui.home

/**
 * 首页三级选择（专区 / 板块 / 筛选）的冷启动恢复规则（纯函数，可单测）。
 *
 * 持久化形态（`docs/home-hierarchy.md` §5）：
 * - 一级：`feed_zone_tab_key` = `all` / `follow` / `z{nodeId}`；
 * - 二级：`feed_zone_board` = `{tabKey}|{type}`（如 `all|action`、`z33476094|hobby`），
 *   按一级前缀校验，前缀不匹配或板块不可用一律取该专区首板块；
 * - 三级：`feed_sub_type` 由 [com.nobody.repoi.data.remote.model.ActionSubType.fromPersisted]
 *   归一（旧键 `all` → `allInfo`，非法值 → `recommend`）。
 *
 * 恢复规则（issue #156 验收标准）：
 * 1. 上次为 `follow`，或 `z{nodeId}` 已不在已关注专区集合中 → 回落 `all`；
 * 2. 二级前缀与当前一级 tabKey 匹配且该板块在可用列表中才恢复，否则取首板块；
 * 3. 三级非法值回落默认项。
 *
 * 本文件只做「纯文本 → 结论」的判定，不读写 DataStore、不依赖网络与 UI。
 */

/** 二级偏好键中一级 tabKey 与板块 type 的分隔符。 */
const val HOME_BOARD_KEY_SEPARATOR: String = "|"

/** 单专区一级 tabKey 的固定前缀（`z{nodeId}`）。 */
private const val ZONE_TAB_KEY_ZONE_PREFIX: String = "z"

/**
 * 编码二级板块偏好键：[tabKey] 与 [boardType] 任一为空时返回空串（调用方视为「未选择」）。
 */
fun encodeBoardKey(tabKey: String, boardType: String): String {
    val normalizedTabKey = tabKey.trim()
    val normalizedBoardType = boardType.trim()
    if (normalizedTabKey.isEmpty() || normalizedBoardType.isEmpty()) return ""
    return normalizedTabKey + HOME_BOARD_KEY_SEPARATOR + normalizedBoardType
}

/**
 * 解码二级板块偏好键；非法值（空串、缺少分隔符、分隔符两侧为空、段数不为 2）返回 null。
 */
fun decodeBoardKey(raw: String?): Pair<String, String>? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val parts = value.split(HOME_BOARD_KEY_SEPARATOR)
    if (parts.size != 2) return null
    val tabKey = parts[0].trim()
    val boardType = parts[1].trim()
    if (tabKey.isEmpty() || boardType.isEmpty()) return null
    return tabKey to boardType
}

/**
 * 解析 `z{nodeId}` 形态的一级 tabKey，返回 nodeId；非该形态或非法返回 null。
 *
 * 供冷启动判定「是否需要等关注专区数据再判断可用性」使用。
 */
internal fun zoneTabKeyNodeId(tabKey: String?): Long? =
    tabKey?.trim()
        ?.takeIf { it.startsWith(ZONE_TAB_KEY_ZONE_PREFIX) }
        ?.removePrefix(ZONE_TAB_KEY_ZONE_PREFIX)
        ?.toLongOrNull()
        ?.takeIf { it > 0L }

/**
 * 一级冷启动落点判定。
 *
 * @param savedTabKey 上次落盘的一级 tabKey
 * @param availableTabKeys 当前可恢复的一级集合（分类列表的 tabKey，含 `all` / `follow` / 各专区）
 *
 * 规则：
 * - 缺失、空串、非法值 → `all`；
 * - 上次为 `follow` 且在 [availableTabKeys] 中 → 恢复「关注」（#212：由既有关注空态兜底，不强制回落）；
 * - 上次为 `z{nodeId}` 且仍在 [availableTabKeys] 中 → 恢复该专区；
 * - 其余（专区已不可用）→ `all`。
 *
 * 注意：调用方必须在关注专区数据就绪后再传入 [availableTabKeys]，不要在首帧空列表时调用，
 * 否则会把合法单专区误判为不可用（#155 移交点）。
 */
fun resolveInitialTabKey(
    savedTabKey: String?,
    availableTabKeys: Collection<String>,
): String {
    val saved = savedTabKey?.trim().orEmpty()
    if (saved.isEmpty()) return ZONE_TAB_KEY_ALL
    if (saved == ZONE_TAB_KEY_ALL) return ZONE_TAB_KEY_ALL
    return saved.takeIf { it in availableTabKeys } ?: ZONE_TAB_KEY_ALL
}

/**
 * 二级板块冷启动落点判定（返回 type 字符串）。
 *
 * 规则：`feed_zone_board` 与 [tabKey] 前缀不匹配、值非法、板块不在 [boards] 中或 [boards]
 * 为空时取该专区首板块；唯一例外是前缀匹配且 type 在 [boards] 中 → 恢复该板块。
 *
 * 聚合态首板块 = `action`，单专区首板块 = 服务端 `tabs` 首项，均由调用方按当前作用域传入 [boards]。
 *
 * @return 可用的板块 type；[boards] 为空时返回空串（调用方视为无板块可渲染）。
 */
fun resolveInitialBoardType(
    savedBoardKey: String?,
    tabKey: String,
    boards: List<String>,
): String {
    val firstBoardType = boards.firstOrNull() ?: return ""
    val decoded = decodeBoardKey(savedBoardKey) ?: return firstBoardType
    if (decoded.first != tabKey) return firstBoardType
    return decoded.second.takeIf { it in boards } ?: firstBoardType
}
