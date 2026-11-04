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
     * 100% 对齐 Hpoi 官方规范 (ActionListAdapter.java:534-574 & FollowAdapter.java:21-36) 解析动态条目的媒体展示数据。
     */
    fun resolve(actionItem: ActionItem): FeedMediaDisplayInfo {
        val relateItem = actionItem.relateItem
        val extraImages = actionItem.relateData?.mapNotNull { it.path }?.filter { it.isNotBlank() } ?: emptyList()

        val isAlbum = relateItem?.isAlbum == true || actionItem.actionType == 3

        val rawPath = relateItem?.path?.trim()
        val rawCover = relateItem?.cover?.trim()

        // 官方判定逻辑（ActionListAdapter.java 536-574 & FollowAdapter.java 21-36）：
        // 1. 若 relateItem 具有 path 属性（picture 类/手办随像）：
        //    直接使用 pic/n/ + path；原图使用 pic/raw/ + path。
        // 2. 若为相册（isAlbum）：
        //    - 若 cover 为空或为 none.jpg，显示纯文本卡片（isAlbumNoCover = true，coverUrl = ""）；
        //    - 若有封面，官方必须走 cover/sr/ + cover（CDN 上相册无 cover/n/ 规格，请求会报 404！）；
        //      原图查看器使用 cover/raw/ + cover。
        // 3. 否则为常规条目（hobby / article 等）：
        //    - 若 cover 为空或为 none.jpg，回退到官方缺省图 cover/n/none.jpg；
        //    - 若有封面，走 cover/n/ + cover；原图查看器使用 cover/raw/ + cover。

        val hasPath = !rawPath.isNullOrBlank()
        val isAlbumNoCover = isAlbum && (rawCover.isNullOrBlank() || CdnUrlUtils.isNoCover(rawCover))

        data class CoverPick(val url: String, val rawUrl: String, val showLeft: Boolean, val dedupPath: String?)

        // 官方判定逻辑（ActionListAdapter.java 536-574 & FollowAdapter.java 21-36）：
        // 存在 path 用 pic/n/；相册有封面走 cover/sr/（无 cover/n/ 规格，请求报 404），无封面走纯文本卡；
        // 常规有封面走 cover/n/，无封面回退首张附图或 cover/n/none.jpg。
        val pick = when {
            hasPath -> CoverPick(
                CdnUrlUtils.getPicNUrl(rawPath),
                CdnUrlUtils.getRawPicUrl(rawPath),
                true,
                rawPath
            )
            isAlbum && isAlbumNoCover -> CoverPick("", "", true, null)
            isAlbum -> CoverPick(
                CdnUrlUtils.getCoverSrUrl(rawCover),
                CdnUrlUtils.getRawCoverUrl(rawCover),
                true,
                rawCover
            )
            (rawCover.isNullOrBlank() || CdnUrlUtils.isNoCover(rawCover)) && extraImages.isNotEmpty() -> CoverPick(
                CdnUrlUtils.getPicNUrl(extraImages.first()),
                CdnUrlUtils.getRawPicUrl(extraImages.first()),
                relateItem != null,
                extraImages.first()
            )
            rawCover.isNullOrBlank() || CdnUrlUtils.isNoCover(rawCover) -> CoverPick(
                CdnUrlUtils.getDefaultCoverUrl(CdnUrlUtils.CoverVariant.NORMAL),
                CdnUrlUtils.getDefaultCoverUrl(CdnUrlUtils.CoverVariant.RAW),
                relateItem != null,
                null
            )
            else -> CoverPick(
                CdnUrlUtils.getCoverUrl(rawCover),
                CdnUrlUtils.getRawCoverUrl(rawCover),
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
            if (nonCoverImages.isNotEmpty()) nonCoverImages else extraImages
        }
        // 原图列表全量收集（供全屏大图查看器）
        val extraRawUrls = displayExtraImages.map { CdnUrlUtils.getRawPicUrl(it) }
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
