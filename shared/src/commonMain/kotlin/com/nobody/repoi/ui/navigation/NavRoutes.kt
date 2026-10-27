package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.AlbumRankListNavParam
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.ui.collect.WorksCollectEditNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.search.SearchNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import kotlinx.serialization.Serializable

sealed interface AppNavKey : NavKey

// 一级底栏 Tab 路由
@Serializable
data object HomeNavKey : AppNavKey

@Serializable
data object ItemsNavKey : AppNavKey
@Serializable
data object AlbumNavKey : AppNavKey

@Serializable
data object FollowNavKey : AppNavKey

@Serializable
data object ProfileNavKey : AppNavKey

/**
 * 判断指定路由是否为一级底栏 Tab 目的地。
 */
fun isTopLevelNavKey(key: Any?): Boolean =
    key is HomeNavKey ||
        key is ItemsNavKey ||
        key is AlbumNavKey ||
        key is FollowNavKey ||
        key is ProfileNavKey

/**
 * 安全出栈防护扩展函数：
 * 1. 确保返回栈深度至少为 2，严禁弹出栈底唯一的根条目，彻底杜绝 NavDisplay backstack empty 崩溃；
 * 2. 确保当前待弹出的栈顶条目不是顶层 Tab（动态/条目/相册/关注/我的）；
 * 3. 返回是否成功弹出了二级条目。
 */
fun NavBackStack<NavKey>.safePop(): Boolean {
    if (size <= 1) return false
    val currentTop = lastOrNull() ?: return false
    if (isTopLevelNavKey(currentTop)) return false
    removeLastOrNull()
    return true
}

// 专区相关路由契约
@Serializable
data object ZoneBrowseNavKey : AppNavKey

@Serializable
data class ZoneDetailNavKey(
    val nodeId: Long,
    val initialName: String? = null,
    val initialIcon: String? = null,
    val initialCover: String? = null,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data object ZoneSettingsNavKey : AppNavKey

// 讨论板相关路由契约
@Serializable
data object ForumHomeNavKey : AppNavKey

@Serializable
data object PlateSubscribeNavKey : AppNavKey

// 讨论贴详情路由契约
@Serializable
data class PostDetailNavKey(
    val postId: Long,
    val initialTitle: String? = null,
    val nodeId: Long? = null,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

// 二手出售详情页路由契约（对应 ResaleDetailScreen，展示价格成色、卖家资料与联系方式）
@Serializable
data class ResaleDetailNavKey(
    val resaleId: Long,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

// 二手市场 / 同款二手独立列表页路由契约（对应 ResaleMarketScreen，支持按手办过滤同款二手）
@Serializable
data class ResaleMarketNavKey(
    val hobbyId: Long = 0L,
    val title: String? = null,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

// 核心消费链路与多窗格分栏路由
@Serializable
data class HobbyListNavKey(
    val param: HobbyListNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey


@Serializable
data class CollectEditNavParam(
    val itemId: Long,
    val nodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)

@Serializable
data class CollectEditNavKey(
    val param: CollectEditNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 我的收藏页路由导航参数。
 *
 * 深链三态语义（勿用 ifBlank 折叠）：
 * - `null` = 调用方**未指定**，ViewModel 回落到各自默认档位；
 * - `""`   = 调用方**显式指定「全部」档**（原版 6 档状态胶囊中 `key:""` 的那一档）；
 * - 其余   = 具体档位值（`care`/`want`/`preorder`/`buy`/`resell` 等）。
 */
@Serializable
data class UserCollectNavParam(
    val userId: Long,
    val initialCollectState: String? = null,
    val initialOrder: String? = null,
    val initialCategory: Int? = null,
    /** 周边页细分子分类（原版 b0.java 分类表：10000 全部/100 手办/200 动漫模型/500 真实模型/400 毛绒布偶/300 Doll） */
    val initialSubCategoryId: Int? = null,
    /** 周边页标签过滤初值 */
    val initialTag: String? = null,
    /** 相册页查询类型：`fav` = 收藏的相册，其余值表示「我创建的相册」 */
    val albumType: String = "fav"
)

@Serializable
data class UserCollectNavKey(
    val param: UserCollectNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class UserCreationNavParam(
    val userId: Long,
    val initialCategory: Int = 70000 // 70000 为相册, 90000 为图片
)

@Serializable
data class UserCreationNavKey(
    val param: UserCreationNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class UserHistoryNavKey(
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class HobbyDetailNavKey(
    val param: DetailNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey {
    constructor(itemId: Long) : this(DetailNavParam(itemId = itemId))
}

@Serializable
data class FriendListNavParam(
    val userId: Long,
    val initialType: Int = 0 // 0=关注, 1=粉丝, 3=获赞
)

@Serializable
data class FriendListNavKey(
    val param: FriendListNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class UserInfoNavParam(
    val userId: Long = 0L
)

@Serializable
data class UserInfoNavKey(
    val param: UserInfoNavParam = UserInfoNavParam(),
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

// 评论详情页（可重复入栈，需唯一键防身份碰撞）
@Serializable
data class CommentDetailNavKey(
    val param: CommentDetailNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

// 全屏评论列表页（可重复入栈，需唯一键防身份碰撞）
@Serializable
data class HobbyCommentsNavKey(
    val param: HobbyCommentsNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class SearchNavKey(
    val param: SearchNavParam = SearchNavParam(),
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class AlbumDetailNavKey(
    val param: AlbumNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class AlbumRankListNavKey(
    val param: AlbumRankListNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class VendorDetailNavKey(
    val param: VendorNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 厂商动态全量列表路由导航参数。
 */
@Serializable
data class VendorActionsNavParam(
    val companyNodeId: Long,
    val companyName: String? = null
)

// 厂商动态全量列表页（可重复入栈，需唯一键防身份碰撞）
@Serializable
data class VendorActionsNavKey(
    val param: VendorActionsNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey


@Serializable
data class PersonDetailNavKey(
    val param: PersonNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 角色/要素详情页路由导航参数（原版 `type="charactar"` 对应的详情实体）。
 */
@Serializable
data class CharacterNavParam(
    val characterId: Long,
    val characterNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)

// 角色/要素详情页（可重复入栈，需唯一键防身份碰撞）
@Serializable
data class CharacterNavKey(
    val param: CharacterNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 角色相关相册列表路由参数。
 */
@Serializable
data class CharacterAlbumListNavParam(
    val characterId: Long,
    val title: String = "相关相册"
)

// 角色相关相册列表页（可重复入栈，需唯一键防身份碰撞）
@Serializable
data class CharacterAlbumListNavKey(
    val param: CharacterAlbumListNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 手办相关相册列表路由参数。
 */
@Serializable
data class HobbyAlbumListNavParam(
    val nodeId: Long,
    val itemType: String = "hobby",
    val title: String = "关联相册"
)

// 手办相关相册列表页（可重复入栈，需唯一键防身份碰撞）
@Serializable
data class HobbyAlbumListNavKey(
    val param: HobbyAlbumListNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 角色登场作品列表路由参数。
 */
@Serializable
data class CharacterWorksListNavParam(
    val characterId: Long,
    val title: String = "登场作品"
)

// 角色登场作品列表页（可重复入栈，需唯一键防身份碰撞）
@Serializable
data class CharacterWorksListNavKey(
    val param: CharacterWorksListNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 作品收藏编辑页路由（对标原版 `CollectWorksActivity`）。
 */
@Serializable
data class WorksCollectEditNavKey(
    val param: WorksCollectEditNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class SeriesDetailNavKey(
    val param: SeriesNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class WorksDetailNavKey(
    val param: WorksNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class PictureListNavParam(
    val itemId: Long,
    val itemType: String = "hobby",
    val subType: String? = "official",
    val title: String = "图片列表",
    val nodeId: Long? = null
)

@Serializable
data class PictureListNavKey(
    val param: PictureListNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

@Serializable
data class ExHobbyListNavKey(
    val itemId: Long,
    val title: String = "ExHobby",
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

// 设置与鉴权路由
@Serializable
data object LoginNavKey : AppNavKey

@Serializable
data object SettingsNavKey : AppNavKey

// ── 消息中心与通知路由 ──────────────────────────────────────────────────

/**
 * 消息中心主页路由（展示 6 分类入口、未读 Badge 与私信联系人列表）。
 */
@Serializable
data class MessageCenterNavKey(
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 分类通知详情页路由参数。
 */
@Serializable
data class MessageNoticeNavParam(
    val type: String, // "system", "reply", "praise", "recommend", "follow", "commodity"
    val typeNumber: Int = 1,
    val title: String = "消息通知"
)

/**
 * 分类通知详情页路由。
 */
@Serializable
data class MessageNoticeNavKey(
    val param: MessageNoticeNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 1v1 私信聊天页路由参数。
 */
@Serializable
data class MessageChatNavParam(
    val targetUserId: Long,
    val targetUserName: String = "",
    val targetUserHeader: String? = null,
    val resaleId: Long? = null
)

/**
 * 1v1 私信聊天页路由。
 */
@Serializable
data class MessageChatNavKey(
    val param: MessageChatNavParam,
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey

/**
 * 黑名单管理页路由。
 */
@Serializable
data class MessageBlockNavKey(
    val id: String = kotlin.uuid.Uuid.random().toString()
) : AppNavKey
