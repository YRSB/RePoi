package com.nobody.repoi.util

/**
 * 列表分页通用规则：按稳定键去重追加，并按整页容量判定是否还有下一页。
 *
 * 评论区、图片瀑布流与各类条目列表共用同一套去重规则；相册图集仅共用整页判定。
 */
object ListPaging {

    /**
     * 取回的条数达到一页容量即认为可能还有下一页。
     */
    fun hasMoreByFullPage(fetchedCount: Int, pageSize: Int): Boolean = fetchedCount >= pageSize

    /**
     * 追加一页后的翻页判定：取回整页且确有新增项才继续，避免服务端重复返回同一页导致无限翻页。
     */
    fun hasMoreAfterPage(fetchedCount: Int, addedCount: Int, pageSize: Int): Boolean =
        fetchedCount >= pageSize && addedCount > 0

    /**
     * 追加一页并与已有列表按稳定键去重。
     *
     * 去重只比对追加前的已有列表：[keyOf] 返回 null 表示该项没有可用键（如服务端未下发 ID），
     * 一律保留。
     *
     * @return 合并后的完整列表（已有项在前，新项在后）
     */
    fun <T> appendPage(existing: List<T>, fetched: List<T>, keyOf: (T) -> Long?): List<T> {
        if (fetched.isEmpty()) return existing
        val keys = existing.mapNotNullTo(HashSet()) { keyOf(it) }
        return existing + fetched.filter { item -> keyOf(item)?.let { it !in keys } ?: true }
    }
}