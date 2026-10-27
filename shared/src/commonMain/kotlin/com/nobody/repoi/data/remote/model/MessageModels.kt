package com.nobody.repoi.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── 1. 未读通知计数模型 ──────────────────────────────────────────────────────────

/**
 * 未读通知消息计数响应主体 (api/unread/push-message/count-v2)。
 */
@Serializable
data class UnreadPushCountPayload(
    val map: UnreadPushCountMap? = null
)

@Serializable
data class UnreadPushCountMap(
    val push: PushCountCategory? = null
)

@Serializable
data class PushCountCategory(
    val system: Int = 0,
    val reply: Int = 0,
    val praise: Int = 0,
    val recommend: Int = 0,
    @SerialName("follownews") val followNews: Int = 0,
    val follow: Int = 0,
    val commodity: Int = 0,
    @SerialName("posts_reply") val postsReply: Int = 0
) {
    /** 关注动态未读数（兼容 follow 与 follownews 键名） */
    val actualFollowCount: Int
        get() = if (followNews > 0) followNews else follow

    /** 全量未读通知总数 */
    val totalCount: Int
        get() = system + reply + praise + recommend + actualFollowCount + commodity + postsReply
}

// ── 2. 私信联系人列表模型 ──────────────────────────────────────────────────────────

/**
 * 私信联系人列表外层载荷 (api/message/contact/get)。
 */
@Serializable
data class MessageContactPayload(
    val contact: List<MessageContactItem> = emptyList()
)

/**
 * 私信联系人列表条目实体。
 */
@Serializable
data class MessageContactItem(
    val id: Long = 0L,
    val otherId: Long = 0L,
    val otherName: String = "",
    val otherHeader: String? = null,
    val lastTime: String? = null,
    val lastContent: String? = null,
    val unreadCount: Int = 0,
    val topTime: String? = null,
    val type: Int = 0
) {
    /** 是否置顶 */
    val isTop: Boolean
        get() = !topTime.isNullOrBlank()
}

// ── 3. 分类通知列表模型 ──────────────────────────────────────────────────────────

/**
 * 分类推送通知外层载荷 (api/push/{type})。
 */
@Serializable
data class PushMessagePayload(
    val pushMessage: List<PushMessageItem> = emptyList()
)

/**
 * 推送通知条目实体。
 */
@Serializable
data class PushMessageItem(
    val id: Long = 0L,
    val type: Int = 0,
    val state: Int = 0, // 0: 未读, 1: 已读
    val title: String? = null,
    val date: String? = null,
    val content: String? = null,
    val params: String? = null,
    val dataMap: PushMessageDataMap? = null
) {
    val isUnread: Boolean
        get() = state == 0
}

/**
 * 推送通知富数据映射载荷。
 */
@Serializable
data class PushMessageDataMap(
    val header: String? = null,
    val nickname: String? = null,
    val itemType: String? = null,
    val relateItemId: Long? = null,
    val relateItemType: String? = null,
    val relateNodeId: Long? = null,
    val nodeId: Long? = null,
    val commentId: Long? = null,
    val comment: String? = null,
    val reply: String? = null,
    val link: String? = null,
    val orderId: String? = null,
    val userId: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val couponsId: String? = null,
    val systemType: Int? = null
)

// ── 4. 私信聊天记录与消息收发模型 ────────────────────────────────────────────────────

/**
 * 1v1 私信聊天历史外层载荷 (api/message/get-v2)。
 */
@Serializable
data class ChatMessagePayload(
    val direction: Int = 0,
    val strangerMessageSetting: Int = 1,
    val canSendState: Int = 1,
    val messages: List<ChatMessageItem> = emptyList()
)

/**
 * 单条私信消息实体。
 */
@Serializable
data class ChatMessageItem(
    val messageId: Long = 0L,
    val id: Long = 0L,
    val fromId: Long = 0L,
    val toId: Long = 0L,
    val content: String = "",
    @Serializable(with = LenientDateTimeToEpochSerializer::class)
    val date: Long = 0L,
    val type: Int = 1, // 1: 文本, 8: 图片
    val emoticon: String? = null,
    val dataMap: ChatMessageDataMap? = null
) {
    /** 真实消息 ID 判定（兼容 messageId 与 id 字段） */
    val actualMessageId: Long
        get() = if (messageId > 0L) messageId else id

    /** 是否为图片消息 */
    val isPicture: Boolean
        get() = type == 8
}

/**
 * 私信富卡片载荷（转让二手/关联条目等）。
 */
@Serializable
data class ChatMessageDataMap(
    val comment: String? = null,
    val itemType: String? = null,
    val resaleId: Long? = null,
    val title: String? = null,
    val pic: String? = null,
    val price: Double? = null
)

/**
 * 轮询最新消息 ID 载荷 (api/message/latestMsgId/get)。
 */
@Serializable
data class LatestMsgIdPayload(
    val latestMsgId: Long = 0L
)

// ── 5. 黑名单管理模型 ──────────────────────────────────────────────────────────

/**
 * 黑名单屏蔽列表外层载荷 (api/block/list)。
 */
@Serializable
data class BlockListPayload(
    val userBlock: List<BlockItem> = emptyList()
)

/**
 * 黑名单用户实体。
 */
@Serializable
data class BlockItem(
    val id: Long = 0L,
    val name: String = "",
    val header: String? = null,
    val intro: String? = null,
    val blockState: Int = 1
)
