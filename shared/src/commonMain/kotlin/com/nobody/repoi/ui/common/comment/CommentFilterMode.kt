package com.nobody.repoi.ui.common.comment

/**
 * 评论区内容过滤与展示模式。
 *
 * Wave 5 转正式契约（与老项目 `CommentSection.kt` 同名同字段）。
 */
enum class CommentFilterMode(val label: String) {
    /** 默认模式：展示前 5 条热门评论 + 分隔线 + 最新时序评论流 */
    DEFAULT("默认"),

    /** 热门模式：只展示所有热门神评 */
    HOT("热门"),

    /** 最新评论模式：只展示按时间倒序的最新评论流 */
    LATEST("最新")
}
