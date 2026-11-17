package com.nobody.repoi.util.paging

/**
 * 追加页“去重后是否还有新增”判定（唯一实现）。
 *
 * 服务端末页可能整页重复返回已展示条目；只按「整页 = 还有下一页」会把列表永远卡在加载中，
 * 因此追加页的 `hasMore` 必须同时满足「页大小达到一页」与「去重后存在新条目」。
 *
 * 首页分享 / 产品与讨论板块共用本函数，避免三处各写一份同形判断（#163 审查）。
 *
 * @param existing 当前已展示的条目（含置顶等不参与分页的条目）
 * @param keySelector 稳定键提取器，与列表 `key` 口径一致
 */
fun <T> List<T>.hasNewKeysAfterDedup(
    existing: List<T>,
    keySelector: (T) -> Any?
): Boolean {
    if (isEmpty()) return false
    val existingKeys = existing.mapNotNullTo(HashSet()) { keySelector(it) }
    return any { keySelector(it) !in existingKeys }
}
