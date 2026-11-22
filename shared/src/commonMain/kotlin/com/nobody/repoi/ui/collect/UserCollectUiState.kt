package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.remote.model.PicItem

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

object CollectStateOption {
    const val ALL = ""
    const val CARE = "care"
    const val WANT = "want"
    const val PREORDER = "preorder"
    const val BUY = "buy"
    const val RESELL = "resell"

    val ALL_STATES = listOf(ALL, CARE, WANT, PREORDER, BUY, RESELL)

    const val DEFAULT = ALL

    fun isValid(state: String) = state in ALL_STATES
}

object HobbySubCategoryOption {

    const val ALL_KEY = 10000

    fun normalize(id: Int?): Int? = if (id == null || id == ALL_KEY) null else id
}

object HobbyOrderOption {
    const val ACTION_DATE = "actionDate"
    const val RELEASE = "release"
    const val RATING = "rating"
    const val MY_RATING = "myRating"
    const val UPDATE_DATE = "updateDate"
    const val ORDER_TIME = "orderTime"

    const val DEFAULT = ACTION_DATE

    val BASE_ORDERS = listOf(ACTION_DATE, RELEASE, RATING, MY_RATING, UPDATE_DATE)

    fun optionsFor(collectState: String): List<String> =
        if (collectState == CollectStateOption.BUY) BASE_ORDERS + ORDER_TIME else BASE_ORDERS

    fun isValid(order: String, collectState: String): Boolean = order in optionsFor(collectState)

    fun effective(collectState: String, order: String): String =
        if (order == ORDER_TIME && collectState != CollectStateOption.BUY) ACTION_DATE else order
}

object CommonOrderOption {
    const val ACTION_DATE = "actionDate"
    const val COLLECT = "collect"

    val ALL_ORDERS = listOf(ACTION_DATE, COLLECT)
    const val DEFAULT = ACTION_DATE

    fun isValid(order: String): Boolean = order in ALL_ORDERS
}

object AlbumTypeOption {
    const val FAV = "fav"
    const val UPLOAD = "upload"

    val ALL_TYPES = listOf(FAV, UPLOAD)
    const val DEFAULT = FAV

    fun isValid(type: String): Boolean = type in ALL_TYPES
}

object MediaOrderOption {
    const val ADD_TIME = "addTime"
    const val ACTION_DATE = "actionDate"
    const val PRAISE_COUNT = "praiseCount"

    val ALL_ORDERS = listOf(ADD_TIME, ACTION_DATE, PRAISE_COUNT)
    const val DEFAULT = ADD_TIME

    fun isValid(order: String): Boolean = order in ALL_ORDERS
}

object WorksStateOption {
    const val WANT = 1
    const val WATCHING = 2
    const val WATCHED = 3

    val ALL_STATES = listOf(WANT, WATCHING, WATCHED)
    const val DEFAULT = WANT

    fun isValid(state: Int): Boolean = state in ALL_STATES
}

data class UserCollectUiState(
    val selectedCategory: UserCollectCategory = UserCollectCategory.HOBBY,
    val isListView: Boolean = false,
    val isInitialized: Boolean = false,

    val isSelf: Boolean = false,

    val collectState: String = CollectStateOption.DEFAULT,

    val order: String = HobbyOrderOption.DEFAULT,

    val sortType: Int = SORT_DESC,

    val subCategoryId: Int? = null,
    val selectedTag: String? = null,

    val availableTags: List<String> = emptyList(),
    val isTagPoolLoading: Boolean = false,
    val showFilterSheet: Boolean = false,

    val albumType: String = AlbumTypeOption.DEFAULT,
    val albumOrder: String = MediaOrderOption.DEFAULT,

    val picOrder: String = MediaOrderOption.DEFAULT,

    val commonOrder: String = CommonOrderOption.DEFAULT,

    val worksState: Int = WorksStateOption.DEFAULT,
    val worksOrderAsc: Boolean = false,
    val worksTag: String? = null,
    val worksAvailableTags: List<String> = emptyList(),
    val isWorksTagPoolLoading: Boolean = false,
    val showWorksFilterSheet: Boolean = false,

    val hobbySelectionMode: Boolean = false,
    val hobbySelectedIds: Set<Long> = emptySet(),
    val albumSelectionMode: Boolean = false,
    val albumSelectedIds: Set<Long> = emptySet(),
    val isCancellingBatch: Boolean = false,
    val batchCancelError: String? = null,

    val hobbyData: SubPageData<UserCollectHobbyItem> = SubPageData(),
    val albumData: SubPageData<UserCollectAlbumItem> = SubPageData(),
    val picData: SubPageData<UserCollectPicItem> = SubPageData(),
    val vendorData: SubPageData<UserCollectCommonItem> = SubPageData(),
    val seriesData: SubPageData<UserCollectCommonItem> = SubPageData(),
    val worksData: SubPageData<UserCollectCommonItem> = SubPageData(),
    val characterData: SubPageData<UserCollectCommonItem> = SubPageData(),
    val personData: SubPageData<UserCollectCommonItem> = SubPageData()
) {

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

    val isCurrentContentHidden: Boolean get() = currentSubPage.isContentHidden

    val isCurrentSelectionMode: Boolean
        get() = when (selectedCategory) {
            UserCollectCategory.HOBBY -> hobbySelectionMode
            UserCollectCategory.ALBUM -> albumSelectionMode
            else -> false
        }

    val currentSelectedCount: Int
        get() = when (selectedCategory) {
            UserCollectCategory.HOBBY -> hobbySelectedIds.size
            UserCollectCategory.ALBUM -> albumSelectedIds.size
            else -> 0
        }

    companion object {

        const val SORT_ASC = 1

        const val SORT_DESC = 2
    }
}
