package com.nobody.repoi.data.remote.model

import com.nobody.repoi.util.CdnUrlUtils
import kotlinx.serialization.Serializable

/**
 * 二手交易条目关联用户信息。
 */
@Serializable
data class ResaleUserData(
    val userId: Long = 0L,
    val nickname: String? = null,
    val header: String? = null
)

typealias ResaleUserSummary = ResaleUserData

/**
 * 二手交易关联手办/周边基础信息。
 */
@Serializable
data class ResaleHobbyData(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null
)

typealias ResaleHobbySummary = ResaleHobbyData

/**
 * 单条二手交易检索条目（api/resale/search）。
 */
@Serializable
data class ResaleSearchItem(
    val id: Long = 0L,
    val price: String? = null,
    val purityStr: String? = null,
    val purity: Int = 0,
    val location: String? = null,
    val detail: String? = null,
    val addDate: String? = null,
    val addDateStr: String? = null,
    val pic: String? = null,
    val type: Int = 1,
    val link: String? = null,
    val item: List<ResaleHobbyData>? = null,
    val user: ResaleUserData? = null,
    val state: Int = 1,
    val zoneIds: List<Long> = emptyList()
) {
    /** 是否为出售类型（1=出售，2=收购） */
    val isSell: Boolean
        get() = type == 1

    /** 是否为收购类型（1=出售，2=收购） */
    val isBuy: Boolean
        get() = type == 2

    /** 是否存在外部链接（如闲鱼/淘宝等） */
    val hasExternalLink: Boolean
        get() = !link.isNullOrBlank()

    /** 纯文本详情描述（过滤 HTML 标签与转义字符） */
    val cleanDetail: String
        get() {
            val d = detail ?: return ""
            if (!d.contains('<') && !d.contains('&')) return d.trim()
            return androidx.core.text.HtmlCompat.fromHtml(d, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
        }

    /** 关联手办条目主封面相对路径 */
    val hobbyCover: String?
        get() = item?.firstOrNull()?.cover?.takeIf { it.isNotBlank() }

    /** 是否包含卖家上传的自定义实物图 */
    val hasUserPic: Boolean
        get() = !pic.isNullOrBlank()

    /** 卖家上传的实物图完整 CDN 链接列表（支持逗号分隔的多张图片，基于 resale/n/ 规范） */
    val userPics: List<String>
        get() = pic?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { CdnUrlUtils.getResalePicUrl(it) }
            ?: emptyList()

    /**
     * 卡片左侧海报展示完整 CDN URL：
     * 100% 对齐 Hpoi 官方原版（ResaleListAdapter: 始终优先展示 item[0].cover 并拼接 cover/n/）。
     * 仅在未关联手办或手办无封面时，安全回退至卖家自定义实物图（拼接 resale/n/）。
     */
    val displayCoverUrl: String?
        get() {
            val hCover = hobbyCover
            if (!hCover.isNullOrBlank()) {
                return CdnUrlUtils.getCoverUrl(hCover)
            }
            return userPics.firstOrNull()
        }

    /** 兼容旧代码的封面相对路径 */
    val displayCover: String?
        get() = hobbyCover ?: pic?.split(",")?.firstOrNull { it.isNotBlank() }

    /** 关联条目详情 ID */
    val targetItemId: Long
        get() = item?.firstOrNull()?.let { if (it.itemId > 0L) it.itemId else it.id } ?: 0L

    /** 格式化显示价格 */
    val displayPriceText: String
        get() = price?.takeIf { it.isNotBlank() }?.let { "¥$it" } ?: "面议"
}

/**
 * api/resale/search 响应 Payload。
 */
@Serializable
data class ResaleSearchPayload(
    val list: List<ResaleSearchItem> = emptyList()
)

/**
 * 二手详情条目关联用户信息。
 */
@Serializable
data class ResaleDetailUser(
    val userId: Long = 0L,
    val nickname: String? = null,
    val header: String? = null,
    val level: Int = 0
)

/**
 * 二手详情关联手办条目（api/resale/data/get 的 linkItems）。
 */
@Serializable
data class ResaleDetailHobbyItem(
    val itemNodeId: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null
) {
    val targetItemId: Long
        get() = if (itemId > 0L) itemId else itemNodeId

    val displayName: String
        get() = nameCN?.takeIf { it.isNotBlank() } ?: name ?: "关联条目"
}

/**
 * 二手详情条目数据（api/resale/data/get 的 resale 节点）。
 */
@Serializable
data class ResaleDetailItem(
    val id: Long = 0L,
    val price: String? = null,
    val purityStr: String? = null,
    val purity: Int = 0,
    val location: String? = null,
    val detail: String? = null,
    val addDate: String? = null,
    val addDateStr: String? = null,
    val link: String? = null,
    val pic: String? = null,
    val type: Int = 1,
    val state: Int = 1,
    val linkItems: List<ResaleDetailHobbyItem> = emptyList(),
    val user: ResaleDetailUser? = null
) {
    /** 卖家上传的实物图完整 CDN 链接列表（支持逗号分隔的多张图片，基于 resale/n/ 规范） */
    val userPics: List<String>
        get() = pic?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { CdnUrlUtils.getResalePicUrl(it) }
            ?: emptyList()

    val displayPriceText: String
        get() = price?.takeIf { it.isNotBlank() }?.let { "¥$it" } ?: "面议"

    val isSell: Boolean get() = type == 1
    val isBuy: Boolean get() = type == 2

    /** 是否包含闲鱼/淘宝淘口令或外链 */
    val hasExternalLink: Boolean get() = !link.isNullOrBlank()

    /** 关联手办 */
    val firstHobby: ResaleDetailHobbyItem? get() = linkItems.firstOrNull()
}

/**
 * api/resale/data/get 响应载荷。
 */
@Serializable
data class ResaleDetailPayload(
    val resale: ResaleDetailItem? = null
)
