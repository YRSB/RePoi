package com.nobody.repoi.util.paging

fun <T> List<T>.hasNewKeysAfterDedup(
    existing: List<T>,
    keySelector: (T) -> Any?
): Boolean {
    if (isEmpty()) return false
    val existingKeys = existing.mapNotNullTo(HashSet()) { keySelector(it) }
    return any { keySelector(it) !in existingKeys }
}
