package com.nobody.repoi.ui.navigation

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam

object AppRouteResolver {

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

    private const val HPOI_PREFIX = """(?:https?://(?:www\.)?hpoi\.net(?:\.cn)?)?/?"""

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
    private val USER_HOME_REGEX = Regex("""^${HPOI_PREFIX}user/home/?(?:\?[^#]*\buserId=(\d+)(?:[&#]|$))""", RegexOption.IGNORE_CASE)

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
        entityId(USER_HOME_REGEX, trimmed)?.let { id ->
            return RouteTarget.Internal(UserInfoNavKey(UserInfoNavParam(userId = id)))
        }

        return RouteTarget.External(trimmed)
    }

    private fun entityId(regex: Regex, url: String): Long? =
        regex.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
}
