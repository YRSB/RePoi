package com.nobody.repoi.data.media

import com.nobody.repoi.util.ImageAspectRatioCache

/**
 * [MediaPipeline] 的默认实现：零依赖纯 Kotlin 单例（不 import Compose / Android / Coil）。
 *
 * 内部把 Hpoi 逆向得到的全部 CDN 变体知识与 404 避坑规则收敛到一处：
 * - 相对路径先剥掉已知变体前缀再拼目标变体；绝对 URL 仅在支持变体替换的家族（`cover/`、`pic/`）
 *   上改写路径段，其余家族（`head/`、`posts/`、`bnr/`、`resale/`、ExHobby）原样透传，
 *   与旧 `CdnUrlUtils` 逐字节保持一致；
 * - 服务端缺档的用途自动降级（相册无 `cover/n/`、讨论贴与横幅无 `raw/`、专区 `zone/` 直连）；
 * - 纵横比缓存写入统一截断到 `[0.4f, 2.5f]`，读取原值返回。
 *
 * `buildXxx` / `cleanXxx` 等成员是给过渡期 `CdnUrlUtils` 兼容桥接用的低阶入口，
 * 业务代码应优先使用语义化的 [resolveUrl] / [resolveRawUrl]。
 */
object DefaultMediaPipeline : MediaPipeline {

    /** Hpoi 主 CDN 根。 */
    const val CDN_BASE_URL = "https://rfx.hpoi.net/gk/"

    /** ExHobby 扩展图库 CDN 根。 */
    const val EX_HOBBY_CDN_BASE_URL = "https://res.e39x.com/pic/"

    /** Hpoi 官方缺省封面相对路径（服务端部署的绿发吉祥物「暂无封面」插画）。 */
    const val DEFAULT_COVER_PATH = "none.jpg"

    /** 纵横比下界：过窄图（长条截图）压缩布局空间。 */
    const val MIN_ASPECT_RATIO = 0.4f

    /** 纵横比上界：过宽图（全景横幅）压缩布局空间。 */
    const val MAX_ASPECT_RATIO = 2.5f

    /** 手办/条目封面变体前缀集合（`sr` 保留纵横比、`s` 方形缩略、`n` 标准、`raw` 原图）。 */
    val COVER_PREFIXES: List<String> = listOf("cover/sr/", "cover/s/", "cover/n/", "cover/raw/")

    /** 用户相片变体前缀集合（服务端无 `pic/sr/`）。 */
    val PIC_PREFIXES: List<String> = listOf("pic/s/", "pic/n/", "pic/raw/")

    private val HEAD_PREFIXES = listOf("head/s/", "head/n/", "head/raw/")
    private val POSTS_PREFIXES = listOf("posts/n/", "posts/raw/")
    private val BANNER_PREFIXES = listOf("bnr/n/", "bnr/raw/")
    private val RESALE_PREFIXES = listOf("resale/n/", "resale/raw/")
    private val EX_HOBBY_PREFIXES = listOf("pic/s/", "pic/n/")

    private const val COVER_SR = "cover/sr/"
    private const val COVER_SMALL = "cover/s/"
    private const val COVER_NORMAL = "cover/n/"
    private const val COVER_RAW = "cover/raw/"

    private const val PIC_SMALL = "pic/s/"
    private const val PIC_NORMAL = "pic/n/"
    private const val PIC_RAW = "pic/raw/"

    private const val HEAD_SMALL = "head/s/"
    private const val HEAD_NORMAL = "head/n/"
    private const val HEAD_RAW = "head/raw/"

    private const val POSTS_NORMAL = "posts/n/"
    private const val BANNER_NORMAL = "bnr/n/"
    private const val RESALE_NORMAL = "resale/n/"

    private const val ZONE_PREFIX = "zone/"
    private const val STICKER_PREFIX = "sticker/item/"

    // ---------------------------------------------------------------- 契约实现

    override fun resolveUrl(path: String?, category: MediaCategory, purpose: ImagePurpose): String =
        when (category) {
            MediaCategory.HOBBY -> buildVariantUrl(path, COVER_PREFIXES, coverPrefixOf(purpose))
            MediaCategory.ALBUM -> buildVariantUrl(path, COVER_PREFIXES, albumPrefixOf(purpose))
            MediaCategory.AVATAR -> buildSimpleVariantUrl(path, HEAD_PREFIXES, headPrefixOf(purpose))
            MediaCategory.POST -> buildSimpleVariantUrl(path, POSTS_PREFIXES, POSTS_NORMAL)
            MediaCategory.BANNER -> buildSimpleVariantUrl(path, BANNER_PREFIXES, BANNER_NORMAL)
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

    // ------------------------------------------------------------ 兼容桥接入口

    /**
     * 清理路径中的任一已知变体前缀，返回纯相对路径或文件名（绝对 URL 不处理前缀）。
     */
    fun cleanVariantPath(path: String, prefixes: List<String>): String {
        var clean = path.removePrefix("/")
        for (prefix in prefixes) {
            if (clean.startsWith(prefix)) {
                clean = clean.removePrefix(prefix)
                break
            }
        }
        return clean
    }

    /**
     * 通用变体 URL 构建：绝对 URL 仅替换非目标变体段，相对路径先清洗再拼目标前缀。
     */
    fun buildVariantUrl(path: String?, prefixes: List<String>, targetPrefix: String): String {
        if (path.isNullOrBlank()) return ""
        if (isAbsoluteUrl(path)) {
            var result: String = path
            for (prefix in prefixes) {
                if (prefix != targetPrefix) {
                    result = result.replace("/$prefix", "/$targetPrefix")
                }
            }
            return result
        }
        return "$CDN_BASE_URL$targetPrefix${cleanVariantPath(path, prefixes)}"
    }

    /** 清理手办/条目封面路径中的变体前缀。 */
    fun cleanCoverPath(path: String): String = cleanVariantPath(path, COVER_PREFIXES)

    /** 以指定变体前缀构建手办/条目封面 URL（[targetPrefix] 形如 `cover/n/`）。 */
    fun buildCoverUrl(path: String?, targetPrefix: String): String =
        buildVariantUrl(path, COVER_PREFIXES, targetPrefix)

    /** 清理用户相片路径中的变体前缀。 */
    fun cleanPicPath(path: String): String = cleanVariantPath(path, PIC_PREFIXES)

    /** 以指定变体前缀构建用户相片 URL（[targetPrefix] 形如 `pic/n/`）。 */
    fun buildPicUrl(path: String?, targetPrefix: String): String =
        buildVariantUrl(path, PIC_PREFIXES, targetPrefix)

    /** 以变体前缀直接拼装官方缺省封面 URL（[prefix] 形如 `cover/sr/`）。 */
    fun getDefaultCoverUrlByPrefix(prefix: String): String = "$CDN_BASE_URL$prefix$DEFAULT_COVER_PATH"

    /**
     * 手办/条目封面「带缺省兜底」解析：命中占位符时回退该用途的官方缺省封面。
     */
    fun resolveCoverUrlOrDefault(path: String?, purpose: ImagePurpose): String =
        if (isPlaceholder(path)) getDefaultCoverUrl(purpose) else resolveUrl(path, MediaCategory.HOBBY, purpose)

    /** 用户相片（`pic/`）家族解析：四档用途映射到 `pic/s/`、`pic/n/`、`pic/raw/`。 */
    fun resolvePicFamilyUrl(path: String?, purpose: ImagePurpose): String =
        buildVariantUrl(
            path,
            PIC_PREFIXES,
            when (purpose) {
                ImagePurpose.THUMBNAIL -> PIC_SMALL
                ImagePurpose.ADAPTIVE_GRID -> PIC_NORMAL
                ImagePurpose.CARD_STANDARD -> PIC_NORMAL
                ImagePurpose.FULLSCREEN_RAW -> PIC_RAW
            }
        )

    /** 表情贴纸 URL（`sticker/item/` 目录，仅此处使用，非通用媒体变体家族）。 */
    fun resolveStickerUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (isAbsoluteUrl(path)) return path
        return "$CDN_BASE_URL$STICKER_PREFIX${path.trimStart('/')}"
    }

    // -------------------------------------------------------------- 用途映射表

    private fun coverPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> COVER_SMALL
        ImagePurpose.ADAPTIVE_GRID -> COVER_SR
        ImagePurpose.CARD_STANDARD -> COVER_NORMAL
        ImagePurpose.FULLSCREEN_RAW -> COVER_RAW
    }

    /**
     * 相册封面变体：服务端**没有** `cover/n/` 目录（请求必 404），故列表/详情/瀑布流统一走
     * 保留纵横比的 `cover/sr/`；全屏原图沿用历史契约 `cover/raw/`——`FeedMediaResolverTest`
     * 与详情页大图查看器均依赖该目录可用，且 `cover/n/` 缺档不影响 `cover/raw/`。
     */
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

    // ------------------------------------------------------------------ 内部实现

    /**
     * `head/`、`posts/`、`bnr/` 等「单/少档变体」家族的构建：绝对 URL 原样透传，
     * 相对路径按旧实现顺序剥前缀（先导 `/` → 变体前缀 → 残留 `/`）。
     */
    private fun buildSimpleVariantUrl(path: String?, prefixes: List<String>, targetPrefix: String): String {
        if (path.isNullOrBlank()) return ""
        if (isAbsoluteUrl(path)) return path
        var clean = path.removePrefix("/")
        for (prefix in prefixes) {
            clean = clean.removePrefix(prefix)
        }
        clean = clean.removePrefix("/")
        return "$CDN_BASE_URL$targetPrefix$clean"
    }

    /** 二手实物图：仅 `resale/n/`，且入参先 `trim`（对齐旧实现）。 */
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

    /**
     * 专区：`zone/` 前缀路径直连 CDN 根；否则按用途回退图标（`cover/s/`）或封面（`cover/n/`）。
     */
    private fun buildZoneUrl(path: String?, purpose: ImagePurpose): String {
        if (path.isNullOrBlank()) return ""
        val trimmed = path.trim()
        if (isAbsoluteUrl(trimmed)) return trimmed
        val clean = trimmed.removePrefix("/")
        if (clean.startsWith(ZONE_PREFIX)) return "$CDN_BASE_URL$clean"
        val fallbackPrefix = when (purpose) {
            ImagePurpose.THUMBNAIL -> COVER_SMALL
            ImagePurpose.ADAPTIVE_GRID -> COVER_SMALL
            ImagePurpose.CARD_STANDARD -> COVER_NORMAL
            ImagePurpose.FULLSCREEN_RAW -> COVER_NORMAL
        }
        return "$CDN_BASE_URL$fallbackPrefix$clean"
    }

    /** ExHobby 扩展图库：独立域名，仅 `s/`（缩略）与 `n/`（标准/大图）两档。 */
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

/**
 * 用户相片（`pic/`）家族解析桥接。
 *
 * `MediaCategory` 目前没有独立的「用户相片」分类槽位（契约枚举已冻结），而动态流附图、
 * 用户相片查看器等路径必须落在 `pic/` 家族；因此以扩展函数承载，任何 [MediaPipeline]
 * 实现都得到同一套纯 CDN 规则。后续 ADR 扩展枚举时一并收敛进 [MediaPipeline.resolveUrl]。
 */
fun MediaPipeline.resolvePictureUrl(path: String?, purpose: ImagePurpose): String =
    DefaultMediaPipeline.resolvePicFamilyUrl(path, purpose)
