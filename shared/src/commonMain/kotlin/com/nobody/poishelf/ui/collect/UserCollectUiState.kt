package com.nobody.poishelf.ui.collect

import com.nobody.poishelf.data.remote.model.UserCollectAlbumItem
import com.nobody.poishelf.data.remote.model.UserCollectCategory
import com.nobody.poishelf.data.remote.model.UserCollectCommonItem
import com.nobody.poishelf.data.remote.model.UserCollectHobbyItem
import com.nobody.poishelf.data.remote.model.UserCollectPicItem
import com.nobody.poishelf.data.remote.model.PicItem

/**
 * 分页子页状态容器（按大类隔离列表与分页状态，支持独立懒加载与缓存）。
 *
 * @param isContentHidden 后端返回「用户已隐藏内容」语义时置位，供 UI 渲染专用空态而非通用错误态。
 */
data class SubPageData<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPaging: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val errorMessage: String? = null,
    val isContentHidden: Boolean = false
)

/**
 * 周边页 6 档收藏状态（原版 `UserCollectHobbySubPagerFragment.java:135-151`）。
 *
 * 注意 [ALL] 对应原版胶囊的「全部」档，键值是**空串**而非 null —— 深链必须能表达它。
 */
object CollectStateOption {
    const val ALL = ""
    const val CARE = "care"
    const val WANT = "want"
    const val PREORDER = "preorder"
    const val BUY = "buy"
    const val RESELL = "resell"

    /** 原版胶囊展示顺序 */
    val ALL_STATES = listOf(ALL, CARE, WANT, PREORDER, BUY, RESELL)

    /** 深链未指定（null）时的回落档位，对齐原版 `UserCollectActivity.java:272` 的默认「全部」档 */
    const val DEFAULT = ALL

    fun isValid(state: String) = state in ALL_STATES
}

/**
 * 周边页排序值域（原版 `ui/common/b0.java:124-143`）。
 *
 * [ORDER_TIME]（入手时间）**仅在「已入(buy)」档合法**，其余档位须回落到 [ACTION_DATE]
 * （原版 `UserCollectHobbyFragment.java:779-781`）。
 */
object HobbyOrderOption {
    const val ACTION_DATE = "actionDate"
    const val RELEASE = "release"
    const val RATING = "rating"
    const val MY_RATING = "myRating"
    const val UPDATE_DATE = "updateDate"
    const val ORDER_TIME = "orderTime"

    /** 深链未指定时的回落排序，对齐原版 `UserCollectActivity.java:269` 的默认「收藏时间」 */
    const val DEFAULT = ACTION_DATE

    /** 5 档基础值域（不含入手时间） */
    val BASE_ORDERS = listOf(ACTION_DATE, RELEASE, RATING, MY_RATING, UPDATE_DATE)

    /** 当前收藏状态下可选的完整排序值域 */
    fun optionsFor(collectState: String): List<String> =
        if (collectState == CollectStateOption.BUY) BASE_ORDERS + ORDER_TIME else BASE_ORDERS

    fun isValid(order: String, collectState: String): Boolean = order in optionsFor(collectState)

    /** 入手时间在非 buy 档回落为收藏时间 */
    fun effective(collectState: String, order: String): String =
        if (order == ORDER_TIME && collectState != CollectStateOption.BUY) ACTION_DATE else order
}

/**
 * 通用 4 页（厂商/系列/角色/人物）排序值域白名单
 * （原版 `UserCollectItemSubPageFragment.java:55-64`）。
 *
 * **白名单只有 2 档**：收藏时间 `actionDate`、人气 `collect`。历史上硬编码的 `orderTime` 不在其中。
 */
object CommonOrderOption {
    const val ACTION_DATE = "actionDate"
    const val COLLECT = "collect"

    val ALL_ORDERS = listOf(ACTION_DATE, COLLECT)
    const val DEFAULT = ACTION_DATE

    fun isValid(order: String): Boolean = order in ALL_ORDERS
}

/**
 * 相册页与图片页共用的 3 档排序值域（原版 `UserCollectActivity.java:110, 139`）。
 */
object MediaOrderOption {
    const val ADD_TIME = "addTime"
    const val ACTION_DATE = "actionDate"
    const val PRAISE_COUNT = "praiseCount"

    val ALL_ORDERS = listOf(ADD_TIME, ACTION_DATE, PRAISE_COUNT)
    const val DEFAULT = ADD_TIME

    fun isValid(order: String): Boolean = order in ALL_ORDERS
}

/**
 * 作品页 3 档收藏状态（原版 `UserCollectWorksSubFragment.java:55`）。
 * 与其它页的「收藏状态胶囊」不同构：作品页的顶部 Tab 就是状态，且只有 1/2/3 三档。
 */
object WorksStateOption {
    const val WANT = 1
    const val WATCHING = 2
    const val WATCHED = 3

    val ALL_STATES = listOf(WANT, WATCHING, WATCHED)
    const val DEFAULT = WANT

    fun isValid(state: Int): Boolean = state in ALL_STATES
}

/**
 * 用户全量收藏中枢 UI 状态。
 *
 * 8 大类共享同一份状态对象，但排序、状态、筛选、多选集合**按大类隔离**：
 * 手办走 [order]/[sortType]/[subCategoryId]/[selectedTag]，相册走 [albumOrder]，图片走 [picOrder]，
 * 通用 4 页走 [commonOrder]，作品走 [worksState]/[worksOrderAsc]/[worksTag]。
 */
data class UserCollectUiState(
    val selectedCategory: UserCollectCategory = UserCollectCategory.HOBBY,
    val isListView: Boolean = false,
    val isInitialized: Boolean = false,
    /** 登录用户 id == 当前浏览用户 id：写操作（批量取消收藏、编辑收藏）仅本人可见 */
    val isSelf: Boolean = false,

    // ===== 周边页（手办）=====
    /** 6 档收藏状态，见 [CollectStateOption]；"" = 全部档 */
    val collectState: String = CollectStateOption.DEFAULT,
    /** 排序，见 [HobbyOrderOption]；入手时间仅 buy 档可用 */
    val order: String = HobbyOrderOption.DEFAULT,
    /** 升降序：1 升序 / 2 降序 */
    val sortType: Int = SORT_DESC,
    /** 细分子分类，null = 全部（请求时下发 10000） */
    val subCategoryId: Int? = null,
    val selectedTag: String? = null,
    /** 周边标签候选池，按当前 collectState/subCategoryId 懒加载 */
    val availableTags: List<String> = emptyList(),
    val isTagPoolLoading: Boolean = false,
    val showFilterSheet: Boolean = false,

    // ===== 相册页 =====
    /** 相册查询类型：fav = 收藏的相册，其余值表示「我创建的相册」 */
    val albumType: String = "fav",
    val albumOrder: String = MediaOrderOption.DEFAULT,

    // ===== 图片页 =====
    val picOrder: String = MediaOrderOption.DEFAULT,

    // ===== 通用 4 页（厂商/系列/角色/人物）=====
    val commonOrder: String = CommonOrderOption.DEFAULT,

    // ===== 作品页（独立实现）=====
    val worksState: Int = WorksStateOption.DEFAULT,
    val worksOrderAsc: Boolean = false,
    val worksTag: String? = null,
    val worksAvailableTags: List<String> = emptyList(),
    val isWorksTagPoolLoading: Boolean = false,
    val showWorksFilterSheet: Boolean = false,

    // ===== 批量取消收藏（周边页与相册页各一套独立选中集合）=====
    val hobbySelectionMode: Boolean = false,
    val hobbySelectedIds: Set<Long> = emptySet(),
    val albumSelectionMode: Boolean = false,
    val albumSelectedIds: Set<Long> = emptySet(),
    val isCancellingBatch: Boolean = false,
    val batchCancelError: String? = null,

    // ===== 8 大类独立数据容器 =====
    val hobbyData: SubPageData<UserCollectHobbyItem> = SubPageData(),
    val albumData: SubPageData<UserCollectAlbumItem> = SubPageData(),
    val picData: SubPageData<UserCollectPicItem> = SubPageData(),
    val vendorData: SubPageData<UserCollectCommonItem> = SubPageData(),
    val seriesData: SubPageData<UserCollectCommonItem> = SubPageData(),
    val worksData: SubPageData<UserCollectCommonItem> = SubPageData(),
    val characterData: SubPageData<UserCollectCommonItem> = SubPageData(),
    val personData: SubPageData<UserCollectCommonItem> = SubPageData()
) {
    // 兼容手办单流现有属性与测试
    val items: List<UserCollectHobbyItem> get() = hobbyData.items

    fun subPageData(category: UserCollectCategory = selectedCategory): SubPageData<*> = when (category) {
        UserCollectCategory.HOBBY -> hobbyData
        UserCollectCategory.ALBUM -> albumData
        UserCollectCategory.PICTURE -> picData
        UserCollectCategory.VENDOR -> vendorData
        UserCollectCategory.SERIES -> seriesData
        UserCollectCategory.WORKS -> worksData
        UserCollectCategory.CHARACTER -> characterData
        UserCollectCategory.PERSON -> personData
    }

    val currentSubPage: SubPageData<*> get() = subPageData(selectedCategory)

    val isLoading: Boolean get() = currentSubPage.isLoading
    val isRefreshing: Boolean get() = currentSubPage.isRefreshing
    val isPaging: Boolean get() = currentSubPage.isPaging
    val hasMore: Boolean get() = currentSubPage.hasMore
    val page: Int get() = currentSubPage.page
    val errorMessage: String? get() = currentSubPage.errorMessage
    val currentHasItems: Boolean get() = currentSubPage.items.isNotEmpty()

    val currentLoading: Boolean get() = isLoading
    val currentRefreshing: Boolean get() = isRefreshing
    val currentPaging: Boolean get() = isPaging
    val currentHasMore: Boolean get() = hasMore
    val currentPage: Int get() = page
    val currentError: String? get() = errorMessage

    /** 当前大类命中「用户已隐藏内容」语义：UI 应渲染专用空态而非通用错误态 */
    val isCurrentContentHidden: Boolean get() = currentSubPage.isContentHidden

    /** 当前大类是否处于多选模式 */
    val isCurrentSelectionMode: Boolean
        get() = when (selectedCategory) {
            UserCollectCategory.HOBBY -> hobbySelectionMode
            UserCollectCategory.ALBUM -> albumSelectionMode
            else -> false
        }

    /** 当前大类已选中的外层收藏节点 id 数量 */
    val currentSelectedCount: Int
        get() = when (selectedCategory) {
            UserCollectCategory.HOBBY -> hobbySelectedIds.size
            UserCollectCategory.ALBUM -> albumSelectedIds.size
            else -> 0
        }

    companion object {
        /** 升序 */
        const val SORT_ASC = 1

        /** 降序 */
        const val SORT_DESC = 2
    }
}
