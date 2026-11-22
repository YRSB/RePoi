package com.nobody.repoi.util

import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.ActionItem

data class FeedMediaDisplayInfo(
    val shouldShowLeftCover: Boolean,
    val coverUrl: String,
    val displayExtraImages: List<String>,
    val allRawUrls: List<String>,
    val isAlbumNoCover: Boolean = false
)

object FeedMediaResolver {

    fun resolve(actionItem: ActionItem): FeedMediaDisplayInfo = resolve(actionItem, MediaPipeline.Default)

    fun resolve(actionItem: ActionItem, media: MediaPipeline): FeedMediaDisplayInfo {
        val relateItem = actionItem.relateItem
        val extraImages = actionItem.relateData?.mapNotNull { it.path }?.filter { it.isNotBlank() } ?: emptyList()

        val isAlbum = relateItem?.isAlbum == true || actionItem.actionType == 3

        val rawPath = relateItem?.path?.trim()
        val rawCover = relateItem?.cover?.trim()
        val hasCoverPath = !rawCover.isNullOrBlank() && !media.isPlaceholder(rawCover)

        val hasPath = !rawPath.isNullOrBlank()
        val isAlbumNoCover = isAlbum && !hasCoverPath

        data class CoverPick(val url: String, val rawUrl: String, val showLeft: Boolean, val dedupPath: String?)

        val pick = when {
            hasPath -> CoverPick(
                media.resolvePictureUrl(rawPath, ImagePurpose.CARD_STANDARD),
                media.resolvePictureUrl(rawPath, ImagePurpose.FULLSCREEN_RAW),
                true,
                rawPath
            )
            isAlbumNoCover -> CoverPick("", "", true, null)
            isAlbum -> CoverPick(
                media.resolveUrl(rawCover, MediaCategory.ALBUM, ImagePurpose.ADAPTIVE_GRID),
                media.resolveRawUrl(rawCover, MediaCategory.ALBUM),
                true,
                rawCover
            )
            !hasCoverPath && extraImages.isNotEmpty() -> CoverPick(
                media.resolvePictureUrl(extraImages.first(), ImagePurpose.CARD_STANDARD),
                media.resolvePictureUrl(extraImages.first(), ImagePurpose.FULLSCREEN_RAW),
                relateItem != null,
                extraImages.first()
            )
            !hasCoverPath -> CoverPick(
                media.getDefaultCoverUrl(ImagePurpose.CARD_STANDARD),
                media.getDefaultCoverUrl(ImagePurpose.FULLSCREEN_RAW),
                relateItem != null,
                null
            )
            else -> CoverPick(
                media.resolveUrl(rawCover, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD),
                media.resolveRawUrl(rawCover, MediaCategory.HOBBY),
                true,
                rawCover
            )
        }
        val coverUrl = pick.url
        val coverRawUrl = pick.rawUrl
        val shouldShowLeftCover = pick.showLeft

        val coverFileName = pick.dedupPath?.substringAfterLast("/")?.substringBefore("?") ?: ""
        val displayExtraImages = if (coverFileName.isBlank()) {
            extraImages
        } else {
            val nonCoverImages = extraImages.filter {
                it.substringAfterLast("/").substringBefore("?") != coverFileName
            }
            if (nonCoverImages.isNotEmpty() || extraImages.size <= 1) nonCoverImages else extraImages
        }

        val extraRawUrls = displayExtraImages.map { media.resolvePictureUrl(it, ImagePurpose.FULLSCREEN_RAW) }
        val allRawUrls = buildList {
            if (coverRawUrl.isNotBlank() && shouldShowLeftCover && !isAlbumNoCover) {
                add(coverRawUrl)
            }
            extraRawUrls.forEach { if (it.isNotBlank() && !contains(it)) add(it) }
        }

        return FeedMediaDisplayInfo(
            shouldShowLeftCover = shouldShowLeftCover,
            coverUrl = coverUrl,
            displayExtraImages = displayExtraImages,
            allRawUrls = allRawUrls,
            isAlbumNoCover = isAlbumNoCover
        )
    }
}

fun ActionItem.resolveMediaDisplayInfo(): FeedMediaDisplayInfo =
    FeedMediaResolver.resolve(this)
