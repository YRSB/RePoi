package com.nobody.repoi.data.remote.api

import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.data.remote.plugin.decodeRefAware
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.serialization.json.Json

/**
 * ExHobby 扩展图库端点（`ExHobbyApiService` 的 Ktor 等价实现）。
 *
 * 返回裸 JSON 数组（无 envelope），`type` 固定 "all"。
 * 独立 client：Cookie `is18=yes` + UA `okhttp/4.9.0`（见 networkModule）。
 */
class ExHobbyApi(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun getPictureList(
        id: Long,
        page: Int,
        item: Long,
        type: String = "all",
    ): List<ExHobbyImageItem> = client.post("picture/$id") {
        parameter("page", page)
        parameter("item", item)
        parameter("type", type)
    }.decodeRefAware(json)
}
