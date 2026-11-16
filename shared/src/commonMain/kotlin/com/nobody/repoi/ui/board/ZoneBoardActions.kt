package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam

/**
 * 板块级筛选与分页动作集合（由宿主机注入回调实现）。
 *
 * 设计约束：pane 不注入仓储、不注入偏好、不直接改 ViewModel；所有交互都经过本类型，
 * 首页与专区详情各自用**方法引用**装配同一份默认实现，不再各写一份
 * `object : ZoneBoardActions { override fun … }` 转发样板（避免两屏动作定义漂移）。
 *
 * 已接入板块：动态（A 阶段）、分享（#157）、讨论（#158）、二手（#160）、实体 5 类（#161）与产品（#159）。
 * 新增板块时在此追加一个**带默认空实现**的回调参数，既有宿主无需同步补齐；
 * `ZoneBoardPane` 的 `when(board)` 分支与两处宿主装配同步补齐（后续板块 ticket）。
 * 讨论板块**没有分类过滤**（原版无入口、接口无参数，见 [PostsBoardOptions] KDoc），
 * 分类回调只为分享与产品板块提供。
 */
class ZoneBoardActions(
    private val selectSubType: (String) -> Unit = {},
    private val selectAlbumOrder: (String) -> Unit = {},
    private val selectAlbumCategory: (Int?) -> Unit = {},
    private val selectPostsOrder: (String) -> Unit = {},
    private val selectResaleType: (Int) -> Unit = {},
    private val selectResalePurity: (String?) -> Unit = {},
    private val resaleKeywordChange: (String) -> Unit = {},
    private val resaleSearch: () -> Unit = {},
    private val retryInitial: () -> Unit = {},
    private val loadMore: () -> Unit = {},
    private val hobbyOrder: (String) -> Unit = {},
    private val hobbyYear: (Int?) -> Unit = {},
    private val hobbyMonth: (Int?) -> Unit = {},
    private val hobbyCategory: (Int, Int?) -> Unit = { _, _ -> },
    private val hobbyKeywordChange: (String) -> Unit = {},
    private val hobbySearch: () -> Unit = {},
    private val hobbyAdvancedApply: (HobbyAdvancedFilterParam) -> Unit = {},
    private val hobbyAdvancedReset: () -> Unit = {},
    private val hobbyViewMode: () -> Unit = {},
    private val hobbyFilterSheetVisible: (Boolean) -> Unit = {},
    private val hobbyCategoryPickerVisible: (Boolean) -> Unit = {}
) {
    /** 动态板块 9 项筛选切换（键值/文案见 [com.nobody.repoi.data.remote.model.ActionSubType]）。 */
    fun onSelectSubType(subType: String) = selectSubType(subType)

    /** 分享板块 6 档排序切换（键值见 [AlbumBoardOptions.ORDERS]）；宿主负责清列表并回第一页重载。 */
    fun onSelectAlbumOrder(order: String) = selectAlbumOrder(order)

    /** 分享板块分类切换；`null` = 全部分类。仅单专区作用域会传入非空选项。 */
    fun onSelectAlbumCategory(categoryId: Int?) = selectAlbumCategory(categoryId)

    /** 讨论板块排序切换（键值见 [PostsBoardOptions.ORDERS]），宿主据此重置并重载首屏。 */
    fun onSelectPostsOrder(order: String) = selectPostsOrder(order)

    /** 二手板块交易类型切换（`1` 出售 / `2` 求购，见 [ResaleBoardOptions.TYPES]）；宿主负责清列表并重载。 */
    fun onSelectResaleType(type: Int) = selectResaleType(type)

    /** 二手板块成色切换；`null` = 全部成色。宿主负责清列表并重载。 */
    fun onSelectResalePurity(purity: String?) = selectResalePurity(purity)

    /** 二手板块搜索关键词输入（仅专区详情渲染搜索栏，首页不渲染）。 */
    fun onResaleKeywordChange(keyword: String) = resaleKeywordChange(keyword)

    /** 二手板块提交搜索（宿主回第一页重载）。 */
    fun onResaleSearch() = resaleSearch()

    /** 首屏错误态「重试」（宿主按当前板块分派刷新）。 */
    fun onRetryInitial() = retryInitial()

    /** 触底翻页与页脚「加载更多失败，点击重试」（宿主按当前板块分派下一页加载）。 */
    fun onLoadMore() = loadMore()

    // ==================== 产品（hobby）板块 #159 ====================

    /** 产品 6 档排序切换（键值见 [HobbyBoardOptions.ORDERS]）。 */
    fun onHobbyOrder(order: String) = hobbyOrder(order)

    /** 产品发售年份（`null` = 不限年）。 */
    fun onHobbyYear(year: Int?) = hobbyYear(year)

    /** 产品发售月份（`null` = 不限月）。 */
    fun onHobbyMonth(month: Int?) = hobbyMonth(month)

    /** 产品分类级联选择（一级 + 可选子分类）。 */
    fun onHobbyCategory(categoryId: Int, subCategoryId: Int?) = hobbyCategory(categoryId, subCategoryId)

    /** 产品关键词输入（不触发请求，回车/搜索时才提交）。 */
    fun onHobbyKeywordChange(keyword: String) = hobbyKeywordChange(keyword)

    /** 提交产品关键词搜索。 */
    fun onHobbySearch() = hobbySearch()

    /** 产品高级筛选「确定」：写回 4 个高级维度并回第一页重载。 */
    fun onHobbyAdvancedApply(filterParam: HobbyAdvancedFilterParam) = hobbyAdvancedApply(filterParam)

    /** 产品高级筛选「重置」。 */
    fun onHobbyAdvancedReset() = hobbyAdvancedReset()

    /** 产品网格 / 列表视图切换（宿主持久化到 `ViewPreferencesManager`）。 */
    fun onHobbyViewMode() = hobbyViewMode()

    /** 产品高级筛选弹层显隐。 */
    fun onHobbyFilterSheetVisible(show: Boolean) = hobbyFilterSheetVisible(show)

    /** 产品分类级联弹层显隐。 */
    fun onHobbyCategoryPickerVisible(show: Boolean) = hobbyCategoryPickerVisible(show)
}

/**
 * 板块条目点击动作（宿主 15 个导航 / 图片浏览回调的窄化子集）。
 *
 * 新增板块时按需在此追加字段（全部可空、带默认值），宿主只填当前板块需要的回调，pane 只透传。
 */
data class ZoneBoardItemActions(
    /** 动态卡片点击（跳转条目详情等）。 */
    val onActionClick: ((ActionItem) -> Unit)? = null,
    /** 动态卡片图片点击（打开全屏看图）。 */
    val onActionImageClick: ((imageUrls: List<String>, initialIndex: Int) -> Unit)? = null,
    /** 讨论帖点击（跳转帖子详情）。 */
    val onPostClick: ((PostItem) -> Unit)? = null,
    /** 讨论帖配图点击（打开全屏看图）。 */
    val onPostImageClick: ((imageUrls: List<String>, initialIndex: Int) -> Unit)? = null,
    /** 讨论帖作者点击（跳转用户主页）。 */
    val onPostUserClick: ((userId: Long) -> Unit)? = null,
    /** 分享（相册）卡片点击（跳转相册详情）。 */
    val onAlbumClick: ((AlbumListItem) -> Unit)? = null,
    /** 二手卡片点击（出售 → 二手详情；求购 → 关联条目详情，由宿主决定）。 */
    val onResaleClick: ((ResaleSearchItem) -> Unit)? = null,
    /** 二手卡片发布者点击（跳转用户主页）。 */
    val onResaleUserClick: ((Long) -> Unit)? = null,
    /** 二手卡片「联系」点击（私信入口，宿主实现）。 */
    val onResaleContactClick: ((ResaleSearchItem) -> Unit)? = null,
    /** 二手卡片三点「更多」点击（宿主弹出更多操作底板/举报弹窗）。 */
    val onResaleMoreClick: ((ResaleSearchItem) -> Unit)? = null,
    /**
     * 实体卡片点击（厂商/系列/作品/角色/人物）。
     *
     * 第一个参数是当前板块（决定跳转哪类详情页），宿主据此构造对应 NavParam；
     * pane 不感知导航类型，保持与专区详情/首页导航解耦。
     */
    val onEntityClick: ((ZoneBoard, ZoneEntityItem) -> Unit)? = null,
    /** 产品卡片点击（跳转产品详情）。 */
    val onHobbyClick: ((HobbyItemSummary) -> Unit)? = null
)
