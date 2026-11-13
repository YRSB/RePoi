package com.nobody.repoi.data.media

import com.nobody.repoi.util.ImageAspectRatioCache

/**
 * [MediaPipeline] 的默认实现：零依赖纯 Kotlin 单例（不 import Compose / Android / Coil）。
 *
 * 内部把 Hpoi 逆向得到的全部 CDN 变体知识与 404 避坑规则收敛到一处：
 * - 相对路径先剥掉已知变体前缀再拼目标变体；绝对 URL 按家族语义处理：`cover/`、`pic/` 家族
 *   与 `posts/`、`bnr/` 家族都改写路径段（后两者服务端没有 `raw/` 目录，原样透传必然 404），
 *   其余家族（`head/`、`resale/`、ExHobby、专区）原样透传，与旧 `CdnUrlUtils` 逐字节保持一致；
 * - 服务端缺档的用途自动降级（相册无 `cover/n/`、讨论贴与横幅无 `raw/`、专区 `zone/` 直连）；
 * - 纵横比缓存写入统一截断到 `[0.4f, 2.5f]`，读取原值返回。
 *
 * 每个媒体家族只保留**一处**「用途 → 变体前缀」映射（`cover` 家族另有相册覆写，见
 * [albumPrefixOf]）。用户相片（`pic/`）家族的纯规则位于 `PicUrlRules`，由
 * [MediaPipeline.resolvePictureUrl] 的接口默认实现承载。
 *
 * `buildXxx` / `cleanXxx` 等成员是给过渡期 `CdnUrlUtils` 兼容桥接用的低阶入口（可见性已收紧为
 * `internal`），业务代码应优先使用语义化的 [resolveUrl] / [resolveRawUrl]。
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
    internal val COVER_PREFIXES: List<String> = listOf(COVER_SR, COVER_SMALL, COVER_NORMAL, COVER_RAW)

    /** 用户相片变体前缀集合（服务端无 `pic/sr/`）。 */
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

    /** CDN 根路径段：绝对 URL 归一化时以它切分域名前缀与资源路径。 */
    private const val CDN_MARKER = "gk/"

    // ---------------------------------------------------------------- 契约实现

    override fun resolveUrl(path: String?, category: MediaCategory, purpose: ImagePurpose): String =
        when (category) {
            MediaCategory.HOBBY -> buildVariantUrl(path, COVER_PREFIXES, coverPrefixOf(purpose))
            MediaCategory.ALBUM -> buildVariantUrl(path, COVER_PREFIXES, albumPrefixOf(purpose))
            MediaCategory.AVATAR -> buildSimpleVariantUrl(path, HEAD_PREFIXES, headPrefixOf(purpose))
            // 讨论贴与横幅都只有 `/n/` 一档：绝对 URL 也要把 `/raw/` 改写掉，否则线上 404
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

    // ------------------------------------------------------------ 兼容桥接入口

    /**
     * 清理路径中的任一已知变体前缀，返回纯相对路径或文件名（绝对 URL 不处理前缀）。
     *
     * 仅供过渡期 `CdnUrlUtils` 桥接与 [PicUrlRules] 使用。
     */
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

    /**
     * 通用变体 URL 构建：绝对 URL 仅替换非目标变体段，相对路径先清洗再拼目标前缀。
     *
     * 绝对 URL 改写与相对路径拼装必须留在同一函数内——`CdnUrlUtils` 的历史断言依赖
     * 「同一入口吃两种入参形态」，拆成两套规则会造成桥接回归。
     */
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

    /**
     * 绝对 URL 的协议/域名归一（仅 hpoi 主域及其子域，外部域名原样返回）。
     *
     * 历史快照类列表接口可能存下多年前的绝对地址：`http://` 在 Android 会被明文流量策略直接拒绝，
     * 旧 CDN 域名也可能已下线，两者都表现为「图片其实在、但列表加载不出来」。此处把 hpoi 系
     * 绝对地址统一收敛到 [CDN_BASE_URL]，保留 `/gk/` 之后的路径与 query；路径里若没有 `gk/`
     * 则原样保留整条 URL，避免把非 CDN 资源误改。
     *
     * 域名按 authority 精确匹配（`hpoi.net` / `hpoi.net.cn` 及其子域），不做整串子串判断，
     * 防止 `not-hpoi.net` 或路径/query 中恰好出现 `hpoi.net` 的外部 URL 被误改写；
     * `gk/` 只在路径段内匹配且忽略大小写，避免 query 里的 `gk/` 把 URL 截断。
     */
    private fun canonicalAbsolutes(path: String): String {
        val host = absoluteHostOf(path) ?: return path
        if (!host.isHpoiHost()) return path
        val pathEnd = path.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) path.length else it }
        val gkIndex = path.substring(0, pathEnd).indexOf(CDN_MARKER, ignoreCase = true)
        if (gkIndex < 0) return path
        return CDN_BASE_URL + path.substring(gkIndex + CDN_MARKER.length)
    }

    /** 取绝对 URL 的 authority 主机名（去 userinfo 与端口）；非绝对 URL 返回 `null`。 */
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

    /** 清理手办/条目封面路径中的变体前缀。 */
    internal fun cleanCoverPath(path: String): String = cleanVariantPath(path, COVER_PREFIXES)

    /** 以指定变体前缀构建手办/条目封面 URL（[targetPrefix] 形如 `cover/n/`）。 */
    internal fun buildCoverUrl(path: String?, targetPrefix: String): String =
        buildVariantUrl(path, COVER_PREFIXES, targetPrefix)

    /** 清理用户相片路径中的变体前缀。 */
    internal fun cleanPicPath(path: String): String = PicUrlRules.clean(path)

    /** 以指定变体前缀构建用户相片 URL（[targetPrefix] 形如 `pic/n/`）。 */
    internal fun buildPicUrl(path: String?, targetPrefix: String): String =
        PicUrlRules.build(path, targetPrefix)

    /** 以变体前缀直接拼装官方缺省封面 URL（[prefix] 形如 `cover/sr/`）。 */
    internal fun getDefaultCoverUrlByPrefix(prefix: String): String =
        "$CDN_BASE_URL$prefix$DEFAULT_COVER_PATH"

    /**
     * 手办/条目封面「带缺省兜底」解析：命中占位符时回退该用途的官方缺省封面。
     */
    override fun resolveCoverUrlOrDefault(path: String?, purpose: ImagePurpose): String =
        if (isPlaceholder(path)) getDefaultCoverUrl(purpose) else resolveUrl(path, MediaCategory.HOBBY, purpose)

    /**
     * 用户相片（`pic/`）家族解析：四档用途映射到 `pic/s/`、`pic/n/`、`pic/raw/`。
     *
     * 纯规则位于 [PicUrlRules]；本方法仅为兼容桥接保留的具名入口，新代码请调用
     * [MediaPipeline.resolvePictureUrl]（接口默认实现，可被自定义管道覆写）。
     */
    internal fun resolvePicFamilyUrl(path: String?, purpose: ImagePurpose): String =
        PicUrlRules.resolve(path, purpose)

    /** 表情贴纸 URL（`sticker/item/` 目录，仅此处使用，非通用媒体变体家族）。 */
    override fun resolveStickerUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (isAbsoluteUrl(path)) return path
        return "$CDN_BASE_URL$STICKER_PREFIX${path.trimStart('/')}"
    }

    // -------------------------------------------------------------- 用途映射表

    /** `cover/` 家族（手办/条目）唯一一处「用途 → 变体前缀」映射。 */
    private fun coverPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> COVER_SMALL
        ImagePurpose.ADAPTIVE_GRID -> COVER_SR
        ImagePurpose.CARD_STANDARD -> COVER_NORMAL
        ImagePurpose.FULLSCREEN_RAW -> COVER_RAW
    }

    /**
     * 相册封面变体：服务端**没有** `cover/n/` 目录（请求必 404），故缩略图、瀑布流、标准卡片
     * 统一走保留纵横比的 `cover/sr/`；全屏原图沿用历史契约 `cover/raw/`——`FeedMediaResolverTest`
     * 与详情页大图查看器均依赖该目录可用，且 `cover/n/` 缺档不影响 `cover/raw/`。
     *
     * 注意不能写成「只覆写 `CARD_STANDARD`，其余委托 [coverPrefixOf]」：那样 `THUMBNAIL` 会落到
     * `cover/s/`（相册无该目录 ⇒ 404），是 `resolveUrl_album_neverEmitsCoverNormalDirectory`
     * 之外的另一处 404 暗坑，故此处必须完整枚举四档。
     */
    private fun albumPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> COVER_SR
        ImagePurpose.ADAPTIVE_GRID -> COVER_SR
        ImagePurpose.CARD_STANDARD -> COVER_SR
        ImagePurpose.FULLSCREEN_RAW -> COVER_RAW
    }

    /** `head/` 家族唯一一处「用途 → 变体前缀」映射。 */
    private fun headPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> HEAD_SMALL
        ImagePurpose.ADAPTIVE_GRID -> HEAD_SMALL
        ImagePurpose.CARD_STANDARD -> HEAD_NORMAL
        ImagePurpose.FULLSCREEN_RAW -> HEAD_RAW
    }

    /**
     * 专区回退档位：图标取 `cover/s/`、封面取 `cover/n/`（服务端无专区原图目录，故
     * `FULLSCREEN_RAW` 与 `CARD_STANDARD` 同档）。
     */
    private fun zoneFallbackPrefixOf(purpose: ImagePurpose): String = when (purpose) {
        ImagePurpose.THUMBNAIL -> COVER_SMALL
        ImagePurpose.ADAPTIVE_GRID -> COVER_SMALL
        ImagePurpose.CARD_STANDARD -> COVER_NORMAL
        ImagePurpose.FULLSCREEN_RAW -> COVER_NORMAL
    }

    // ------------------------------------------------------------------ 内部实现

    /**
     * `head/`、`posts/`、`bnr/` 等「单/少档变体」家族的构建：相对路径按旧实现顺序剥前缀
     * （先导 `/` → 变体前缀 → 残留 `/`）。
     *
     * @param rewriteAbsolute 绝对 URL 是否也要改写变体段。仅当服务端确实缺少 `raw/` 目录
     *   （讨论贴、横幅）时传 `true`；`head/` 家族保持原样透传以对齐历史契约与既有断言。
     */
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
        return "$CDN_BASE_URL${zoneFallbackPrefixOf(purpose)}$clean"
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
        // 该家族只有两档，此处即其唯一一处用途映射
        val variant = if (purpose == ImagePurpose.THUMBNAIL) "s/" else "n/"
        return "$EX_HOBBY_CDN_BASE_URL$variant$clean"
    }

    private fun isAbsoluteUrl(path: String): Boolean =
        path.startsWith("http://") || path.startsWith("https://")
}
