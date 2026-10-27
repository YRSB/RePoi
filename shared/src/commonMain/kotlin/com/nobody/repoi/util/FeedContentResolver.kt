package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.ActionItem

object FeedContentResolver {
    fun isTradeAction(actionItem: ActionItem): Boolean =
        actionItem.actionType == 105 || actionItem.actionType == 106

    fun resolveMainComment(actionItem: ActionItem): String? {
        val firstRelateData = actionItem.relateData?.firstOrNull()
        val relateContent = firstRelateData?.content?.trim()?.ifBlank { null }
            ?: firstRelateData?.emoticonText
        return relateContent
            ?: firstRelateData?.detail?.trim()?.ifBlank { null }?.takeIf { isTradeAction(actionItem) }
            ?: actionItem.actionComment?.trim()?.ifBlank { null }
    }

    fun resolveActionVerb(actionItem: ActionItem): String {
        return when (actionItem.actionType) {
            103, 104 -> "传图"
            102 -> "更新"
            105 -> "出物"
            106 -> "求购"
            401 -> when (actionItem.actionData) {
                "confirm" -> "制作"
                "official_pic" -> "官图"
                "preorder" -> "预订"
                "delay" -> "延期"
                "release" -> "发售"
                "reorder" -> "再版"
                else -> "动态"
            }
            else -> if (!resolveMainComment(actionItem).isNullOrBlank()) "评论" else "动态"
        }
    }
}
