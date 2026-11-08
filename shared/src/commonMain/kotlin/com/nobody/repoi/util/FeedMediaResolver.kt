package com.nobody.repoi.util

import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.ActionItem

/**
 * 动态流卡片媒体（封面图、附图与全屏大图）展示信息。
 */
data class FeedMediaDisplayInfo(
    val shouldShowLeftCover: Boolean,
    val coverUrl: String,
    val displayExtraImages: List<String>,
    val allRawUrls: List<String>,
    val isAlbumNoCover: Boolean = false
)

/**
 * 动态流媒体解析器：保留动态流卡片展示信息拼装模型，底层统一委托 [MediaPipeline]。
 *
 * 默认消费 [MediaPipeline.Default]（无状态纯计算）；需要替换实现（测试替身/未来策略切换）时
 * 直接调用接受 [MediaPipeline] 参数的重载，避免引入全局可变状态。
 */
object FeedMediaResolver {
    /**
     * 100% 对齐 Hpoi 官方规范 (ActionListAdapter.java:534-574 & FollowAdapter.java:21-36) 解析动态条目的媒体展示数据。
     */
    fun resolve(actionItem: ActionItem): FeedMediaDisplayInfo = resolve(actionItem, MediaPipeline.Default)

    /** 以指定管道解析（测试注入用）。 */
    fun resolve(actionItem: ActionItem, media: MediaPipeline): FeedMediaDisplayInfo {
        val relateItem = actionItem.relateItem
        val extraImages = actionItem.relateData?.mapNotNull { it.path }?.filter { it.isNotBlank() } ?: emptyList()

        val isAlbum = relateItem?.isAlbum == true || actionItem.actionType == 3

        val rawPath = relateItem?.path?.trim()
        val rawCover = relateItem?.cover?.trim()
        val hasCoverPath = !rawCover.isNullOrBlank() && !media.isPlaceholder(rawCover)

        // 官方判定逻辑（ActionListAdapter.java 536-574 & FollowAdapter.java 21-36）：
        // 1. 若 relateItem 具有 path 属性（picture 类/手办随像）：直接使用 pic/n/ + path；原图使用 pic/raw/ + path。
        // 2. 若为相册（isAlbum）：
        //    - cover 为空或为 none.jpg → 纯文本卡片（isAlbumNoCover = true，coverUrl = ""）；
        //    - 有封面 → 官方必须走 cover/sr/（CDN 上相册无 cover/n/ 规格，请求会 404！）。
        // 3. 否则为常规条目（hobby / article 等）：
        //    - cover 为空或 none.jpg → 回退首张附图（pic/n/），无附图则 cover/n/none.jpg 缺省图；
        //    - 有封面 → cover/n/ + cover。
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
        // 原图列表全量收集（供全屏大图查看器）
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

/**
 * 针对 [ActionItem] 的便捷扩展方法。
 */
fun ActionItem.resolveMediaDisplayInfo(): FeedMediaDisplayInfo =
    FeedMediaResolver.resolve(this)
