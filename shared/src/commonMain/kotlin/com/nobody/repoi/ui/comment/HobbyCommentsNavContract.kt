package com.nobody.repoi.ui.comment

import kotlinx.serialization.Serializable

/**
 * 全屏评论列表页路由导航参数。
 *
 * @param nodeId 条目节点 id（详情页 actualNodeId，评论接口的 id 参数）。
 * @param itemType 条目类型，默认 hobby。
 * @param title 页标题，默认评论。
 * @param initialCommentCount 进入时已知的评论总数，用于首屏占位。
 */
@Serializable
data class HobbyCommentsNavParam(
    val nodeId: Long,
    val itemType: String = "hobby",
    val title: String = "评论",
    val initialCommentCount: Int = 0
)
