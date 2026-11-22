package com.nobody.repoi.util

import com.nobody.repoi.data.media.DefaultMediaPipeline
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline

object CdnUrlUtils {

    const val CDN_BASE_URL = DefaultMediaPipeline.CDN_BASE_URL

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

    const val DEFAULT_COVER_PATH = DefaultMediaPipeline.DEFAULT_COVER_PATH

    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.isPlaceholder(path)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.isPlaceholder(path)",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun isNoCover(path: String?): Boolean = MediaPipeline.Default.isPlaceholder(path)

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

    @Deprecated(
        message = "请使用语义明确的 getPicSmallUrl（/s 变体）或 getPicNUrl（/n 变体）",
        replaceWith = ReplaceWith("getPicSmallUrl(path)")
    )
    fun getPicUrl(path: String?): String = DefaultMediaPipeline.resolvePicFamilyUrl(path, ImagePurpose.THUMBNAIL)

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

    @Deprecated(
        message = "请迁移至 MediaPipeline.Default.resolveStickerUrl(path)",
        replaceWith = ReplaceWith(
            "MediaPipeline.Default.resolveStickerUrl(path)",
            "com.nobody.repoi.data.media.MediaPipeline"
        )
    )
    fun getStickerUrl(path: String?): String = DefaultMediaPipeline.resolveStickerUrl(path)

    const val EXHOBBY_CDN_BASE_URL = DefaultMediaPipeline.EX_HOBBY_CDN_BASE_URL

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
