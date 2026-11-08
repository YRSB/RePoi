package com.nobody.repoi.ui.navigation

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam

/**
 * 统一路由解析深模块：把动态流 [ActionItem] 与富文本超链接纯函数解析为 [RouteTarget]。
 *
 * 本对象零 Compose 依赖、零平台副作用，可直接在纯 JVM commonTest 中全量覆盖；
 * 副作用（压栈 / 打开浏览器 / 提示）统一由 `NavExtensions.kt` 中的
 * [RePoiNavigator.navigate] 系列扩展执行。
 */
object AppRouteResolver {

    // ── 动态流动作解析（继承旧 FeedActionDispatcher 全部语义）────────────

    /**
     * 根据 [ActionItem] 的关联条目与类型解析路由目标。
     *
     * 判定顺序：相册 -> 厂商 -> 角色 -> 系列 -> 人物 -> 作品 -> 手办（默认）。
     * 关联条目缺失时回退到动态作者用户页，作者也无效则提示条目信息未就绪。
     */
    fun resolveAction(actionItem: ActionItem): RouteTarget {
        val relate = actionItem.relateItem
        if (relate == null) {
            val userId = actionItem.user?.actualUserId ?: 0L
            return if (userId > 0L) {
                RouteTarget.Internal(UserInfoNavKey(UserInfoNavParam(userId = userId)))
            } else {
                RouteTarget.Notice("条目信息暂未就绪")
            }
        }

        val itemType = relate.itemType?.lowercase()?.trim()
        val categoryId = relate.categoryId
        val displayName = relate.nameCN?.ifBlank { null } ?: relate.name
        val cover = relate.cover ?: relate.path

        return when {
            // 1. 相册（album / isAlbum / 分类 60000..79999 / actionType=3）
            itemType == "album" || relate.isAlbum || categoryId in 60000..79999 || actionItem.actionType == 3 -> {
                val albumId = if (relate.id > 0) relate.id else relate.itemId
                if (albumId > 0) {
                    RouteTarget.Internal(
                        AlbumDetailNavKey(
                            AlbumNavParam(
                                albumId = albumId,
                                initialTitle = displayName,
                                initialCover = cover
                            )
                        )
                    )
                } else {
                    invalid(displayName, "相册详情")
                }
            }

            // 2. 厂商 / 品牌（company / vendor / 分类 40000；40100 起为角色要素子分类）
            itemType == "company" || itemType == "vendor" || categoryId == 40000 -> {
                val companyId = if (relate.itemId > 0) relate.itemId else relate.id
                val companyNodeId = if (relate.id > 0) relate.id else relate.itemId
                if (companyId > 0) {
                    RouteTarget.Internal(
                        VendorDetailNavKey(
                            VendorNavParam(
                                companyId = companyId,
                                companyNodeId = companyNodeId,
                                initialName = displayName,
                                initialCover = cover
                            )
                        )
                    )
                } else {
                    invalid(displayName, "厂商详情")
                }
            }

            // 3. 角色 / 要素（character / charactar / 分类 40100..40599、50000..59999）
            itemType == "character" || itemType == "charactar" || categoryId in 40100..40599 || categoryId in 50000..59999 -> {
                val characterId = if (relate.itemId > 0) relate.itemId else relate.id
                val characterNodeId = if (relate.id > 0) relate.id else relate.itemId
                if (characterId > 0) {
                    RouteTarget.Internal(
                        CharacterNavKey(
                            CharacterNavParam(
                                characterId = characterId,
                                characterNodeId = characterNodeId,
                                initialName = displayName,
                                initialCover = cover
                            )
                        )
                    )
                } else {
                    invalid(displayName, "角色详情")
                }
            }

            // 4. 系列（series / 分类 80000..89999）
            itemType == "series" || categoryId in 80000..89999 -> {
                val seriesId = if (relate.itemId > 0) relate.itemId else relate.id
                val seriesNodeId = if (relate.id > 0) relate.id else relate.itemId
                if (seriesId > 0) {
                    RouteTarget.Internal(
                        SeriesDetailNavKey(
                            SeriesNavParam(
                                seriesId = seriesId,
                                seriesNodeId = seriesNodeId,
                                initialName = displayName,
                                initialCover = cover
                            )
                        )
                    )
                } else {
                    invalid(displayName, "系列详情")
                }
            }

            // 5. 人物 / 创作者 / 声优（person / 分类 30000..39999）
            itemType == "person" || categoryId in 30000..39999 -> {
                val personId = if (relate.itemId > 0) relate.itemId else relate.id
                val personNodeId = if (relate.id > 0) relate.id else relate.itemId
                if (personId > 0) {
                    RouteTarget.Internal(
                        PersonDetailNavKey(
                            PersonNavParam(
                                personId = personId,
                                personNodeId = personNodeId,
                                initialName = displayName,
                                initialCover = cover
                            )
                        )
                    )
                } else {
                    invalid(displayName, "人物详情")
                }
            }

            // 6. 作品（works / 分类 20000..29999）
            itemType == "works" || categoryId in 20000..29999 -> {
                val worksId = if (relate.itemId > 0) relate.itemId else relate.id
                if (worksId > 0) {
                    RouteTarget.Internal(
                        WorksDetailNavKey(
                            WorksNavParam(
                                worksId = worksId,
                                initialName = displayName,
                                initialCover = cover
                            )
                        )
                    )
                } else {
                    invalid(displayName, "作品详情")
                }
            }

            // 7. 手办 / 周边 / 默认条目（hobby / figure / 分类 10000..19999 / 缺省）
            else -> {
                val itemId = if (relate.itemId > 0) relate.itemId else relate.id
                if (itemId > 0) {
                    RouteTarget.Internal(
                        HobbyDetailNavKey(
                            DetailNavParam(
                                itemId = itemId,
                                itemType = itemType ?: "hobby",
                                initialName = displayName,
                                initialCover = cover
                            )
                        )
                    )
                } else {
                    invalid(displayName, "手办详情")
                }
            }
        }
    }

    private fun invalid(displayName: String?, entityLabel: String): RouteTarget.Notice =
        RouteTarget.Notice("条目信息暂未就绪：${displayName ?: entityLabel}")

    // ── 超链接解析（站内 9 类实体，其余回退外部浏览器）──────────────────

    /** 可选协议与 hpoi 域名前缀，兼容站内相对路径（含前导斜杠）与绝对链接。 */
    private const val HPOI_PREFIX = """(?:https?://(?:www\.)?hpoi\.net(?:\.cn)?)?/?"""

    /** 实体 ID 之后必须紧跟路径分隔符、查询、锚点或字符串结尾。 */
    private const val TRAILING_BOUNDARY = """(?:[/?#]|$)"""

    private val HOBBY_REGEX = Regex("""^${HPOI_PREFIX}(?:hobby|item)/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)
    private val ALBUM_REGEX = Regex("""^${HPOI_PREFIX}album/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)
    private val CHARACTER_REGEX = Regex("""^${HPOI_PREFIX}charact[ae]r/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)
    private val POST_REGEX = Regex("""^${HPOI_PREFIX}posts?/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)
    private val PERSON_REGEX = Regex("""^${HPOI_PREFIX}person/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)
    private val VENDOR_REGEX = Regex("""^${HPOI_PREFIX}(?:company|vendor)/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)
    private val WORKS_REGEX = Regex("""^${HPOI_PREFIX}works/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)
    private val SERIES_REGEX = Regex("""^${HPOI_PREFIX}series/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)
    private val USER_REGEX = Regex("""^${HPOI_PREFIX}user/(\d+)${TRAILING_BOUNDARY}""", RegexOption.IGNORE_CASE)

    /**
     * 解析超链接为站内目标；非站内、空白或非法 ID 一律回退 [RouteTarget.External]，
     * 并保持调用方输入（仅去除首尾空白），不做任何前缀补全。
     */
    fun resolveUrl(rawUrl: String): RouteTarget {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return RouteTarget.External("")

        entityId(HOBBY_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(HobbyDetailNavKey(DetailNavParam(itemId = id)))
        }
        entityId(ALBUM_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(AlbumDetailNavKey(AlbumNavParam(albumId = id)))
        }
        entityId(CHARACTER_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(CharacterNavKey(CharacterNavParam(characterId = id)))
        }
        entityId(POST_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(PostDetailNavKey(postId = id))
        }
        entityId(PERSON_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(PersonDetailNavKey(PersonNavParam(personId = id)))
        }
        entityId(VENDOR_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(VendorDetailNavKey(VendorNavParam(companyId = id)))
        }
        entityId(WORKS_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(WorksDetailNavKey(WorksNavParam(worksId = id)))
        }
        entityId(SERIES_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(SeriesDetailNavKey(SeriesNavParam(seriesId = id)))
        }
        entityId(USER_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(UserInfoNavKey(UserInfoNavParam(userId = id)))
        }

        return RouteTarget.External(trimmed)
    }

    private fun entityId(regex: Regex, url: String): Long? =
        regex.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
}
