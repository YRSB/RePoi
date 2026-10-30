package com.nobody.repoi.ui.user.history

/**
 * 用户浏览历史的一级分类项。
 * 遵循原版 Hpoi 支持的手办与相册两大核心类别。
 */
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
