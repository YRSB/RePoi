package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.POSTS_BOARD_PAGE_SIZE
import com.nobody.repoi.data.remote.model.POSTS_DEFAULT_ORDER
import com.nobody.repoi.data.remote.model.PostItem

object PostsBoardOptions {

    const val PAGE_SIZE = POSTS_BOARD_PAGE_SIZE

    const val DEFAULT_ORDER = POSTS_DEFAULT_ORDER

    val ORDERS: List<Pair<String, String>> = listOf(
        "upd" to "回复",
        "add" to "新贴",
        "top" to "推荐"
    )

    fun partition(rawList: List<PostItem>): Pair<List<PostItem>, List<PostItem>> {
        return if (rawList.any { it.isPinned }) {
            val pinned = rawList.filter { it.isPinned }.take(5)
            val unpinned = rawList.filterNot { it.isPinned }
            pinned to unpinned
        } else if (rawList.size > 5) {
            val pinned = rawList.take(5)
            val unpinned = rawList.drop(5)
            pinned to unpinned
        } else if (rawList.size > 2) {
            val pinned = rawList.take(2)
            val unpinned = rawList.drop(2)
            pinned to unpinned
        } else {
            emptyList<PostItem>() to rawList
        }
    }
}
