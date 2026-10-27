package com.nobody.repoi.data.remote.api

import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.network.bodyAsJsonRef
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import kotlinx.serialization.json.Json

/**
 * ExHobby 扩展图库 Ktor client（由 Retrofit `ExHobbyApiService` 翻译，1 方法）。
 *
 * 注意：此端点是 POST + `@Query` 形态（query 进 URL，空 body）；
 * 挂在 `exhobby` 独立 client 上（Cookie + UA 头，无公共参数插件）。
 * 返回原始图片列表（非 `CommonResponse` 包装），仍经 `bodyAsJsonRef` 解码以兼容 `$ref`。
 */
class ExHobbyApi(
    private val client: HttpClient,
    private val jsonCodec: Json
) {
    /**
     * 分页拉取条目扩展图片列表。
     *
     * @param id 条目 ID (手办 itemId 或相册 albumId)
     * @param page 分页页码，从 1 开始
     * @param item 条目 ID (与 id 一致)
     * @param type 类型，固定为 "all"
     * @return 图片项列表（翻页至末尾时返回空列表）
     */
    suspend fun getPictureList(
        id: Long,
        page: Int,
        item: Long,
        type: String = "all"
    ): List<ExHobbyImageItem> = client.post("picture/${id}") {
        url {
            parameters.append("page", page.toString())
            parameters.append("item", item.toString())
            parameters.append("type", type)
        }
    }.bodyAsJsonRef(jsonCodec)
}
