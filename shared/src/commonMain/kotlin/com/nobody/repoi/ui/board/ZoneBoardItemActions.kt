package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem

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
    val onHobbyClick: ((HobbyItemSummary) -> Unit)? = null,
    /** 富文本超链接点击（跳转用户/条目/外部网页等）。 */
    val onUrlClick: ((String) -> Unit)? = null
)
