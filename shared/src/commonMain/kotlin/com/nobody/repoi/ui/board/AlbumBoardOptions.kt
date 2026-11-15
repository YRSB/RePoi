package com.nobody.repoi.ui.board

/**
 * 分享（相册）板块的排序档位与分类选项：首页与专区详情**唯一**共享定义（issue #157）。
 *
 * 键值来自 `docs/home-hierarchy.md` §3.4「推荐/最新创建/最近更新/图片数量/总热度/一天热度」，
 * 与线上匿名实测一致（2026-11-15，`POST api/album/list` 六个键均返回不同批次：
 * `top`/`add`/`update`/`picCount`/`hits`/`hitsDay`）；文案与专区详情历史实现保持一致。
 *
 * 禁止在首页/详情各写一份（本定义即 #153 那次「共享常量收敛」的相册版本）。
 */
object AlbumBoardOptions {
    /** 默认排序（与专区详情历史默认值一致）。 */
    const val DEFAULT_ORDER: String = "top"

    /** 列表页大小（专区详情与首页共用，服务端 `pageSize` 语义见 `aggregate-api-verification.md` R7）。 */
    const val PAGE_SIZE: Int = 24

    /** 6 档排序：`key` = 协议 `order` 值，`value` = 界面文案；顺序即渲染顺序。 */
    val ORDERS: List<Pair<String, String>> = listOf(
        "top" to "推荐",
        "add" to "最新创建",
        "update" to "最近更新",
        "picCount" to "图片数量",
        "hits" to "总热度",
        "hitsDay" to "一天热度"
    )

    /** 合法排序键集合（宿主切换入口的边界校验）。 */
    val ORDER_KEYS: Set<String> = ORDERS.map { it.first }.toSet()
}

/**
 * 相册分类选项（`GET api/zone/categories?itemType=album` 映射后的 UI 只读快照）。
 *
 * 分类 id 与手办分类树**不同域**（实测相册分类 id 为 60100/60200/61000/61100/60900），
 * 只在**单专区**作用域可用：原版 `ZoneDetailSortablePageFragment.m()` 仅在
 * `zoneIds.toLongOrNull() != null`（单专区）时请求分类选项，聚合态无分类入口。
 */
data class AlbumCategoryOption(
    val id: Int,
    val name: String
)
