package com.nobody.repoi.util

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

object FeedMediaResolver {
    /**
     * 根据 Hpoi 官方规范解析动态条目的媒体展示数据。
     */
    fun resolve(actionItem: ActionItem): FeedMediaDisplayInfo {
        val relateItem = actionItem.relateItem
        val extraImages = actionItem.relateData?.mapNotNull { it.path }?.filter { it.isNotBlank() } ?: emptyList()

        val itemType = relateItem?.itemType?.lowercase()
        val isAlbum = itemType == "album" || actionItem.actionType == 3

        val rawCover = relateItem?.cover?.trim()
        val isAlbumNoCover = isAlbum && (rawCover.isNullOrBlank() || CdnUrlUtils.isNoCover(rawCover))
        val hasValidCover = !rawCover.isNullOrBlank() && (!isAlbum || !isAlbumNoCover)

        // 检查是否为用户传图 / picture 类（官方逻辑：有 path 则为 picture 类）
        val rawPath = relateItem?.path?.trim()
        val isPicture = !rawPath.isNullOrBlank() ||
            (relateItem?.cover == null && relateItem?.path == null && extraImages.isNotEmpty())

        val coverPath = when {
            isAlbum -> if (hasValidCover) rawCover else null
            hasValidCover -> rawCover
            !rawPath.isNullOrBlank() -> rawPath
            extraImages.isNotEmpty() -> extraImages.firstOrNull()
            relateItem != null -> CdnUrlUtils.DEFAULT_COVER_PATH
            else -> null
        }

        // 官方规则（ActionListAdapter.java:540-563）：
        // 相册有图时左侧展示封面图，无图时左侧展示 17:24 的浅天青纯文本卡片 (titleCover)！
        val shouldShowLeftCover = when {
            isAlbum -> true
            else -> !coverPath.isNullOrBlank()
        }

        // 首页左侧大封面 CDN 档位策略：
        // 1. 相册：若无封面则为 ""（由 ActionCard 渲染 AlbumTextCover），有封面则为 cover/sr/
        // 2. 用户发的图（picture 类型）：pic/n/
        // 3. 周边/手办/模型等常规条目：cover/n/（列表中图）
        val coverUrl = when {
            !shouldShowLeftCover || coverPath.isNullOrBlank() || isAlbumNoCover -> ""
            isPicture -> CdnUrlUtils.getPicNUrl(coverPath)
            isAlbum -> CdnUrlUtils.getCoverSrUrl(coverPath)
            else -> CdnUrlUtils.getCoverUrl(coverPath)
        }

        // 智能去重：若仅有一张图且与左侧主封面相同，右侧附图不重复展示
        val coverFileName = coverPath?.substringAfterLast("/")?.substringBefore("?") ?: ""
        val isSingleDuplicateCover = extraImages.size == 1 &&
            extraImages[0].substringAfterLast("/").substringBefore("?") == coverFileName

        val displayExtraImages = if (isSingleDuplicateCover) {
            emptyList()
        } else {
            val nonCoverImages = extraImages.filter {
                it.substringAfterLast("/").substringBefore("?") != coverFileName
            }
            if (nonCoverImages.isNotEmpty()) nonCoverImages else extraImages
        }

        // 原图 URL 收集（全屏图片查看器直接使用 raw 档全量原图）
        val coverRawUrl = when {
            !shouldShowLeftCover || coverPath.isNullOrBlank() || isAlbumNoCover -> ""
            isPicture -> CdnUrlUtils.getRawPicUrl(coverPath)
            else -> CdnUrlUtils.getRawCoverUrl(coverPath)
        }
        val extraRawUrls = displayExtraImages.map { CdnUrlUtils.getRawPicUrl(it) }

        val allRawUrls = buildList {
            if (coverRawUrl.isNotBlank() && shouldShowLeftCover) add(coverRawUrl)
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