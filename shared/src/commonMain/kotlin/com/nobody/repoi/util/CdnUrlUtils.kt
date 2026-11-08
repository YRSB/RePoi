package com.nobody.repoi.util

import com.nobody.repoi.data.media.DefaultMediaPipeline
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline

/**
 * Hpoi 资源 CDN URL 静态补全工具（**过渡期兼容桥接层**）。
 *
 * 全部函数已迁移至 [MediaPipeline] / [DefaultMediaPipeline]：CDN 变体拼装、404 避坑规则、
 * 官方缺省图判定与纵横比缓存已收敛到管道深服务内一处生效。本对象保留全部既有签名并标注
 * [Deprecated]，函数体单行委托，保证既有调用点零破坏；新代码请直接使用
 * `MediaPipeline.Default` 或 `PoiAsyncImage`。
 *
 * 常量 [CDN_BASE_URL]、[CoverVariant]、[PicVariant]、[DEFAULT_COVER_PATH]、
 * [EXHOBBY_CDN_BASE_URL] 仍保留可用，供回归测试与路径清理逻辑引用。
 */
object CdnUrlUtils {
    /** Hpoi 主 CDN 根（与 [DefaultMediaPipeline.CDN_BASE_URL] 同源）。 */
    const val CDN_BASE_URL = DefaultMediaPipeline.CDN_BASE_URL

    /** 手办/条目封面变体（`sr` 保留纵横比、`s` 方形缩略、`n` 标准、`raw` 原图）。 */
    enum class CoverVariant(val prefix: String) {
        SR("cover/sr/"),
        SMALL("cover/s/"),
        NORMAL("cover/n/"),
        RAW("cover/raw/");

        companion object {
            val allPrefixes: List<String> = entries.map { it.prefix }
        }
    }

    /** 用户相片变体（服务端无 `pic/sr/`）。 */
    enum class PicVariant(val prefix: String) {
        SMALL("pic/s/"),
        NORMAL("pic/n/"),
        RAW("pic/raw/");

        companion object {
            val allPrefixes: List<String> = entries.map { it.prefix }
        }
    }

    @Deprecated(
        message = "请迁移至 MediaPipeline.Default（DefaultMediaPipeline.cleanVariantPath）",
        replaceWith = ReplaceWith("DefaultMediaPipeline.cleanVariantPath(path, prefixes)")
    )
    fun cleanVariantPath(path: String, prefixes: List<String>): String =
        DefaultMediaPipeline.cleanVariantPath(path, prefixes)

    @Deprecated(
        message = "请迁移至 MediaPipeline.Default（DefaultMediaPipeline.buildVariantUrl）",
        replaceWith = ReplaceWith("DefaultMediaPipeline.buildVariantUrl(path, prefixes, targetPrefix)")
    )
    fun buildVariantUrl(path: String?, prefixes: List<String>, targetPrefix: String): String =
        DefaultMediaPipeline.buildVariantUrl(path, prefixes, targetPrefix)

    @Deprecated(
        message = "请迁移至 MediaPipeline.Default（DefaultMediaPipeline.cleanCoverPath）",
        replaceWith = ReplaceWith("DefaultMediaPipeline.cleanCoverPath(path)")
    )
    fun cleanCoverPath(path: String): String = DefaultMediaPipeline.cleanCoverPath(path)

    @Deprecated(
        message = "请迁移至 MediaPipeline.Default（DefaultMediaPipeline.buildCoverUrl）",
        replaceWith = ReplaceWith("DefaultMediaPipeline.buildCoverUrl(path, variant.prefix)")
    )
    fun buildCoverUrl(path: String?, variant: CoverVariant): String =
        DefaultMediaPipeline.buildCoverUrl(path, variant.prefix)

    /** 手办/相册封面自适应比例缩略图（`cover/sr/`，保留原始纵横比，适合瀑布流）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getCoverSrUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID)

    /** 手办/条目封面小图（`cover/s/`，强制 1:1 正方形缩略图）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getCoverSmallUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL)

    /** 手办/条目封面标准图（`cover/n/`，适合详情页与列表展示）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getCoverUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)

    /** 手办/条目封面高清原图（`cover/raw/`，适合全屏看图）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveRawUrl(path, MediaCategory.HOBBY)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveRawUrl(path, MediaCategory.HOBBY)",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getRawCoverUrl(path: String?): String =
        MediaPipeline.Default.resolveRawUrl(path, MediaCategory.HOBBY)

    /**
     * Hpoi 官方缺省封面相对路径。
     * 当条目没有上传独立封面时，服务端 CDN 部署了固定的 "none.jpg"（绿发吉祥物“暂无封面”插画）。
     */
    const val DEFAULT_COVER_PATH = DefaultMediaPipeline.DEFAULT_COVER_PATH

    /** 校验给定封面路径是否为空、空白、字符串 "null" 或官方缺省占位符。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.isPlaceholder(path)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.isPlaceholder(path)",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun isNoCover(path: String?): Boolean = MediaPipeline.Default.isPlaceholder(path)

    /** 获取指定变体的官方缺省封面完整 CDN URL。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.getDefaultCoverUrl(purpose)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.getDefaultCoverUrl(ImagePurpose.CARD_STANDARD)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getDefaultCoverUrl(variant: CoverVariant = CoverVariant.NORMAL): String =
        DefaultMediaPipeline.getDefaultCoverUrlByPrefix(variant.prefix)

    /** 手办/条目封面标准图（`cover/n/`），路径为空或占位符时回退官方缺省封面。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveCoverUrlOrDefault(path, ImagePurpose.CARD_STANDARD)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveCoverUrlOrDefault(path, ImagePurpose.CARD_STANDARD)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getCoverUrlOrDefault(path: String?): String =
        DefaultMediaPipeline.resolveCoverUrlOrDefault(path, ImagePurpose.CARD_STANDARD)

    /** 手办/条目封面小图（`cover/s/`），路径为空或占位符时回退官方缺省封面。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveCoverUrlOrDefault(path, ImagePurpose.THUMBNAIL)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveCoverUrlOrDefault(path, ImagePurpose.THUMBNAIL)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getCoverSmallUrlOrDefault(path: String?): String =
        DefaultMediaPipeline.resolveCoverUrlOrDefault(path, ImagePurpose.THUMBNAIL)

    /** 手办/相册封面自适应比例缩略图（`cover/sr/`），路径为空或占位符时回退官方缺省封面。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveCoverUrlOrDefault(path, ImagePurpose.ADAPTIVE_GRID)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveCoverUrlOrDefault(path, ImagePurpose.ADAPTIVE_GRID)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getCoverSrUrlOrDefault(path: String?): String =
        DefaultMediaPipeline.resolveCoverUrlOrDefault(path, ImagePurpose.ADAPTIVE_GRID)

    /** 手办/条目高清原图（`cover/raw/`），路径为空或占位符时回退官方缺省封面。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveCoverUrlOrDefault(path, ImagePurpose.FULLSCREEN_RAW)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveCoverUrlOrDefault(path, ImagePurpose.FULLSCREEN_RAW)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getRawCoverUrlOrDefault(path: String?): String =
        DefaultMediaPipeline.resolveCoverUrlOrDefault(path, ImagePurpose.FULLSCREEN_RAW)

    @Deprecated(
        message = "请迁移至 MediaPipeline.Default（DefaultMediaPipeline.cleanPicPath）",
        replaceWith = ReplaceWith("DefaultMediaPipeline.cleanPicPath(path)")
    )
    fun cleanPicPath(path: String): String = DefaultMediaPipeline.cleanPicPath(path)

    @Deprecated(
        message = "请迁移至 MediaPipeline.Default（DefaultMediaPipeline.buildPicUrl）",
        replaceWith = ReplaceWith("DefaultMediaPipeline.buildPicUrl(path, variant.prefix)")
    )
    fun buildPicUrl(path: String?, variant: PicVariant): String =
        DefaultMediaPipeline.buildPicUrl(path, variant.prefix)

    /** 用户头像小图（`head/s/`，适合列表项等小尺寸展示）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.AVATAR, ImagePurpose.THUMBNAIL)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.AVATAR, ImagePurpose.THUMBNAIL)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getHeadSmallUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.AVATAR, ImagePurpose.THUMBNAIL)

    /** 用户头像标准图（`head/n/`，个人中心、资料页、主页头部）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getHeadUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD)

    /** 用户头像高清原图（`head/raw/`，适合全屏看图）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveRawUrl(path, MediaCategory.AVATAR)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveRawUrl(path, MediaCategory.AVATAR)",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getRawHeadUrl(path: String?): String =
        MediaPipeline.Default.resolveRawUrl(path, MediaCategory.AVATAR)

    /** 讨论贴图片标准缩略图（`posts/n/`，适合列表多图平铺展示）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.POST, ImagePurpose.CARD_STANDARD)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.POST, ImagePurpose.CARD_STANDARD)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getPostsUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.POST, ImagePurpose.CARD_STANDARD)

    /**
     * 获取讨论贴图片高清原图。
     * 说明：Hpoi CDN 对讨论帖图片仅部署了 `posts/n/` 路径（单规格标准图），不存在 `posts/raw/` 独立目录（请求会报 404）。
     */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveRawUrl(path, MediaCategory.POST)（管道内已强制降级 posts/n/）",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveRawUrl(path, MediaCategory.POST)",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getPostsRawUrl(path: String?): String =
        MediaPipeline.Default.resolveRawUrl(path, MediaCategory.POST)

    /** 用户相片小图缩略图（`pic/s/`，1:1 方形最小档，适合列表附图九宫格）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.THUMBNAIL)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.THUMBNAIL)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getPicSmallUrl(path: String?): String =
        MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.THUMBNAIL)

    /**
     * 兼容别名：默认返回小图缩略图。
     * @deprecated 请使用语义明确的 [getPicSmallUrl]（/s 变体）或 [getPicNUrl]（/n 变体）。
     */
    @Deprecated(
        message = "请使用语义明确的 getPicSmallUrl（/s 变体）或 getPicNUrl（/n 变体）",
        replaceWith = ReplaceWith("getPicSmallUrl(path)")
    )
    fun getPicUrl(path: String?): String = getPicSmallUrl(path)

    /** 用户相片中图/大图（`pic/n/`，动态流主图与全屏大图初次加载）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.CARD_STANDARD)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.CARD_STANDARD)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getPicNUrl(path: String?): String =
        MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.CARD_STANDARD)

    /** 用户相片高清原图（`pic/raw/`，未经压缩全量图）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.FULLSCREEN_RAW)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.FULLSCREEN_RAW)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getRawPicUrl(path: String?): String =
        MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.FULLSCREEN_RAW)

    /** 用户主页横幅图片 URL（`bnr/n/` 变体）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getBannerUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD)

    /**
     * 获取用户主页横幅高清原图。
     * 说明：Hpoi CDN 对横幅背景图仅部署了 `bnr/n/` 路径，不存在 `bnr/raw/` 独立目录（请求会报 404）。
     */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveRawUrl(path, MediaCategory.BANNER)（管道内已强制降级 bnr/n/）",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveRawUrl(path, MediaCategory.BANNER)",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getRawBannerUrl(path: String?): String =
        MediaPipeline.Default.resolveRawUrl(path, MediaCategory.BANNER)

    /** 二手交易卖家上传的实物照片 URL（`resale/n/`，100% 对齐 Hpoi 原版逆向 pe/c.p 规则）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.RESALE, ImagePurpose.CARD_STANDARD)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.RESALE, ImagePurpose.CARD_STANDARD)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getResalePicUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.RESALE, ImagePurpose.CARD_STANDARD)

    /** 表情贴纸 URL（`sticker/item/` 目录，非通用媒体变体家族，仅此处使用）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default（DefaultMediaPipeline.resolveStickerUrl）",
        replaceWith = ReplaceWith("DefaultMediaPipeline.resolveStickerUrl(path)")
    )
    fun getStickerUrl(path: String?): String = DefaultMediaPipeline.resolveStickerUrl(path)

    /** ExHobby 扩展图库 CDN 根（与 [DefaultMediaPipeline.EX_HOBBY_CDN_BASE_URL] 同源）。 */
    const val EXHOBBY_CDN_BASE_URL = DefaultMediaPipeline.EX_HOBBY_CDN_BASE_URL

    /** ExHobby 扩展图库缩略图 URL（`s/` 变体，用于列表与横滑流）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getExHobbyThumbUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL)

    /** ExHobby 扩展图库中/大图与高清全屏画廊 URL（`n/` 变体）。 */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW)",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getExHobbyFullUrl(path: String?): String =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW)

    /**
     * 获取专区图标 CDN URL（对齐 Hpoi 原版逆向规则）：
     * - 若以 "zone/" 开头，使用 https://rfx.hpoi.net/gk/
     * - 否则回退使用 cover/s/ 缩略图变体 https://rfx.hpoi.net/gk/cover/s/
     */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.ZONE, ImagePurpose.THUMBNAIL)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.ZONE, ImagePurpose.THUMBNAIL).ifEmpty { null }",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getZoneIconUrl(path: String?): String? =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.ZONE, ImagePurpose.THUMBNAIL).ifEmpty { null }

    /**
     * 获取专区封面背景大图 CDN URL（对齐 Hpoi 原版逆向规则）：
     * - 若以 "zone/" 开头，使用 https://rfx.hpoi.net/gk/
     * - 否则回退使用 cover/n/ 标准图变体 https://rfx.hpoi.net/gk/cover/n/
     */
    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveUrl(path, MediaCategory.ZONE, ImagePurpose.CARD_STANDARD)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveUrl(path, MediaCategory.ZONE, ImagePurpose.CARD_STANDARD).ifEmpty { null }",
            "com.nobody.repoi.data.media.ImagePurpose",
            "com.nobody.repoi.data.media.MediaCategory",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getZoneCoverUrl(path: String?): String? =
        MediaPipeline.Default.resolveUrl(path, MediaCategory.ZONE, ImagePurpose.CARD_STANDARD).ifEmpty { null }
}
