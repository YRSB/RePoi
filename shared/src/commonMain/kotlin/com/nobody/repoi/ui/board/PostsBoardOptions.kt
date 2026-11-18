package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.POSTS_BOARD_PAGE_SIZE
import com.nobody.repoi.data.remote.model.POSTS_DEFAULT_ORDER
import com.nobody.repoi.data.remote.model.PostItem

/**
 * 讨论板块的共享定义：排序档位 + 首屏置顶切分。
 *
 * 首页与专区详情共用本对象，避免两处各写一份排序键值/文案而漂移（首页只多一步「按作用域
 * 决定是否展示置顶条」，排序档位完全一致）。
 *
 * ## 排序（对齐 Hpoi 2.43 原版）
 *
 * 反编译 `ZoneDetailSortablePageFragment` 的排序条：讨论分支为
 * `upd`（回复）/ `add`（新贴）/ `top`（推荐），与专区详情现状一致。
 *
 * ## 分类过滤：不适用（原版无入口 + 接口无参数，刻意不提供 UI）
 *
 * 证据（2026-11-15 复核，匿名 `utoken=` 空串，`appVersion=200135`）：
 * 1. **反编译**：`ZoneDetailSortablePageFragment.m()` 只在 `type == "album"` 时请求分类并渲染
 *    分类下拉（`ItemType=album` 的 albums 分类接口）；讨论分支（`type != album`）构造
 *    `ForumPageFragment` 时固定 `itemType = ""`，bundle 中**没有任何分类参数**。
 * 2. **线上实测 `GET api/posts/search`**：
 *    - `itemType` 取反编译 `ForumHomeActivity.type` 的 6 个合法键（`""`/`hobby`/`works`/
 *      `works_element`/`company`/`person`）与非法值，首屏 id 序列**全部相同** → 服务端忽略该参数；
 *    - `category` / `categoryId` / `zoneCategoryId` / `plateId` 同样被忽略（id 序列不变）；
 *    - 实际生效的过滤维度只有 `zoneNodeId`（单值）、`forumNodeId`、`keyword` 与 `order`/`page`。
 * 3. 因此 #158 验收标准中的「分类过滤」以「原版无入口、接口无参数」记为**不适用**，
 *    不伪造分类 Chip；详见 `docs/research/aggregate-api-verification.md` §4.3 与 §5。
 */
object PostsBoardOptions {

    /** 每页条数（原版与专区详情现状均为 20）。 */
    const val PAGE_SIZE = POSTS_BOARD_PAGE_SIZE

    /** 默认排序：回复（`upd`），与专区详情现状一致。 */
    const val DEFAULT_ORDER = POSTS_DEFAULT_ORDER

    /**
     * 3 档排序（协议键 → 2.43 中文文案），顺序对齐原版排序条。
     *
     * 线上实测备注：`upd` 按最后回复时间倒序、`add` 按发帖时间倒序；采样批次里 `top`
     * 与 `add` 返回同一序列（当区无精华/置顶帖），键值仍按原版原样下发。
     */
    val ORDERS: List<Pair<String, String>> = listOf(
        "upd" to "回复",
        "add" to "新贴",
        "top" to "推荐"
    )

    /**
     * 把首屏原始列表切成「置顶帖 + 普通帖」。
     *
     * 从 `ZoneDetailViewModel.partitionPosts` 原样迁移（启发式：存在 `isPinned` 时取前 5 条；
     * 否则 >5 取前 5、>2 取前 2、其余不置顶），保证专区详情讨论板块行为不变。
     *
     * 已知取舍（沿用原实现，勿在别处复制该函数）：显式置顶超过 5 条时，第 6 条起的置顶帖
     * 既不进置顶条也不进普通列表（`unpinned` 只取 `!isPinned`），会**丢帖**；若要修正，请同时
     * 更新 `PostsBoardOptionsTest.partition_capsExplicitPinnedAtFive` 并把超出部分回补到普通列表。
     *
     * 聚合态（全部/关注）不展示置顶条：关注聚合因无多专区通道已隐藏整个讨论板块；全部聚合
     * 由宿主直接跳过本函数，所有条目进入普通列表（置顶是单专区语义）。
     */
    fun partition(rawList: List<PostItem>): Pair<List<PostItem>, List<PostItem>> {
        return if (rawList.any { it.isPinned }) {
            val pinned = rawList.filter { it.isPinned }.take(5)
            val unpinned = rawList.filterNot { it.isPinned }
            pinned to unpinned
        } else if (rawList.size > 5) {
            val pinned = rawList.take(5)
            val unpinned = rawList.drop(5)
            pinned to unpinned
        } else if (rawList.size > 2) {
            val pinned = rawList.take(2)
            val unpinned = rawList.drop(2)
            pinned to unpinned
        } else {
            emptyList<PostItem>() to rawList
        }
    }
}
