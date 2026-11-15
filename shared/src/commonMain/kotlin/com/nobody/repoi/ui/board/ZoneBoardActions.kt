package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.PostItem

/**
 * 板块级筛选与分页动作集合（由宿主机适配现有 ViewModel / loader 实现）。
 *
 * 设计约束：pane 不注入仓储、不注入偏好、不直接改 ViewModel；所有交互都经过本接口，
 * 便于首页与专区详情各自适配，也便于纯逻辑测试。
 *
 * 已接入板块：动态（A 阶段）、讨论（#158）。迁移其余板块时在此接口上**按板块追加方法**，
 * `ZoneBoardPane` 的 `when(board)` 分支与两处宿主适配器同步补齐（后续板块 ticket）。
 * 讨论板块**没有分类过滤**（原版无入口、接口无参数，见 [PostsBoardOptions] KDoc），
 * 因此本接口不提供分类回调。
 */
interface ZoneBoardActions {
    /**
     * 动态板块 9 项筛选切换。
     *
     * A 阶段键值/文案沿用 `ZoneDetailViewModel.subTypes`（旧键 `all`）；#153 合入后改由共享定义注入，
     * 本接口签名不变。
     */
    fun onSelectSubType(subType: String)

    /** 讨论板块排序切换（键值见 [PostsBoardOptions.ORDERS]），宿主据此重置并重载首屏。 */
    fun onSelectPostsOrder(order: String)

    /** 首屏错误态「重试」（宿主按当前板块分派刷新）。 */
    fun onRetryInitial()

    /** 触底翻页与页脚「加载更多失败，点击重试」（宿主按当前板块分派下一页加载）。 */
    fun onLoadMore()
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
    val onPostUserClick: ((userId: Long) -> Unit)? = null
)
