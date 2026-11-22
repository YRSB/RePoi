package com.nobody.repoi.data.media

import com.nobody.repoi.util.ImageAspectRatioCache

object DefaultMediaPipeline : MediaPipeline {

    const val CDN_BASE_URL = "https://rfx.hpoi.net/gk/"

    const val EX_HOBBY_CDN_BASE_URL = "https://res.e39x.com/pic/"

    const val DEFAULT_COVER_PATH = "none.jpg"

    const val MIN_ASPECT_RATIO = 0.4f

    const val MAX_ASPECT_RATIO = 2.5f

    internal val COVER_PREFIXES: List<String> = listOf(COVER_SR, COVER_SMALL, COVER_NORMAL, COVER_RAW)

    internal val PIC_PREFIXES: List<String> = PicUrlRules.PREFIXES

    private val HEAD_PREFIXES = listOf("head/s/", "head/n/", "head/raw/")
    private val POSTS_PREFIXES = listOf("posts/n/", "posts/raw/")
    private val BANNER_PREFIXES = listOf("bnr/n/", "bnr/raw/")
    private val RESALE_PREFIXES = listOf("resale/n/", "resale/raw/")
    private val EX_HOBBY_PREFIXES = listOf("pic/s/", "pic/n/")

    private const val COVER_SR = "cover/sr/"
    private const val COVER_SMALL = "cover/s/"
    private const val COVER_NORMAL = "cover/n/"
    private const val COVER_RAW = "cover/raw/"

    private const val HEAD_SMALL = "head/s/"
    private const val HEAD_NORMAL = "head/n/"
    private const val HEAD_RAW = "head/raw/"

    private const val POSTS_NORMAL = "posts/n/"
    private const val BANNER_NORMAL = "bnr/n/"
    private const val RESALE_NORMAL = "resale/n/"

    private const val ZONE_PREFIX = "zone/"
    private const val STICKER_PREFIX = "sticker/item/"

    private const val CDN_MARKER = "gk/"

    override fun resolveUrl(path: String?, category: MediaCategory, purpose: ImagePurpose): String =
        when (category) {
            MediaCategory.HOBBY -> buildVariantUrl(path, COVER_PREFIXES, coverPrefixOf(purpose))
            MediaCategory.ALBUM -> buildVariantUrl(path, COVER_PREFIXES, albumPrefixOf(purpose))
            MediaCategory.AVATAR -> buildSimpleVariantUrl(path, HEAD_PREFIXES, headPrefixOf(purpose))

            MediaCategory.POST ->
                buildSimpleVariantUrl(path, POSTS_PREFIXES, POSTS_NORMAL, rewriteAbsolute = true)
            MediaCategory.BANNER ->
                buildSimpleVariantUrl(path, BANNER_PREFIXES, BANNER_NORMAL, rewriteAbsolute = true)
            MediaCategory.RESALE -> buildResaleUrl(path)
            MediaCategory.ZONE -> buildZoneUrl(path, purpose)
            MediaCategory.EX_HOBBY -> buildExHobbyUrl(path, purpose)
        }

    override fun resolveRawUrl(path: String?, category: MediaCategory): String =
        resolveUrl(path, category, ImagePurpose.FULLSCREEN_RAW)

    override fun isPlaceholder(path: String?): Boolean {
        if (path.isNullOrBlank()) return true
        val trimmed = path.trim().lowercase()
        if (trimmed == "null" || trimmed == "none" || trimmed == "none.jpg") return true
        val clean = cleanCoverPath(trimmed).removePrefix("/")
        if (clean.isEmpty() || clean == "none.jpg" || clean == "none" || clean == "null") return true
        val filename = clean.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
        return filename == "none.jpg" || filename == "none" || filename == "null" || filename.isEmpty()
    }

    override fun getDefaultCoverUrl(purpose: ImagePurpose): String =
        "$CDN_BASE_URL${coverPrefixOf(purpose)}$DEFAULT_COVER_PATH"

    override fun getAspectRatio(key: String): Float? = ImageAspectRatioCache[key]

    override fun recordAspectRatio(key: String, ratio: Float) {
        ImageAspectRatioCache[key] = ratio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
    }

    internal fun cleanVariantPath(path: String, prefixes: List<String>): String {
        var clean = path.removePrefix("/")
        for (prefix in prefixes) {
            if (clean.startsWith(prefix)) {
                clean = clean.removePrefix(prefix)
                break
            }
        }
        return clean
    }

    fun buildVariantUrl(path: String?, prefixes: List<String>, targetPrefix: String): String {
        if (path.isNullOrBlank()) return ""
        if (isAbsoluteUrl(path)) {
            var result: String = canonicalAbsolutes(path)
            for (prefix in prefixes) {
                if (prefix != targetPrefix) {
                    result = result.replace("/$prefix", "/$targetPrefix")
                }
            }
            return result
        }
        return "$CDN_BASE_URL$targetPrefix${cleanVariantPath(path, prefixes)}"
    }

    private fun canonicalAbsolutes(path: String): String {
        val host = absoluteHostOf(path) ?: return path
        if (!host.isHpoiHost()) return path
        val pathEnd = path.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) path.length else it }
        val gkIndex = path.substring(0, pathEnd).indexOf(CDN_MARKER, ignoreCase = true)
        if (gkIndex < 0) return path
        return CDN_BASE_URL + path.substring(gkIndex + CDN_MARKER.length)
    }

    private fun absoluteHostOf(path: String): String? {
        val schemeEnd = path.indexOf("://")
        if (schemeEnd < 0) return null
        val hostStart = schemeEnd + 3
        val hostEnd = path.indexOfAny(charArrayOf('/', '?', '#'), hostStart)
            .let { if (it < 0) path.length else it }
        val host = path.substring(hostStart, hostEnd)
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase()
        return host.ifBlank { null }
    }

    private fun String.isHpoiHost(): Boolean =
        this == "hpoi.net" || endsWith(".hpoi.net") ||
            this == "hpoi.net.cn" || endsWith(".hpoi.net.cn")

    internal fun cleanCoverPath(path: String): String = cleanVariantPath(path, COVER_PREFIXES)

    internal fun buildCoverUrl(path: String?, targetPrefix: String): String =
        buildVariantUrl(path, COVER_PREFIXES, targetPrefix)

    internal fun cleanPicPath(path: String): String = PicUrlRules.clean(path)

    internal fun buildPicUrl(path: String?, targetPrefix: String): String =
        PicUrlRules.build(path, targetPrefix)

    internal fun getDefaultCoverUrlByPrefix(prefix: String): String =
        "$CDN_BASE_URL$prefix$DEFAULT_COVER_PATH"

    override fun resolveCoverUrlOrDefault(path: String?, purpose: ImagePurpose): String =
        if (isPlaceholder(path)) getDefaultCoverUrl(purpose) else resolveUrl(path, MediaCategory.HOBBY, purpose)

    internal fun resolvePicFamilyUrl(path: String?, purpose: ImagePurpose): String =
        PicUrlRules.resolve(path, purpose)

    override fun resolveStickerUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (isAbsoluteUrl(path)) return path
        return "$CDN_BASE_URL$STICKER_PREFIX${path.trimStart('/')}"
    }

    private fun coverPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> COVER_SMALL
        ImagePurpose.ADAPTIVE_GRID -> COVER_SR
        ImagePurpose.CARD_STANDARD -> COVER_NORMAL
        ImagePurpose.FULLSCREEN_RAW -> COVER_RAW
    }

    private fun albumPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> COVER_SR
        ImagePurpose.ADAPTIVE_GRID -> COVER_SR
        ImagePurpose.CARD_STANDARD -> COVER_SR
        ImagePurpose.FULLSCREEN_RAW -> COVER_RAW
    }

    private fun headPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> HEAD_SMALL
        ImagePurpose.ADAPTIVE_GRID -> HEAD_SMALL
        ImagePurpose.CARD_STANDARD -> HEAD_NORMAL
        ImagePurpose.FULLSCREEN_RAW -> HEAD_RAW
    }

    private fun zoneFallbackPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> COVER_SMALL
        ImagePurpose.ADAPTIVE_GRID -> COVER_SMALL
        ImagePurpose.CARD_STANDARD -> COVER_NORMAL
        ImagePurpose.FULLSCREEN_RAW -> COVER_NORMAL
    }

    private fun buildSimpleVariantUrl(
        path: String?,
        prefixes: List<String>,
        targetPrefix: String,
        rewriteAbsolute: Boolean = false
    ): String {
        if (path.isNullOrBlank()) return ""
        if (isAbsoluteUrl(path)) {
            if (!rewriteAbsolute) return path
            var result: String = path
            for (prefix in prefixes) {
                if (prefix != targetPrefix) {
                    result = result.replace("/$prefix", "/$targetPrefix")
                }
            }
            return result
        }
        var clean = path.removePrefix("/")
        for (prefix in prefixes) {
            clean = clean.removePrefix(prefix)
        }
        clean = clean.removePrefix("/")
        return "$CDN_BASE_URL$targetPrefix$clean"
    }

    private fun buildResaleUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        val trimmed = path.trim()
        if (isAbsoluteUrl(trimmed)) return trimmed
        var clean = trimmed.removePrefix("/")
        for (prefix in RESALE_PREFIXES) {
            clean = clean.removePrefix(prefix)
        }
        clean = clean.removePrefix("/")
        return "$CDN_BASE_URL$RESALE_NORMAL$clean"
    }

    private fun buildZoneUrl(path: String?, purpose: ImagePurpose): String {
        if (path.isNullOrBlank()) return ""
        val trimmed = path.trim()
        if (isAbsoluteUrl(trimmed)) return trimmed
        val clean = trimmed.removePrefix("/")
        if (clean.startsWith(ZONE_PREFIX)) return "$CDN_BASE_URL$clean"
        return "$CDN_BASE_URL${zoneFallbackPrefixOf(purpose)}$clean"
    }

    private fun buildExHobbyUrl(path: String?, purpose: ImagePurpose): String {
        if (path.isNullOrBlank()) return ""
        if (isAbsoluteUrl(path)) return path
        var clean = path.removePrefix("/")
        for (prefix in EX_HOBBY_PREFIXES) {
            clean = clean.removePrefix(prefix)
        }
        clean = clean.removePrefix("/")

        val variant = if (purpose == ImagePurpose.THUMBNAIL) "s/" else "n/"
        return "$EX_HOBBY_CDN_BASE_URL$variant$clean"
    }

    private fun isAbsoluteUrl(path: String): Boolean =
        path.startsWith("http://") || path.startsWith("https://")
}
