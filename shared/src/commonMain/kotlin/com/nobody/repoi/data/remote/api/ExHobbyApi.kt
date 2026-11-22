package com.nobody.repoi.data.remote.api

import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.data.remote.plugin.decodeRefAware
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.serialization.json.Json

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
