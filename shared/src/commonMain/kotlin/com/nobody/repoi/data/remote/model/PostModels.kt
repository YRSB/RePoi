package com.nobody.repoi.data.remote.model

import com.nobody.repoi.platform.nowMillis
import kotlinx.serialization.Serializable

/**
 * 讨论贴列表返回外层结构。
 */
@Serializable
data class PostListPayload(
    val list: List<PostItem> = emptyList()
)

/**
 * 讨论贴单个条目数据模型 (api/posts/search)。
 */
@Serializable
data class PostItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val title: String = "",
    val content: String? = null,
    val userName: String = "",
    val userId: Long = 0L,
    val userLevel: Int = 0,
    val userHeader: String? = null,
    val forumName: String? = null,
    val forumNodeId: Long? = null,
    val userNodeId: Long? = null,
    val discussCount: Int = 0,
    val pics: String? = null,
    val addTime: Long = 0L,
    val updTime: Long = 0L,
    val topTime: Long = 0L,
    val elite: Int = 0,
    val state: Int = 0,
    val relateItemId: Long? = null,
    val relateItemType: String? = null,
    val relateItemName: String? = null,
    val zoneIds: List<Long>? = null
) {
    /** 解析逗号分隔的图片相对路径列表 */
    val picList: List<String>
        get() = pics?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    /** 是否置顶 */
    val isPinned: Boolean
        get() = topTime > 0L

    /** 是否展示回复时间（有更新时间且与发帖时间不一致） */
    val isReplyTime: Boolean
        get() = updTime > 0L && updTime != addTime

    /** 清理 HTML 转义（如 &nbsp;）的纯文本内容 */
    val displayContent: String
        get() = content?.replace("&nbsp;", " ")?.trim() ?: ""

    /** 格式化显示相对时间文本（例如：发布于5天前、回复于6天前） */
    val formattedTimeText: String
        get() {
            val targetTime = if (isReplyTime) updTime else addTime
            val prefix = if (isReplyTime) "回复于" else "发布于"
            if (targetTime <= 0L) return ""

            val now = nowMillis()
            val diff = now - targetTime
            val minute = 60 * 1000L
            val hour = 60 * minute
            val day = 24 * hour

            val relative = when {
                diff < minute -> "刚刚"
                diff < hour -> "${(diff / minute).coerceAtLeast(1)}分钟前"
                diff < day -> "${diff / hour}小时前"
                diff < 30 * day -> "${diff / day}天前"
                diff < 365 * day -> "${diff / (30 * day)}个月前"
                else -> "${diff / (365 * day)}年前"
            }
            return "$prefix$relative"
        }
}

/**
 * 帖子详情返回外层结构 (api/posts/info-v2)。
 */
@Serializable
data class PostDetailPayload(
    val posts: PostDetailData? = null
)

/**
 * 讨论贴作者信息。
 */
@Serializable
data class PostAuthor(
    val id: Long = 0L,
    val userId: Long = 0L,
    val nickname: String? = null,
    val header: String? = null,
    val level: Int = 0,
    val userType: Int = 0
) {
    val effectiveUserId: Long
        get() = if (id > 0L) id else userId

    val levelText: String
        get() = if (level > 0) "Lv.$level" else ""
}

/**
 * 讨论贴所属板块信息。
 */
@Serializable
data class PostForum(
    val id: Long = 0L,
    val name: String? = null
)

/**
 * 讨论贴关联手办/条目信息。
 */
@Serializable
data class PostRelateItem(
    val itemId: Long = 0L,
    val nameCN: String? = null,
    val name: String? = null,
    val cover: String? = null,
    val itemType: String? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name?.ifBlank { null } ?: "关联手办"
}

/**
 * 帖子详情实体数据模型 (api/posts/info-v2 中的 posts 结构)。
 */
@Serializable
data class PostDetailData(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val title: String = "",
    val content: String? = null,
    val hits: Int = 0,
    val discussCount: Int = 0,
    val commentCount: Int = 0,
    val pics: String? = null,
    @Serializable(with = LenientDateTimeToEpochSerializer::class)
    val addTime: Long = 0L,
    val user: PostAuthor? = null,
    val forum: PostForum? = null,
    val relateItem: PostRelateItem? = null,
    val zoneIds: List<Long>? = null
) {
    /** 解析逗号分隔的图片相对路径列表 */
    val picList: List<String>
        get() = pics?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    /** 格式化显示发布相对时间 */
    val formattedTimeText: String
        get() {
            if (addTime <= 0L) return ""
            val now = nowMillis()
            val diff = now - addTime
            val minute = 60 * 1000L
            val hour = 60 * minute
            val day = 24 * hour

            val relative = when {
                diff < minute -> "刚刚"
                diff < hour -> "${(diff / minute).coerceAtLeast(1)}分钟前"
                diff < day -> "${diff / hour}小时前"
                diff < 30 * day -> "${diff / day}天前"
                diff < 365 * day -> "${diff / (30 * day)}个月前"
                else -> "${diff / (365 * day)}年前"
            }
            return "发布于$relative"
        }
}

/**
 * 讨论贴回帖列表响应外层结构 (api/discuss/get)。
 */
@Serializable
data class DiscussListPayload(
    val discussList: List<DiscussItem>? = emptyList()
)

/**
 * 讨论贴单条回帖模型。
 */
@Serializable
data class DiscussItem(
    val id: Long = 0L,
    val user: CommentUser? = null,
    val content: String? = null,
    val floor: Int = 0,
    val addTime: String? = null,
    val replyCount: Int = 0,
    val reply: Int = 0,
    val emoticon: kotlinx.serialization.json.JsonElement? = null,
    val replyArr: List<DiscussReplyItem>? = null
) {
    fun toCommentItem(): CommentItem {
        val finalReplyCount = if (replyCount > 0) replyCount else reply
        val userMap = mutableMapOf<String, CommentUser>()
        user?.atId?.let { userMap[it] = user }
        val mappedReplies = replyArr?.map { item ->
            item.user?.atId?.let { userMap[it] = item.user }
            item.toUser?.atId?.let { userMap[it] = item.toUser }
            val resolvedUser = item.user?.let { u ->
                if (u.nickname.isNullOrBlank() && u.atRef != null) userMap[u.atRef] ?: u else u
            }
            val resolvedToUser = item.toUser?.let { tu ->
                if (tu.nickname.isNullOrBlank() && tu.atRef != null) {
                    userMap[tu.atRef] ?: tu
                } else if (tu.nickname.isNullOrBlank() && tu.id == user?.id && user?.nickname != null) {
                    user
                } else {
                    tu
                }
            }
            item.toCommentReplyItem(resolvedUser = resolvedUser, resolvedToUser = resolvedToUser)
        }
        return CommentItem(
            id = id,
            content = content,
            floor = floor,
            addTime = addTime,
            user = user,
            reply = finalReplyCount,
            replys = mappedReplies,
            emoticon = emoticon
        )
    }
}

/**
 * 讨论贴楼中楼回复模型。
 */
@Serializable
data class DiscussReplyItem(
    val id: Long = 0L,
    val user: CommentUser? = null,
    val toUser: CommentUser? = null,
    val content: String? = null,
    val addTime: String? = null,
    val emoticon: kotlinx.serialization.json.JsonElement? = null
) {
    fun toCommentReplyItem(
        resolvedUser: CommentUser? = null,
        resolvedToUser: CommentUser? = null
    ): CommentReplyItem {
        return CommentReplyItem(
            id = id,
            user = resolvedUser ?: user,
            toUser = resolvedToUser ?: toUser,
            content = content,
            addTime = addTime,
            emoticon = emoticon
        )
    }
}

/**
 * 讨论贴楼中楼回复列表响应载荷 (api/discuss/reply/get)。
 */
@Serializable
data class DiscussReplyListPayload(
    val discuss: DiscussItem? = null,
    val discussReplyList: List<DiscussReplyItem>? = emptyList()
) {
    /**
     * 将讨论贴楼中楼响应转换为通用 [CommentThreadPage]。
     * 自动处理 FastJson 循环引用与 `@ref` 节点还原。
     */
    fun toCommentThreadPage(parentSnapshot: CommentItem? = null): CommentThreadPage {
        val rootItem = discuss?.toCommentItem() ?: parentSnapshot
        val userMap = mutableMapOf<String, CommentUser>()
        rootItem?.user?.let { u ->
            u.atId?.let { userMap[it] = u }
        }
        val replies = discussReplyList.orEmpty().map { item ->
            item.user?.atId?.let { userMap[it] = item.user }
            item.toUser?.atId?.let { userMap[it] = item.toUser }
            val resolvedUser = item.user?.let { u ->
                if (u.nickname.isNullOrBlank() && u.atRef != null) userMap[u.atRef] ?: u else u
            }
            val resolvedToUser = item.toUser?.let { tu ->
                if (tu.nickname.isNullOrBlank() && tu.atRef != null) {
                    userMap[tu.atRef] ?: tu
                } else if (tu.nickname.isNullOrBlank() && tu.id == rootItem?.user?.id && rootItem.user.nickname != null) {
                    rootItem.user
                } else {
                    tu
                }
            }
            item.toCommentReplyItem(resolvedUser = resolvedUser, resolvedToUser = resolvedToUser)
        }
        return CommentThreadPage(
            comment = rootItem,
            replies = replies
        )
    }
}
