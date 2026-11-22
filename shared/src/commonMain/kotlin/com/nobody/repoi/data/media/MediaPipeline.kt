package com.nobody.repoi.data.media

enum class MediaCategory {

    HOBBY,

    ALBUM,

    AVATAR,

    POST,

    BANNER,

    RESALE,

    ZONE,

    EX_HOBBY
}

enum class ImagePurpose {

    THUMBNAIL,

    ADAPTIVE_GRID,

    CARD_STANDARD,

    FULLSCREEN_RAW
}

interface MediaPipeline {
    companion object {

        val Default: MediaPipeline get() = DefaultMediaPipeline
    }

    fun resolveUrl(path: String?, category: MediaCategory, purpose: ImagePurpose): String

    fun resolveRawUrl(path: String?, category: MediaCategory): String

    fun isPlaceholder(path: String?): Boolean

    fun getDefaultCoverUrl(purpose: ImagePurpose): String

    fun getAspectRatio(key: String): Float?

    fun recordAspectRatio(key: String, ratio: Float)

    fun resolveCoverUrlOrDefault(path: String?, purpose: ImagePurpose): String =
        if (isPlaceholder(path)) getDefaultCoverUrl(purpose) else resolveUrl(path, MediaCategory.HOBBY, purpose)

    fun resolveStickerUrl(path: String?): String

    fun resolvePictureUrl(path: String?, purpose: ImagePurpose): String =
        PicUrlRules.resolve(path, purpose)
}
