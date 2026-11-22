package com.nobody.repoi.util

object ListPaging {

    fun hasMoreByFullPage(fetchedCount: Int, pageSize: Int): Boolean = fetchedCount >= pageSize

    fun hasMoreAfterPage(fetchedCount: Int, addedCount: Int, pageSize: Int): Boolean =
        fetchedCount >= pageSize && addedCount > 0

    fun <T> appendPage(existing: List<T>, fetched: List<T>, keyOf: (T) -> Long?): List<T> {
        if (fetched.isEmpty()) return existing
        val keys = existing.mapNotNullTo(HashSet()) { keyOf(it) }
        return existing + fetched.filter { item -> keyOf(item)?.let { it !in keys } ?: true }
    }
}
