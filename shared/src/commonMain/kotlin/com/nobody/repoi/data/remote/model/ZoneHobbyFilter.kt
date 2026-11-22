package com.nobody.repoi.data.remote.model

const val HOBBY_BOARD_PAGE_SIZE: Int = 24

const val HOBBY_DEFAULT_ORDER: String = "hits7Day"

const val HOBBY_DEFAULT_CATEGORY_ID: Int = 10000

const val HOBBY_SEX_UNLIMITED: Int = -1

const val HOBBY_SCALE_UNLIMITED: String = "0"

const val HOBBY_R18_UNLIMITED: Int = -1

data class ZoneHobbyFilter(
    val categoryId: Int = HOBBY_DEFAULT_CATEGORY_ID,
    val subCategoryId: Int? = null,
    val order: String = HOBBY_DEFAULT_ORDER,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val keyword: String = "",
    val sex: Int? = null,
    val specs: String? = null,
    val scale: String? = null,
    val r18: Int? = null
) {

    val hasAdvancedFilters: Boolean
        get() = isHobbyAdvancedFilterActive(sex = sex, specs = specs, scale = scale, r18 = r18)
}

fun isHobbyAdvancedFilterActive(
    sex: Int?,
    specs: String?,
    scale: String?,
    r18: Int?
): Boolean =
    (sex != null && sex != HOBBY_SEX_UNLIMITED) ||
        !specs.isNullOrBlank() ||
        (!scale.isNullOrBlank() && scale != HOBBY_SCALE_UNLIMITED) ||
        (r18 != null && r18 != HOBBY_R18_UNLIMITED)
