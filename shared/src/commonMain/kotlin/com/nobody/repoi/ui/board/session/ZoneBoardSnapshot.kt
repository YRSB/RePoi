package com.nobody.repoi.ui.board.session

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView

/**
 * 板块滚动位置记忆。
 *
 * @param index 首个可见条目下标
 * @param offset 首个可见条目滚动偏移量（px）
 * @param filterCollapseFraction 筛选条折叠比例（0f 为完全展开，1f 为完全折叠）
 * @param filterRowScrollX 筛选条横向滚动偏移量（px）
 */
data class BoardScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
    val filterCollapseFraction: Float = 0f,
    val filterRowScrollX: Int = 0
)

/**
 * 单个子板块在特定时刻的完整只读状态快照。
 *
 * @param board 所属板块枚举
 * @param content 强类型板块内容快照（统一封装数据列表与分页视图）
 * @param filters 板块筛选条件快照
 * @param pageView 分页加载与错误视图状态（与 content.page 保持同步）
 * @param scrollPosition 列表及筛选栏的滚动恢复位置
 */
data class ZoneBoardSnapshot(
    val board: ZoneBoard,
    val content: ZoneBoardContent,
    val filters: ZoneBoardFilters,
    val pageView: ZoneBoardPageView,
    val scrollPosition: BoardScrollPosition = BoardScrollPosition()
)
