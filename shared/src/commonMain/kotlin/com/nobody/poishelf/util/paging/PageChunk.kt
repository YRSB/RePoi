package com.nobody.poishelf.util.paging

/**
 * 分页数据批次分块模型。
 *
 * @param items 本批次拉取回的数据项列表
 * @param hasMore 服务端是否指示存在后续数据
 */
data class PageChunk<T>(
    val items: List<T>,
    val hasMore: Boolean
) {
    companion object {
        /**
         * 根据单页容量便捷推导 hasMore（当拉取条目数达到单页容量时认为可能还有更多）。
         */
        fun <T> of(items: List<T>, pageSize: Int): PageChunk<T> =
            PageChunk(items = items, hasMore = items.size >= pageSize)

        /**
         * 空批次工厂。
         */
        fun <T> empty(): PageChunk<T> = PageChunk(items = emptyList(), hasMore = false)
    }
}
