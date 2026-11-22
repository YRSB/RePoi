package com.nobody.repoi.ui.user.history

enum class UserHistoryCategory(
    val itemType: String,
    val label: String
) {
    HOBBY("hobby", "手办"),
    ALBUM("album", "相册");

    companion object {
        fun fromItemType(itemType: String): UserHistoryCategory =
            entries.firstOrNull { it.itemType.equals(itemType, ignoreCase = true) } ?: HOBBY
    }
}
