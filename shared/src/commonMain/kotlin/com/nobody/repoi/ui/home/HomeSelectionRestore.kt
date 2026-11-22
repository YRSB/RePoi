package com.nobody.repoi.ui.home

const val HOME_BOARD_KEY_SEPARATOR: String = "|"

private const val ZONE_TAB_KEY_ZONE_PREFIX: String = "z"

fun encodeBoardKey(tabKey: String, boardType: String): String {
    val normalizedTabKey = tabKey.trim()
    val normalizedBoardType = boardType.trim()
    if (normalizedTabKey.isEmpty() || normalizedBoardType.isEmpty()) return ""
    return normalizedTabKey + HOME_BOARD_KEY_SEPARATOR + normalizedBoardType
}

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

internal fun zoneTabKeyNodeId(tabKey: String?): Long? =
    tabKey?.trim()
        ?.takeIf { it.startsWith(ZONE_TAB_KEY_ZONE_PREFIX) }
        ?.removePrefix(ZONE_TAB_KEY_ZONE_PREFIX)
        ?.toLongOrNull()
        ?.takeIf { it > 0L }

fun resolveInitialTabKey(
    savedTabKey: String?,
    availableTabKeys: Collection<String>,
): String {
    val saved = savedTabKey?.trim().orEmpty()
    if (saved.isEmpty()) return ZONE_TAB_KEY_ALL
    if (saved == ZONE_TAB_KEY_ALL) return ZONE_TAB_KEY_ALL
    return saved.takeIf { it in availableTabKeys } ?: ZONE_TAB_KEY_ALL
}

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
