package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam

/**
 * 板块级筛选与分页动作集合（由宿主机适配现有 ViewModel / loader 实现）。
 *
 * 设计约束：pane 不注入仓储、不注入偏好、不直接改 ViewModel；所有交互都经过本接口，
 * 便于首页与专区详情各自适配，也便于纯逻辑测试。
 *
 * 新增板块时在此接口上**按板块追加方法**（给默认空实现，避免其它宿主的匿名对象被迫同步补齐），
 * `ZoneBoardPane` 的 `when(board)` 分支与两处宿主适配器同步补齐。A 阶段（#155）只有动态，
 * B 阶段补分享（#157）与产品（#159）。
 */
interface ZoneBoardActions {
    /**
     * 动态板块 9 项筛选切换。
     *
     * 键值/文案由共享定义 `ActionSubType` 派生，本接口签名不变。
     */
    fun onSelectSubType(subType: String)

    /** 分享板块 6 档排序切换（键值见 [AlbumBoardOptions.ORDERS]）；宿主负责清列表并回第一页重载。 */
    fun onSelectAlbumOrder(order: String)

    /** 分享板块分类切换；`null` = 全部分类。仅单专区作用域会传入非空选项。 */
    fun onSelectAlbumCategory(categoryId: Int?)

    /** 首屏错误态「重试」（宿主按当前板块分派刷新）。 */
    fun onRetryInitial()

    /** 触底翻页与页脚「加载更多失败，点击重试」（宿主按当前板块分派下一页加载）。 */
    fun onLoadMore()

    // ==================== 产品（hobby）板块 #159 ====================

    /** 产品 6 档排序切换（键值见 [HobbyBoardOptions.ORDERS]）。 */
    fun onHobbyOrder(order: String) {}

    /** 产品发售年份（`null` = 不限年）。 */
    fun onHobbyYear(year: Int?) {}

    /** 产品发售月份（`null` = 不限月）。 */
    fun onHobbyMonth(month: Int?) {}

    /** 产品分类级联选择（一级 + 可选子分类）。 */
    fun onHobbyCategory(categoryId: Int, subCategoryId: Int?) {}

    /** 产品关键词输入（不触发请求，回车/搜索时才提交）。 */
    fun onHobbyKeywordChange(keyword: String) {}

    /** 提交产品关键词搜索。 */
    fun onHobbySearch() {}

    /** 产品高级筛选「确定」：写回 4 个高级维度并回第一页重载。 */
    fun onHobbyAdvancedApply(filterParam: HobbyAdvancedFilterParam) {}

    /** 产品高级筛选「重置」。 */
    fun onHobbyAdvancedReset() {}

    /** 产品网格 / 列表视图切换（宿主持久化到 `ViewPreferencesManager`）。 */
    fun onHobbyViewMode() {}

    /** 产品高级筛选弹层显隐。 */
    fun onHobbyFilterSheetVisible(show: Boolean) {}

    /** 产品分类级联弹层显隐。 */
    fun onHobbyCategoryPickerVisible(show: Boolean) {}
}

/**
 * 板块条目点击动作（宿主导航 / 图片浏览回调的窄化子集）。
 *
 * 新增板块时按需在此追加字段（全部可空、带默认值），宿主只填当前板块需要的回调，pane 只透传。
 */
data class ZoneBoardItemActions(
    /** 动态卡片点击（跳转条目详情等）。 */
    val onActionClick: ((ActionItem) -> Unit)? = null,
    /** 动态卡片图片点击（打开全屏看图）。 */
    val onActionImageClick: ((imageUrls: List<String>, initialIndex: Int) -> Unit)? = null,
    /** 分享（相册）卡片点击（跳转相册详情）。 */
    val onAlbumClick: ((AlbumListItem) -> Unit)? = null,
    /** 产品卡片点击（跳转产品详情）。 */
    val onHobbyClick: ((HobbyItemSummary) -> Unit)? = null
)
