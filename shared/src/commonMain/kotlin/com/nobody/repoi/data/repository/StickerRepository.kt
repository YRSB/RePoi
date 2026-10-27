package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.StickerPackData

class StickerRepository constructor(
    private val apiService: HpoiApi
) {
    private var cachedPacks: List<StickerPackData>? = null
    private var cachedBuyTime: String? = null

    suspend fun getCommentPacks(): Result<List<StickerPackData>> = runCatching {
        val buyTimeResponse = apiService.getStickerBuyTime()
        val serverBuyTime = if (buyTimeResponse.isSuccess) buyTimeResponse.data?.buyTime else null
        val cached = cachedPacks
        if (!cached.isNullOrEmpty() && serverBuyTime != null && serverBuyTime == cachedBuyTime) {
            return@runCatching cached
        }
        val packsResponse = apiService.getStickerPacks()
        if (!packsResponse.isSuccess) {
            throw ApiException(packsResponse.code, packsResponse.msg ?: "表情包加载失败")
        }
        val packs = packsResponse.data?.list.orEmpty()
            .filter { it.applyLocation <= 0 || it.applyLocation == 1 }
        cachedPacks = packs
        cachedBuyTime = packsResponse.data?.buyTime ?: serverBuyTime
        packs
    }
}
