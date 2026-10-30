package com.nobody.repoi.ui.navigation

import kotlinx.serialization.Serializable

/**
 * M2 最小路由参数集：仅含 ViewModel 直接引用的 NavParam。
 * 完整 root nav（含 NavKey/AppNavKey/NavBackStack）归属 M6。
 * `java.io.Serializable` 上级已删（KMP 无此类型；`@Serializable` 保留）。
 */

@Serializable
data class CollectEditNavParam(
    val itemId: Long,
    val nodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
)

/**
 * 我的收藏页路由导航参数。
 *
 * 深链三态语义（勿用 `ifBlank` 折叠）：
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
    val albumType: String = "fav",
)

@Serializable
data class UserCreationNavParam(
    val userId: Long,
    val initialCategory: Int = 70000, // 70000 为相册, 90000 为图片
)

@Serializable
data class FriendListNavParam(
    val userId: Long,
    val initialType: Int = 0, // 0=关注, 1=粉丝, 3=获赞
)

@Serializable
data class UserInfoNavParam(
    val userId: Long = 0L,
)

/**
 * M4 batch-A 人物/作品/系列/厂商详情路由参数（M6 NavKey 包装目标）。
 *
 * 单一事实来源为各 VM 文件内的 `@Serializable data class`
 * （各 `ui/person|works|series|vendor` 目录 ViewModel 文件，M2 已移植）；
 * 此处仅作类型别名，便于 M6 root nav 按 `navigation.*NavParam` 统一 import
 *（与 `CharacterNavParam` 等已在 `navigation` 包的形态对齐）。
 * TODO(M6): root nav 落地 `*NavKey` 时复用这些别名。
 */
typealias PersonDetailNavParam = com.nobody.repoi.ui.person.PersonNavParam
typealias WorksDetailNavParam = com.nobody.repoi.ui.works.WorksNavParam
typealias SeriesDetailNavParam = com.nobody.repoi.ui.series.SeriesNavParam
typealias VendorDetailNavParam = com.nobody.repoi.ui.vendor.VendorNavParam

/**
 * 厂商动态全量列表路由导航参数。
 */
@Serializable
data class VendorActionsNavParam(
    val companyNodeId: Long,
    val companyName: String? = null,
)

/**
 * 角色/要素详情页路由导航参数（原版 `type="charactar"` 对应的详情实体）。
 */
@Serializable
data class CharacterNavParam(
    val characterId: Long,
    val characterNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
)

/**
 * 角色相关相册列表路由参数。
 */
@Serializable
data class CharacterAlbumListNavParam(
    val characterId: Long,
    val title: String = "相关相册",
)

/**
 * 手办相关相册列表路由参数。
 */
@Serializable
data class HobbyAlbumListNavParam(
    val nodeId: Long,
    val itemType: String = "hobby",
    val title: String = "关联相册",
)

/**
 * 角色登场作品列表路由参数。
 */
@Serializable
data class CharacterWorksListNavParam(
    val characterId: Long,
    val title: String = "登场作品",
)

@Serializable
data class PictureListNavParam(
    val itemId: Long,
    val itemType: String = "hobby",
    val subType: String? = "official",
    val title: String = "图片列表",
    val nodeId: Long? = null,
)

/**
 * 分类通知详情页路由参数。
 */
@Serializable
data class MessageNoticeNavParam(
    val type: String, // "system", "reply", "praise", "recommend", "follow", "commodity"
    val typeNumber: Int = 1,
    val title: String = "消息通知",
)

/**
 * 1v1 私信聊天页路由参数。
 */
@Serializable
data class MessageChatNavParam(
    val targetUserId: Long,
    val targetUserName: String = "",
    val targetUserHeader: String? = null,
    val resaleId: Long? = null,
)
