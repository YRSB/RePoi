package com.nobody.repoi.ui.search

enum class SearchTab(
    val categoryId: Int,
    val title: String,
    val itemType: String
) {
    HOBBY(10000, "周边", "hobby"),
    ALBUM(70000, "相册", "album"),
    COMPANY(40000, "厂商", "company"),
    WORKS_ELEMENT(50000, "角色/要素", "works_element"),
    WORKS(20000, "作品", "works"),
    SERIES(80000, "系列", "series"),
    PERSON(30000, "人物", "person"),
    USER(60000, "用户", "user");

    companion object {
        fun fromCategoryId(id: Int): SearchTab {
            return entries.firstOrNull { it.categoryId == id } ?: HOBBY
        }
    }
}

enum class HobbySortOption(val key: String, val label: String) {
    DEFAULT("", "综合排序"),
    ADD_TIME("addTime", "最新添加"),
    RELEASE_DATE("releaseDate", "最晚发售"),
    RATING("rating", "评分最高"),
    SCORE("_score", "相关度"),
    HITS("hits", "最热门")
}

enum class AlbumSortOption(val key: String, val label: String) {
    DEFAULT("", "综合排序"),
    ADD_TIME("addTime", "发布时间"),
    HITS("hits", "总热度")
}

enum class AlbumSubCategory(val categoryId: Int, val label: String) {
    ALL(70000, "全部"),
    FIGURE(60001, "手办"),
    ANIME_MODEL(60002, "动漫模型"),
    REAL_MODEL(60004, "真实模型"),
    PLUSH(60005, "毛绒布偶"),
    DOLL(60003, "Doll娃娃"),
    GK_DIY(60200, "GK_DIY")
}

val HobbyAllCategory = com.nobody.repoi.data.local.CategoryItem(10000, "全部周边", 10000)

enum class ElementsSubCategory(val categoryId: Int, val label: String) {
    ALL(50000, "全部"),
    CHARACTER(40100, "角色"),
    MECHA(40200, "机体"),
    MONSTER(40300, "怪物"),
    ITEM(40400, "物品"),
    ORGANIZATION(40500, "组织")
}

fun resolveSearchCategory(
    tab: SearchTab,
    hobbyCategory: Int = HobbyAllCategory.id,
    albumCategory: AlbumSubCategory = AlbumSubCategory.ALL,
    elementsCategory: ElementsSubCategory = ElementsSubCategory.ALL
): Int = when (tab) {
    SearchTab.HOBBY -> hobbyCategory.takeIf { it > 0 } ?: HobbyAllCategory.id
    SearchTab.ALBUM -> albumCategory.categoryId
    SearchTab.WORKS_ELEMENT -> elementsCategory.categoryId
    else -> tab.categoryId
}

/**
 * 判定当前 Tab 是否具备筛选与排序功能
 */
fun isTabFilterable(tab: SearchTab): Boolean = when (tab) {
    SearchTab.HOBBY,
    SearchTab.ALBUM,
    SearchTab.WORKS_ELEMENT -> true
    else -> false
}

/**
 * 判定当前 Tab 是否存在已激活的非默认筛选或排序项（用于显示 Badge 提示）
 */
fun isFilterActive(
    tab: SearchTab,
    order: String = "",
    hobbyCategory: Int = HobbyAllCategory.id,
    hasCompany: Boolean = false,
    hasYear: Boolean = false,
    albumCategory: AlbumSubCategory = AlbumSubCategory.ALL,
    elementsCategory: ElementsSubCategory = ElementsSubCategory.ALL,
    attribute: Int = 0
): Boolean = when (tab) {
    SearchTab.HOBBY -> order.isNotEmpty() || hobbyCategory != HobbyAllCategory.id || hasCompany || hasYear
    SearchTab.ALBUM -> order.isNotEmpty() || albumCategory != AlbumSubCategory.ALL
    SearchTab.WORKS_ELEMENT -> elementsCategory != ElementsSubCategory.ALL || attribute != 0
    else -> false
}

