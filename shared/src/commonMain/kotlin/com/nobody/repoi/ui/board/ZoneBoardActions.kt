package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem

/**
 * 板块级筛选与分页动作集合（由宿主机适配现有 ViewModel / loader 实现）。
 *
 * 设计约束：pane 不注入仓储、不注入偏好、不直接改 ViewModel；所有交互都经过本接口，
 * 便于首页与专区详情各自适配，也便于纯逻辑测试。
 *
 * 已接入板块：动态（A 阶段）、分享（#157）、讨论（#158）、二手（#160）、实体 5 类（#161）。
 * 迁移其余板块时在此接口上
 * **按板块追加方法**，`ZoneBoardPane` 的 `when(board)` 分支与两处宿主适配器同步补齐（后续板块 ticket）。
 * 讨论板块**没有分类过滤**（原版无入口、接口无参数，见 [PostsBoardOptions] KDoc），
 * 分类回调只为分享板块提供。
 */
interface ZoneBoardActions {
    /**
     * 动态板块 9 项筛选切换。
     *
     * A 阶段键值/文案沿用 `ZoneDetailViewModel.subTypes`（旧键 `all`）；#153 合入后改由共享定义注入，
     * 本接口签名不变。
     */
    fun onSelectSubType(subType: String)

    /** 分享板块 6 档排序切换（键值见 [AlbumBoardOptions.ORDERS]）；宿主负责清列表并回第一页重载。 */
    fun onSelectAlbumOrder(order: String)

    /** 分享板块分类切换；`null` = 全部分类。仅单专区作用域会传入非空选项。 */
    fun onSelectAlbumCategory(categoryId: Int?)

    /** 讨论板块排序切换（键值见 [PostsBoardOptions.ORDERS]），宿主据此重置并重载首屏。 */
    fun onSelectPostsOrder(order: String)

    /** 二手板块交易类型切换（`1` 出售 / `2` 求购，见 [ResaleBoardOptions.TYPES]）；宿主负责清列表并重载。 */
    fun onSelectResaleType(type: Int)

    /** 二手板块成色切换；`null` = 全部成色。宿主负责清列表并重载。 */
    fun onSelectResalePurity(purity: String?)

    /** 二手板块搜索关键词输入（仅专区详情渲染搜索栏，首页不渲染）。 */
    fun onResaleKeywordChange(keyword: String)

    /** 二手板块提交搜索（宿主回第一页重载）。 */
    fun onResaleSearch()

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
    val onEntityClick: ((ZoneBoard, ZoneEntityItem) -> Unit)? = null
)
