package com.nobody.repoi.util

/**
 * Hpoi 资源 CDN URL 静态补全工具。
 */
object CdnUrlUtils {
    const val CDN_BASE_URL = "https://rfx.hpoi.net/gk/"

    enum class CoverVariant(val prefix: String) {
        SR("cover/sr/"),
        SMALL("cover/s/"),
        NORMAL("cover/n/"),
        RAW("cover/raw/");

        companion object {
            val allPrefixes: List<String> = entries.map { it.prefix }
        }
    }

    enum class PicVariant(val prefix: String) {
        SMALL("pic/s/"),
        NORMAL("pic/n/"),
        RAW("pic/raw/");

        companion object {
            val allPrefixes: List<String> = entries.map { it.prefix }
        }
    }

    /**
     * 清理资源路径中的已知变体前缀，提取纯相对路径或文件名。
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
     * 统一的 CDN URL 变体构建与协议完整性转换方法。
     */
    fun buildVariantUrl(path: String?, prefixes: List<String>, targetPrefix: String): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) {
            var result: String = path
            for (prefix in prefixes) {
                if (prefix != targetPrefix) {
                    result = result.replace("/$prefix", "/$targetPrefix")
                }
            }
            return result
        }
        val clean = cleanVariantPath(path, prefixes)
        return "$CDN_BASE_URL$targetPrefix$clean"
    }

    fun cleanCoverPath(path: String): String = cleanVariantPath(path, CoverVariant.allPrefixes)

    fun buildCoverUrl(path: String?, variant: CoverVariant): String =
        buildVariantUrl(path, CoverVariant.allPrefixes, variant.prefix)

    /**
     * 获取手办/相册封面自适应比例缩略图（/sr 变体，保留原始纵横比，适合瀑布流展示）。
     */
    fun getCoverSrUrl(path: String?): String = buildCoverUrl(path, CoverVariant.SR)

    /**
     * 获取手办/条目封面小图（/s 变体，强制 1:1 正方形缩略图）。
     */
    fun getCoverSmallUrl(path: String?): String = buildCoverUrl(path, CoverVariant.SMALL)

    /**
     * 获取手办/条目封面标准图（/n 变体，适合详情页与列表展示）。
     */
    fun getCoverUrl(path: String?): String = buildCoverUrl(path, CoverVariant.NORMAL)

    /**
     * 获取手办/条目封面高清原图（/raw 变体，适合全屏看图等）。
     */
    fun getRawCoverUrl(path: String?): String = buildCoverUrl(path, CoverVariant.RAW)

    fun cleanPicPath(path: String): String = cleanVariantPath(path, PicVariant.allPrefixes)

    fun buildPicUrl(path: String?, variant: PicVariant): String =
        buildVariantUrl(path, PicVariant.allPrefixes, variant.prefix)

    /**
     * 获取用户头像小图缩略图（head/s/ 变体，适合列表项等小尺寸展示）。
     */
    fun getHeadSmallUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = path.removePrefix("/").removePrefix("head/s/").removePrefix("head/n/").removePrefix("head/raw/").removePrefix("/")
        return "${CDN_BASE_URL}head/s/$cleanPath"
    }

    /**
     * 获取用户头像标准图（head/n/ 变体，高清大头像，适合个人中心、资料页、主页头部）。
     */
    fun getHeadUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = path.removePrefix("/").removePrefix("head/s/").removePrefix("head/n/").removePrefix("head/raw/").removePrefix("/")
        return "${CDN_BASE_URL}head/n/$cleanPath"
    }

    /**
     * 获取用户头像高清原图（head/raw/ 变体，适合全屏看图）。
     */
    fun getRawHeadUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = path.removePrefix("/").removePrefix("head/s/").removePrefix("head/n/").removePrefix("head/raw/").removePrefix("/")
        return "${CDN_BASE_URL}head/raw/$cleanPath"
    }

    /**
     * 获取讨论贴图片标准缩略图（posts/n/ 变体，适合列表多图平铺展示）。
     */
    fun getPostsUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = path.removePrefix("/").removePrefix("posts/n/").removePrefix("posts/raw/").removePrefix("/")
        return "${CDN_BASE_URL}posts/n/$cleanPath"
    }

    /**
     * 获取讨论贴图片高清原图。
     * 说明：Hpoi CDN 对讨论帖图片仅部署了 `posts/n/` 路径（单规格标准图），不存在 `posts/raw/` 独立目录（请求会报 404）。
     * 因此全屏大图查看器查看讨论帖图片时统一使用 [getPostsUrl]（`posts/n/`）。
     */
    fun getPostsRawUrl(path: String?): String = getPostsUrl(path)

    /**
     * 获取用户相片小图缩略图（/s 变体，1:1 方形最小档，适合列表附图九宫格）。
     */
    fun getPicSmallUrl(path: String?): String = buildPicUrl(path, PicVariant.SMALL)

    /**
     * 兼容别名：默认返回小图缩略图。
     * @deprecated 请使用语义明确的 [getPicSmallUrl]（/s 变体）或 [getPicNUrl]（/n 变体）。
     */
    @Deprecated(
        message = "请使用语义明确的 getPicSmallUrl（/s 变体）或 getPicNUrl（/n 变体）",
        replaceWith = ReplaceWith("getPicSmallUrl(path)")
    )
    fun getPicUrl(path: String?): String = getPicSmallUrl(path)

    /**
     * 获取用户相片中图/大图（/n 变体，高清晰度自适应图，适合动态流主图与全屏大图初次加载）。
     */
    fun getPicNUrl(path: String?): String = buildPicUrl(path, PicVariant.NORMAL)

    /**
     * 获取用户相片高清原图（/raw 变体，未经压缩全量图，适合全屏看图点击“查看原图”）。
     */
    fun getRawPicUrl(path: String?): String = buildPicUrl(path, PicVariant.RAW)

    /**
     * 获取用户主页横幅图片 URL（bnr/n/ 变体）。
     */
    fun getBannerUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = path.removePrefix("/").removePrefix("bnr/n/").removePrefix("bnr/raw/").removePrefix("/")
        return "${CDN_BASE_URL}bnr/n/$cleanPath"
    }

    /**
     * 获取用户主页横幅高清原图。
     * 说明：Hpoi CDN 对横幅背景图仅部署了 `bnr/n/` 路径（单规格标准图），不存在 `bnr/raw/` 独立目录（请求会报 404）。
     * 因此全屏大图查看器查看横幅时直接使用 `bnr/n/`。
     */
    fun getRawBannerUrl(path: String?): String = getBannerUrl(path)

    /**
     * 获取二手交易卖家上传的实物照片 URL（resale/n/ 变体，100% 对齐 Hpoi 原版逆向 pe/c.p 规则）。
     */
    fun getResalePicUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val cleanPath = trimmed.removePrefix("/").removePrefix("resale/n/").removePrefix("resale/raw/").removePrefix("/")
        return "${CDN_BASE_URL}resale/n/$cleanPath"
    }

    fun getStickerUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return "${CDN_BASE_URL}sticker/item/${path.trimStart('/')}"
    }

    const val EXHOBBY_CDN_BASE_URL = "https://res.e39x.com/pic/"

    /**
     * 获取 ExHobby 扩展图库缩略图 URL（/s 变体，用于列表与横滑流）。
     */
    fun getExHobbyThumbUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val clean = path.removePrefix("/").removePrefix("pic/s/").removePrefix("pic/n/").removePrefix("/")
        return "${EXHOBBY_CDN_BASE_URL}s/$clean"
    }

    /**
     * 获取 ExHobby 扩展图库中/大图与高清全屏画廊 URL（/n 变体，供列表卡片与大图画廊共享缓存以实现点击秒开）。
     */
    fun getExHobbyFullUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val clean = path.removePrefix("/").removePrefix("pic/s/").removePrefix("pic/n/").removePrefix("/")
        return "${EXHOBBY_CDN_BASE_URL}n/$clean"
    }

    /**
     * 获取专区图标 CDN URL（对齐 Hpoi 原版逆向规则）：
     * - 若以 "zone/" 开头，使用 https://rfx.hpoi.net/gk/
     * - 否则回退使用 cover/s/ 缩略图变体 https://rfx.hpoi.net/gk/cover/s/
     */
    fun getZoneIconUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val clean = trimmed.removePrefix("/")
        return if (clean.startsWith("zone/")) {
            "${CDN_BASE_URL}$clean"
        } else {
            "${CDN_BASE_URL}cover/s/$clean"
        }
    }

    /**
     * 获取专区封面背景大图 CDN URL（对齐 Hpoi 原版逆向规则）：
     * - 若以 "zone/" 开头，使用 https://rfx.hpoi.net/gk/
     * - 否则回退使用 cover/n/ 标准图变体 https://rfx.hpoi.net/gk/cover/n/
     */
    fun getZoneCoverUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val trimmed = path.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val clean = trimmed.removePrefix("/")
        return if (clean.startsWith("zone/")) {
            "${CDN_BASE_URL}$clean"
        } else {
            "${CDN_BASE_URL}cover/n/$clean"
        }
    }
}
