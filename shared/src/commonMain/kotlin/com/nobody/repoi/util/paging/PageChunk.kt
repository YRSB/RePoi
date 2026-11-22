package com.nobody.repoi.util.paging

data class PageChunk<T>(
    val items: List<T>,
    val hasMore: Boolean
) {
    companion object {

        fun <T> of(items: List<T>, pageSize: Int): PageChunk<T> =
            PageChunk(items = items, hasMore = items.size >= pageSize)

        fun <T> empty(): PageChunk<T> = PageChunk(items = emptyList(), hasMore = false)
    }
}
