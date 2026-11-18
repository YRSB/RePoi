package com.nobody.repoi.ui.board.session

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam

/**
 * 专区子板块会话单向数据流意图契约。
 */
sealed interface ZoneBoardIntent {

    // ── 核心通用生命周期意图 ──

    /**
     * 选中并激活指定子板块。
     *
     * 若目标板块尚未加载，触发首屏懒加载。
     */
    data class SelectBoard(val board: ZoneBoard) : ZoneBoardIntent

    /**
     * 触发触底翻页。
     *
     * @param board 目标板块；为 null 时默认作用于当前活跃板块。
     */
    data class LoadMore(val board: ZoneBoard? = null) : ZoneBoardIntent

    /**
     * 首屏加载重试（或闲置态重试）。
     *
     * @param board 目标板块；为 null 时默认作用于当前活跃板块。
     */
    data class RetryInitial(val board: ZoneBoard? = null) : ZoneBoardIntent

    /**
     * 刷新板块数据（保持现有条目可见）。
     *
     * @param board 目标板块；为 null 时默认作用于当前活跃板块。
     */
    data class Refresh(val board: ZoneBoard? = null) : ZoneBoardIntent

    // ── 筛选意图：Action 动态板块 ──

    /** 切换动态二级分类（推荐/入库/发售/相册等）。 */
    data class SelectSubType(val subType: String) : ZoneBoardIntent

    // ── 筛选意图：Album 分享板块 ──

    /** 切换相册 6 档排序。 */
    data class SelectAlbumOrder(val order: String) : ZoneBoardIntent

    /** 切换相册二级分类（仅单专区生效）。 */
    data class SelectAlbumCategory(val categoryId: Int?) : ZoneBoardIntent

    // ── 筛选意图：Posts 讨论板块 ──

    /** 切换讨论 3 档排序（回复/新贴/推荐）。 */
    data class SelectPostsOrder(val order: String) : ZoneBoardIntent

    // ── 筛选意图：Resale 二手板块 ──

    /** 切换二手交易类型（出售/求购）。 */
    data class SelectResaleType(val type: Int) : ZoneBoardIntent

    /** 切换二手成色筛选（null 表示全部成色）。 */
    data class SelectResalePurity(val purity: String?) : ZoneBoardIntent

    /** 更新二手搜索关键词。 */
    data class UpdateResaleKeyword(val keyword: String) : ZoneBoardIntent

    /** 提交二手关键词搜索。 */
    data object SubmitResaleSearch : ZoneBoardIntent

    // ── 筛选意图：Hobby 产品板块 ──

    /** 切换产品 6 档排序。 */
    data class SelectHobbyOrder(val order: String) : ZoneBoardIntent

    /** 切换发售年份（null 表示不限年）。 */
    data class SelectHobbyYear(val year: Int?) : ZoneBoardIntent

    /** 切换发售月份（null 表示不限月）。 */
    data class SelectHobbyMonth(val month: Int?) : ZoneBoardIntent

    /** 切换产品分类（一级分类 + 可选二级子分类）。 */
    data class SelectHobbyCategory(val categoryId: Int, val subCategoryId: Int?) : ZoneBoardIntent

    /** 应用产品 4 维度高级筛选（性别/比例/规格/R18）。 */
    data class ApplyHobbyFilter(val filterParam: HobbyAdvancedFilterParam) : ZoneBoardIntent

    /** 重置产品高级筛选条件。 */
    data object ResetHobbyFilter : ZoneBoardIntent

    /** 切换产品列表网格 / 列表视图展示形态。 */
    data object ToggleHobbyViewMode : ZoneBoardIntent

    /** 设置产品高级筛选弹层显隐。 */
    data class SetHobbyFilterSheetVisible(val visible: Boolean) : ZoneBoardIntent

    /** 设置产品分类级联选择器弹层显隐。 */
    data class SetHobbyCategoryPickerVisible(val visible: Boolean) : ZoneBoardIntent

    /** 更新产品搜索关键词。 */
    data class UpdateHobbyKeyword(val keyword: String) : ZoneBoardIntent

    /** 提交产品关键词搜索。 */
    data object SubmitHobbySearch : ZoneBoardIntent

    // ── 滚动记忆意图 ──

    /** 保存指定板块的列表滚动位置。 */
    data class SaveScrollPosition(val board: ZoneBoard, val index: Int, val offset: Int) : ZoneBoardIntent

    /** 保存指定板块筛选栏的折叠比例。 */
    data class SaveFilterCollapseFraction(val board: ZoneBoard, val fraction: Float) : ZoneBoardIntent

    /** 保存指定板块筛选栏的横向滚动偏移量。 */
    data class SaveFilterRowScroll(val board: ZoneBoard, val scrollX: Int) : ZoneBoardIntent

    /** 清空指定板块的滚动记忆。 */
    data class ResetScrollMemory(val board: ZoneBoard) : ZoneBoardIntent
}
