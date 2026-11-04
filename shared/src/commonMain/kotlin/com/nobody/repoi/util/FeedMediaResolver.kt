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
    val isAlbumNoCover: Boolean = false,
    val badgeText: String? = null,
    val badgeCategoryType: String? = null
)

object FeedMediaResolver {
    /**
     * 100% 对齐 Hpoi 官方规范 (ActionListAdapter.java:534-574 & FollowAdapter.java:21-36) 解析动态条目的媒体展示数据。
     */
    fun resolve(actionItem: ActionItem): FeedMediaDisplayInfo {
        val relateItem = actionItem.relateItem
        val extraImages = actionItem.relateData?.mapNotNull { it.path }?.filter { it.isNotBlank() } ?: emptyList()

        // 判定条目类型（对齐官方：优先 itemType，兼容 categoryId 区间 60000..69999 与 actionType）
        val itemType = relateItem?.itemType?.lowercase()
        val isAlbum = relateItem?.isAlbum == true ||
            itemType == "album" ||
            actionItem.actionType == 3 ||
            (relateItem?.categoryId != null && relateItem.categoryId in 60000..69999)

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

        val coverUrl: String
        val coverRawUrl: String
        val shouldShowLeftCover: Boolean

        when {
            // 分支 1：存在 path 属性（picture 类实体 / 用户随像单图）
            hasPath -> {
                coverUrl = CdnUrlUtils.getPicNUrl(rawPath)
                coverRawUrl = CdnUrlUtils.getRawPicUrl(rawPath)
                shouldShowLeftCover = true
            }
            // 分支 2：相册无有效封面 -> 官方规范展示浅蓝纯文本卡片 titleCover (AlbumTextCover)
            isAlbum && isAlbumNoCover -> {
                coverUrl = ""
                coverRawUrl = ""
                shouldShowLeftCover = true
            }
            // 分支 3：相册有有效封面 -> 官方规范走 cover/sr/ 自适应比例档位（严禁走 cover/n/ 导致 CDN 404）
            isAlbum -> {
                coverUrl = CdnUrlUtils.getCoverSrUrl(rawCover)
                coverRawUrl = CdnUrlUtils.getRawCoverUrl(rawCover)
                shouldShowLeftCover = true
            }
            // 分支 4：常规条目无封面或为缺省占位符 -> 走 cover/n/none.jpg
            rawCover.isNullOrBlank() || CdnUrlUtils.isNoCover(rawCover) -> {
                coverUrl = CdnUrlUtils.getDefaultCoverUrl(CdnUrlUtils.CoverVariant.NORMAL)
                coverRawUrl = CdnUrlUtils.getDefaultCoverUrl(CdnUrlUtils.CoverVariant.RAW)
                shouldShowLeftCover = relateItem != null
            }
            // 分支 5：常规条目（hobby/周边等）有封面 -> 走 cover/n/
            else -> {
                coverUrl = CdnUrlUtils.getCoverUrl(rawCover)
                coverRawUrl = CdnUrlUtils.getRawCoverUrl(rawCover)
                shouldShowLeftCover = true
            }
        }

        // 附图列表（对齐官方 ActionListAdapter.java:666-677）：
        // 官方直接遍历 relateData 渲染附图九宫格（pic/s/），若仅有 1 张附图且与左侧主图完全同名，保留单图去重以保持精致排版
        val coverFileName = (rawPath ?: rawCover)?.substringAfterLast("/")?.substringBefore("?") ?: ""
        val displayExtraImages = if (extraImages.size == 1 && extraImages[0].substringAfterLast("/").substringBefore("?") == coverFileName) {
            emptyList()
        } else {
            extraImages
        }

        // 原图列表全量收集（供全屏大图查看器）
        val extraRawUrls = displayExtraImages.map { CdnUrlUtils.getRawPicUrl(it) }
        val allRawUrls = buildList {
            if (coverRawUrl.isNotBlank() && shouldShowLeftCover && !isAlbumNoCover) {
                add(coverRawUrl)
            }
            extraRawUrls.forEach { if (it.isNotBlank() && !contains(it)) add(it) }
        }

        // 解析左上角角标文本与分类类型（对齐官方 Companion.a & o.r）
        val badgeCategoryType = when {
            hasPath -> "picture"
            isAlbum -> "album"
            itemType != null -> itemType
            else -> "hobby"
        }
        val badgeText = resolveBadgeText(relateItem?.categoryId, isAlbum)

        return FeedMediaDisplayInfo(
            shouldShowLeftCover = shouldShowLeftCover,
            coverUrl = coverUrl,
            displayExtraImages = displayExtraImages,
            allRawUrls = allRawUrls,
            isAlbumNoCover = isAlbumNoCover,
            badgeText = badgeText,
            badgeCategoryType = badgeCategoryType
        )
    }

    /**
     * 根据 categoryId 与是否为相册推断官方同款分类标签文本（对齐 net.hpoi.utils.o.r 逻辑）。
     */
    private fun resolveBadgeText(categoryId: Int?, isAlbum: Boolean): String? {
        if (categoryId == null || categoryId <= 0) return if (isAlbum) "相册" else null
        return when (categoryId) {
            60001 -> "手办随像"
            60003 -> "漫展"
            60004 -> "痛车"
            60100 -> "日常"
            60900 -> "其他"
            in 60000..69999 -> "相册"
            1, 101, 102, 103, 104, 105, 106, 107, 108 -> "手办"
            2, 201, 202, 203, 204 -> "模型"
            3, 301, 302, 303, 304 -> "周边"
            else -> if (isAlbum) "相册" else null
        }
    }
}

/**
 * 针对 [ActionItem] 的便捷扩展方法。
 */
fun ActionItem.resolveMediaDisplayInfo(): FeedMediaDisplayInfo =
    FeedMediaResolver.resolve(this)
