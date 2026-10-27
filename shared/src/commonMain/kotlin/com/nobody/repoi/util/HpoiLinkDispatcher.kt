package com.nobody.repoi.util

import androidx.compose.ui.platform.UriHandler
import com.nobody.repoi.platform.PlatformActions
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.CharacterNavParam

/**
 * Hpoi 链接解析路由目标。
 */
sealed interface HpoiRoute {
    /** 站内手办详情路由 */
    data class Hobby(val hobbyId: Long) : HpoiRoute

    /** 站内相册详情路由 */
    data class Album(val albumId: Long) : HpoiRoute

    /** 站内角色/要素详情路由 */
    data class Character(val characterId: Long) : HpoiRoute

    /** 站内讨论帖子详情路由 */
    data class Post(val postId: Long) : HpoiRoute

    /** 外部链接或其他 Web 页面 */
    data class Web(val url: String) : HpoiRoute
}

/**
 * Hpoi 链接解析与分流调度器。
 * 负责从富文本或普通超链接中识别手办详情、相册详情等站内路径并执行原生页面导航，其余链接回退至系统浏览器。
 */
object HpoiLinkDispatcher {

    private val HOBBY_REGEX = Regex(
        """^(?:https?://(?:www\.)?hpoi\.net(?:\.cn)?/)?(?:hobby|item)/(\d+)(?:[/?#]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val ALBUM_REGEX = Regex(
        """^(?:https?://(?:www\.)?hpoi\.net(?:\.cn)?/)?album/(\d+)(?:[/?#]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val CHARACTER_REGEX = Regex(
        """^(?:https?://(?:www\.)?hpoi\.net(?:\.cn)?/)?charact[ae]r/(\d+)(?:[/?#]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val POST_REGEX = Regex(
        """^(?:https?://(?:www\.)?hpoi\.net(?:\.cn)?/)?posts?/(\d+)(?:[/?#]|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 解析 URL 并返回对应的 [HpoiRoute] 路由类型。
     */
    fun parse(rawUrl: String): HpoiRoute {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return HpoiRoute.Web("")

        // 统一规范化 URL 前缀
        val normalized = HtmlRichTextParser.normalizeUrl(trimmed)

        // 1. 匹配手办详情 (/hobby/{id} 或 /item/{id})
        HOBBY_REGEX.find(normalized)?.let { match ->
            val id = match.groupValues[1].toLongOrNull()
            if (id != null && id > 0L) {
                return HpoiRoute.Hobby(id)
            }
        }

        // 2. 匹配相册详情 (/album/{id})
        ALBUM_REGEX.find(normalized)?.let { match ->
            val id = match.groupValues[1].toLongOrNull()
            if (id != null && id > 0L) {
                return HpoiRoute.Album(id)
            }
        }

        // 3. 匹配角色/要素详情 (/charactar/{id} 或 /character/{id})
        CHARACTER_REGEX.find(normalized)?.let { match ->
            val id = match.groupValues[1].toLongOrNull()
            if (id != null && id > 0L) {
                return HpoiRoute.Character(id)
            }
        }

        // 4. 匹配帖子详情 (/posts/{id} 或 /post/{id})
        POST_REGEX.find(normalized)?.let { match ->
            val id = match.groupValues[1].toLongOrNull()
            if (id != null && id > 0L) {
                return HpoiRoute.Post(id)
            }
        }

        return HpoiRoute.Web(normalized)
    }

    /**
     * 执行链接分流调度与导航。
     */
    fun dispatch(
        url: String,
        uriHandler: UriHandler,
        platformActions: PlatformActions,
        onNavigateToHobbyDetail: ((DetailNavParam) -> Unit)? = null,
        onNavigateToAlbum: ((Long) -> Unit)? = null,
        onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
        onNavigateToPost: ((Long) -> Unit)? = null
    ) {
        when (val route = parse(url)) {
            is HpoiRoute.Hobby -> {
                if (onNavigateToHobbyDetail != null) {
                    onNavigateToHobbyDetail(DetailNavParam(itemId = route.hobbyId))
                } else {
                    openWeb("https://www.hpoi.net/hobby/${route.hobbyId}", uriHandler, platformActions)
                }
            }
            is HpoiRoute.Album -> {
                if (onNavigateToAlbum != null) {
                    onNavigateToAlbum(route.albumId)
                } else {
                    openWeb("https://www.hpoi.net/album/${route.albumId}", uriHandler, platformActions)
                }
            }
            is HpoiRoute.Character -> {
                if (onNavigateToCharacter != null) {
                    onNavigateToCharacter(CharacterNavParam(characterId = route.characterId))
                } else {
                    openWeb("https://www.hpoi.net/charactar/${route.characterId}", uriHandler, platformActions)
                }
            }
            is HpoiRoute.Post -> {
                if (onNavigateToPost != null) {
                    onNavigateToPost(route.postId)
                } else {
                    openWeb("https://www.hpoi.net/posts/${route.postId}", uriHandler, platformActions)
                }
            }
            is HpoiRoute.Web -> {
                if (route.url.isNotBlank()) {
                    openWeb(route.url, uriHandler, platformActions)
                }
            }
        }
    }

    private fun openWeb(url: String, uriHandler: UriHandler, platformActions: PlatformActions) {
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
            platformActions.showToast("无法打开链接：$url")
        }
    }
}
