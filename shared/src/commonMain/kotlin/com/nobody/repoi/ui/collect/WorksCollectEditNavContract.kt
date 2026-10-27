package com.nobody.repoi.ui.collect

import kotlinx.serialization.Serializable

/**
 * 作品收藏编辑页导航参数（原版 `CollectWorksActivity` 的 intent extra）。
 *
 * ⚠️ 路由键 `WorksCollectEditNavKey` 需在 `ui/navigation/NavRoutes.kt` 补齐
 * （该文件不在本页写入范围，由宿主统一接线）。
 */
@kotlinx.serialization.Serializable
data class WorksCollectEditNavParam(
    /** 作品条目 ID */
    val worksId: Long,
    /** 条目分类 ID，`10200` 为游戏（三态文案变体），默认沿用原版兜底值 10000 */
    val categoryId: Int = DEFAULT_WORKS_CATEGORY_ID,
    val initialName: String? = null,
    val initialCover: String? = null
)


/** 原版 `CollectWorksActivity.java:532` 的 `categoryId` 兜底值 */
const val DEFAULT_WORKS_CATEGORY_ID = 10000


/** 游戏分类：三态文案变体为「想玩 / 在玩 / 玩过」 */
const val GAME_WORKS_CATEGORY_ID = 10200


/** 单个标签字数上限（原版 `CollectWorksActivity.java:420`：`tag.length > 8` 直接拒绝） */
const val MAX_WORKS_TAG_LENGTH = 8


/** 单个作品标签总数上限（原版 `CollectWorksActivity.java:427`） */
const val MAX_WORKS_TAG_COUNT = 15


/** 简评上限（原版后端口径，与手办收藏编辑页一致） */
const val MAX_WORKS_COMMENT_LENGTH = 500
