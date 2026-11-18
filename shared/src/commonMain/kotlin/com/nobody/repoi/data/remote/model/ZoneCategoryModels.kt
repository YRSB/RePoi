package com.nobody.repoi.data.remote.model

import kotlinx.serialization.Serializable

/**
 * 专区板块分类选项（`GET api/zone/categories?nodeId=&itemType=`）。
 *
 * 来源证据：反编译 `net.hpoi.ui.zone.ZoneDetailSortablePageFragment.m()` 以
 * `zone/nodeId + itemType=album` 请求 `net.hpoi.web.g.A`（`api/zone/categories`），
 * 读取 `data.categories[].categoryId/name` 构造分类菜单；`categoryId=0` 表示「全部」，
 * 左侧选项少于 2 个时原版不展示分类入口。
 *
 * 线上匿名实测（2026-11-15）：`nodeId=33476094` 返回 5 项
 * （60100 手办模型周边 / 60200 GK/DIY / 61000 作品角色 / 61100 Cosplay / 60900 其他分享），
 * 与手办分类树（100/200/…）**不同域**，不能复用 `CategoryManager`。
 */
@Serializable
data class ZoneCategoriesPayload(
    val visible: Boolean = true,
    val categories: List<ZoneCategoryItem>? = null
)

/** 单个专区分类选项；`name` 为空或 `categoryId <= 0` 的脏数据由调用方丢弃。 */
@Serializable
data class ZoneCategoryItem(
    val categoryId: Int = 0,
    val name: String? = null
)
