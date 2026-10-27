package com.nobody.repoi.ui.comment

import kotlinx.serialization.Serializable
import com.nobody.repoi.data.remote.model.CommentItem

/**
 * 评论详情页路由导航参数。
 */
@Serializable
data class CommentDetailNavParam(
    val commentId: Long,
    val relateNodeId: Long,
    val snapshot: CommentItem? = null,
    val isHot: Boolean = false,
    val isDiscuss: Boolean = false
)
