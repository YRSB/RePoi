package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.ZoneHobbyFilter
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam

fun ZoneHobbyFilter.toAdvancedFilterParam(): HobbyAdvancedFilterParam = HobbyAdvancedFilterParam(
    sex = sex,
    specs = specs,
    scale = scale,
    r18 = r18
)

fun HobbyAdvancedFilterParam.applyTo(base: ZoneHobbyFilter): ZoneHobbyFilter = base.copy(
    sex = sex,
    specs = specs,
    scale = scale,
    r18 = r18
)

data class ZoneBoardFilters(
    val subType: String = ActionSubType.DEFAULT.apiValue,
    val albumOrder: String = AlbumBoardOptions.DEFAULT_ORDER,
    val albumCategory: Int? = null,
    val postsOrder: String = PostsBoardOptions.DEFAULT_ORDER,
    val hobby: ZoneHobbyFilter = ZoneHobbyFilter(),
    val resaleType: Int = ResaleBoardOptions.DEFAULT_TYPE,
    val resalePurity: String? = null,
    val resaleKeyword: String = "",

    val showHobbyFilterSheet: Boolean = false,

    val showHobbyCategoryPickerSheet: Boolean = false
)

fun boardFilterToken(filters: ZoneBoardFilters, board: ZoneBoard): String = when (board) {
    ZoneBoard.Album -> "${filters.albumOrder}|${filters.albumCategory ?: "all"}"
    ZoneBoard.Posts -> filters.postsOrder
    ZoneBoard.Resale -> "${filters.resaleType}|${filters.resalePurity ?: "all"}"
    ZoneBoard.Hobby -> with(filters) {
        listOf(
            hobby.order,
            hobby.categoryId,
            hobby.subCategoryId ?: "all",
            hobby.releaseYear ?: "all",
            hobby.releaseMonth ?: "all",
            hobby.keyword,
            hobby.sex ?: "all",
            hobby.specs ?: "all",
            hobby.scale ?: "all",
            hobby.r18 ?: "all"
        ).joinToString("|")
    }
    else -> filters.subType
}
