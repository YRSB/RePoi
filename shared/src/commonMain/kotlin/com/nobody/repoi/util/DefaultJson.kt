package com.nobody.repoi.util

import kotlinx.serialization.json.Json

/**
 * 全项目共享的反序列化默认值（`other_info` 等二次编码 JSON、表情与表情图路径解析共用）。
 *
 * 三元组是刻意放宽的容错策略：`ignoreUnknownKeys` 适配服务端字段增删，`isLenient` 接受历史接口的
 * 非严格 JSON，`coerceInputValues` 把显式 `null` 收敛为属性默认值。
 *
 * 代价是畸形报文可能被静默洗成默认对象而不是解析失败：调用方若必须区分「解析失败」，
 * 应使用该处专用的 `Json` 实例，禁止收窄本全局策略（会波及所有模型与表情解析）。
 */
internal val DefaultJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}
